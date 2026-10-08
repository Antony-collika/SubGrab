package com.subgrab.app.data

import com.subgrab.app.domain.DownloadConfig
import com.subgrab.app.domain.DownloadReasons
import com.subgrab.app.domain.OutputFormat
import com.subgrab.app.domain.RequestLane
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.VideoDownloadResult
import com.subgrab.app.domain.VideoItem
import com.subgrab.app.domain.VideoOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

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
        val totalTasks: Int = 1,
        val failed: Int = 0
    ) : DownloadState
    data class Paused(
        val current: Int,
        val total: Int,
        val logs: List<String>,
        val etaSeconds: Long? = null,
        val taskIndex: Int = 1,
        val totalTasks: Int = 1,
        val saved: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0
    ) : DownloadState
    data class Done(
        val saved: Int,
        val skipped: Int,
        val logs: List<String>,
        val outputRelativePath: String? = null,
        val taskIndex: Int = 1,
        val totalTasks: Int = 1,
        val failed: Int = 0,
        val historyId: String? = null
    ) : DownloadState
    data class Cancelled(
        val saved: Int,
        val logs: List<String>,
        val taskIndex: Int = 1,
        val totalTasks: Int = 1,
        val skipped: Int = 0,
        val failed: Int = 0,
        val historyId: String? = null
    ) : DownloadState
    data class Error(
        val saved: Int,
        val skipped: Int,
        val message: String,
        val logs: List<String>,
        val taskIndex: Int = 1,
        val totalTasks: Int = 1,
        val failed: Int = 0,
        val historyId: String? = null
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

    private data class Progress(val saved: Int, val skipped: Int, val failed: Int, val logs: List<String>)

    /**
     * @param onVideoSaved được gọi đúng lúc một video lưu phụ đề thành công
     *        (dùng để ghi nhận "đã tải phụ đề" chính xác).
     */
    suspend fun start(
        source: Source,
        videos: List<VideoItem>,
        folderName: String,
        config: DownloadConfig,
        onVideoSaved: suspend (VideoItem) -> Unit = {},
        onState: suspend (DownloadState) -> Unit = {}
    ) {
        paused = false
        cancelled = false
        val selected = videos.filter { it.isSelected }
        val dir = storage.createTaskDirectory(folderName, config.outputDir)
        val historyId = UUID.randomUUID().toString()
        val logs = mutableListOf<String>()
        val results = LinkedHashMap<String, VideoDownloadResult>()
        val mutex = Mutex()
        val concurrency = config.subtitleConcurrency.coerceIn(1, selected.size.coerceAtLeast(1))
        val startedAt = System.currentTimeMillis()
        var saved = 0
        var skipped = 0
        var failed = 0
        var completed = 0

        suspend fun publish(state: DownloadState) {
            _state.value = state
            onState(state)
        }

        suspend fun log(message: String) {
            mutex.withLock { logs += message }
        }

        suspend fun progress(): Progress =
            mutex.withLock { Progress(saved, skipped, failed, logs.toList()) }

        suspend fun finalResults(notRunReason: String): List<VideoDownloadResult> =
            mutex.withLock {
                selected.map { video ->
                    results[video.videoId] ?: VideoDownloadResult(
                        video.videoId, video.title, VideoOutcome.NOT_RUN, notRunReason
                    )
                }
            }

        suspend fun markCompleted(
            video: VideoItem,
            outcome: VideoOutcome,
            reason: String?,
            filesSaved: Int = 0
        ): Long? {
            return mutex.withLock {
                saved += filesSaved
                when (outcome) {
                    VideoOutcome.SKIPPED -> skipped++
                    VideoOutcome.FAILED -> failed++
                    else -> Unit
                }
                results[video.videoId] = VideoDownloadResult(video.videoId, video.title, outcome, reason)
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

        suspend fun publishRunning(title: String, eta: Long?) {
            val p = progress()
            publish(
                DownloadState.Running(
                    completed, selected.size, title, p.saved, p.skipped, p.logs, eta, failed = p.failed
                )
            )
        }

        suspend fun waitUntilRunnable(title: String): Boolean {
            while (!cancelled && !control.isCancelled() && (paused || control.isPaused())) {
                val p = progress()
                publish(
                    DownloadState.Paused(
                        completed, selected.size, p.logs, null,
                        saved = p.saved, skipped = p.skipped, failed = p.failed
                    )
                )
                delay(250)
            }
            if (cancelled || control.isCancelled()) return false
            val p = progress()
            publish(
                DownloadState.Running(
                    completed, selected.size, title, p.saved, p.skipped, p.logs, null, failed = p.failed
                )
            )
            return true
        }

        suspend fun process(video: VideoItem) {
            if (!waitUntilRunnable(video.title)) return

            var videoToDownload = video
            val priorityLanguage = config.languages.firstOrNull().orEmpty()
            val roomSatisfied = priorityLanguage.isNotBlank() &&
                subtitleDownloader.canSatisfyFromRoom(video.videoId, priorityLanguage, config.formats.firstOrNull() ?: OutputFormat.TXT, config.preferManual)

            if (!roomSatisfied) {
                val subtitleResult = runCatching {
                    extractorClient.listSubtitles(video.videoUrl()).getOrThrow()
                }
                if (subtitleResult.isFailure) {
                    val error = subtitleResult.exceptionOrNull()
                    val failureType = (error as? SubtitleFailure)?.type
                        ?: FailureClassifier.classify((error as? HttpFailure)?.status, error)
                    log("❌ " + video.title + ": kiểm tra phụ đề thất bại: " + (error?.message ?: "không rõ lỗi"))
                    val outcome = if (FailureClassifier.benign(failureType)) VideoOutcome.SKIPPED else VideoOutcome.FAILED
                    val eta = markCompleted(video, outcome, DownloadReasons.forFailure(failureType, error?.message))
                    publishRunning(video.title, eta)
                    return
                }
                val subs = subtitleResult.getOrThrow()
                if (subs.isEmpty()) {
                    log("⚠️ " + video.title + ": không tìm thấy phụ đề")
                    // Video không có phụ đề luôn được tính là "bỏ qua", dù cài đặt có bật hay không,
                    // để tổng các ô trên màn hình tiến độ luôn khớp với số video đã xử lý.
                    val eta = markCompleted(
                        video, VideoOutcome.SKIPPED,
                        DownloadReasons.forFailure(com.subgrab.app.domain.FailureType.NO_SUBTITLE)
                    )
                    publishRunning(video.title, eta)
                    return
                }
                videoToDownload = video.copy(availableSubs = subs, subtitleChecked = true)
            }

            val downloadResult = runCatching {
                subtitleDownloader.download(videoToDownload, config, dir).getOrThrow()
            }
            if (downloadResult.isSuccess) {
                val files = downloadResult.getOrThrow()
                if (files.isEmpty()) {
                    log("⚠️ " + video.title + ": không có file nào được tạo")
                    val eta = markCompleted(video, VideoOutcome.SKIPPED, DownloadReasons.NO_FILE)
                    publishRunning(video.title, eta)
                    return
                }
                log("✅ " + video.title + ": " + files.size + " file")
                val eta = markCompleted(video, VideoOutcome.SAVED, null, files.size)
                runCatching { onVideoSaved(video) }
                publishRunning(video.title, eta)
            } else {
                val error = downloadResult.exceptionOrNull()
                val failureType = (error as? SubtitleFailure)?.type
                    ?: FailureClassifier.classify((error as? HttpFailure)?.status, error)
                log("❌ " + video.title + ": " + (error?.message ?: "tải phụ đề thất bại"))
                if (pacer.governorState(RequestLane.SUBTITLE_EXTRACTOR) == com.subgrab.app.domain.GovernorState.SLOWDOWN) {
                    log("⏳ YouTube đang giới hạn request — app đã chuyển sang giảm tốc độ")
                }
                val outcome = if (FailureClassifier.benign(failureType)) VideoOutcome.SKIPPED else VideoOutcome.FAILED
                val eta = markCompleted(video, outcome, DownloadReasons.forFailure(failureType, error?.message))
                publishRunning(video.title, eta)
            }
        }

        try {
            if (selected.isNotEmpty()) {
                // Trước đây: selected.map { async { ... } }.awaitAll() tạo TOÀN BỘ coroutine
                // (và giữ tham chiếu tới từng VideoItem) ngay từ đầu, dù chỉ `concurrency` video
                // được xử lý cùng lúc — hàng trăm video còn lại vẫn nằm sẵn trong bộ nhớ chờ tới lượt.
                // Bây giờ: flatMapMerge chỉ "mở" đúng tối đa `concurrency` video tại một thời điểm;
                // video kế tiếp chỉ được lấy ra và giữ trong bộ nhớ khi có 1 slot trống.
                // Hành vi với người dùng (thứ tự xử lý, tốc độ, log, kết quả) không đổi.
                selected.asFlow()
                    .flatMapMerge(concurrency = concurrency) { video ->
                        kotlinx.coroutines.flow.flow {
                            process(video)
                            emit(Unit)
                        }.flowOn(Dispatchers.IO)
                    }
                    .collect { }
            }

            if (cancelled || control.isCancelled()) {
                log("⏹ Tác vụ đã bị hủy")
                val p = progress()
                val finalResults = finalResults(DownloadReasons.CANCELLED)
                runCatching {
                    history.add(
                        DownloadHistoryEntry(
                            System.currentTimeMillis(), source.title, dir.name, p.saved, p.skipped,
                            id = historyId, sourceUrl = source.url, total = selected.size, status = "CANCELLED",
                            logs = p.logs, videoIds = selected.map { it.videoId },
                            failed = p.failed, results = finalResults
                        )
                    )
                }
                database.runtimeLogDao().insert(
                    RuntimeLogEntity(timestamp = System.currentTimeMillis(), level = "INFO", category = "TASK", lane = null, operation = null, message = "task cancelled")
                )
                publish(DownloadState.Cancelled(p.saved, p.logs, skipped = p.skipped, failed = p.failed, historyId = historyId))
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
            val p = progress()
            history.add(
                DownloadHistoryEntry(
                    System.currentTimeMillis(), source.title, dir.name, p.saved, p.skipped,
                    id = historyId, sourceUrl = source.url, total = selected.size, status = "DONE",
                    logs = p.logs, videoIds = selected.map { it.videoId },
                    failed = p.failed, results = finalResults(DownloadReasons.NOT_PROCESSED)
                )
            )
            publish(DownloadState.Done(p.saved, p.skipped, p.logs, relativePath, failed = p.failed, historyId = historyId))
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
            val p = progress()
            val errorResults = finalResults(DownloadReasons.aborted(message))
            runCatching {
                history.add(
                    DownloadHistoryEntry(
                        System.currentTimeMillis(), source.title, dir.name, p.saved, p.skipped,
                        id = historyId, sourceUrl = source.url, total = selected.size, status = "ERROR",
                        logs = p.logs, videoIds = selected.map { it.videoId },
                        failed = p.failed, results = errorResults
                    )
                )
            }
            publish(DownloadState.Error(p.saved, p.skipped, message, p.logs, failed = p.failed, historyId = historyId))
        }
    }

    fun pause() { paused = true }
    fun resume() { paused = false }
    fun cancel() { cancelled = true; paused = false }

    private fun VideoItem.videoUrl(): String = "https://www.youtube.com/watch?v=" + videoId
}
