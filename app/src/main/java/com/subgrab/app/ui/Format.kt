package com.subgrab.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Các hàm định dạng thuần (không phụ thuộc Android) để hiển thị số liệu theo kiểu Việt Nam. */
object Format {
    /** 340 -> "340", 1500 -> "1,5 N", 340000 -> "340 N", 1200000 -> "1,2 Tr", 2500000000 -> "2,5 Tỷ". */
    fun count(value: Long): String = when {
        value < 1_000 -> value.toString()
        value < 999_500 -> compact(value / 1_000.0, "N")
        value < 999_500_000 -> compact(value / 1_000_000.0, "Tr")
        else -> compact(value / 1_000_000_000.0, "Tỷ")
    }

    private fun compact(x: Double, unit: String): String {
        val rounded = if (x < 10) (x * 10).roundToLong() / 10.0 else x.roundToLong().toDouble()
        val text = if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else rounded.toString().replace('.', ',')
        return "$text $unit"
    }

    /** 754 -> "12:34", 3723 -> "1:02:03". */
    fun duration(seconds: Long): String {
        val total = seconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** "còn khoảng 3 phút" / "còn 40 giây" / "Đang tính thời gian…". */
    fun etaLabel(seconds: Long?): String = when {
        seconds == null -> "Đang tính thời gian…"
        seconds < 60 -> "còn ${seconds.coerceAtLeast(1)} giây"
        else -> "còn khoảng ${ceil(seconds / 60.0).toLong()} phút"
    }

    /** "hôm nay", "hôm qua", "3 ngày trước", hoặc ngày đầy đủ nếu quá 6 ngày. */
    fun relativeDay(timestamp: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val day = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(day, today)
        return when {
            days <= 0 -> "hôm nay"
            days == 1L -> "hôm qua"
            days < 7 -> "$days ngày trước"
            else -> day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
    }

    /** "Hôm nay 14:05", "Hôm qua", "2 ngày trước". */
    fun relativeWithTime(timestamp: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val label = relativeDay(timestamp, now, zone)
        if (label == "hôm nay") {
            val time = Instant.ofEpochMilli(timestamp).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
            return "Hôm nay $time"
        }
        return label.replaceFirstChar { it.uppercaseChar() }
    }

    fun dateOnly(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM"))

    fun localDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}
