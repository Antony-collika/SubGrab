package com.subgrab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subgrab.app.data.repository.ResearchRepository
import com.subgrab.app.domain.ResearchFilters
import com.subgrab.app.domain.ResearchSort
import com.subgrab.app.domain.SearchState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ResearchSearchViewModel(private val repository: ResearchRepository) : ViewModel() {
    private val pageSize = 50
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var debounceJob: Job? = null

    /** Gõ đến đâu tìm đến đó: chờ 300 ms sau lần gõ cuối rồi mới tìm. */
    fun updateQuery(query: String) {
        _state.update { it.copy(query = query, error = null) }
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(300)
            loadPage(0, replace = true)
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
        _state.update { it.copy(loading = true, error = null, page = 0) }
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
            runCatching { repository.search(snapshot.query, snapshot.filters, snapshot.sort, page, pageSize) }
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
}
