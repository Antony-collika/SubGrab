package com.subgrab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subgrab.app.data.DownloadOrchestrator
import com.subgrab.app.data.DownloadState
import com.subgrab.app.data.YouTubeSearchClient
import com.subgrab.app.data.YtDlpRunner
import com.subgrab.app.domain.AppSettings
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.UrlValidator
import com.subgrab.app.domain.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnalysisState { data object Idle : AnalysisState; data object Loading : AnalysisState; data class Ready(val source: Source, val videos: List<VideoItem>, val folder: String) : AnalysisState; data class Error(val message: String, val retryUrl: String? = null) : AnalysisState }
class DownloadViewModel(private val runner: YtDlpRunner?, private val orchestrator: DownloadOrchestrator? = null, private val searchClient: YouTubeSearchClient = YouTubeSearchClient()) : ViewModel() {
    private val _state = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val state: StateFlow<AnalysisState> = _state.asStateFlow()
    val downloadState: StateFlow<DownloadState> = orchestrator?.state ?: MutableStateFlow(DownloadState.Idle)
    private var lastUrl: String? = null
    private var lastKeyword: String? = null

    fun analyze(url: String) {
        lastUrl = url
        if (!UrlValidator.isValid(url)) { _state.value = AnalysisState.Error("Link không hợp lệ. Vui lòng kiểm tra lại", url); return }
        val actual = runner ?: run { _state.value = AnalysisState.Error("Không thể khởi tạo yt-dlp trên thiết bị này", url); return }
        _state.value = AnalysisState.Loading
        viewModelScope.launch { actual.fetch(url).onSuccess { (source, videos) -> _state.value = AnalysisState.Ready(source, videos, source.title) }.onFailure { _state.value = AnalysisState.Error(it.message ?: "Không thể phân tích link", lastUrl) } }
    }

    fun searchKeyword(keyword: String) {
        lastKeyword = keyword
        _state.value = AnalysisState.Loading
        viewModelScope.launch {
            searchClient.search(keyword).onSuccess { (source, videos) ->
                _state.value = AnalysisState.Ready(source, videos, "Search - ${keyword.trim()}")
            }.onFailure { _state.value = AnalysisState.Error(it.message ?: "Không thể tìm video", null) }
        }
    }

    fun retryAnalysis() { lastUrl?.let(::analyze) ?: lastKeyword?.let(::searchKeyword) }
    fun resetAnalysis() { _state.value = AnalysisState.Idle }
    fun toggle(index: Int) { val current = _state.value as? AnalysisState.Ready ?: return; _state.value = current.copy(videos = current.videos.map { if (it.index == index && (it.isSelected || current.videos.count { v -> v.isSelected } < 50) && it.canSelect) it.copy(isSelected = !it.isSelected) else it }) }
    fun selectAll() { val current = _state.value as? AnalysisState.Ready ?: return; _state.value = current.copy(videos = current.videos.map { if (it.canSelect) it.copy(isSelected = true) else it }) }
    fun clearSelection() { val current = _state.value as? AnalysisState.Ready ?: return; _state.value = current.copy(videos = current.videos.map { it.copy(isSelected = false) }) }
    fun updateFolder(folder: String) { val current = _state.value as? AnalysisState.Ready ?: return; _state.value = current.copy(folder = folder) }
    fun startDownload(settings: AppSettings) { val current = _state.value as? AnalysisState.Ready ?: return; val worker = orchestrator ?: return; viewModelScope.launch { worker.start(current.source, current.videos, current.folder, settings.toDownloadConfig()) } }
    fun pauseDownload() = orchestrator?.pause()
    fun resumeDownload() = orchestrator?.resume()
    fun cancelDownload() = orchestrator?.cancel()
}
private fun AppSettings.toDownloadConfig() = com.subgrab.app.domain.DownloadConfig(languages, formats, preferManualSub, skipNoSub, outputDir)
