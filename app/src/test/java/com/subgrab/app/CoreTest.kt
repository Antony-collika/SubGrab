package com.subgrab.app

import com.subgrab.app.data.SubtitleFormatter\nimport com.subgrab.app.data.FailureClassifier\nimport com.subgrab.app.data.RequestGovernor\nimport com.subgrab.app.domain.FailureType\nimport com.subgrab.app.domain.RequestLane\nimport com.subgrab.app.domain.RequestResult
import com.subgrab.app.data.SubtitleParser
import com.subgrab.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    @Test fun acceptsSupportedYoutubeUrls() {
        listOf(
            "https://www.youtube.com/@demo",
            "https://youtube.com/c/demo",
            "https://www.youtube.com/channel/UC1",
            "https://www.youtube.com/playlist?list=PL1",
            "https://www.youtube.com/watch?v=abc",
            "https://youtu.be/abc"
        ).forEach { assertTrue(UrlValidator.isValid(it)) }
    }

    @Test fun rejectsUnsupportedUrls() {
        listOf("abcxyz", "https://vimeo.com/1", "https://facebook.com/a", "https://youtube.com/about")
            .forEach { assertFalse(UrlValidator.isValid(it)) }
    }

    @Test fun sanitizesVietnameseAndSpecialCharacters() {
        assertEquals("huong_dan_kotlin", FileNameSanitizer.sanitize("Hướng dẫn Kotlin: *"))
    }

    @Test fun parsesInlineWebVttTimestampsAndMarkup() {
        val cues = SubtitleParser.parseWebVtt(
            """
            WEBVTT

            00:00:10.000 --> 00:00:13.000
            Good<00:00:11.599><c> day.</c>
            """.trimIndent()
        )
        assertEquals(1, cues.size)
        assertEquals("Good day.", cues.single().text)
        assertEquals(
            "1\n00:00:10,000 --> 00:00:13,000\nGood day.\n",
            SubtitleFormatter.format(cues, SubtitleTimestampMode.WITH_TIMESTAMP)
        )
    }

    @Test fun formatsWithoutTimestamp() {
        val cues = SubtitleParser.parseWebVtt(
            """
            WEBVTT

            00:00:00.000 --> 00:00:02.000
            Good<00:00:00.500><c> day.</c>

            00:00:02.000 --> 00:00:04.000
            Good day.
            """.trimIndent()
        )
        assertEquals("Good day.\n", SubtitleFormatter.format(cues, SubtitleTimestampMode.WITHOUT_TIMESTAMP))
    }

    @Test fun taskProgressNeverExceedsLimit() {
        val videos = (1..50).map { VideoItem(it, "$it", "Video $it", 60, listOf(SubtitleLanguage("vi"))) }
        assertEquals(50, videos.size)
    }
}
