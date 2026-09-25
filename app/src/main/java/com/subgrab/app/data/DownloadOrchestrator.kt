package com.subgrab.app.data

import com.subgrab.app.domain.DownloadConfig
import com.subgrab.app.domain.RequestLane
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
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
        val etaSeconds: Long? = null,
        val taskIndex: Int = 1,
        val totalTasks: Int = 1
    ) : DownloadState
    data class Paused(
        val current: Int,
        val total: Int,
        val logs: List<String>,
        val etaSeconds: Long? = null,
        val taskIndex: Int = 1,
        val totalTasks: Int = 1
    ) : DownloadState
    data class Done(val saved: Int, val skipped: Int, val logs: List<String>, val outputRelativePath: String? = null, val taskIndex: Int = 1, val totalTasks: Int = 1) : DownloadState
    data class Cancelled(val saved: Int, val logs: List<String>, val taskIndex: Int = 1, val totalTasks: Int = 1) : DownloadState
    data class Error(
        val saved: Int,
        val skipped: Int,
        val message: String,
        val logs: List<String>,
        val taskIndex: Int = 1,
        val totalTasks: Int = 1
    ) : DownloadState
}

class DownloadOrchestrator(
    private val extractorClient: NewPipeExtractorClient,
    private val subtitleDownloader: SubtitleDownloader,
    private val storage: FileStorage,
    private val history: HistoryRepository,
    private val control: DownloadControlStore,
    private val database: SubGrabDatabase,
    private val pacer: RequestPacer
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
                val elapsedMs = System.currentTimeMillis() - startedAt
                val remaining = (selected.size - completed).coerceAtLeast(0)
                if (completed > 0 && remaining > 0) {
                    // The observed wall-clock rate already includes pacing, retries and actual
                    // subtitle/extractor latency. Do not add a second synthetic pacing term.
                    // Round up so a non-zero remaining workload never displays 0 seconds.
                    kotlin.math.ceil(
                        (elapsedMs.toDouble() / completed) * remaining / 1000.0
                    ).toLong().coerceAtLeast(1)
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

        suspend fun process(video: VideoItem) {
            if (!waitUntilRunnable(video.title)) return

            var videoToDownload = video
            if (!video.subtitleChecked) {
                val subtitleResult = runCatching {
                    extractorClient.listSubtitles(video.videoUrl()).getOrThrow()
                }
                if (subtitleResult.isFailure) {
                    val error = subtitleResult.exceptionOrNull()
                    val failureType = (error as? SubtitleFailure)?.type
                        ?: FailureClassifier.classify((error as? HttpFailure)?.status, error)
                    val message = "❌ " + video.title + ": kiểm tra phụ đề thất bại: " + (error?.message ?: "không rõ lỗi")
                    log(message)
                    val eta = markCompleted(0, FailureClassifier.benign(failureType))
                    val (savedNow, skippedNow, logSnapshot) = progress()
                    publish(DownloadState.Running(completed, selected.size, video.title, savedNow, skippedNow, logSnapshot, eta))
                    return
                }
                val subs = subtitleResult.getOrThrow()
                if (subs.isEmpty()) {
                    log("⚠️ " + video.title + ": không tìm thấy phụ đề")
                    val eta = markCompleted(0, config.skipNoSub)
                    val (savedNow, skippedNow, logSnapshot) = progress()
                    publish(DownloadState.Running(completed, selected.size, video.title, savedNow, skippedNow, logSnapshot, eta))
                    return
                }
                videoToDownload = video.copy(availableSubs = subs, subtitleChecked = true)
            } else if (!video.hasSub) {
                log("⚠️ " + video.title + ": không có phụ đề")
                val eta = markCompleted(0, config.skipNoSub)
                val (savedNow, skippedNow, logSnapshot) = progress()
                publish(DownloadState.Running(completed, selected.size, video.title, savedNow, skippedNow, logSnapshot, eta))
                return
            }

            val downloadResult = runCatching {
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
                val failureType = (error as? SubtitleFailure)?.type
                    ?: FailureClassifier.classify((error as? HttpFailure)?.status, error)
                val message = "❌ " + video.title + ": " + (error?.message ?: "tải phụ đề thất bại")
                log(message)
                if (pacer.governorState(RequestLane.SUBTITLE_EXTRACTOR) == com.subgrab.app.domain.GovernorState.SLOWDOWN) {
                    log("⏳ YouTube đang giới hạn request — app đã chuyển sang giảm tốc độ")
                }
                val eta = markCompleted(0, FailureClassifier.benign(failureType))
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
                database.runtimeLogDao().insert(
                    RuntimeLogEntity(timestamp = System.currentTimeMillis(), level = "INFO", category = "TASK", lane = null, operation = null, message = "task cancelled")
                )
                publish(DownloadState.Cancelled(savedNow, finalLogs))
                return
            }

            val basePath = config.outputDir.removePrefix("Download/").removePrefix("Download\\").ifBlank { "Subtitles" }
            val relativePath = if (basePath == "Subtitles" && config.outputDir.equals("Download", ignoreCase = true)) {
                dir.name
            } else {
                basePath + "/" + dir.name
            }
            val published = storage.publishToDownloads(dir, relativePath)
            log("📁 Đã xuất " + published.size + " file vào Download/" + relativePath)
            val (savedNow, skippedNow, finalLogs) = progress()
            history.add(
                DownloadHistoryEntry(
                    System.currentTimeMillis(), source.title, dir.name, savedNow, skippedNow,
                    sourceUrl = source.url, total = selected.size, status = "DONE", logs = finalLogs
                )
            )
            publish(DownloadState.Done(savedNow, skippedNow, finalLogs, relativePath))
        } catch (error: Throwable) {
            if (error is CancellationException) {
                database.runtimeLogDao().insert(
                    RuntimeLogEntity(
                        timestamp = System.currentTimeMillis(),
                        level = "INFO",
                        category = "TASK",
                        lane = null,
                        operation = null,
                        message = "task cancellation requested"
                    )
                )
                throw error
            }
            val (savedNow, skippedNow, _) = progress()
            val message = error.message ?: error::class.java.simpleName
            log("❌ Tác vụ thất bại: " + message)
            database.runtimeLogDao().insert(
                RuntimeLogEntity(
                    timestamp = System.currentTimeMillis(),
                    level = "ERROR",
                    category = if (error is StorageFailure) "STORAGE" else "TASK",
                    lane = null,
                    operation = null,
                    message = message.take(500)
                )
            )
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
}
