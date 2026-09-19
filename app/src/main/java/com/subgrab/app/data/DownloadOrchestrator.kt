package com.subgrab.app.data

import com.subgrab.app.domain.DownloadConfig
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay

sealed interface DownloadState { data object Idle : DownloadState; data class Running(val current: Int, val total: Int, val title: String, val saved: Int, val skipped: Int, val logs: List<String>) : DownloadState; data class Paused(val current: Int, val total: Int, val logs: List<String>) : DownloadState; data class Done(val saved: Int, val skipped: Int, val logs: List<String>) : DownloadState; data class Cancelled(val saved: Int, val logs: List<String>) : DownloadState }
class DownloadOrchestrator(private val runner: YtDlpRunner, private val storage: FileStorage, private val history: HistoryRepository) {
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle); val state: StateFlow<DownloadState> = _state.asStateFlow()
    @Volatile private var paused = false; @Volatile private var cancelled = false
    suspend fun start(source: Source, videos: List<VideoItem>, folderName: String, config: DownloadConfig) {
        paused = false; cancelled = false; val selected = videos.filter { it.isSelected }.take(50); val dir = storage.createTaskDirectory(folderName, config.outputDir); var saved = 0; var skipped = 0; val logs = mutableListOf<String>()
        selected.forEachIndexed { index, video ->
            while (paused && !cancelled) { _state.value = DownloadState.Paused(index, selected.size, logs.toList()); delay(250) }
            if (cancelled) { _state.value = DownloadState.Cancelled(saved, logs); return }
            var videoToDownload = video
            if (!video.subtitleChecked) {
                runner.listSubs("https://www.youtube.com/watch?v=${video.videoId}").fold(
                    onSuccess = { subs ->
                        if (subs.isEmpty()) {
                            skipped++
                            logs += "⚠️ ${video.title}: không tìm thấy phụ đề"
                        } else {
                            videoToDownload = video.copy(availableSubs = subs, subtitleChecked = true)
                        }
                    },
                    onFailure = { error ->
                        logs += "⚠️ ${video.title}: kiểm tra phụ đề thất bại: ${error.message}"
                    }
                )
                if (!videoToDownload.hasSub) return@forEachIndexed
            } else if (!video.hasSub) {
                skipped++
                logs += "⚠️ ${video.title}: không có phụ đề"
                return@forEachIndexed
            }
            _state.value = DownloadState.Running(index + 1, selected.size, video.title, saved, skipped, logs.toList())
            runner.downloadSubs(videoToDownload, config.languages, config.formats, dir).onSuccess { files -> saved += files.size; logs += "✅ ${video.title}: ${files.size} file" }.onFailure { skipped++; logs += "❌ ${video.title}: ${it.message}" }
        }
        storage.convertSrtToTxt(dir)
        val basePath = config.outputDir.removePrefix("Download/").removePrefix("Download\\").ifBlank { "Subtitles" }
        val relativePath = "$basePath/${dir.name}"
        val published = storage.publishToDownloads(dir, relativePath)
        logs += "📁 Đã xuất ${published.size} file vào Download/$relativePath"
        history.add(DownloadHistoryEntry(System.currentTimeMillis(), source.title, dir.name, saved, skipped))
        _state.value = DownloadState.Done(saved, skipped, logs)
    }
    fun pause() { paused = true }
    fun resume() { paused = false }
    fun cancel() { cancelled = true; paused = false }
}
