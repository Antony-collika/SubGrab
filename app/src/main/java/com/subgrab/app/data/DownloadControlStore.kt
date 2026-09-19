package com.subgrab.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.downloadControlStore by preferencesDataStore("subgrab_download_control")

class DownloadControlStore(private val context: Context) {
    private object Keys {
        val paused = booleanPreferencesKey("paused")
        val cancelled = booleanPreferencesKey("cancelled")
    }

    suspend fun reset() {
        context.downloadControlStore.edit {
            it[Keys.paused] = false
            it[Keys.cancelled] = false
        }
    }

    suspend fun pause() { context.downloadControlStore.edit { it[Keys.paused] = true } }
    suspend fun resume() { context.downloadControlStore.edit { it[Keys.paused] = false } }
    suspend fun cancel() {
        context.downloadControlStore.edit {
            it[Keys.cancelled] = true
            it[Keys.paused] = false
        }
    }

    suspend fun isPaused(): Boolean = context.downloadControlStore.data.first()[Keys.paused] ?: false
    suspend fun isCancelled(): Boolean = context.downloadControlStore.data.first()[Keys.cancelled] ?: false
}
