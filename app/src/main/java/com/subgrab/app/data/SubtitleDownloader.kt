package com.subgrab.app.data

import com.subgrab.app.data.repository.KnowledgeRepository
import com.subgrab.app.domain.*
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
    suspend fun listSubtitles(videoUrl: String): Result<List<SubtitleLanguage>> =
        extractorClient.listSubtitles(videoUrl)

    suspend fun canSatisfyFromRoom(videoId: String, language: String, format: OutputFormat, preferManual: Boolean): Boolean {
        if (format != OutputFormat.TXT || preferManual) return false
        val cached = knowledgeRepository?.getTranscript(videoId) ?: return false
        val stored = cached.language ?: return false
        return !cached.content.isNullOrBlank() && matchesLanguage(stored, language)
    }

    suspend fun download(video: VideoItem, config: DownloadConfig, outputDir: File): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val cached = knowledgeRepository?.getTranscript(video.videoId)
            val cachedLanguage = cached?.language
            val cachedContent = cached?.content?.takeIf { it.isNotBlank() }
            val requested = config.languages.distinct().filter { it.isNotBlank() }
            val files = mutableListOf<File>()

            if (!config.preferManual && cachedLanguage != null && cachedContent != null && OutputFormat.TXT in config.formats &&
                requested.any { matchesLanguage(cachedLanguage, it) }) {
                files += write(outputDir, fileBase(video, cachedLanguage) + ".txt", cachedContent)
            }

            val needsSource = OutputFormat.SRT in config.formats ||
                requested.none { cachedLanguage != null && cachedContent != null && matchesLanguage(cachedLanguage, it) }
            if (!needsSource) return@runCatching files.distinctBy { it.absolutePath }

            val url = "https://www.youtube.com/watch?v=" + video.videoId
            val extractor = extractorClient.fetchSubtitleExtractor(url)
            val tracks = extractor.getSubtitles(MediaFormat.VTT).toList()
            if (tracks.isEmpty()) throw SubtitleFailure(FailureType.NO_SUBTITLE, "Không tìm thấy phụ đề")

            val selected = linkedMapOf<String, SubtitlesStream>()
            requested.forEach { language ->
                selectBulkTrack(tracks, language, config.preferManual)?.let { selected[trackKey(it)] = it }
            }
            if (selected.isEmpty()) tracks.firstOrNull()?.let { selected[trackKey(it)] = it }
            if (selected.isEmpty()) throw SubtitleFailure(FailureType.LANGUAGE_UNAVAILABLE, "Không tìm thấy subtitle phù hợp")

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
                    val roomMatches = cachedLanguage != null && cachedContent != null && matchesLanguage(cachedLanguage, language)
                    if (config.preferManual || !roomMatches) files += write(outputDir, base + ".txt", cleanText)
                }
                if (cachedLanguage.isNullOrBlank() || cachedContent.isNullOrBlank()) {
                    knowledgeRepository?.saveTranscript(video.videoId, cleanText, language)
                }
            }
            files.distinctBy { it.absolutePath }
        }
    }

    suspend fun downloadSingle(video: VideoItem, language: SubtitleLanguage, format: OutputFormat, outputDir: File, timestampMode: SubtitleTimestampMode = SubtitleTimestampMode.WITH_TIMESTAMP): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val cached = knowledgeRepository?.getTranscript(video.videoId)
            val cachedLanguage = cached?.language
            val cachedContent = cached?.content?.takeIf { it.isNotBlank() }
            val files = mutableListOf<File>()
            val url = "https://www.youtube.com/watch?v=" + video.videoId
            val extractor = extractorClient.fetchSubtitleExtractor(url)
            val tracks = extractor.getSubtitles(MediaFormat.VTT).toList()
            val track = selectExactTrack(tracks, language)
                ?: throw SubtitleFailure(FailureType.LANGUAGE_UNAVAILABLE, "Không tìm thấy subtitle: " + language.name)
            val rawVtt = downloader.fetchText(track.content, url)
            val cues = SubtitleParser.parseWebVtt(rawVtt)
            val cleanText = SubtitleFormatter.format(cues, SubtitleTimestampMode.WITHOUT_TIMESTAMP)
            val base = fileBase(video, track.getLanguageTag())
            if (format == OutputFormat.SRT) files += write(outputDir, base + ".srt", SubtitleFormatter.format(cues, timestampMode))
            else files += write(outputDir, base + ".txt", cleanText)
            if (cachedLanguage.isNullOrBlank() || cachedContent.isNullOrBlank()) {
                knowledgeRepository?.saveTranscript(video.videoId, cleanText, track.getLanguageTag())
            }
            files
        }
    }

    suspend fun fetchAndPersistTranscript(videoId: String, languages: List<String>, preferManual: Boolean, requestedLanguage: String? = null, requestedAuto: Boolean? = null, requestedFormat: OutputFormat = OutputFormat.TXT, forceRefresh: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = knowledgeRepository?.getTranscript(videoId)
            val wantedLanguage = requestedLanguage ?: languages.firstOrNull { language -> existing?.language?.let { stored -> matchesLanguage(stored, language) } == true }
                ?: languages.firstOrNull() ?: throw SubtitleFailure(FailureType.LANGUAGE_UNAVAILABLE, "Chưa chọn ngôn ngữ transcript")
            val existingLanguage = existing?.language
            val existingContent = existing?.content?.takeIf { it.isNotBlank() }
            if (!existingLanguage.isNullOrBlank() && existingContent != null && matchesLanguage(existingLanguage, wantedLanguage)) return@runCatching existingLanguage
            if (!existingLanguage.isNullOrBlank() && existingContent != null && !forceRefresh) return@runCatching existingLanguage
            val url = "https://www.youtube.com/watch?v=" + videoId
            val extractor = extractorClient.fetchSubtitleExtractor(url)
            val tracks = extractor.getSubtitles(MediaFormat.VTT).toList()
            val track = if (requestedLanguage != null && requestedAuto != null) {
                selectExactTrack(tracks, SubtitleLanguage(wantedLanguage, requestedAuto))
            } else {
                selectTrackForLanguage(tracks, wantedLanguage, preferManual)
            } ?: throw SubtitleFailure(FailureType.LANGUAGE_UNAVAILABLE, "Không tìm thấy subtitle: " + wantedLanguage)
            val rawVtt = downloader.fetchText(track.content, url)
            val cues = SubtitleParser.parseWebVtt(rawVtt)
            val cleanText = SubtitleFormatter.format(cues, SubtitleTimestampMode.WITHOUT_TIMESTAMP)
            val actualLanguage = track.getLanguageTag()
            if (requestedFormat == OutputFormat.TXT) {
                if (existingLanguage.isNullOrBlank() || existingContent == null) knowledgeRepository?.saveTranscript(videoId, cleanText, actualLanguage)
                else if (forceRefresh && !matchesLanguage(existingLanguage, actualLanguage)) knowledgeRepository?.saveTranscript(videoId, cleanText, actualLanguage, overwrite = true)
            }
            actualLanguage
        }
    }

    private fun selectBulkTrack(tracks: List<SubtitlesStream>, wantedLanguage: String, preferManual: Boolean): SubtitlesStream? {
        val matches = tracks.filter { matchesLanguage(it.getLanguageTag(), wantedLanguage) }
        if (matches.isEmpty()) return null
        return if (preferManual) matches.firstOrNull { !it.isAutoGenerated() } ?: matches.firstOrNull() else matches.firstOrNull()
    }
    private fun selectExactTrack(tracks: List<SubtitlesStream>, wanted: SubtitleLanguage): SubtitlesStream? =
        tracks.firstOrNull { it.getLanguageTag().equals(wanted.code, true) && it.isAutoGenerated() == wanted.isAuto }
            ?: tracks.firstOrNull { matchesLanguage(it.getLanguageTag(), wanted.code) && it.isAutoGenerated() == wanted.isAuto }
    private fun selectTrackForLanguage(tracks: List<SubtitlesStream>, wantedLanguage: String, preferManual: Boolean): SubtitlesStream? =
        selectBulkTrack(tracks, wantedLanguage, preferManual)
    private fun trackKey(track: SubtitlesStream): String = track.getLanguageTag() + "|" + track.isAutoGenerated()
    private fun matchesLanguage(actual: String, wanted: String): Boolean = actual.equals(wanted, true) || actual.startsWith(wanted + "-", true)
    private fun fileBase(video: VideoItem, language: String): String = video.index.toString().padStart(3, '0') + " - " + FileNameSanitizer.sanitize(video.title) + "-" + language
    private fun write(directory: File, name: String, content: String): File = runCatching { File(directory, name).apply { parentFile?.mkdirs(); writeText(content) } }.getOrElse { throw StorageFailure("Không thể ghi subtitle: " + name, it) }
}

class SubtitleFailure(val type: FailureType, message: String) : RuntimeException(message)