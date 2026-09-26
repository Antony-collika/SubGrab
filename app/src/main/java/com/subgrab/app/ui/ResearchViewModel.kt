package com.subgrab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subgrab.app.data.ApiDiscoveryClient
import com.subgrab.app.data.SettingsRepository
import com.subgrab.app.data.SubtitleDownloader
import com.subgrab.app.data.repository.KnowledgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ResearchFetchState {
    data object Idle : ResearchFetchState
    data object Loading : ResearchFetchState
    data class Success(val message: String) : ResearchFetchState
    data class Error(val message: String) : ResearchFetchState
}

class ResearchViewModel(
    private val subtitleDownloader: SubtitleDownloader,
    private val apiDiscovery: ApiDiscoveryClient,
    private val settings: SettingsRepository,
    private val knowledgeRepository: KnowledgeRepository
) : ViewModel() {
    private val _transcriptState = MutableStateFlow<ResearchFetchState>(ResearchFetchState.Idle)
    val transcriptState: StateFlow<ResearchFetchState> = _transcriptState.asStateFlow()

    private val _commentsState = MutableStateFlow<ResearchFetchState>(ResearchFetchState.Idle)
    val commentsState: StateFlow<ResearchFetchState> = _commentsState.asStateFlow()

    fun fetchTranscript(
        videoId: String,
        language: String? = null,
        format: com.subgrab.app.domain.OutputFormat = com.subgrab.app.domain.OutputFormat.TXT,
        forceRefresh: Boolean = false
    ) {
        _transcriptState.value = ResearchFetchState.Loading
        viewModelScope.launch {
            val current = settings.current()
            subtitleDownloader.fetchAndPersistTranscript(
                videoId = videoId,
                languages = current.languages,
                preferManual = current.preferManualSub,
                requestedLanguage = language,
                requestedFormat = format,
                forceRefresh = forceRefresh
            )
                .onSuccess { actualLanguage ->
                    val prefix = if (forceRefresh) "Đã làm mới transcript" else "Đã lấy transcript"
                    _transcriptState.value = ResearchFetchState.Success("$prefix ($actualLanguage)")
                }
                .onFailure { _transcriptState.value = ResearchFetchState.Error(it.message ?: "Không thể tải transcript") }
        }
    }

    fun refreshTranscript(
        videoId: String,
        language: String? = null,
        format: com.subgrab.app.domain.OutputFormat = com.subgrab.app.domain.OutputFormat.TXT
    ) = fetchTranscript(videoId, language, format, forceRefresh = true)

    fun fetchComments(videoId: String) {
        _commentsState.value = ResearchFetchState.Loading
        viewModelScope.launch {
            runCatching { apiDiscovery.fetchComments(videoId) }
                .mapCatching { threads ->
                    knowledgeRepository.saveComments(videoId, threads)
                    threads.size
                }
                .onSuccess { count -> _commentsState.value = ResearchFetchState.Success("Đã lưu $count comment thread") }
                .onFailure { _commentsState.value = ResearchFetchState.Error(it.message ?: "Không thể tải comments") }
        }
    }

    suspend fun getTranscript(videoId: String) = knowledgeRepository.getTranscript(videoId)
    suspend fun getCommentThreads(videoId: String) = knowledgeRepository.getCommentThreads(videoId)
    suspend fun getComments(threadId: String) = knowledgeRepository.getComments(threadId)
}
