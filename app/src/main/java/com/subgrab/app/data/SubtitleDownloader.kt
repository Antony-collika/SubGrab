package com.subgrab.app.data

import com.subgrab.app.domain.DownloadConfig
import com.subgrab.app.domain.FileNameSanitizer
import com.subgrab.app.domain.OutputFormat
import com.subgrab.app.domain.SubtitleTimestampMode
import com.subgrab.app.domain.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import java.io.File

class SubtitleDownloader(
    private val extractorClient: NewPipeExtractorClient,
    private val downloader: NewPipeDownloader
) {
    suspend fun download(
        video: VideoItem,
        config: DownloadConfig,
        outputDir: File
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://www.youtube.com/watch?v=" + video.videoId
            val extractor = extractorClient.streamExtractor(url)
            extractor.fetchPage()
            val tracks = extractor.getSubtitles(MediaFormat.VTT)
                .filter { track ->
                    config.languages.any { wanted ->
                        track.getLanguageTag().equals(wanted, true) ||
                            track.getLanguageTag().startsWith(wanted + "-")
                    }
                }
            require(tracks.isNotEmpty()) { "NewPipeExtractor không tìm thấy subtitle phù hợp" }

            val files = mutableListOf<File>()
            tracks.forEach { track ->
                val rawVtt = downloader.fetchText(track.content, url)
                val cues = SubtitleParser.parseWebVtt(rawVtt)
                val language = track.getLanguageTag()
                val base = video.index.toString().padStart(3, '0') + " - " +
                    FileNameSanitizer.sanitize(video.title) + "-" + language

                if (OutputFormat.SRT in config.formats) {
                    files += write(outputDir, "$base.srt", SubtitleFormatter.format(cues, config.timestampMode))
                }
                if (OutputFormat.TXT in config.formats) {
                    files += write(outputDir, "$base.txt", SubtitleFormatter.format(cues, SubtitleTimestampMode.WITHOUT_TIMESTAMP))
                }
            }
            files
        }
    }

    private fun write(directory: File, name: String, content: String): File =
        File(directory, name).apply {
            parentFile?.mkdirs()
            writeText(content)
        }
}
