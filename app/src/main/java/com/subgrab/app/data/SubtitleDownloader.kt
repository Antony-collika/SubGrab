package com.subgrab.app.data

import com.subgrab.app.domain.*
import com.subgrab.app.data.repository.KnowledgeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import java.io.File

class SubtitleDownloader(
    private val extractorClient: NewPipeExtractorClient,
    private val downloader: NewPipeDownloader,
    private val knowledgeRepository: KnowledgeRepository? = null
) {
    suspend fun canSatisfyFromRoom(videoId: String, languages: List<String>, formats: Set<OutputFormat>): Boolean {
        if (formats != setOf(OutputFormat.TXT)) return false
        val cached = knowledgeRepository?.getTranscript(videoId) ?: return false
        val stored = cached.language ?: return false
        return !cached.content.isNullOrBlank() && languages.any { matchesLanguage(stored, it) }
    }

    suspend fun download(video: VideoItem, config: DownloadConfig, outputDir: File): Result<List<File>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cached = knowledgeRepository?.getTranscript(video.videoId)
                val cachedLanguage = cached?.language
                val cachedContent = cached?.content?.takeIf { it.isNotBlank() }
                val requested = config.languages.distinct().filter { it.isNotBlank() }
                val files = mutableListOf<File>()

                if (cachedLanguage != null && cachedContent != null && OutputFormat.TXT in config.formats) {
                    files += write(outputDir, fileBase(video, cachedLanguage) + ".txt", cachedContent)
                }

                val needsSource = OutputFormat.SRT in config.formats ||
                    requested.any { cachedLanguage == null || cachedContent == null || !matchesLanguage(cachedLanguage, it) }
                if (!needsSource) return@runCatching files

                val url = "https://www.youtube.com/watch?v=" + video.videoId
                val extractor = extractorClient.fetchSubtitleExtractor(url)
                val tracks = extractor.getSubtitles(MediaFormat.VTT).toList()
                if (tracks.isEmpty()) throw SubtitleFailure(FailureType.LANGUAGE_UNAVAILABLE, "Không tìm thấy subtitle phù hợp")

                val selected = linkedMapOf<String, SubtitlesStream>()
                if (cachedLanguage != null && OutputFormat.SRT in config.formats) {
                    selectTrackForLanguage(tracks, cachedLanguage, config.preferManual)?.let { selected[it.getLanguageTag()] = it }
                }
                requested.forEach { language ->
                    val cachedMatch = cachedLanguage != null && cachedContent != null && matchesLanguage(cachedLanguage, language)
                    if (!cachedMatch || OutputFormat.SRT in config.formats) {
                        val track = selectTrackForLanguage(tracks, language, config.preferManual)
                            ?: throw SubtitleFailure(FailureType.LANGUAGE_UNAVAILABLE, "Không tìm thấy subtitle: " + language)
                        selected[track.getLanguageTag()] = track
                    }
                }

                selected.values.forEach { track ->
                    val rawVtt = downloader.fetchText(track.content, url)
                    val cues = SubtitleParser.parseWebVtt(rawVtt)
                    val language = track.getLanguageTag()
                    val base = fileBase(video, language)
                    val cleanText = SubtitleFormatter.format(cues, SubtitleTimestampMode.WITHOUT_TIMESTAMP)
                    if (OutputFormat.SRT in config.formats) {
                        files += write(outputDir, base + ".srt", SubtitleFormatter.format(cues, config.timestampMode))
                    }
                    if (OutputFormat.TXT in config.formats) {
                        val fromRoom = cachedLanguage != null && cachedContent != null && matchesLanguage(cachedLanguage, language)
                        if (!fromRoom) {
                            files += write(outputDir, "$" + "base.txt", cleanText)
                            knowledgeRepository?.saveTranscript(video.videoId, cleanText, language)
                        }
                    }
                }
                files.distinctBy { it.absolutePath }
            }
        }

    suspend fun fetchAndPersistTranscript(
        videoId: String,
        languages: List<String>,
        preferManual: Boolean,
        requestedLanguage: String? = null,
        requestedFormat: OutputFormat = OutputFormat.TXT,
        forceRefresh: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = knowledgeRepository?.getTranscript(videoId)
            val wantedLanguage = requestedLanguage
                ?: languages.firstOrNull { existing?.language?.let { stored -> matchesLanguage(stored, it) } == true }
                ?: languages.firstOrNull { it.equals("en", true) }
                ?: languages.firstOrNull()
            val languageMatches = wantedLanguage != null && existing?.language?.let { matchesLanguage(it, wantedLanguage) } == true
            if (requestedFormat == OutputFormat.TXT && languageMatches && !existing?.content.isNullOrBlank()) {
                return@runCatching existing!!.language.orEmpty()
            }

            val url = "https://www.youtube.com/watch?v=" + videoId
            val extractor = extractorClient.fetchSubtitleExtractor(url)
            val tracks = extractor.getSubtitles(MediaFormat.VTT).toList()
            val track = if (requestedLanguage == null) {
                selectRoomTrack(tracks, languages, preferManual)
            } else {
                selectTrackForLanguage(tracks, wantedLanguage ?: requestedLanguage, preferManual)
            } ?: throw SubtitleFailure(FailureType.LANGUAGE_UNAVAILABLE, "Không tìm thấy subtitle phù hợp")
            val rawVtt = downloader.fetchText(track.content, url)
            val cues = SubtitleParser.parseWebVtt(rawVtt)
            val cleanText = SubtitleFormatter.format(cues, SubtitleTimestampMode.WITHOUT_TIMESTAMP)
            val language = track.getLanguageTag()
            if (requestedFormat == OutputFormat.TXT) {
                knowledgeRepository?.saveTranscript(videoId, cleanText, language, overwrite = forceRefresh)
            }
            language
        }
    }

    private fun selectRoomTrack(
        tracks: List<SubtitlesStream>,
        languages: List<String>,
        preferManual: Boolean
    ): SubtitlesStream? {
        if (preferManual) tracks.firstOrNull { !it.isAutoGenerated() }?.let { return it }
        languages.firstOrNull { lang -> tracks.any { matchesLanguage(it.getLanguageTag(), lang) } }?.let { lang ->
            return selectTrackForLanguage(tracks, lang, false)
        }
        return tracks.firstOrNull()
    }

    private fun selectTrackForLanguage(tracks: List<SubtitlesStream>, wantedLanguage: String, preferManual: Boolean): SubtitlesStream? {
        val matches = tracks.filter { matchesLanguage(it.getLanguageTag(), wantedLanguage) }
        if (matches.isEmpty()) return null
        return if (preferManual) matches.firstOrNull { !it.isAutoGenerated() } ?: matches.first() else matches.first()
    }

    private fun matchesLanguage(actual: String, wanted: String): Boolean =
        actual.equals(wanted, true) || actual.startsWith(wanted + "-", true)

    private fun fileBase(video: VideoItem, language: String): String =
        video.index.toString().padStart(3, '0') + " - " + FileNameSanitizer.sanitize(video.title) + "-" + language

    private fun write(directory: File, name: String, content: String): File =
        runCatching { File(directory, name).apply { parentFile?.mkdirs(); writeText(content) } }
            .getOrElse { throw StorageFailure("Không thể ghi subtitle: " + name, it) }
}

class SubtitleFailure(val type: FailureType, message: String) : RuntimeException(message)
