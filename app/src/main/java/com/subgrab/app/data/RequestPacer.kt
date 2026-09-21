package com.subgrab.app.data

import com.subgrab.app.domain.FailureType
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
            val wait = estimatedDelayMs(operation.lane)
            if (wait > 0) delay(wait)

            database.runtimeLogDao().insert(
                RuntimeLogEntity(
                    timestamp = System.currentTimeMillis(),
                    level = "INFO",
                    category = "REQUEST",
                    lane = operation.lane.name,
                    operation = operation.operation,
                    message = "request start attempt=" + attempt
                )
            )

            val started = System.currentTimeMillis()
            try {
                val value = block()
                val response = value as? Response
                val status = response?.responseCode()
                val success = status == null || status in 200..299
                val failureType = if (success) null else FailureClassifier.classify(
                    status,
                    HttpFailure(status ?: 0, response?.responseBody().orEmpty())
                )
                val result = RequestResult(
                    success = success,
                    httpStatus = status,
                    durationMs = System.currentTimeMillis() - started,
                    failureType = failureType
                )
                persist(operation, result)
                val oldDelay = governor.delay(operation.lane)
                if (governor.observe(operation.lane, result)) logGovernor(operation, oldDelay)

                val retryable = retryableFailure(failureType)
                if (!success && retryable != null && attempt < MAX_ATTEMPTS) {
                    logRetry(operation, attempt, retryable)
                    delay(RETRY_BASE_DELAY_MS * attempt)
                    attempt++
                    continue
                }
                return value
            } catch (t: Throwable) {
                val status = (t as? HttpFailure)?.status
                val failureType = (t as? SubtitleFailure)?.type
                    ?: FailureClassifier.classify(status, t)
                val result = RequestResult(
                    success = false,
                    httpStatus = status,
                    durationMs = System.currentTimeMillis() - started,
                    failureType = failureType
                )
                persist(operation, result)
                val oldDelay = governor.delay(operation.lane)
                if (governor.observe(operation.lane, result)) logGovernor(operation, oldDelay)

                val retryable = retryableFailure(failureType)
                if (retryable != null && attempt < MAX_ATTEMPTS) {
                    logRetry(operation, attempt, retryable)
                    delay(RETRY_BASE_DELAY_MS * attempt)
                    attempt++
                    continue
                }
                throw t
            }
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

    private fun retryableFailure(type: FailureType?): FailureType? = type?.takeIf {
        it in setOf(
            FailureType.TIMEOUT,
            FailureType.CONNECTION_ERROR,
            FailureType.SERVER_ERROR,
            FailureType.HTTP_403,
            FailureType.HTTP_429,
            FailureType.BOT_DETECTION,
            FailureType.ACCESS_DENIED,
            FailureType.PARSE_ERROR
        )
    }

    private suspend fun logRetry(operation: RequestOperation, attempt: Int, failure: FailureType) {
        database.runtimeLogDao().insert(
            RuntimeLogEntity(
                timestamp = System.currentTimeMillis(),
                level = "INFO",
                category = "RETRY",
                lane = operation.lane.name,
                operation = operation.operation,
                message = "retry " + attempt + "/" + MAX_ATTEMPTS +
                    " after " + RETRY_BASE_DELAY_MS * attempt + "ms failure=" + failure.name
            )
        )
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

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_BASE_DELAY_MS = 1_000L
    }
}

class HttpFailure(
    val status: Int,
    val body: String = ""
) : RuntimeException("HTTP " + status)
