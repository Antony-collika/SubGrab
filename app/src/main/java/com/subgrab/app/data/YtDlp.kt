package com.subgrab.app.data

import android.content.Context
import com.subgrab.app.domain.OutputFormat
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.SubtitleLanguage
import com.subgrab.app.domain.VideoItem
import dev.ffmpegkit_maintained.ytdlp.DownloadProgressCallback
import dev.ffmpegkit_maintained.ytdlp.LogCallback
import dev.ffmpegkit_maintained.ytdlp.YtDlp
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class YtDlpOutputParser {
    fun parseFlatPlaylist(lines: Sequence<String>, sourceUrl: String): Pair<Source, List<VideoItem>> {
        val videos = lines.mapIndexedNotNull { index, line ->
            val id = jsonValue(line, "id") ?: return@mapIndexedNotNull null
            val title = jsonValue(line, "title") ?: "Video $id"
            val duration = jsonValue(line, "duration")?.toIntOrNull() ?: 0
            VideoItem(index + 1, id, title, duration, emptyList())
        }.take(50).toList()
        val title = videos.firstOrNull()?.title?.substringBefore(" - ") ?: "YouTube"
        return Source(sourceUrl, sourceUrl, title, videos.size) to videos
    }
    fun parseAvailableSubs(output: String): List<SubtitleLanguage> = buildList {
        listOf("vi" to "Tiếng Việt", "en" to "English").forEach { (code, name) ->
            if (Regex("(?m)^\\s*$code(?:[-_].*)?\\s+").containsMatchIn(output)) add(SubtitleLanguage(code, false, name))
            if (Regex("(?m)^\\s*$code[-_]auto(?:\\s|$)").containsMatchIn(output)) add(SubtitleLanguage(code, true, name))
        }
    }.distinctBy { it.code to it.isAuto }
    private fun jsonValue(line: String, key: String): String? = Regex("\\\"$key\\\"\\s*:\\s*(?:\\\"([^\\\"]*)\\\"|([^,}]+))").find(line)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] }?.trim('"') }
}

class YtDlpRunner(context: Context, private val parser: YtDlpOutputParser = YtDlpOutputParser()) {
    private val youtubeClients = listOf("web_embedded", "android_vr", "tv")
    init { runCatching { YtDlp.init(context.applicationContext) }.getOrElse { throw IllegalStateException("Không thể khởi tạo yt-dlp Android runtime", it) } }

    suspend fun fetch(url: String, timeoutSeconds: Long = 30): Result<Pair<Source, List<VideoItem>>> = withContext(Dispatchers.IO) {
        executeLogsWithFallback(timeoutSeconds) { client -> YtDlpRequest(url).addOption("--flat-playlist").addOption("--playlist-end", "50").addOption("--dump-json").addOption("--no-warnings").youtubeClient(client) }.map { output ->
            val (source, videos) = parser.parseFlatPlaylist(output.lineSequence(), url)
            source to videos.map { video ->
                val subtitles = listSubs("https://www.youtube.com/watch?v=${video.videoId}", timeoutSeconds).getOrDefault(emptyList())
                video.copy(availableSubs = subtitles)
            }
        }
    }

    suspend fun listSubs(videoUrl: String, timeoutSeconds: Long = 30): Result<List<SubtitleLanguage>> = withContext(Dispatchers.IO) {
        executeLogsWithFallback(timeoutSeconds) { client -> YtDlpRequest(videoUrl).addOption("--list-subs").addOption("--skip-download").addOption("--no-warnings").youtubeClient(client) }.map(parser::parseAvailableSubs)
    }

    suspend fun downloadSubs(video: VideoItem, languages: List<String>, formats: Set<OutputFormat>, outputDir: File, timeoutSeconds: Long = 60): Result<List<File>> = withContext(Dispatchers.IO) {
        val before = outputDir.listFiles()?.map { it.name }?.toSet().orEmpty()
        val formatArg = if (formats.contains(OutputFormat.SRT)) "vtt/srt/best" else "vtt/best"
        executeWithFallback(timeoutSeconds) { client ->
            YtDlpRequest("https://www.youtube.com/watch?v=${video.videoId}")
                .setOutputTemplate(File(outputDir, "%(playlist_index)03d - %(title)s.%(ext)s").absolutePath)
                .addOption("--skip-download")
                .addOption("--write-subs")
                .addOption("--write-auto-subs")
                .addOption("--sub-langs", languages.joinToString(","))
                .addOption("--convert-subs", "srt")
                .addOption("--sub-format", formatArg)
                .youtubeClient(client)
        }.map { outputDir.listFiles()?.filter { it.name !in before }.orEmpty() }
    }

    private suspend fun executeLogsWithFallback(timeoutSeconds: Long, requestFactory: (String) -> YtDlpRequest): Result<String> {
        var last: Result<String> = Result.failure(IllegalStateException("yt-dlp không trả về dữ liệu"))
        for (client in youtubeClients) {
            last = executeLogs(requestFactory(client), timeoutSeconds)
            if (last.isSuccess || !last.exceptionOrNull().isYoutube403()) return last
        }
        return last
    }

    private suspend fun executeWithFallback(timeoutSeconds: Long, requestFactory: (String) -> YtDlpRequest): Result<String> {
        var last: Result<String> = Result.failure(IllegalStateException("yt-dlp không trả về dữ liệu"))
        for (client in youtubeClients) {
            last = execute(requestFactory(client), timeoutSeconds)
            if (last.isSuccess || !last.exceptionOrNull().isYoutube403()) return last
        }
        return last
    }

    private suspend fun executeLogs(request: YtDlpRequest, timeoutSeconds: Long): Result<String> {
        val logs = StringBuilder()
        return runCatching {
            val callback = object : LogCallback { override fun onLog(level: String?, message: String?) { if (!message.isNullOrBlank()) logs.appendLine(message) } }
            val response = YtDlp.executeDebug(request, callback, DownloadProgressCallback { _, _, _ -> }).get(timeoutSeconds, TimeUnit.SECONDS)
            check(response.isSuccess) { response.errorOutput.ifBlank { "yt-dlp thất bại: ${response.exitCode}" } }
            logs.toString()
        }
    }

    private suspend fun execute(request: YtDlpRequest, timeoutSeconds: Long): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = YtDlp.executeAsync(request, DownloadProgressCallback { _, _, _ -> }).get(timeoutSeconds, TimeUnit.SECONDS)
            check(response.isSuccess) { response.errorOutput.ifBlank { "yt-dlp thất bại: ${response.exitCode}" } }
            response.output
        }
    }

    private fun Throwable?.isYoutube403(): Boolean = this?.message?.contains("403", ignoreCase = true) == true
}

private fun YtDlpRequest.youtubeClient(client: String): YtDlpRequest =
    addOption("--extractor-args", "youtube:player_client=$client")
