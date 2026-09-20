package com.subgrab.app.data
import com.subgrab.app.domain.*
import kotlinx.coroutines.delay
import kotlin.random.Random
import org.schabi.newpipe.extractor.downloader.Response

class RequestPacer(
    private val settings: SettingsRepository,
    private val governor: RequestGovernor,
    private val database: SubGrabDatabase
) {
    suspend fun <T> execute(operation: RequestOperation, block: suspend () -> T): T {
        val s = settings.current()
        val api = operation.lane == RequestLane.DISCOVERY_API ||
            operation.lane == RequestLane.API_METADATA
        val base = if (api) s.apiBaseDelayMs else s.subtitleBaseDelayMs
        val mode = if (api) s.apiDelayMode else s.subtitleDelayMode
        val min = if (api) s.apiJitterMinMs else s.subtitleJitterMinMs
        val max = if (api) s.apiJitterMaxMs else s.subtitleJitterMaxMs
        val userDelay = if (mode == "AUTO") {
            base + if (max >= min) Random.nextLong(min, max + 1) else 0
        } else 0
        val wait = userDelay + governor.delay(operation.lane)
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
        try {
            val value = block()
            val status = (value as? Response)?.responseCode()
            val success = status == null || status in 200..299
            val failureType = if (success) null else FailureClassifier.classify(status, null)
            val result = RequestResult(
                success = success,
                httpStatus = status,
                durationMs = System.currentTimeMillis() - started,
                failureType = failureType
            )
            persist(operation, result)
            val oldDelay = governor.delay(operation.lane)
            val changed = governor.observe(operation.lane, result)
            if (changed) logGovernor(operation, oldDelay)
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
            val changed = governor.observe(operation.lane, result)
            if (changed) logGovernor(operation, oldDelay)
            throw t
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

    private suspend fun persist(op: RequestOperation, r: RequestResult) {
        database.requestMetricDao().insert(
            RequestMetricEntity(
                timestamp = System.currentTimeMillis(),
                lane = op.lane.name,
                operation = op.operation,
                durationMs = r.durationMs,
                httpStatus = r.httpStatus,
                success = r.success,
                failureType = r.failureType?.name
            )
        )
        database.runtimeLogDao().insert(
            RuntimeLogEntity(
                timestamp = System.currentTimeMillis(),
                level = if (r.success) "INFO" else "ERROR",
                category = if (r.success) "REQUEST" else "FAILURE",
                lane = op.lane.name,
                operation = op.operation,
                message = "success=" + r.success +
                    " status=" + r.httpStatus +
                    " durationMs=" + r.durationMs +
                    " failure=" + r.failureType?.name
            )
        )
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        database.requestMetricDao().deleteOlderThan(cutoff)
        database.runtimeLogDao().deleteOlderThan(cutoff)
    }
}

class HttpFailure(
    val status: Int,
    val body: String = ""
) : RuntimeException("HTTP $status")
