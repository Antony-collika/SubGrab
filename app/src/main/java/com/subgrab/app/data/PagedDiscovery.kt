package com.subgrab.app.data

import com.subgrab.app.domain.VideoItem

/**
 * Một "trang" kết quả khi khám phá kênh / playlist / từ khóa.
 *
 * - videos: các video của trang này (hoặc của nhiều trang đã gộp lại).
 * - total: tổng số video mà nguồn báo cho biết (null nếu nguồn không cho biết).
 * - loadNext: hàm lấy trang kế tiếp. null nghĩa là đã hết, không còn gì để lấy.
 *
 * Nhờ vậy chỉ cần lấy trang đầu là biết ngay "còn nữa hay không", không tốn request thừa.
 */
data class DiscoveryPage(
    val videos: List<VideoItem>,
    val total: Int? = null,
    val loadNext: (suspend () -> DiscoveryPage)? = null
) {
    val hasMore: Boolean get() = loadNext != null
}

/** Tạo hàm lấy trang kế tiếp từ "dấu trang" mà nguồn trả về (null = hết). */
fun nextLoader(token: String?, load: suspend (String) -> DiscoveryPage): (suspend () -> DiscoveryPage)? =
    if (token == null) null else suspend { load(token) }

/**
 * Lấy thêm các trang kế tiếp cho đến khi đủ [limit] video (0 = lấy hết) hoặc hết dữ liệu.
 *
 * - Trang đầu (this) được tính vào [limit].
 * - [shouldStop] được hỏi trước mỗi lần lấy trang mới; trả về true để dừng và giữ phần đã có.
 * - [onPage] được gọi sau mỗi trang mới lấy được, kèm kết quả đã gộp đến lúc đó.
 * - Kết quả luôn được đánh lại số thứ tự (index) và bỏ video trùng.
 */
suspend fun DiscoveryPage.collectUntil(
    limit: Int,
    shouldStop: () -> Boolean = { false },
    onPage: (DiscoveryPage) -> Unit = {}
): DiscoveryPage {
    var acc = this
    var emptyPages = 0
    while (true) {
        val loader = acc.loadNext ?: break
        if (limit > 0 && acc.videos.size >= limit) break
        if (shouldStop()) break
        val next = loader()
        val known = acc.videos.map { it.videoId }.toHashSet()
        val added = next.videos.filter { it.videoId !in known }
        emptyPages = if (added.isEmpty()) emptyPages + 1 else 0
        val merged = (acc.videos + added).mapIndexed { i, v -> v.copy(index = i + 1) }
        acc = DiscoveryPage(merged, next.total ?: acc.total, next.loadNext)
        onPage(acc)
        // Phòng trường hợp nguồn trả liên tiếp các trang rỗng/trùng: dừng để không lặp vô hạn.
        if (emptyPages >= 3) break
    }
    return acc
}
