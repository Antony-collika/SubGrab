package com.subgrab.app

import com.subgrab.app.ui.Format
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FormatTest {
    @Test
    fun countUsesVietnameseAbbreviations() {
        assertEquals("0", Format.count(0))
        assertEquals("999", Format.count(999))
        assertEquals("1,5 N", Format.count(1_500))
        assertEquals("340 N", Format.count(340_000))
        assertEquals("1 Tr", Format.count(999_999))
        assertEquals("1,2 Tr", Format.count(1_200_000))
        assertEquals("2,5 Tỷ", Format.count(2_500_000_000))
    }

    @Test
    fun durationFormatsMinutesAndHours() {
        assertEquals("0:00", Format.duration(0))
        assertEquals("8:05", Format.duration(485))
        assertEquals("12:34", Format.duration(754))
        assertEquals("1:02:03", Format.duration(3723))
    }

    @Test
    fun etaLabelRoundsUpMinutes() {
        assertEquals("Đang tính thời gian…", Format.etaLabel(null))
        assertEquals("còn 40 giây", Format.etaLabel(40))
        assertEquals("còn khoảng 3 phút", Format.etaLabel(170))
    }

    @Test
    fun relativeDayLabels() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val now = ZonedDateTime.of(2026, 10, 2, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 5, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("hôm nay", Format.relativeDay(at(2, 8), now, zone))
        assertEquals("hôm qua", Format.relativeDay(at(1, 23), now, zone))
        val twoDaysAgo = ZonedDateTime.of(2026, 9, 30, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("2 ngày trước", Format.relativeDay(twoDaysAgo, now, zone))
        assertEquals("Hôm nay 14:05", Format.relativeWithTime(at(2, 14), now, zone))
        assertEquals("Hôm qua", Format.relativeWithTime(at(1, 9), now, zone))
        assertEquals("02/09/2026", Format.relativeDay(ZonedDateTime.of(2026, 9, 2, 9, 0, 0, 0, zone).toInstant().toEpochMilli(), now, zone))
    }
}
