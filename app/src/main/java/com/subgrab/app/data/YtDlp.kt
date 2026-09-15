package com.subgrab.app.data

import com.subgrab.app.domain.OutputFormat
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.SubtitleLanguage
import com.subgrab.app.domain.VideoItem
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

class YtDlpRunner(private val binary: File, private val parser: YtDlpOutputParser = YtDlpOutputParser()) {
    suspend fun fetch(url: String, timeoutSeconds: Long = 30): Result<Pair<Source, List<VideoItem>>> = withContext(Dispatchers.IO) {
        run(listOf("--flat-playlist", "--playlist-end", "50", "--dump-json", "--no-warnings", url), timeoutSeconds).map { output ->
            val (source, videos) = parser.parseFlatPlaylist(output.lineSequence(), url)
            source to videos.map { video ->
                val subtitles = run(listOf("--list-subs", "--skip-download", "--no-warnings", "https://www.youtube.com/watch?v=${video.videoId}"), timeoutSeconds).map(parser::parseAvailableSubs).getOrDefault(emptyList())
                video.copy(availableSubs = subtitles)
            }
        }
    }
    suspend fun listSubs(videoUrl: String, timeoutSeconds: Long = 30): Result<List<SubtitleLanguage>> = withContext(Dispatchers.IO) { run(listOf("--list-subs", "--skip-download", "--no-warnings", videoUrl), timeoutSeconds).map(parser::parseAvailableSubs) }
    suspend fun downloadSubs(video: VideoItem, languages: List<String>, formats: Set<OutputFormat>, outputDir: File, timeoutSeconds: Long = 60): Result<List<File>> = withContext(Dispatchers.IO) {
        val formatArg = if (formats.contains(OutputFormat.SRT)) "srt/best" else "vtt/best"
        val args = mutableListOf("--skip-download", "--write-subs", "--write-auto-subs", "--sub-langs", languages.joinToString(","), "--convert-subs", "srt", "--sub-format", formatArg, "--sleep-requests", "1", "--output", File(outputDir, "% (playlist_index)03d - %(title)s.%(ext)s".replace("% ", "%")).absolutePath, "https://www.youtube.com/watch?v=${video.videoId}")
        run(args, timeoutSeconds).map { outputDir.listFiles()?.toList().orEmpty() }
    }
    private suspend fun run(args: List<String>, timeoutSeconds: Long): Result<String> = withContext(Dispatchers.IO) { runCatching {
        require(binary.exists() && binary.canExecute()) { "Không tìm thấy yt-dlp executable" }
        val process = ProcessBuilder(listOf(binary.absolutePath) + args).redirectErrorStream(true).start()
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) { process.destroyForcibly(); error("Quá thời gian xử lý") }
        val output = process.inputStream.bufferedReader().readText(); check(process.exitValue() == 0) { output.ifBlank { "yt-dlp thất bại" } }; output
    } }
}
