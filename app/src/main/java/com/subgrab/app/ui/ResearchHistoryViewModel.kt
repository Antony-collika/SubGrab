package com.subgrab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subgrab.app.data.repository.ResearchRepository
import com.subgrab.app.domain.ResearchActivityItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResearchHistoryState(
    val items: List<ResearchActivityItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val offset: Int = 0
)

class ResearchHistoryViewModel(private val repository: ResearchRepository) : ViewModel() {
    private val pageSize = 50
    private val _state = MutableStateFlow(ResearchHistoryState())
    val state: StateFlow<ResearchHistoryState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = ResearchHistoryState(loading = true)
        viewModelScope.launch {
            runCatching { repository.getActivityPage(pageSize, 0) }
                .onSuccess { rows -> _state.value = ResearchHistoryState(items = rows, hasMore = rows.size == pageSize, offset = rows.size) }
                .onFailure { _state.value = ResearchHistoryState(error = it.message ?: "Không thể tải lịch sử nghiên cứu") }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasMore) return
        _state.value = current.copy(loading = true)
        viewModelScope.launch {
            runCatching { repository.getActivityPage(pageSize, current.offset) }
                .onSuccess { rows ->
                    val all = current.items + rows
                    _state.value = current.copy(items = all, loading = false, hasMore = rows.size == pageSize, offset = all.size, error = null)
                }
                .onFailure { _state.value = current.copy(loading = false, error = it.message ?: "Không thể tải thêm lịch sử") }
        }
    }
}
