package com.subgrab.app.data

import com.subgrab.app.domain.SubtitleCue

object SubtitleNormalizer {
    private val inlineTimestamp = Regex("""<\d{1,2}:\d{2}:\d{2}\.\d{3}>""")
    private val cueClass = Regex("""</?c(?:\.[^ >]+)?(?:\s+[^>]*)?>""", RegexOption.IGNORE_CASE)
    private val webVttTag = Regex("""</?(?:b|i|u|ruby|rt|v)(?:\s+[^>]*)?>""", RegexOption.IGNORE_CASE)
    private val htmlEntity = Regex("""&(amp|lt|gt|quot|#39);""", RegexOption.IGNORE_CASE)

    fun normalize(cue: SubtitleCue): SubtitleCue = cue.copy(text = clean(cue.text))

    fun clean(text: String): String {
        var value = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(inlineTimestamp, "")
            .replace(cueClass, "")
            .replace(webVttTag, "")
            .replace(Regex("""<[^>]+>"""), "")
            .replace("\u200B", "")
            .replace(htmlEntity) {
                when (it.value.lowercase()) {
                    "&amp;" -> "&"
                    "&lt;" -> "<"
                    "&gt;" -> ">"
                    "&quot;" -> "\""
                    "&#39;" -> "'"
                    else -> it.value
                }
            }

        value = value
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .fold(mutableListOf<String>()) { lines, line ->
                if (lines.lastOrNull() != line) lines += line
                lines
            }
            .joinToString("\n")
            .replace(Regex("""[ \t]+"""), " ")
            .trim()

        return value
    }

    fun mergeConsecutive(cues: List<SubtitleCue>): List<SubtitleCue> {
        if (cues.isEmpty()) return emptyList()
        val result = mutableListOf<SubtitleCue>()
        cues.forEach { cue ->
            val normalized = normalize(cue)
            if (normalized.text.isBlank()) return@forEach

            val previous = result.lastOrNull()
            if (previous != null &&
                previous.text == normalized.text &&
                normalized.startMs <= previous.endMs + 250
            ) {
                result[result.lastIndex] = previous.copy(endMs = maxOf(previous.endMs, normalized.endMs))
                return@forEach
            }

            result += normalized
        }
        return result
    }

    /**
     * Removes YouTube rolling-caption overlap for plain-text export.
     *
     * This is intentionally separate from mergeConsecutive(): timestamped
     * exports must preserve the original cue text so that cue timing remains
     * faithful to the source VTT.
     */
    fun mergeRollingForText(cues: List<SubtitleCue>): List<SubtitleCue> {
        if (cues.isEmpty()) return emptyList()

        val result = mutableListOf<SubtitleCue>()
        cues.forEach { cue ->
            val normalized = normalize(cue)
            if (normalized.text.isBlank()) return@forEach

            val previous = result.lastOrNull()
            if (previous == null) {
                result += normalized
                return@forEach
            }

            val previousLines = previous.text.lines()
            val currentLines = normalized.text.lines()
            val overlap = maxLineOverlap(previousLines, currentLines)

            if (overlap == currentLines.size) {
                // The whole cue is already represented by the preceding cue.
                // Keep its timing information by extending the previous cue.
                result[result.lastIndex] = previous.copy(
                    endMs = maxOf(previous.endMs, normalized.endMs)
                )
                return@forEach
            }

            val remaining = currentLines.drop(overlap).joinToString("\n").trim()
            if (remaining.isBlank()) {
                result[result.lastIndex] = previous.copy(
                    endMs = maxOf(previous.endMs, normalized.endMs)
                )
            } else if (overlap > 0) {
                result += normalized.copy(text = remaining)
            } else {
                result += normalized
            }
        }

        return result
    }

    private fun maxLineOverlap(previousLines: List<String>, currentLines: List<String>): Int {
        val max = minOf(previousLines.size, currentLines.size)
        for (size in max downTo 1) {
            if (previousLines.takeLast(size) == currentLines.take(size)) {
                return size
            }
        }
        return 0
    }
}
