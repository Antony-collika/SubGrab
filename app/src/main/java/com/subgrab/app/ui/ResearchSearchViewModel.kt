package com.subgrab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subgrab.app.data.ApiDiscoveryClient
import com.subgrab.app.data.FileStorage
import com.subgrab.app.data.HttpFailure
import com.subgrab.app.data.export.ResearchExport
import com.subgrab.app.data.repository.KnowledgeRepository
import com.subgrab.app.data.repository.ResearchRepository
import com.subgrab.app.domain.ResearchFilters
import com.subgrab.app.domain.LibraryMode
import com.subgrab.app.domain.LibraryScope
import com.subgrab.app.domain.ResearchSort
import com.subgrab.app.domain.SearchState
import com.subgrab.app.domain.VideoSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Trạng thái của một tác vụ hàng loạt (lấy comment / xuất data) trên các video đã chọn.
 * - running = true: đang chạy, màn hình hiện hộp tiến trình có nút Dừng.
 * - summary != null (và running = false): đã xong, màn hình hiện hộp kết quả.
 */
data class BatchState(
    val running: Boolean = false,
    val label: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val currentTitle: String = "",
    val summary: String? = null
)

class ResearchSearchViewModel(
    private val repository: ResearchRepository,
    private val apiDiscovery: ApiDiscoveryClient,
    private val knowledgeRepository: KnowledgeRepository,
    private val fileStorage: FileStorage
) : ViewModel() {
    private val pageSize = 50
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _batch = MutableStateFlow(BatchState())
    val batch: StateFlow<BatchState> = _batch.asStateFlow()

    private var loadJob: Job? = null
    private var debounceJob: Job? = null
    private var batchJob: Job? = null

    /** Gõ đến đâu tìm đến đó: chờ 300 ms sau lần gõ cuối rồi mới tìm. */
    fun updateQuery(query: String) {
        _state.update { it.copy(query = query, error = null) }
        debounceJob?.cancel()
        if (_state.value.libraryMode != LibraryMode.VIDEO && _state.value.libraryScope == null) return
        debounceJob = viewModelScope.launch {
            delay(300)
            loadPage(0, replace = true)
        }
    }

    fun setLibraryMode(mode: LibraryMode) {
        debounceJob?.cancel()
        loadJob?.cancel()
        _state.update { it.copy(libraryMode = mode, libraryScope = null, results = emptyList(), libraryObjects = emptyList(), resultCount = 0, page = 0) }
        if (mode == LibraryMode.VIDEO) search() else loadLibraryObjects(mode)
    }

    fun openLibraryObject(item: com.subgrab.app.domain.LibraryObject) {
        val mode = _state.value.libraryMode
        if (mode == LibraryMode.VIDEO) return
        _state.update { it.copy(libraryScope = LibraryScope(mode, item.id), results = emptyList(), page = 0, loading = true, error = null) }
        loadPage(0, replace = true)
    }

    fun backToLibraryObjects() {
        val mode = _state.value.libraryMode
        if (mode == LibraryMode.VIDEO) return
        _state.update { it.copy(libraryScope = null, results = emptyList(), page = 0, loading = false) }
        loadLibraryObjects(mode)
    }

    private fun loadLibraryObjects(mode: LibraryMode) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.getLibraryObjects(mode) }
                .onSuccess { objects -> _state.update { it.copy(libraryObjects = objects, loading = false, resultCount = objects.size) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Không thể đọc thư viện") } }
        }
    }

    fun reset() {
        debounceJob?.cancel()
        loadJob?.cancel()
        _state.value = SearchState()
    }

    fun updateFilters(filters: ResearchFilters) {
        _state.update { it.copy(filters = filters, error = null) }
    }

    /** Đặt bộ lọc mới và tìm lại ngay. */
    fun applyFilters(filters: ResearchFilters) {
        updateFilters(filters)
        search()
    }

    fun updateSort(sort: ResearchSort) {
        _state.update { it.copy(sort = sort, error = null) }
        search()
    }

    /** Xóa từ khóa và toàn bộ bộ lọc, giữ nguyên cách sắp xếp. */
    fun clearAll() {
        debounceJob?.cancel()
        _state.update { it.copy(query = "", filters = ResearchFilters(), error = null) }
        search()
    }

    fun search() {
        debounceJob?.cancel()
        loadPage(0, replace = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasMore) return
        loadPage(current.page + 1, replace = false)
    }

    /** Đếm số kết quả sẽ có nếu áp dụng bộ lọc này (dùng cho nút "Xem N kết quả"). */
    suspend fun countFor(filters: ResearchFilters): Int {
        val current = _state.value
        return runCatching { repository.search(current.query, filters, current.sort, 0, 1).second }
            .getOrElse { if (it is CancellationException) throw it else 0 }
    }

    fun toggleSelection(videoId: String) {
        _state.update { current ->
            val next = current.selectedVideos.toMutableSet()
            if (!next.add(videoId)) next.remove(videoId)
            current.copy(selectedVideos = next)
        }
    }

    fun selectAllVisible() {
        _state.update { it.copy(selectedVideos = it.selectedVideos + it.results.map { r -> r.videoId }) }
    }

    fun clearSelection() { _state.update { it.copy(selectedVideos = emptySet()) } }

    fun loadSession(sessionId: String) {
        _state.update { it.copy(loading = true, error = null, page = 0, libraryMode = LibraryMode.VIDEO, libraryScope = null) }
        viewModelScope.launch {
            runCatching { repository.getSearchSession(sessionId) to repository.getSessionResults(sessionId, 0, pageSize) }
                .onSuccess { (session, result) ->
                    val (rows, count) = result
                    _state.update {
                        it.copy(query = session?.query.orEmpty(), results = rows, resultCount = count, loading = false, hasMore = rows.size < count, page = 0)
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Không thể mở kết quả đã lưu") } }
        }
    }

    private fun loadPage(page: Int, replace: Boolean) {
        loadJob?.cancel()
        val snapshot = _state.value
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            runCatching { repository.search(snapshot.query, snapshot.filters, snapshot.sort, page, pageSize, snapshot.libraryScope) }
                .onSuccess { (rows, count) ->
                    _state.update { current ->
                        val merged = if (replace) rows else current.results + rows
                        current.copy(
                            results = merged,
                            selectedVideos = if (replace) emptySet() else current.selectedVideos,
                            resultCount = count,
                            loading = false,
                            error = null,
                            hasMore = merged.size < count,
                            page = page
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update { it.copy(loading = false, error = e.message ?: "Không thể tìm kiếm dữ liệu cục bộ") }
                }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Tác vụ hàng loạt trên các video đã chọn
    // -----------------------------------------------------------------------------------------

    /** Dừng tác vụ hàng loạt đang chạy. */
    fun cancelBatch() { batchJob?.cancel() }

    /** Đóng hộp kết quả sau khi tác vụ hàng loạt kết thúc. */
    fun dismissBatchSummary() { _batch.value = BatchState() }

    private enum class CommentIssue { DISABLED, STOP, OTHER }

    private fun issueOf(e: Throwable): Pair<CommentIssue, String> {
        val http = e as? HttpFailure
        val body = http?.body.orEmpty()
        return when {
            e is IllegalStateException -> CommentIssue.STOP to (e.message ?: "Chưa cấu hình YouTube Data API")
            http != null && body.contains("commentsDisabled", ignoreCase = true) -> CommentIssue.DISABLED to "tắt bình luận"
            http != null && body.contains("quotaExceeded", ignoreCase = true) -> CommentIssue.STOP to "Đã hết hạn mức YouTube API trong ngày"
            http != null -> CommentIssue.OTHER to ("lỗi HTTP " + http.status)
            else -> CommentIssue.OTHER to (e.message ?: "lỗi không rõ")
        }
    }

    /**
     * Lấy comment cho từng video đã chọn, y hệt nút làm mới comments ở màn Chi tiết, rồi lưu vào máy.
     * Một video lỗi không làm dừng cả đợt (trừ khi thiếu API key hoặc hết hạn mức).
     */
    fun fetchComments(videos: List<VideoSearchResult>) {
        if (videos.isEmpty() || _batch.value.running) return
        batchJob = viewModelScope.launch {
            val total = videos.size
            var saved = 0
            var commentTotal = 0
            val disabled = mutableListOf<String>()
            val failed = mutableListOf<String>()
            var stopReason: String? = null
            var processed = 0
            _batch.value = BatchState(running = true, label = "Đang lấy comment", total = total)
            try {
                for ((index, video) in videos.withIndex()) {
                    _batch.update { it.copy(done = index, currentTitle = video.title) }
                    try {
                        val threads = apiDiscovery.fetchComments(video.videoId)
                        knowledgeRepository.saveComments(video.videoId, threads)
                        saved++
                        commentTotal += threads.sumOf { 1 + it.replies.size }
                        processed = index + 1
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        val (issue, message) = issueOf(e)
                        if (issue == CommentIssue.STOP) {
                            stopReason = message
                            break
                        } else if (issue == CommentIssue.DISABLED) {
                            disabled += video.title
                        } else {
                            failed += video.title + " (" + message + ")"
                        }
                        processed = index + 1
                    }
                }
                _batch.value = BatchState(summary = buildCommentSummary(total, processed, saved, commentTotal, disabled, failed, stopReason))
            } catch (e: CancellationException) {
                _batch.value = BatchState(
                    summary = "Đã dừng. " + buildCommentSummary(total, processed, saved, commentTotal, disabled, failed, null)
                )
                throw e
            }
        }
    }

    private fun buildCommentSummary(
        total: Int,
        processed: Int,
        saved: Int,
        commentTotal: Int,
        disabled: List<String>,
        failed: List<String>,
        stopReason: String?
    ): String = buildString {
        appendLine("Đã lấy comment cho $saved/$total video ($commentTotal comment).")
        if (processed < total && stopReason == null) appendLine("Còn ${total - processed} video chưa xử lý.")
        if (disabled.isNotEmpty()) appendLine("\n${disabled.size} video tắt bình luận:\n" + disabled.joinToString("\n") { "• $it" })
        if (failed.isNotEmpty()) appendLine("\n${failed.size} video lỗi:\n" + failed.joinToString("\n") { "• $it" })
        if (stopReason != null) appendLine("\nĐã dừng sớm: $stopReason. ${total - processed} video chưa được xử lý.")
    }.trim()

    /**
     * Xuất data của các video đã chọn vào MỘT file duy nhất, đọc từ dữ liệu đã lưu trong máy
     * (giống nút Xuất data ở màn Chi tiết, nhưng gộp nhiều video).
     */
    fun exportData(videos: List<VideoSearchResult>, options: ResearchExport.ExportOptions, outputDir: String) {
        if (videos.isEmpty() || _batch.value.running) return
        batchJob = viewModelScope.launch {
            _batch.value = BatchState(running = true, label = "Đang xuất data", total = videos.size)
            try {
                var noTranscript = 0
                var noComments = 0
                val items = videos.mapIndexed { index, video ->
                    _batch.update { it.copy(done = index, currentTitle = video.title) }
                    val history = repository.getSnapshotHistory(video.videoId)
                    val metadata = if (options.history) history else history.firstOrNull()?.let { listOf(it) }.orEmpty()
                    val transcript = if (options.transcript) repository.getTranscript(video.videoId) else null
                    val comments = if (options.comments) {
                        repository.getCommentThreads(video.videoId).flatMap { repository.getComments(it.threadId) }
                    } else emptyList()
                    if (options.transcript && transcript == null) noTranscript++
                    if (options.comments && comments.isEmpty()) noComments++
                    ResearchExport.VideoExportData(video.videoId, video.title, metadata, transcript, comments)
                }
                val body = ResearchExport.exportVideos(items, options)
                val ext = when (options.format) {
                    ResearchExport.ExportFormat.JSON -> "json"
                    ResearchExport.ExportFormat.CSV -> "csv"
                    ResearchExport.ExportFormat.MD -> "md"
                }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                val fileName = "subgrab-export-" + videos.size + "-video-" + stamp + "." + ext
                val sub = outputDir.removePrefix("Download/").removePrefix("Download\\").trim('/')
                    .let { if (it.equals("Download", ignoreCase = true)) "" else it }
                withContext(Dispatchers.IO) { fileStorage.publishTextFile(fileName, body, sub) }
                val path = "Download/" + if (sub.isBlank()) fileName else "$sub/$fileName"
                _batch.value = BatchState(
                    summary = buildString {
                        appendLine("Đã xuất ${videos.size} video vào 1 file:")
                        appendLine(path)
                        if (noTranscript > 0) appendLine("\n$noTranscript video chưa có transcript (sẽ để trống).")
                        if (noComments > 0) appendLine("\n$noComments video chưa có comment (hãy bấm Comments trước nếu cần).")
                    }.trim()
                )
            } catch (e: CancellationException) {
                _batch.value = BatchState(summary = "Đã dừng, chưa tạo file.")
                throw e
            } catch (e: Throwable) {
                _batch.value = BatchState(summary = "Không thể xuất dữ liệu: " + (e.message ?: "lỗi không rõ"))
            }
        }
    }
}
