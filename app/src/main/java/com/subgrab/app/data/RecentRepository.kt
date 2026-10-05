package com.subgrab.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Base64

/** Loại nguồn hiển thị ở mục "Gần đây" trên Trang chủ. */
enum class RecentKind { PLAYLIST, CHANNEL, VIDEO, KEYWORD }

data class RecentItem(
    val kind: RecentKind,
    /** Link (hoặc từ khóa) dùng để chạy lại phân tích khi chạm vào. */
    val input: String,
    val title: String,
    val videoCount: Int,
    val timestamp: Long
) {
    val key: String get() = kind.name + "|" + input
}

/** Mã hóa/giải mã tách riêng để kiểm thử được mà không cần Android. */
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

    /** Thêm mục mới lên đầu, bỏ mục trùng nguồn và giữ tối đa [MAX_ITEMS] mục. */
    fun push(current: List<RecentItem>, item: RecentItem): List<RecentItem> =
        (listOf(item) + current.filterNot { it.key == item.key }).take(MAX_ITEMS)
}

private val Context.recentStore by preferencesDataStore("subgrab_recent")

class RecentRepository(private val context: Context) {
    private object Keys { val items = stringPreferencesKey("items") }

    val entries: Flow<List<RecentItem>> = context.recentStore.data.map { prefs ->
        RecentCodec.decode(prefs[Keys.items].orEmpty())
    }

    suspend fun add(item: RecentItem) {
        context.recentStore.edit { prefs ->
            val updated = RecentCodec.push(RecentCodec.decode(prefs[Keys.items].orEmpty()), item)
            prefs[Keys.items] = RecentCodec.encode(updated)
        }
    }
}
