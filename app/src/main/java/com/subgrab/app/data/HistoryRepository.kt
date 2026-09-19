package com.subgrab.app.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.subgrab.app.domain.DownloadConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class DownloadHistoryEntry(
    val timestamp: Long,
    val title: String,
    val folder: String,
    val saved: Int,
    val skipped: Int,
    val id: String = UUID.randomUUID().toString(),
    val sourceUrl: String = "",
    val total: Int = 0,
    val status: String = "DONE",
    val logs: List<String> = emptyList()
)

private val Context.historyStore by preferencesDataStore("subgrab_history")

class HistoryRepository(private val context: Context) {
    private object Keys {
        val entries = stringPreferencesKey("entries")
    }

    val entries: Flow<List<DownloadHistoryEntry>> = context.historyStore.data.map { prefs ->
        prefs[Keys.entries].orEmpty().split("\n").mapNotNull(::decode).take(MAX_ENTRIES)
    }

    suspend fun add(entry: DownloadHistoryEntry) {
        context.historyStore.edit { prefs ->
            val current = prefs[Keys.entries].orEmpty().split("\n").mapNotNull(::decode)
            prefs[Keys.entries] = listOf(entry).plus(current).take(MAX_ENTRIES).joinToString("\n", transform = ::encode)
        }
    }

    suspend fun clear() {
        context.historyStore.edit { it.remove(Keys.entries) }
    }

    suspend fun getById(id: String): DownloadHistoryEntry? = entries.map { list -> list.firstOrNull { it.id == id } }.let { flow ->
        kotlinx.coroutines.flow.first(flow)
    }

    private fun encode(entry: DownloadHistoryEntry): String = listOf(
        entry.id,
        entry.timestamp.toString(),
        entry.saved.toString(),
        entry.skipped.toString(),
        entry.total.toString(),
        entry.status,
        entry.title,
        entry.folder,
        entry.sourceUrl,
        entry.logs.joinToString("\u001e")
    ).joinToString("|") { Base64.encodeToString(it.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) }

    private fun decode(raw: String): DownloadHistoryEntry? {
        if (raw.isBlank()) return null
        val parts = raw.split("|")
        return runCatching {
            if (parts.size == 5) {
                DownloadHistoryEntry(
                    timestamp = parts[0].decodeBase64().toLong(),
                    title = parts[3].decodeBase64(),
                    folder = parts[4].decodeBase64(),
                    saved = parts[1].decodeBase64().toInt(),
                    skipped = parts[2].decodeBase64().toInt()
                )
            } else {
                DownloadHistoryEntry(
                    id = parts[0].decodeBase64(),
                    timestamp = parts[1].decodeBase64().toLong(),
                    saved = parts[2].decodeBase64().toInt(),
                    skipped = parts[3].decodeBase64().toInt(),
                    total = parts[4].decodeBase64().toInt(),
                    status = parts[5].decodeBase64(),
                    title = parts[6].decodeBase64(),
                    folder = parts[7].decodeBase64(),
                    sourceUrl = parts[8].decodeBase64(),
                    logs = parts[9].decodeBase64().split("\u001e").filter(String::isNotBlank)
                )
            }
        }.getOrNull()
    }

    private fun String.decodeBase64(): String = String(Base64.decode(this, Base64.NO_WRAP), Charsets.UTF_8)

    private companion object { const val MAX_ENTRIES = 30 }
}
