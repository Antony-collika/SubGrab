package com.subgrab.app.data

import com.subgrab.app.domain.FailureType
import com.subgrab.app.domain.RequestLane
import com.subgrab.app.domain.RequestOperation
import com.subgrab.app.domain.RequestResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.random.Random
import org.schabi.newpipe.extractor.downloader.Response

data class PacedHttpResult<T>(val value: T, val status: Int, val errorBody: String = "")

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
                val pacedResponse = value as? PacedHttpResult<*>
                val status = response?.responseCode() ?: pacedResponse?.status
                val errorBody = response?.responseBody().orEmpty().ifBlank { pacedResponse?.errorBody.orEmpty() }
                val success = status == null || status in 200..299
                val failureType = if (success) null else FailureClassifier.classify(
                    status,
                    HttpFailure(status ?: 0, errorBody)
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
                if (!success) {
                    if (retryable != null && attempt < maxAttempts(retryable)) {
                        logRetry(operation, attempt, retryable)
                        delay(RETRY_BASE_DELAY_MS * attempt)
                        attempt++
                        continue
                    }
                    throw RecordedHttpFailure(status ?: 0, errorBody)
                }
                return value
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (t is RecordedHttpFailure) throw t
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
                if (retryable != null && attempt < maxAttempts(retryable)) {
                    logRetry(operation, attempt, retryable)
                    delay(RETRY_BASE_DELAY_MS * attempt)
                    attempt++
                    continue
                }
                throw t
            }
        }
    }

    suspend fun estimatedDelayMs(lane: RequestLane): Long {
        val s = settings.current()
        val api = lane == RequestLane.DISCOVERY_API || lane == RequestLane.API_METADATA
        val subtitle = lane == RequestLane.SUBTITLE_EXTRACTOR
        val base = when {
            api -> s.apiBaseDelayMs
            subtitle -> s.subtitleBaseDelayMs
            else -> 0L
        }
        val mode = when {
            api -> s.apiDelayMode
            subtitle -> s.subtitleDelayMode
            else -> "NONE"
        }
        val min = when {
            api -> s.apiJitterMinMs
            subtitle -> s.subtitleJitterMinMs
            else -> 0L
        }
        val max = when {
            api -> s.apiJitterMaxMs
            subtitle -> s.subtitleJitterMaxMs
            else -> 0L
        }
        val jitter = if (mode == "AUTO" && max >= min && max > 0L) {
            Random.nextLong(min, max + 1L)
        } else {
            0L
        }
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

    suspend fun logConfigurationError(operation: RequestOperation, message: String) {
        database.runtimeLogDao().insert(
            RuntimeLogEntity(
                timestamp = System.currentTimeMillis(),
                level = "ERROR",
                category = "FAILURE",
                lane = operation.lane.name,
                operation = operation.operation,
                message = message.take(500)
            )
        )
    }

    private fun maxAttempts(failure: FailureType): Int = when (failure) {
        // Rate-limit/access signals must not trigger a retry loop. One retry is enough
        // after the Governor has already entered slowdown.
        FailureType.HTTP_429,
        FailureType.HTTP_403,
        FailureType.BOT_DETECTION,
        FailureType.ACCESS_DENIED -> RESTRICTION_MAX_ATTEMPTS
        else -> MAX_ATTEMPTS
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
        const val RESTRICTION_MAX_ATTEMPTS = 2
        const val RETRY_BASE_DELAY_MS = 1_000L
    }
}

open class HttpFailure(
    val status: Int,
    val body: String = ""
) : RuntimeException("HTTP " + status)

private class RecordedHttpFailure(status: Int, body: String) : HttpFailure(status, body)
