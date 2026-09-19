package com.subgrab.app.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DownloadHistoryEntry(
    val timestamp: Long,
    val title: String,
    val folder: String,
    val saved: Int,
    val skipped: Int
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

    private fun encode(entry: DownloadHistoryEntry): String = listOf(
        entry.timestamp.toString(), entry.saved.toString(), entry.skipped.toString(), entry.title, entry.folder
    ).joinToString("|") { Base64.encodeToString(it.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) }

    private fun decode(raw: String): DownloadHistoryEntry? {
        if (raw.isBlank()) return null
        val parts = raw.split("|")
        if (parts.size != 5) return null
        return runCatching {
            DownloadHistoryEntry(parts[0].decodeBase64().toLong(), parts[3].decodeBase64(), parts[4].decodeBase64(), parts[1].decodeBase64().toInt(), parts[2].decodeBase64().toInt())
        }.getOrNull()
    }

    private fun String.decodeBase64(): String = String(Base64.decode(this, Base64.NO_WRAP), Charsets.UTF_8)

    private companion object { const val MAX_ENTRIES = 30 }
}
