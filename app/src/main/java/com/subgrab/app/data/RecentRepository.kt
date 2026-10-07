package com.subgrab.app.data

import android.content.Context
import com.subgrab.app.data.db.AnalystTaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Loại nguồn hiển thị ở mục "Gần đây" trên Trang chủ. */
enum class RecentKind { PLAYLIST, CHANNEL, VIDEO, KEYWORD }

data class RecentItem(
    val kind: RecentKind,
    val input: String,
    val title: String,
    val videoCount: Int,
    val timestamp: Long
) {
    val key: String get() = kind.name + "|" + input
}

/** Nguồn dữ liệu Room cho các tác vụ người dùng đã chủ động phân tích/tìm kiếm. */
class RecentRepository(private val context: Context) {
    private val database by lazy { SubGrabDatabase.get(context.applicationContext) }

    val entries: Flow<List<RecentItem>> = database.analystTaskDao().recent(8, 0).map { rows -> rows.map(::toRecentItem) }

    suspend fun add(item: RecentItem) {
        database.analystTaskDao().upsert(
            AnalystTaskEntity(item.key, item.kind.name, item.input, item.title, item.videoCount, item.timestamp)
        )
    }

    private fun toRecentItem(row: AnalystTaskEntity) = RecentItem(
        kind = RecentKind.valueOf(row.kind),
        input = row.input,
        title = row.title,
        videoCount = row.videoCount,
        timestamp = row.timestamp
    )
}
