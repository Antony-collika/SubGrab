package com.subgrab.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.subgrab.app.domain.AppSettings
import com.subgrab.app.domain.OutputFormat
import com.subgrab.app.domain.SubtitleTimestampMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("subgrab_settings")
class SettingsRepository(private val context: Context) {
    private object Keys {
        val languages = stringPreferencesKey("languages")
        val formats = stringPreferencesKey("formats")
        val outputDir = stringPreferencesKey("output_dir")
        val preferManual = booleanPreferencesKey("prefer_manual")
        val skipNoSub = booleanPreferencesKey("skip_no_sub")
        val timestampMode = stringPreferencesKey("timestamp_mode")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            languages = p[Keys.languages]?.split(",")?.filter(String::isNotBlank) ?: listOf("vi", "en"),
            formats = p[Keys.formats]?.split(",")?.mapNotNull { value ->
                OutputFormat.entries.find { it.name == value }
            }?.toSet() ?: setOf(OutputFormat.TXT),
            outputDir = p[Keys.outputDir] ?: "Download/Subtitles",
            preferManualSub = p[Keys.preferManual] ?: true,
            skipNoSub = p[Keys.skipNoSub] ?: true,
            timestampMode = p[Keys.timestampMode]?.let {
                runCatching { SubtitleTimestampMode.valueOf(it) }.getOrNull()
            } ?: SubtitleTimestampMode.WITH_TIMESTAMP
        )
    }

    suspend fun update(value: AppSettings) {
        context.settingsStore.edit { p ->
            p[Keys.languages] = value.languages.joinToString(",")
            p[Keys.formats] = value.formats.joinToString(",") { it.name }
            p[Keys.outputDir] = value.outputDir
            p[Keys.preferManual] = value.preferManualSub
            p[Keys.skipNoSub] = value.skipNoSub
            p[Keys.timestampMode] = value.timestampMode.name
        }
    }
}
