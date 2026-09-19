package com.subgrab.app.data

import com.subgrab.app.domain.SubtitleCue
import com.subgrab.app.domain.SubtitleTimestampMode
import java.util.Locale

object SubtitleFormatter {
    fun format(cues: List<SubtitleCue>, mode: SubtitleTimestampMode): String {
        val normalized = SubtitleNormalizer.mergeConsecutive(cues)
        if (mode == SubtitleTimestampMode.WITHOUT_TIMESTAMP) {
            return normalized.joinToString("\n") { it.text }.trimEnd() + "\n"
        }

        return normalized.mapIndexed { index, cue ->
            buildString {
                append(index + 1)
                append('\n')
                append(formatTimestamp(cue.startMs))
                append(" --> ")
                append(formatTimestamp(cue.endMs))
                append('\n')
                append(cue.text)
            }
        }.joinToString("\n\n").trimEnd() + "\n"
    }

    private fun formatTimestamp(ms: Long): String {
        val safe = ms.coerceAtLeast(0)
        val hours = safe / 3_600_000
        val minutes = (safe % 3_600_000) / 60_000
        val seconds = (safe % 60_000) / 1_000
        val millis = safe % 1_000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}
