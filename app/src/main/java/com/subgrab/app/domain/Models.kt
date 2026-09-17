package com.subgrab.app.domain

enum class OutputFormat(val ext: String) { TXT("txt"), SRT("srt") }
enum class TaskStatus { IDLE, FETCHING, RUNNING, PAUSED, DONE, CANCELLED, ERROR }
data class SubtitleLanguage(val code: String, val isAuto: Boolean = false, val name: String = code)
data class VideoItem(
    val index: Int,
    val videoId: String,
    val title: String,
    val durationSec: Int,
    val availableSubs: List<SubtitleLanguage>,
    val isSelected: Boolean = false,
    val subtitleChecked: Boolean = true
) {
    val hasSub get() = availableSubs.isNotEmpty()
    val canSelect get() = !subtitleChecked || hasSub
}
data class Source(val id: String, val url: String, val title: String, val originalTotalVideos: Int)
data class DownloadConfig(val languages: List<String> = listOf("vi", "en"), val formats: Set<OutputFormat> = setOf(OutputFormat.TXT), val preferManual: Boolean = true, val skipNoSub: Boolean = true, val outputDir: String = "Download/Subtitles")
data class AppSettings(
    val languages: List<String> = listOf("vi", "en"),
    val formats: Set<OutputFormat> = setOf(OutputFormat.TXT),
    val outputDir: String = "Download/Subtitles",
    val preferManualSub: Boolean = true,
    val skipNoSub: Boolean = true,
    val youtubeApiKey: String = ""
)
object UrlValidator {
    private val youtube = Regex("^https?://(www\\.)?(youtube\\.com|youtu\\.be)/.*", RegexOption.IGNORE_CASE)
    fun isValid(url: String): Boolean = url.trim().let { it.isNotEmpty() && youtube.matches(it) && (it.contains("watch?v=") || it.contains("youtu.be/") || it.contains("/playlist?") || it.contains("/channel/") || it.contains("/c/") || it.contains("/@")) }
}
object FileNameSanitizer {
    fun sanitize(input: String): String = java.text.Normalizer.normalize(input.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("[\\p{M}]+"), "").replace('đ','d').replace(Regex("[/\\\\:*?\"<>|]"), "").trim().replace(Regex("\\s+"), "_").take(80)
}
object SrtToTxtConverter { fun convert(srt: String): String = srt.lineSequence().filter { it.isNotBlank() && !it.matches(Regex("\\d+")) && !it.contains("-->") }.fold(mutableListOf<String>()) { acc, line -> if (acc.lastOrNull() != line.trim()) acc.add(line.trim()); acc }.joinToString("\n") }
