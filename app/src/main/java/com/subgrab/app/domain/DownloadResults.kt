package com.subgrab.app.domain

/** Kết quả cuối cùng của từng video trong một lượt tải. */
enum class VideoOutcome { SAVED, SKIPPED, FAILED, NOT_RUN }

data class VideoDownloadResult(
    val videoId: String,
    val title: String,
    val outcome: VideoOutcome,
    /** Lý do bằng tiếng Việt khi video không được lưu. Null nếu đã lưu thành công. */
    val reason: String? = null
)

/** Chuyển loại lỗi kỹ thuật thành lý do dễ hiểu để hiện cho người dùng. */
object DownloadReasons {
    const val CANCELLED = "Chưa tải vì bạn đã hủy tác vụ"
    const val NO_FILE = "Không có file nào được tạo"
    const val NOT_PROCESSED = "Chưa được xử lý"
    private const val ABORTED = "Chưa tải vì tác vụ dừng đột ngột"

    fun forFailure(type: FailureType, detail: String? = null): String {
        val base = when (type) {
            FailureType.NO_SUBTITLE -> "Video không có phụ đề"
            FailureType.LANGUAGE_UNAVAILABLE -> "Không có phụ đề đúng ngôn ngữ đã chọn"
            FailureType.VIDEO_UNAVAILABLE -> "Video không khả dụng (riêng tư, đã xóa hoặc bị chặn)"
            FailureType.TIMEOUT -> "Hết thời gian chờ phản hồi từ YouTube"
            FailureType.CONNECTION_ERROR -> "Mất kết nối mạng"
            FailureType.SERVER_ERROR -> "Máy chủ YouTube đang lỗi"
            FailureType.HTTP_403 -> "YouTube từ chối truy cập (403)"
            FailureType.HTTP_429 -> "YouTube đang giới hạn request (429)"
            FailureType.BOT_DETECTION -> "YouTube yêu cầu xác minh không phải bot"
            FailureType.ACCESS_DENIED -> "Bị từ chối truy cập"
            FailureType.PARSE_ERROR -> "Không đọc được dữ liệu phụ đề"
            FailureType.STORAGE_ERROR -> "Không lưu được file vào bộ nhớ"
            FailureType.CONFIGURATION_ERROR -> "Cấu hình chưa đúng"
            FailureType.UNKNOWN -> "Lỗi không xác định"
        }
        val needsDetail = type == FailureType.UNKNOWN || type == FailureType.PARSE_ERROR ||
            type == FailureType.CONFIGURATION_ERROR || type == FailureType.STORAGE_ERROR
        val clean = detail?.trim().orEmpty().replace(Regex("\\s+"), " ")
        return if (needsDetail && clean.isNotEmpty()) "$base: ${clean.take(80)}" else base
    }

    fun aborted(message: String?): String {
        val clean = message?.trim().orEmpty().replace(Regex("\\s+"), " ")
        return if (clean.isEmpty()) ABORTED else "$ABORTED: ${clean.take(80)}"
    }
}
