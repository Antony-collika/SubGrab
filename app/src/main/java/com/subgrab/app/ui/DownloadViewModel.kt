package com.subgrab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subgrab.app.data.YtDlpRunner
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.VideoItem
import com.subgrab.app.domain.UrlValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnalysisState { data object Idle : AnalysisState; data object Loading : AnalysisState; data class Ready(val source: Source, val videos: List<VideoItem>, val folder: String) : AnalysisState; data class Error(val message: String) : AnalysisState }
class DownloadViewModel(private val runner: YtDlpRunner? = null) : ViewModel() {
    private val _state = MutableStateFlow<AnalysisState>(AnalysisState.Idle); val state: StateFlow<AnalysisState> = _state.asStateFlow()
    fun analyze(url: String) {
        if (!UrlValidator.isValid(url)) { _state.value = AnalysisState.Error("Link không hợp lệ. Vui lòng kiểm tra lại"); return }
        val actualRunner = runner ?: run { _state.value = AnalysisState.Error("Chưa cấu hình yt-dlp trong ứng dụng"); return }
        _state.value = AnalysisState.Loading
        viewModelScope.launch { actualRunner.fetch(url).onSuccess { (source, videos) -> _state.value = AnalysisState.Ready(source, videos, source.title) }.onFailure { _state.value = AnalysisState.Error(it.message ?: "Không thể phân tích link") } }
    }
    fun toggle(index: Int) { val current = _state.value as? AnalysisState.Ready ?: return; _state.value = current.copy(videos = current.videos.map { if (it.index == index && (it.isSelected || current.videos.count { v -> v.isSelected } < 50) && it.hasSub) it.copy(isSelected = !it.isSelected) else it }) }
    fun selectAll() { val current = _state.value as? AnalysisState.Ready ?: return; _state.value = current.copy(videos = current.videos.map { if (it.hasSub) it.copy(isSelected = true) else it }) }
    fun clearSelection() { val current = _state.value as? AnalysisState.Ready ?: return; _state.value = current.copy(videos = current.videos.map { it.copy(isSelected = false) }) }
    fun updateFolder(folder: String) { val current = _state.value as? AnalysisState.Ready ?: return; _state.value = current.copy(folder = folder) }
    fun selectedCount(): Int = (state.value as? AnalysisState.Ready)?.videos?.count { it.isSelected } ?: 0
}
