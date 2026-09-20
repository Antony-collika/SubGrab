package com.subgrab.app.data

import com.subgrab.app.domain.DownloadConfig
import com.subgrab.app.domain.FailureType
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(
        val current: Int,
        val total: Int,
        val title: String,
        val saved: Int,
        val skipped: Int,
        val logs: List<String>,
        val etaSeconds: Long? = null
    ) : DownloadState
    data class Paused(
        val current: Int,
        val total: Int,
        val logs: List<String>,
        val etaSeconds: Long? = null
    ) : DownloadState
    data class Done(val saved: Int, val skipped: Int, val logs: List<String>) : DownloadState
    data class Cancelled(val saved: Int, val logs: List<String>) : DownloadState
    data class Error(
        val saved: Int,
        val skipped: Int,
        val message: String,
        val logs: List<String>
    ) : DownloadState
}

class DownloadOrchestrator(
    private val extractorClient: NewPipeExtractorClient,
    private val subtitleDownloader: SubtitleDownloader,
    private val storage: FileStorage,
    private val history: HistoryRepository,
    private val control: DownloadControlStore,
    private val database: SubGrabDatabase
) {
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    @Volatile private var paused = false
    @Volatile private var cancelled = false

    suspend fun start(
        source: Source,
        videos: List<VideoItem>,
        folderName: String,
        config: DownloadConfig,
        onState: suspend (DownloadState) -> Unit = {}
    ) {
        paused = false
        cancelled = false
        val selected = videos.filter { it.isSelected }.take(50)
        val dir = storage.createTaskDirectory(folderName, config.outputDir)
        val logs = mutableListOf<String>()
        val mutex = Mutex()
        val concurrency = config.subtitleConcurrency.coerceIn(1, selected.size.coerceAtLeast(1))
        val semaphore = Semaphore(concurrency)
        val startedAt = System.currentTimeMillis()
        var saved = 0
        var skipped = 0
        var completed = 0

        suspend fun publish(state: DownloadState) {
            _state.value = state
            onState(state)
        }

        suspend fun log(message: String) {
            mutex.withLock { logs += message }
        }

        suspend fun progress(): Triple<Int, Int, List<String>> =
            mutex.withLock { Triple(saved, skipped, logs.toList()) }

        suspend fun markCompleted(filesSaved: Int, wasSkipped: Boolean): Long? {
            return mutex.withLock {
                saved += filesSaved
                if (wasSkipped) skipped++
                completed++
                val elapsed = System.currentTimeMillis() - startedAt
                val remaining = (selected.size - completed).coerceAtLeast(0)
                if (completed > 0 && remaining > 0) {
                    (elapsed * remaining / completed).coerceAtLeast(0)
                } else null
            }
        }

        suspend fun waitUntilRunnable(title: String): Boolean {
            while (!cancelled && !control.isCancelled() && (paused || control.isPaused())) {
                val (_, _, logSnapshot) = progress()
                publish(DownloadState.Paused(completed, selected.size, logSnapshot, null))
                delay(250)
            }
            if (cancelled || control.isCancelled()) return false
            val (savedNow, skippedNow, logSnapshot) = progress()
            publish(DownloadState.Running(completed, selected.size, title, savedNow, skippedNow, logSnapshot, null))
            return true
        }

        fun retryable(error: Throwable): Boolean {
            val failureType = (error as? SubtitleFailure)?.type
                ?: (error as? HttpFailure)?.let { FailureClassifier.classify(it.status, it) }
                ?: FailureClassifier.classify(null, error)
            return failureType in setOf(
                FailureType.TIMEOUT, FailureType.CONNECTION_ERROR, FailureType.SERVER_ERROR,
                FailureType.HTTP_403, FailureType.HTTP_429, FailureType.BOT_DETECTION,
                FailureType.ACCESS_DENIED, FailureType.PARSE_ERROR
            )
        }

        suspend fun <T> retry(label: String, block: suspend () -> T): Result<T> {
            var attempt = 1
            while (true) {
                val result = runCatching { block() }
                if (result.isSuccess || attempt >= MAX_ATTEMPTS || cancelled || control.isCancelled()) return result
                val error = result.exceptionOrNull() ?: return result
                if (!retryable(error)) return result
                val delayMs = RETRY_BASE_DELAY_MS * attempt
                val retryMessage = "↻ " + label + ": thử lại lần " + (attempt + 1) + "/" + MAX_ATTEMPTS + " sau " + delayMs + "ms"
                log(retryMessage)
                database.runtimeLogDao().insert(RuntimeLogEntity(
                    timestamp = System.currentTimeMillis(),
                    level = "INFO",
                    category = "RETRY",
                    lane = "SUBTITLE_EXTRACTOR",
                    operation = label.take(120),
                    message = retryMessage
                ))
                delay(delayMs)
                attempt++
            }
        }

        suspend fun process(video: VideoItem) {
            if (!waitUntilRunnable(video.title)) return

            var videoToDownload = video
            if (!video.subtitleChecked) {
                val subtitleResult = retry("kiểm tra phụ đề " + video.title) {
                    extractorClient.listSubtitles(video.videoUrl()).getOrThrow()
                }
                if (subtitleResult.isFailure) {
                    val error = subtitleResult.exceptionOrNull()
                    val message = "❌ " + video.title + ": kiểm tra phụ đề thất bại: " + (error?.message ?: "không rõ lỗi")
                    log(message)
                    database.runtimeLogDao().insert(RuntimeLogEntity(System.currentTimeMillis(), "ERROR", "FAILURE", "SUBTITLE_EXTRACTOR", "subtitle.list", message.take(500)))
                    val eta = markCompleted(0, true)
                    val (savedNow, skippedNow, logSnapshot) = progress()
                    publish(DownloadState.Running(completed, selected.size, video.title, savedNow, skippedNow, logSnapshot, eta))
                    return
                }
                val subs = subtitleResult.getOrThrow()
                if (subs.isEmpty()) {
                    log("⚠️ " + video.title + ": không tìm thấy phụ đề")
                    val eta = markCompleted(0, true)
                    val (savedNow, skippedNow, logSnapshot) = progress()
                    publish(DownloadState.Running(completed, selected.size, video.title, savedNow, skippedNow, logSnapshot, eta))
                    return
                }
                videoToDownload = video.copy(availableSubs = subs, subtitleChecked = true)
            } else if (!video.hasSub) {
                log("⚠️ " + video.title + ": không có phụ đề")
                val eta = markCompleted(0, true)
                val (savedNow, skippedNow, logSnapshot) = progress()
                publish(DownloadState.Running(completed, selected.size, video.title, savedNow, skippedNow, logSnapshot, eta))
                return
            }

            val downloadResult = retry("tải phụ đề " + video.title) {
                subtitleDownloader.download(videoToDownload, config, dir).getOrThrow()
            }
            if (downloadResult.isSuccess) {
                val files = downloadResult.getOrThrow()
                log("✅ " + video.title + ": " + files.size + " file")
                val eta = markCompleted(files.size, false)
                val (savedNow, skippedNow, logSnapshot) = progress()
                publish(DownloadState.Running(completed, selected.size, video.title, savedNow, skippedNow, logSnapshot, eta))
            } else {
                val error = downloadResult.exceptionOrNull()
                val message = "❌ " + video.title + ": " + (error?.message ?: "tải phụ đề thất bại")
                log(message)
                database.runtimeLogDao().insert(RuntimeLogEntity(System.currentTimeMillis(), "ERROR", "FAILURE", "SUBTITLE_EXTRACTOR", "subtitle.download", message.take(500)))
                val eta = markCompleted(0, true)
                val (savedNow, skippedNow, logSnapshot) = progress()
                publish(DownloadState.Running(completed, selected.size, video.title, savedNow, skippedNow, logSnapshot, eta))
            }
        }

        try {
            if (selected.isNotEmpty()) {
                coroutineScope {
                    selected.map { video ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit { process(video) }
                        }
                    }.awaitAll()
                }
            }

            if (cancelled || control.isCancelled()) {
                val (savedNow, skippedNow, _) = progress()
                log("⏹ Tác vụ đã bị hủy")
                val finalLogs = progress().third
                runCatching {
                    history.add(
                        DownloadHistoryEntry(
                            System.currentTimeMillis(), source.title, dir.name, savedNow, skippedNow,
                            sourceUrl = source.url, total = selected.size, status = "CANCELLED", logs = finalLogs
                        )
                    )
                }
                publish(DownloadState.Cancelled(savedNow, finalLogs))
                return
            }

            val basePath = config.outputDir.removePrefix("Download/").removePrefix("Download\\").ifBlank { "Subtitles" }
            val relativePath = basePath + "/" + dir.name
            val published = storage.publishToDownloads(dir, relativePath)
            log("📁 Đã xuất " + published.size + " file vào Download/" + relativePath)
            val (savedNow, skippedNow, finalLogs) = progress()
            history.add(
                DownloadHistoryEntry(
                    System.currentTimeMillis(), source.title, dir.name, savedNow, skippedNow,
                    sourceUrl = source.url, total = selected.size, status = "DONE", logs = finalLogs
                )
            )
            publish(DownloadState.Done(savedNow, skippedNow, finalLogs))
        } catch (error: Throwable) {
            val (savedNow, skippedNow, _) = progress()
            val message = error.message ?: error::class.java.simpleName
            log("❌ Tác vụ thất bại: " + message)
            database.runtimeLogDao().insert(RuntimeLogEntity(
                timestamp = System.currentTimeMillis(),
                level = "ERROR",
                category = if (error is StorageFailure) "STORAGE" else "TASK",
                lane = null,
                operation = null,
                message = message.take(500)
            ))
            val errorLogs = progress().third
            runCatching {
                history.add(
                    DownloadHistoryEntry(
                        System.currentTimeMillis(), source.title, dir.name, savedNow, skippedNow,
                        sourceUrl = source.url, total = selected.size, status = "ERROR", logs = errorLogs
                    )
                )
            }
            publish(DownloadState.Error(savedNow, skippedNow, message, errorLogs))
        }
    }

    fun pause() { paused = true }
    fun resume() { paused = false }
    fun cancel() { cancelled = true; paused = false }

    private fun VideoItem.videoUrl(): String = "https://www.youtube.com/watch?v=" + videoId

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_BASE_DELAY_MS = 1_000L
    }
}
