package com.subgrab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subgrab.app.data.ApiDiscoveryClient
import com.subgrab.app.data.FileStorage
import com.subgrab.app.data.SettingsRepository
import com.subgrab.app.data.SubtitleDownloader
import com.subgrab.app.data.repository.ResearchRepository
import com.subgrab.app.data.repository.KnowledgeRepository
import com.subgrab.app.domain.VideoSearchResult
import com.subgrab.app.domain.OutputFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VideoDetailState(
    val result: VideoSearchResult? = null,
    val snapshotHistory: List<com.subgrab.app.data.db.VideoMetadataSnapshotEntity> = emptyList(),
    val transcript: com.subgrab.app.data.db.TranscriptEntity? = null,
    val transcriptExpanded: Boolean = false,
    val commentThreads: List<com.subgrab.app.data.db.CommentThreadEntity> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class VideoDetailViewModel(
    private val repository: ResearchRepository,
    private val subtitleDownloader: SubtitleDownloader,
    private val apiDiscovery: ApiDiscoveryClient,
    private val settings: SettingsRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val fileStorage: FileStorage
) : ViewModel() {
    private val _state = MutableStateFlow(VideoDetailState())
    val state: StateFlow<VideoDetailState> = _state.asStateFlow()

    fun load(videoId: String) {
        viewModelScope.launch { repository.recordView(videoId) }
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

    fun refreshTranscript(videoId: String, selectedLanguages: List<String>, preferManual: Boolean) {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            var error: Throwable? = null
            selectedLanguages.distinct().filter { it.isNotBlank() }.forEach { language ->
                subtitleDownloader.fetchAndPersistTranscript(videoId, selectedLanguages, preferManual, language, OutputFormat.TXT, false)
                    .onFailure { error = it }
            }
            if (error == null) load(videoId)
            else _state.value = _state.value.copy(loading = false, error = error?.message ?: "Không thể làm mới transcript")
        }
    }

    fun toggleTranscriptExpanded() {
        _state.value = _state.value.copy(transcriptExpanded = !_state.value.transcriptExpanded)
    }

    fun downloadSubtitles(videoId: String, title: String, languages: List<String>, preferManual: Boolean, format: OutputFormat) {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val dir = fileStorage.createTaskDirectory(title, "SubGrab/VideoDetail")
            val config = com.subgrab.app.domain.DownloadConfig(languages = languages, formats = setOf(format), preferManual = preferManual)
            subtitleDownloader.download(com.subgrab.app.domain.VideoItem(1, videoId, title, 0, emptyList()), config, dir)
                .onSuccess {
                    fileStorage.publishToDownloads(dir, "SubGrab/" + com.subgrab.app.domain.FileNameSanitizer.sanitize(title))
                    load(videoId)
                }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, error = it.message ?: "Không thể tải phụ đề")
                }
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
