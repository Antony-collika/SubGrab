package com.subgrab.app.domain

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

enum class SubtitleTimestampMode {
    WITH_TIMESTAMP,
    WITHOUT_TIMESTAMP
}
