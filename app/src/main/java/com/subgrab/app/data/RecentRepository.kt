package com.subgrab.app.data

import android.content.Context
import com.subgrab.app.data.db.AnalystTaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Base64

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

object RecentCodec {
    private const val MAX_ITEMS = 8

    fun encode(items: List<RecentItem>): String = items.take(MAX_ITEMS).joinToString("\n") { item ->
        listOf(item.kind.name, item.input, item.title, item.videoCount.toString(), item.timestamp.toString())
            .joinToString("|") { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) }
    }

    fun decode(raw: String): List<RecentItem> = raw.split("\n").mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        runCatching {
            val p = line.split("|").map { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }
            RecentItem(RecentKind.valueOf(p[0]), p[1], p[2], p[3].toInt(), p[4].toLong())
        }.getOrNull()
    }

    fun push(current: List<RecentItem>, item: RecentItem): List<RecentItem> =
        (listOf(item) + current.filterNot { it.key == item.key }).take(MAX_ITEMS)
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
