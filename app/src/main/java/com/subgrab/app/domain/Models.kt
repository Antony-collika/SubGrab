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
    val subtitleConcurrency: Int = 1,
    val maxSubtitlesPerTask: Int = 10
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
    val maxSubtitlesPerTask: Int = 10,
    val apiDelayMode: String = "NONE",
    val apiBaseDelayMs: Long = 0,
    val apiJitterMinMs: Long = 0,
    val apiJitterMaxMs: Long = 0
)
object YoutubeUrlParser {
    private val urlStart = Regex("""(?i)https?://[^\\s]*?(?=https?://|\\s|$)""")
    private val trailingPunctuation = Regex("""[.,;:!?…\\)\\]\\}>'"]+$""")

    fun extractUrls(input: String): List<String> =
        urlStart.findAll(input)
            .map { normalize(it.value) }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    fun normalize(url: String): String =
        url.trim().trimStart('(', '[', '{', '<', '"', ''')
            .replace(trailingPunctuation, "")

    fun isValid(url: String): Boolean = parse(url) != null

    fun isVideoUrl(url: String): Boolean = parse(url)?.type == Type.VIDEO

    fun videoId(url: String): String? = parse(url)?.videoId

    fun isPlaylistUrl(url: String): Boolean = parse(url)?.type == Type.PLAYLIST

    fun isChannelUrl(url: String): Boolean = parse(url)?.type == Type.CHANNEL

    private fun parse(rawUrl: String): Parsed? {
        val url = normalize(rawUrl)
        if (url.isBlank()) return null
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme !in setOf("http", "https") || host !in setOf("youtube.com", "www.youtube.com", "youtu.be")) {
            return null
        }

        if (host == "youtu.be") {
            val id = uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
            return id?.let { Parsed(Type.VIDEO, it) }
        }

        val segments = uri.pathSegments.filter { it.isNotBlank() }
        return when {
            segments.firstOrNull()?.equals("watch", true) == true -> {
                uri.getQueryParameter("v")?.takeIf { it.isNotBlank() }?.let { Parsed(Type.VIDEO, it) }
            }
            segments.firstOrNull()?.equals("playlist", true) == true -> {
                uri.getQueryParameter("list")?.takeIf { it.isNotBlank() }?.let { Parsed(Type.PLAYLIST, null) }
            }
            segments.firstOrNull()?.equals("channel", true) == true &&
                segments.getOrNull(1).isNullOrBlank().not() -> Parsed(Type.CHANNEL, null)
            segments.firstOrNull()?.equals("c", true) == true &&
                segments.getOrNull(1).isNullOrBlank().not() -> Parsed(Type.CHANNEL, null)
            segments.firstOrNull()?.startsWith("@") == true &&
                segments.first().length > 1 -> Parsed(Type.CHANNEL, null)
            else -> null
        }
    }

    private enum class Type { VIDEO, PLAYLIST, CHANNEL }
    private data class Parsed(val type: Type, val videoId: String?)
}

object UrlValidator {
    fun isValid(url: String): Boolean = YoutubeUrlParser.isValid(url)
}
object FileNameSanitizer {
    fun sanitize(input: String): String = java.text.Normalizer.normalize(input.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("[\\p{M}]+"), "").replace('đ','d')
        .replace(Regex("[/\\\\:*?\"<>|]"), "").trim().replace(Regex("\\s+"), "_").take(80)
}