package com.subgrab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subgrab.app.data.ApiDiscoveryClient
import com.subgrab.app.data.FileStorage
import com.subgrab.app.data.SettingsRepository
import com.subgrab.app.data.SubtitleDownloader
import com.subgrab.app.data.repository.ResearchRepository
import com.subgrab.app.data.repository.KnowledgeRepository
import com.subgrab.app.data.export.ResearchExport
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
                VideoDetailState(result = result, snapshotHistory = history, transcript = transcript, commentThreads = threads, loading = false, error = null)
            }.onSuccess { _state.value = it }
             .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Không thể tải chi tiết video") }
        }
    }

    fun listSubtitles(videoId: String, onResult: (Result<List<com.subgrab.app.domain.SubtitleLanguage>>) -> Unit) {
        viewModelScope.launch {
            onResult(subtitleDownloader.listSubtitles("https://www.youtube.com/watch?v=" + videoId))
        }
    }

    fun refreshTranscript(videoId: String, language: com.subgrab.app.domain.SubtitleLanguage) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            subtitleDownloader.fetchAndPersistTranscript(
                videoId = videoId,
                languages = listOf(language.code),
                preferManual = !language.isAuto,
                requestedLanguage = language.code,
                requestedAuto = language.isAuto,
                requestedFormat = com.subgrab.app.domain.OutputFormat.TXT,
                forceRefresh = true
            ).onSuccess { load(videoId) }
             .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Không thể làm mới transcript") }
        }
    }


    fun exportData(videoId: String, title: String, options: ResearchExport.ExportOptions) {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            runCatching {
                val history = repository.getSnapshotHistory(videoId)
                val metadata = if (options.history) history else history.firstOrNull()?.let { listOf(it) }.orEmpty()
                val body = ResearchExport.exportVideo(
                    ResearchExport.VideoExportData(
                        videoId,
                        title,
                        metadata,
                        if (options.transcript) repository.getTranscript(videoId) else null,
                        if (options.comments) repository.getCommentThreads(videoId).flatMap { repository.getComments(it.threadId) } else emptyList()
                    ),
                    options
                )
                val ext = when (options.format) {
                    ResearchExport.ExportFormat.JSON -> "json"
                    ResearchExport.ExportFormat.CSV -> "csv"
                    ResearchExport.ExportFormat.MD -> "md"
                }
                fileStorage.publishTextFile(
                    "subgrab-" + com.subgrab.app.domain.FileNameSanitizer.sanitize(title) + "." + ext,
                    body,
                    "SubGrab/Exports"
                )
            }.onSuccess { load(videoId) }
             .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Không thể xuất dữ liệu") }
        }
    }

    fun toggleTranscriptExpanded() {
        _state.value = _state.value.copy(transcriptExpanded = !_state.value.transcriptExpanded)
    }

    fun downloadSubtitles(videoId: String, title: String, language: com.subgrab.app.domain.SubtitleLanguage, format: com.subgrab.app.domain.OutputFormat, outputDir: String) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val dir = fileStorage.createTaskDirectory(title, outputDir)
            val video = com.subgrab.app.domain.VideoItem(1, videoId, title, 0, emptyList())
            subtitleDownloader.downloadSingle(video, language, format, dir)
                .onSuccess {
                    fileStorage.publishToDownloads(dir, if (outputDir.equals("Download", true)) "" else outputDir.removePrefix("Download/").removePrefix("Download\\").trim('/'))
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
