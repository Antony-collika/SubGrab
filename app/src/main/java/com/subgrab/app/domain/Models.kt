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
data class UrlCandidate(
    val raw: String,
    val normalized: String,
    val start: Int,
    val end: Int
)

/** Extract web URL candidates from arbitrary text before applying service-specific parsing. */
object WebUrlExtractor {
    private val candidate = Regex(
        """(?i)(?<![\w@])(?:https?://|www\.)[^\s<>\[\]{}"']+|(?<![\w@])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}(?::\d{1,5})?(?:/[^\s<>\[\]{}"']*)?"""
    )
    private val trailingPunctuation = Regex("""[.,;:!?…)\]}>'"]+$""")

    fun extract(input: String): List<UrlCandidate> = candidate.findAll(input)
        .mapNotNull { match ->
            val normalized = match.value
                .trimStart('(', '[', '{', '<', '"', '\'')
                .replace(trailingPunctuation, "")
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            UrlCandidate(match.value, normalized, match.range.first, match.range.first + normalized.length)
        }
        .distinctBy { it.start to it.normalized }
        .toList()
}

object YoutubeUrlParser {
    fun extractUrls(input: String): List<String> =
        WebUrlExtractor.extract(input)
            .mapNotNull { candidate ->
                val normalized = normalize(candidate.normalized)
                if (parse(normalized) != null) canonicalize(normalized) else null
            }
            .distinct()

    fun normalize(url: String): String =
        url.trim().trimStart('(', '[', '{', '<', '"', '\'')
            .replace(Regex("""[.,;:!?…)\]}>'"]+$"""), "")

    fun isValid(url: String): Boolean = parse(url) != null
    fun isVideoUrl(url: String): Boolean = parse(url)?.type == Type.VIDEO
    fun videoId(url: String): String? = parse(url)?.videoId
    fun isPlaylistUrl(url: String): Boolean = parse(url)?.type == Type.PLAYLIST
    fun isChannelUrl(url: String): Boolean = parse(url)?.type == Type.CHANNEL

    private fun parse(rawUrl: String): Parsed? {
        val normalized = normalize(rawUrl)
        if (normalized.isBlank()) return null
        val url = if (normalized.contains("://")) normalized else "https://$normalized"
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme !in setOf("http", "https") || !isYoutubeHost(host)) return null

        val segments = uri.path
            ?.split('/')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        if (host == "youtu.be") {
            val id = segments.firstOrNull()?.takeIf { it.isNotBlank() }
            return id?.let { Parsed(Type.VIDEO, it) }
        }

        return when {
            segments.firstOrNull()?.equals("watch", true) == true ->
                queryParameter(uri.rawQuery, "v")?.takeIf { it.isNotBlank() }?.let { Parsed(Type.VIDEO, it) }
            segments.firstOrNull()?.equals("playlist", true) == true ->
                queryParameter(uri.rawQuery, "list")?.takeIf { it.isNotBlank() }?.let { Parsed(Type.PLAYLIST, null) }
            segments.firstOrNull()?.equals("channel", true) == true &&
                segments.getOrNull(1).isNullOrBlank().not() -> Parsed(Type.CHANNEL, null)
            segments.firstOrNull()?.equals("c", true) == true &&
                segments.getOrNull(1).isNullOrBlank().not() -> Parsed(Type.CHANNEL, null)
            segments.firstOrNull()?.startsWith("@") == true &&
                segments.first().length > 1 -> Parsed(Type.CHANNEL, null)
            else -> null
        }
    }

    private fun isYoutubeHost(host: String?): Boolean =
        host == "youtu.be" || host == "youtube.com" || host?.endsWith(".youtube.com") == true

    private fun queryParameter(rawQuery: String?, name: String): String? =
        rawQuery
            ?.split('&')
            ?.asSequence()
            ?.mapNotNull { part ->
                val separator = part.indexOf('=')
                val key = if (separator >= 0) part.substring(0, separator) else part
                if (key != name) return@mapNotNull null
                if (separator < 0) return@mapNotNull ""
                runCatching {
                    java.net.URLDecoder.decode(part.substring(separator + 1), Charsets.UTF_8.name())
                }.getOrNull()
            }
            ?.firstOrNull()

    private fun canonicalize(url: String): String =
        if (url.contains("://")) url else "https://$url"

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