package com.subgrab.app.data

import com.subgrab.app.domain.SubtitleCue

object SubtitleParser {
    private val timingLine = Regex(
        """^\s*(\d{1,2}:)?\d{2}:\d{2}[\.,]\d{3}\s*-->\s*(\d{1,2}:)?\d{2}:\d{2}[\.,]\d{3}.*$"""
    )

    fun parseWebVtt(input: String): List<SubtitleCue> {
        val lines = input.replace("\r\n", "\n").replace("\r", "\n").lines()
        val cues = mutableListOf<SubtitleCue>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index].trim()
            if (line.isBlank() || line == "WEBVTT" || line.startsWith("NOTE") ||
                line.startsWith("STYLE") || line.startsWith("REGION") ||
                line.startsWith("Kind:") || line.startsWith("Language:")
            ) {
                index++
                continue
            }

            val timingIndex = if (timingLine.matches(line)) index else {
                if (index + 1 < lines.size && timingLine.matches(lines[index + 1].trim())) index + 1 else -1
            }
            if (timingIndex < 0) {
                index++
                continue
            }

            val timing = lines[timingIndex].trim()
            val parts = timing.split("-->", limit = 2)
            val start = parseTimestamp(parts[0].trim())
            val end = parseTimestamp(parts[1].trim().substringBefore(' '))
            index = timingIndex + 1

            val textLines = mutableListOf<String>()
            while (index < lines.size && lines[index].isNotBlank()) {
                textLines += lines[index]
                index++
            }
            cues += SubtitleCue(start, end, textLines.joinToString("\n"))
        }

        return SubtitleNormalizer.mergeConsecutive(cues)
    }

    private fun parseTimestamp(value: String): Long {
        val normalized = value.replace(',', '.')
        val parts = normalized.split(':')
        require(parts.size == 3) { "Timestamp WebVTT không hợp lệ: $value" }
        val hours = parts[0].toLong()
        val minutes = parts[1].toLong()
        val seconds = parts[2].toDouble()
        return ((hours * 3600 + minutes * 60) * 1000 + (seconds * 1000).toLong())
    }
}
