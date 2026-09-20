package com.subgrab.app.domain

enum class OutputFormat(val ext: String) { TXT("txt"), SRT("srt") }
enum class TaskStatus { IDLE, FETCHING, RUNNING, PAUSED, DONE, CANCELLED, ERROR }
data class SubtitleLanguage(val code: String, val isAuto: Boolean = false, val name: String = code)

enum class RequestLane { DISCOVERY_API, DISCOVERY_EXTRACTOR, API_METADATA, SUBTITLE_EXTRACTOR }
data class RequestOperation(val lane: RequestLane, val operation: String, val url: String)
data class RequestResult(val success: Boolean, val httpStatus: Int?, val durationMs: Long, val failureType: FailureType?)
enum class FailureType {
    NO_SUBTITLE, LANGUAGE_UNAVAILABLE, VIDEO_UNAVAILABLE, TIMEOUT, CONNECTION_ERROR,
    SERVER_ERROR, HTTP_403, HTTP_429, BOT_DETECTION, ACCESS_DENIED, PARSE_ERROR,
    STORAGE_ERROR, CONFIGURATION_ERROR, UNKNOWN
}
enum class GovernorState { NORMAL, SLOWDOWN }

data class VideoItem(
    val index: Int,
    val videoId: String,
    val title: String,
    val durationSec: Int,
    val availableSubs: List<SubtitleLanguage>,
    val isSelected: Boolean = false,
    val subtitleChecked: Boolean = false,
    val channelTitle: String = "",
    val publishedAt: String = "",
    val viewCount: Long? = null,
    val thumbnailUrl: String = "",
    val description: String? = null,
    val durationSeconds: Long? = null,
    val likeCount: Long? = null
) {
    val hasSub get() = availableSubs.isNotEmpty()
    val canSelect get() = !subtitleChecked || hasSub
}
data class Source(val id: String, val url: String, val title: String, val originalTotalVideos: Int)
data class DownloadConfig(
    val languages: List<String> = listOf("vi", "en"),
    val formats: Set<OutputFormat> = setOf(OutputFormat.TXT),
    val preferManual: Boolean = true,
    val skipNoSub: Boolean = true,
    val outputDir: String = "Download/Subtitles",
    val timestampMode: SubtitleTimestampMode = SubtitleTimestampMode.WITH_TIMESTAMP,
    val subtitleConcurrency: Int = 1
)
data class AppSettings(
    val languages: List<String> = listOf("vi", "en"),
    val formats: Set<OutputFormat> = setOf(OutputFormat.TXT),
    val outputDir: String = "Download/Subtitles",
    val preferManualSub: Boolean = true,
    val skipNoSub: Boolean = true,
    val timestampMode: SubtitleTimestampMode = SubtitleTimestampMode.WITH_TIMESTAMP,
    val useYouTubeDataApi: Boolean = false,
    val youtubeDataApiKey: String = "",
    val subtitleDelayMode: String = "AUTO",
    val subtitleBaseDelayMs: Long = 0,
    val subtitleJitterMinMs: Long = 0,
    val subtitleJitterMaxMs: Long = 0,
    val subtitleConcurrency: Int = 1,
    val apiDelayMode: String = "NONE",
    val apiBaseDelayMs: Long = 0,
    val apiJitterMinMs: Long = 0,
    val apiJitterMaxMs: Long = 0
)
object UrlValidator {
    private val youtube = Regex("^https?://(www\\.)?(youtube\\.com|youtu\\.be)/.*", RegexOption.IGNORE_CASE)
    fun isValid(url: String): Boolean = url.trim().let {
        it.isNotEmpty() && youtube.matches(it) &&
            (it.contains("watch?v=") || it.contains("youtu.be/") || it.contains("/playlist?") ||
                it.contains("/channel/") || it.contains("/c/") || it.contains("/@"))
    }
}
object FileNameSanitizer {
    fun sanitize(input: String): String = java.text.Normalizer.normalize(input.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("[\\p{M}]+"), "").replace('đ','d')
        .replace(Regex("[/\\\\:*?\"<>|]"), "").trim().replace(Regex("\\s+"), "_").take(80)
}