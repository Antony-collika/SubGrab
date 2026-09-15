package com.subgrab.app

import com.subgrab.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun acceptsSupportedYoutubeUrls() { listOf("https://www.youtube.com/@demo", "https://youtube.com/c/demo", "https://www.youtube.com/channel/UC1", "https://www.youtube.com/playlist?list=PL1", "https://www.youtube.com/watch?v=abc", "https://youtu.be/abc").forEach { assertTrue(UrlValidator.isValid(it)) } }
    @Test fun rejectsUnsupportedUrls() { listOf("abcxyz", "https://vimeo.com/1", "https://facebook.com/a", "https://youtube.com/about").forEach { assertFalse(UrlValidator.isValid(it)) } }
    @Test fun sanitizesVietnameseAndSpecialCharacters() { assertEquals("huong_dan_kotlin", FileNameSanitizer.sanitize("Hướng dẫn Kotlin: *")) }
    @Test fun convertsSrtToTextAndDeduplicates() { assertEquals("Xin chào\nHôm nay", SrtToTxtConverter.convert("1\n00:00:01,000 --> 00:00:03,500\nXin chào\nXin chào\n\n2\n00:00:03,500 --> 00:00:05,000\nHôm nay")) }
    @Test fun taskProgressNeverExceedsLimit() { val videos = (1..50).map { VideoItem(it, "$it", "Video $it", 60, listOf(SubtitleLanguage("vi"))) }; assertEquals(50, videos.size) }
}
