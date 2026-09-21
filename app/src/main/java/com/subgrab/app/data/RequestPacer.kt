package com.subgrab.app.data

import com.subgrab.app.domain.RequestLane
import com.subgrab.app.domain.RequestOperation
import com.subgrab.app.domain.RequestResult
import kotlinx.coroutines.delay
import kotlin.random.Random
import org.schabi.newpipe.extractor.downloader.Response

class RequestPacer(
    private val settings: SettingsRepository,
    private val governor: RequestGovernor,
    private val database: SubGrabDatabase
) {
    suspend fun <T> execute(operation: RequestOperation, block: suspend () -> T): T {
        var attempt = 1
        while (true) {
            val result = executeAttempt(operation, block)
            if (result.retryableFailure == null || attempt >= MAX_ATTEMPTS) {
                return result.value
            }

            val retryMessage = "retry " + attempt + "/" + MAX_ATTEMPTS +
                " after " + RETRY_BASE_DELAY_MS * attempt + "ms: " +
                result.retryableFailure.name
            database.runtimeLogDao().insert(
                RuntimeLogEntity(
                    timestamp = System.currentTimeMillis(),
                    level = "INFO",
                    category = "RETRY",
                    lane = operation.lane.name,
                    operation = operation.operation,
                    message = retryMessage
                )
            )
            delay(RETRY_BASE_DELAY_MS * attempt)
            attempt++
        }
    }

    fun estimatedDelayMs(lane: RequestLane): Long {
        val s = settings.current()
        val api = lane == RequestLane.DISCOVERY_API || lane == RequestLane.API_METADATA
        val base = if (api) s.apiBaseDelayMs else s.subtitleBaseDelayMs
        val mode = if (api) s.apiDelayMode else s.subtitleDelayMode
        val min = if (api) s.apiJitterMinMs else s.subtitleJitterMinMs
        val max = if (api) s.apiJitterMaxMs else s.subtitleJitterMaxMs
        val jitter = if (mode == "AUTO" && max >= min) (min + max) / 2 else 0L
        return base + jitter + governor.delay(lane)
    }

    private suspend fun <T> executeAttempt(
        operation: RequestOperation,
        block: suspend () -> T
    ): AttemptResult<T> {
        val wait = estimatedDelayMs(operation.lane)
        if (wait > 0) delay(wait)

        database.runtimeLogDao().insert(
            RuntimeLogEntity(
                timestamp = System.currentTimeMillis(),
                level = "INFO",
                category = "REQUEST",
                lane = operation.lane.name,
                operation = operation.operation,
                message = "request start"
            )
        )

        val started = System.currentTimeMillis()
        return try {
            val value = block()
            val response = value as? Response
            val status = response?.responseCode()
            val success = status == null || status in 200..299
            val failureType = if (success) null else FailureClassifier.classify(
                status,
                HttpFailure(status ?: 0, response?.responseBody().orEmpty())
            )
            val requestResult = RequestResult(
                success = success,
                httpStatus = status,
                durationMs = System.currentTimeMillis() - started,
                failureType = failureType
            )
            persist(operation, requestResult)
            val oldDelay = governor.delay(operation.lane)
            if (governor.observe(operation.lane, requestResult)) {
                logGovernor(operation, oldDelay)
            }
            AttemptResult(value, retryableFailure(failureType))
        } catch (t: Throwable) {
            val status = (t as? HttpFailure)?.status
            val failureType = (t as? SubtitleFailure)?.type
                ?: FailureClassifier.classify(status, t)
            val requestResult = RequestResult(
                success = false,
                httpStatus = status,
                durationMs = System.currentTimeMillis() - started,
                failureType = failureType
            )
            persist(operation, requestResult)
            val oldDelay = governor.delay(operation.lane)
            if (governor.observe(operation.lane, requestResult)) {
                logGovernor(operation, oldDelay)
            }
            val retryable = retryableFailure(failureType)
            if (retryable != null) {
                AttemptResultFailure<T>(t, retryable).throwIt()
            }
            throw t
        }
    }

    private fun retryableFailure(type: com.subgrab.app.domain.FailureType?): com.subgrab.app.domain.FailureType? {
        return type?.takeIf {
            it in setOf(
                com.subgrab.app.domain.FailureType.TIMEOUT,
                com.subgrab.app.domain.FailureType.CONNECTION_ERROR,
                com.subgrab.app.domain.FailureType.SERVER_ERROR,
                com.subgrab.app.domain.FailureType.HTTP_403,
                com.subgrab.app.domain.FailureType.HTTP_429,
                com.subgrab.app.domain.FailureType.BOT_DETECTION,
                com.subgrab.app.domain.FailureType.ACCESS_DENIED,
                com.subgrab.app.domain.FailureType.PARSE_ERROR
            )
        }
    }

    private suspend fun logGovernor(op: RequestOperation, oldDelay: Long) {
        database.runtimeLogDao().insert(
            RuntimeLogEntity(
                timestamp = System.currentTimeMillis(),
                level = "INFO",
                category = "GOVERNOR",
                lane = op.lane.name,
                operation = op.operation,
                message = "state=" + governor.state(op.lane) +
                    " delay=" + governor.delay(op.lane) +
                    " previousDelay=" + oldDelay
            )
        )
    }

    private suspend fun persist(op: RequestOperation, result: RequestResult) {
        database.requestMetricDao().insert(
            RequestMetricEntity(
                timestamp = System.currentTimeMillis(),
                lane = op.lane.name,
                operation = op.operation,
                durationMs = result.durationMs,
                httpStatus = result.httpStatus,
                success = result.success,
                failureType = result.failureType?.name
            )
        )
        database.runtimeLogDao().insert(
            RuntimeLogEntity(
                timestamp = System.currentTimeMillis(),
                level = if (result.success) "INFO" else "ERROR",
                category = if (result.success) "REQUEST" else "FAILURE",
                lane = op.lane.name,
                operation = op.operation,
                message = "success=" + result.success +
                    " status=" + result.httpStatus +
                    " durationMs=" + result.durationMs +
                    " failure=" + result.failureType?.name
            )
        )
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        database.requestMetricDao().deleteOlderThan(cutoff)
        database.runtimeLogDao().deleteOlderThan(cutoff)
    }

    private data class AttemptResult<T>(
        val value: T,
        val retryableFailure: com.subgrab.app.domain.FailureType?
    )

    private class AttemptResultFailure<T>(
        private val throwable: Throwable,
        val failure: com.subgrab.app.domain.FailureType
    ) {
        fun throwIt(): Nothing = throw throwable
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_BASE_DELAY_MS = 1_000L
    }
}

class HttpFailure(
    val status: Int,
    val body: String = ""
) : RuntimeException("HTTP $status")
