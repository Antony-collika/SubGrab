package com.subgrab.app.data

import com.subgrab.app.domain.DownloadConfig
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import java.util.UUID

sealed interface DownloadState { data object Idle : DownloadState; data class Running(val current: Int, val total: Int, val title: String, val saved: Int, val skipped: Int, val logs: List<String>) : DownloadState; data class Paused(val current: Int, val total: Int, val logs: List<String>) : DownloadState; data class Done(val saved: Int, val skipped: Int, val logs: List<String>) : DownloadState; data class Cancelled(val saved: Int, val logs: List<String>) : DownloadState }
class DownloadOrchestrator(private val runner: YtDlpRunner, private val storage: FileStorage) {
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle); val state: StateFlow<DownloadState> = _state.asStateFlow()
    @Volatile private var paused = false; @Volatile private var cancelled = false
    suspend fun start(source: Source, videos: List<VideoItem>, folderName: String, config: DownloadConfig) {
        paused = false; cancelled = false; val selected = videos.filter { it.isSelected }.take(50); val dir = storage.createTaskDirectory(folderName); var saved = 0; var skipped = 0; val logs = mutableListOf<String>()
        selected.forEachIndexed { index, video ->
            while (paused && !cancelled) { _state.value = DownloadState.Paused(index, selected.size, logs.toList()); delay(250) }
            if (cancelled) { _state.value = DownloadState.Cancelled(saved, logs); return }
            if (!video.hasSub) { skipped++; logs += "⚠️ ${video.title}: không có phụ đề"; return@forEachIndexed }
            _state.value = DownloadState.Running(index + 1, selected.size, video.title, saved, skipped, logs.toList())
            runner.downloadSubs(video, config.languages, config.formats, dir).onSuccess { files -> saved += files.size; logs += "✅ ${video.title}: ${files.size} file" }.onFailure { logs += "❌ ${video.title}: ${it.message}" }
        }
        storage.convertSrtToTxt(dir); _state.value = DownloadState.Done(saved, skipped, logs)
    }
    fun pause() { paused = true }
    fun resume() { paused = false }
    fun cancel() { cancelled = true; paused = false }
}
