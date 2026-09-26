package com.subgrab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subgrab.app.data.repository.ResearchRepository
import com.subgrab.app.domain.ResearchFilters
import com.subgrab.app.domain.ResearchSort
import com.subgrab.app.domain.SearchState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResearchSearchViewModel(private val repository: ResearchRepository) : ViewModel() {
    private val pageSize = 50
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    fun updateQuery(query: String) { _state.value = _state.value.copy(query = query, error = null) }
    fun updateFilters(filters: ResearchFilters) { _state.value = _state.value.copy(filters = filters, error = null) }
    fun updateSort(sort: ResearchSort) { _state.value = _state.value.copy(sort = sort, error = null) }

    fun search() {
        loadPage(0, replace = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasMore) return
        loadPage(current.page + 1, replace = false)
    }

    fun toggleSelection(videoId: String) {
        val current = _state.value
        val next = current.selectedVideos.toMutableSet()
        if (!next.add(videoId)) next.remove(videoId)
        _state.value = current.copy(selectedVideos = next)
    }

    fun selectAllVisible() {
        val current = _state.value
        _state.value = current.copy(selectedVideos = current.selectedVideos + current.results.map { it.videoId })
    }

    fun clearSelection() { _state.value = _state.value.copy(selectedVideos = emptySet()) }

    fun loadSession(sessionId: String) {
        _state.value = _state.value.copy(loading = true, error = null, page = 0)
        viewModelScope.launch {
            runCatching { repository.getSessionResults(sessionId, 0, pageSize) }
                .onSuccess { (rows, count) ->
                    _state.value = _state.value.copy(results = rows, resultCount = count, loading = false, hasMore = rows.size < count, page = 0)
                }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Không thể mở kết quả đã lưu") }
        }
    }

    private fun loadPage(page: Int, replace: Boolean) {
        val current = _state.value
        _state.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.search(current.query, current.filters, current.sort, page, pageSize) }
                .onSuccess { (rows, count) ->
                    val merged = if (replace) rows else current.results + rows
                    _state.value = current.copy(
                        results = merged,
                        resultCount = count,
                        loading = false,
                        error = null,
                        hasMore = merged.size < count,
                        page = page
                    )
                }
                .onFailure { _state.value = current.copy(loading = false, error = it.message ?: "Không thể tìm kiếm dữ liệu cục bộ") }
        }
    }
}
