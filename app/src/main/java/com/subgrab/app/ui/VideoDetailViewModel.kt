package com.subgrab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subgrab.app.data.ApiDiscoveryClient
import com.subgrab.app.data.SettingsRepository
import com.subgrab.app.data.SubtitleDownloader
import com.subgrab.app.data.repository.ResearchRepository
import com.subgrab.app.data.repository.KnowledgeRepository
import com.subgrab.app.domain.VideoSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VideoDetailState(
    val result: VideoSearchResult? = null,
    val snapshotHistory: List<com.subgrab.app.data.db.VideoMetadataSnapshotEntity> = emptyList(),
    val transcript: com.subgrab.app.data.db.TranscriptEntity? = null,
    val commentThreads: List<com.subgrab.app.data.db.CommentThreadEntity> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class VideoDetailViewModel(
    private val repository: ResearchRepository,
    private val subtitleDownloader: SubtitleDownloader,
    private val apiDiscovery: ApiDiscoveryClient,
    private val settings: SettingsRepository,
    private val knowledgeRepository: KnowledgeRepository
) : ViewModel() {
    private val _state = MutableStateFlow(VideoDetailState())
    val state: StateFlow<VideoDetailState> = _state.asStateFlow()

    fun load(videoId: String) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                val snapshot = repository.getLatestSnapshot(videoId)
                val history = repository.getSnapshotHistory(videoId)
                val transcript = repository.getTranscript(videoId)
                val threads = repository.getCommentThreads(videoId)
                val result = snapshot?.let {
                    VideoSearchResult(videoId, it.title, it.description, it.channelId, it.channelName, it.thumbnail,
                        it.publishedAt, it.fetchedAt, it.durationSeconds, it.subscriberCount, it.viewCount,
                        it.likeCount, it.commentCount, it.tags.joinToString(" "), it.category,
                        it.topic.joinToString(" "), null, null)
                }
                VideoDetailState(result, history, transcript, threads, false, null)
            }.onSuccess { _state.value = it }
             .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Không thể tải chi tiết video") }
        }
    }

    fun refreshTranscript(videoId: String) {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val current = settings.current()
            subtitleDownloader.fetchAndPersistTranscript(videoId, current.languages, current.preferManualSub, null,
                com.subgrab.app.domain.OutputFormat.TXT, true)
                .onSuccess { load(videoId) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Không thể làm mới transcript") }
        }
    }

    fun refreshComments(videoId: String) {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            runCatching { apiDiscovery.fetchComments(videoId) }
                .mapCatching { threads -> knowledgeRepository.saveComments(videoId, threads) }
                .onSuccess { load(videoId) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Không thể làm mới comments") }
        }
    }

    suspend fun comments(threadId: String) = repository.getComments(threadId)
}
