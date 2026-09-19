package com.subgrab.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.subgrab.app.data.DownloadControlStore
import com.subgrab.app.data.DownloadState
import com.subgrab.app.data.DownloadOrchestrator
import com.subgrab.app.data.NewPipeExtractorClient
import com.subgrab.app.domain.AppSettings
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.UrlValidator
import com.subgrab.app.domain.VideoItem
import com.subgrab.app.service.DownloadWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Loading : AnalysisState
    data class Ready(val source: Source, val videos: List<VideoItem>, val folder: String) : AnalysisState
    data class Error(val message: String, val retryUrl: String? = null) : AnalysisState
}

class DownloadViewModel(
    private val context: Context,
    private val extractorClient: NewPipeExtractorClient,
    private val orchestrator: DownloadOrchestrator? = null
) : ViewModel() {
    private val _state = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val state: StateFlow<AnalysisState> = _state.asStateFlow()
    private val control = DownloadControlStore(context.applicationContext)
    private val workManager = WorkManager.getInstance(context.applicationContext)

    val downloadState: StateFlow<DownloadState> = workManager
        .getWorkInfosForUniqueWorkFlow(DownloadWorker.UNIQUE_WORK)
        .map { infos ->
            infos.firstOrNull()?.let { work ->
                val data = if (work.state.isFinished) work.outputData else work.progress
                work.toDownloadState(data)
            } ?: DownloadState.Idle
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadState.Idle)

    private var lastUrl: String? = null
    private var lastKeyword: String? = null

    fun analyze(url: String) {
        lastUrl = url
        if (!UrlValidator.isValid(url)) { _state.value = AnalysisState.Error("Link không hợp lệ. Vui lòng kiểm tra lại", url); return }
        _state.value = AnalysisState.Loading
        viewModelScope.launch { extractorClient.extractSource(url).onSuccess { (source, videos) -> _state.value = AnalysisState.Ready(source, videos, source.title) }.onFailure { _state.value = AnalysisState.Error(it.message ?: "Không thể phân tích link", lastUrl) } }
    }

    fun searchKeyword(keyword: String) {
        lastKeyword = keyword
        _state.value = AnalysisState.Loading
        viewModelScope.launch {
            extractorClient.search(keyword).onSuccess { (source, videos) ->
                _state.value = AnalysisState.Ready(source, videos, "Search - ${keyword.trim()}")
            }.onFailure { _state.value = AnalysisState.Error(it.message ?: "Không thể tìm video", null) }
        }
    }

    fun retryAnalysis() { lastUrl?.let(::analyze) ?: lastKeyword?.let(::searchKeyword) }
    fun resetAnalysis() { _state.value = AnalysisState.Idle }

    fun toggle(index: Int) {
        val current = _state.value as? AnalysisState.Ready ?: return
        _state.value = current.copy(videos = current.videos.map {
            if (it.index == index && (it.isSelected || current.videos.count { v -> v.isSelected } < 50) && it.canSelect) it.copy(isSelected = !it.isSelected) else it
        })
    }

    fun selectAll() {
        val current = _state.value as? AnalysisState.Ready ?: return
        _state.value = current.copy(videos = current.videos.map { if (it.canSelect) it.copy(isSelected = true) else it })
    }

    fun clearSelection() {
        val current = _state.value as? AnalysisState.Ready ?: return
        _state.value = current.copy(videos = current.videos.map { it.copy(isSelected = false) })
    }

    fun updateFolder(folder: String) {
        val current = _state.value as? AnalysisState.Ready ?: return
        _state.value = current.copy(folder = folder)
    }

    fun startDownload(settings: AppSettings) {
        val current = _state.value as? AnalysisState.Ready ?: return
        viewModelScope.launch {
            DownloadWorker.enqueue(context, current.source, current.videos, current.folder, settings.toDownloadConfig())
        }
    }

    fun pauseDownload() {
        viewModelScope.launch { control.pause() }
    }

    fun resumeDownload() {
        viewModelScope.launch { control.resume() }
    }

    fun cancelDownload() {
        viewModelScope.launch { control.cancel() }
    }

    private fun WorkInfo.toDownloadState(data: androidx.work.Data): DownloadState {
        val current = data.getInt(DownloadWorker.KEY_CURRENT, 0)
        val total = data.getInt(DownloadWorker.KEY_TOTAL, 0)
        val title = data.getString(DownloadWorker.KEY_TITLE).orEmpty()
        val saved = data.getInt(DownloadWorker.KEY_SAVED, 0)
        val skipped = data.getInt(DownloadWorker.KEY_SKIPPED, 0)
        val logs = data.getStringArray(DownloadWorker.KEY_LOGS)?.toList().orEmpty()
        return when (data.getString(DownloadWorker.KEY_STATE)) {
            "running" -> DownloadState.Running(current, total, title, saved, skipped, logs)
            "paused" -> DownloadState.Paused(current, total, logs)
            "done" -> DownloadState.Done(saved, skipped, logs)
            "cancelled" -> DownloadState.Cancelled(saved, logs)
            else -> when (state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Idle
                WorkInfo.State.CANCELLED -> DownloadState.Cancelled(saved, logs)
                else -> DownloadState.Idle
            }
        }
    }
}

private fun AppSettings.toDownloadConfig() = com.subgrab.app.domain.DownloadConfig(languages, formats, preferManualSub, skipNoSub, outputDir, timestampMode)
