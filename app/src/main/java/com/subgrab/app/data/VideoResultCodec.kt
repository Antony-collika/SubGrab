package com.subgrab.app.data

import com.subgrab.app.domain.VideoDownloadResult
import com.subgrab.app.domain.VideoOutcome

/**
 * Mã hóa danh sách kết quả từng video thành một chuỗi để lưu cùng lịch sử tải.
 * Dùng ký tự điều khiển làm dấu ngăn nên không cần thêm bảng cơ sở dữ liệu.
 */
object VideoResultCodec {
    private const val RECORD = '\u001e'
    private const val FIELD = '\u001f'

    fun encode(results: List<VideoDownloadResult>): String =
        results.joinToString(RECORD.toString()) { r ->
            listOf(r.videoId, r.outcome.name, clean(r.title).take(120), clean(r.reason.orEmpty()).take(160))
                .joinToString(FIELD.toString())
        }

    fun decode(raw: String): List<VideoDownloadResult> {
        if (raw.isBlank()) return emptyList()
        return raw.split(RECORD).mapNotNull { record ->
            val p = record.split(FIELD)
            if (p.size < 4) return@mapNotNull null
            val outcome = VideoOutcome.entries.firstOrNull { it.name == p[1] } ?: return@mapNotNull null
            VideoDownloadResult(p[0], p[2], outcome, p[3].ifBlank { null })
        }
    }

    private fun clean(value: String): String = value.replace(RECORD, ' ').replace(FIELD, ' ').replace('\n', ' ')
}
