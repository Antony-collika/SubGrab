package com.subgrab.app

import com.subgrab.app.data.RecentCodec
import com.subgrab.app.data.RecentItem
import com.subgrab.app.data.RecentKind
import com.subgrab.app.data.VideoResultCodec
import com.subgrab.app.domain.DownloadReasons
import com.subgrab.app.domain.FailureType
import com.subgrab.app.domain.VideoDownloadResult
import com.subgrab.app.domain.VideoOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadResultsTest {
    @Test
    fun everyFailureTypeHasAVietnameseReason() {
        FailureType.entries.forEach { type ->
            val reason = DownloadReasons.forFailure(type)
            assertTrue("$type thiếu lý do", reason.isNotBlank())
        }
        assertEquals("Video không có phụ đề", DownloadReasons.forFailure(FailureType.NO_SUBTITLE))
        assertEquals("YouTube đang giới hạn request (429)", DownloadReasons.forFailure(FailureType.HTTP_429))
    }

    @Test
    fun genericFailuresAppendShortDetail() {
        val reason = DownloadReasons.forFailure(FailureType.UNKNOWN, "  boom \n   bang  ")
        assertEquals("Lỗi không xác định: boom bang", reason)
        // Lỗi đã rõ nguyên nhân thì không thêm chi tiết kỹ thuật.
        assertEquals(DownloadReasons.forFailure(FailureType.TIMEOUT), DownloadReasons.forFailure(FailureType.TIMEOUT, "socket"))
    }

    @Test
    fun resultCodecRoundTripsAndSurvivesControlCharacters() {
        val input = listOf(
            VideoDownloadResult("a1", "Tiêu đề | có dấu gạch", VideoOutcome.SAVED),
            VideoDownloadResult("b2", "Video\u001fcó\u001eký tự lạ", VideoOutcome.FAILED, "Mất kết nối mạng"),
            VideoDownloadResult("c3", "Bị hủy", VideoOutcome.NOT_RUN, DownloadReasons.CANCELLED),
            VideoDownloadResult("d4", "Không phụ đề", VideoOutcome.SKIPPED, "Video không có phụ đề")
        )
        val decoded = VideoResultCodec.decode(VideoResultCodec.encode(input))
        assertEquals(4, decoded.size)
        assertEquals(VideoOutcome.SAVED, decoded[0].outcome)
        assertNull(decoded[0].reason)
        assertEquals("Tiêu đề | có dấu gạch", decoded[0].title)
        assertEquals("Mất kết nối mạng", decoded[1].reason)
        assertEquals("Video có ký tự lạ", decoded[1].title)
        assertEquals(DownloadReasons.CANCELLED, decoded[2].reason)
        assertEquals(VideoOutcome.SKIPPED, decoded[3].outcome)
    }

    @Test
    fun resultCodecHandlesEmptyAndGarbage() {
        assertTrue(VideoResultCodec.decode("").isEmpty())
        assertTrue(VideoResultCodec.decode("khong-hop-le").isEmpty())
    }

    @Test
    fun recentCodecRoundTripsAndDeduplicates() {
        val a = RecentItem(RecentKind.PLAYLIST, "https://youtube.com/playlist?list=1", "Playlist du lịch", 32, 1000)
        val b = RecentItem(RecentKind.KEYWORD, "học Python", "học Python", 50, 2000)
        val decoded = RecentCodec.decode(RecentCodec.encode(listOf(b, a)))
        assertEquals(listOf(b, a), decoded)

        val pushedAgain = RecentCodec.push(listOf(b, a), a.copy(timestamp = 3000, videoCount = 40))
        assertEquals(2, pushedAgain.size)
        assertEquals(3000L, pushedAgain.first().timestamp)
        assertEquals(RecentKind.PLAYLIST, pushedAgain.first().kind)
    }

    @Test
    fun recentCodecKeepsAtMostEightItems() {
        val many = (1..12).map { RecentItem(RecentKind.VIDEO, "v$it", "Video $it", 1, it.toLong()) }
        var list = emptyList<RecentItem>()
        many.forEach { list = RecentCodec.push(list, it) }
        assertEquals(8, list.size)
        assertEquals("v12", list.first().input)
    }
}
