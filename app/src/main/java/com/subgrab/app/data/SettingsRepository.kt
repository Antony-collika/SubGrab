package com.subgrab.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.subgrab.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("subgrab_settings")
class SettingsRepository(private val context: Context) {
    private object Keys {
        val languages = stringPreferencesKey("languages"); val formats = stringPreferencesKey("formats")
        val outputDir = stringPreferencesKey("output_dir"); val preferManual = booleanPreferencesKey("prefer_manual")
        val skipNoSub = booleanPreferencesKey("skip_no_sub"); val timestampMode = stringPreferencesKey("timestamp_mode")
        val useApi = booleanPreferencesKey("use_youtube_data_api"); val apiKey = stringPreferencesKey("youtube_data_api_key")
        val subtitleDelayMode = stringPreferencesKey("subtitle_delay_mode"); val subtitleBase = longPreferencesKey("subtitle_base_delay_ms")
        val subtitleJitterMin = longPreferencesKey("subtitle_jitter_min_ms"); val subtitleJitterMax = longPreferencesKey("subtitle_jitter_max_ms")
        val subtitleConcurrency = intPreferencesKey("subtitle_concurrency")
        val apiDelayMode = stringPreferencesKey("api_delay_mode"); val apiBase = longPreferencesKey("api_base_delay_ms")
        val apiJitterMin = longPreferencesKey("api_jitter_min_ms"); val apiJitterMax = longPreferencesKey("api_jitter_max_ms")
    }
    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            languages=p[Keys.languages]?.split(",")?.filter(String::isNotBlank) ?: listOf("vi","en"),
            formats=p[Keys.formats]?.split(",")?.mapNotNull { v -> OutputFormat.entries.find { it.name==v } }?.toSet() ?: setOf(OutputFormat.TXT),
            outputDir=p[Keys.outputDir] ?: "Download/Subtitles", preferManualSub=p[Keys.preferManual] ?: true,
            skipNoSub=p[Keys.skipNoSub] ?: true, timestampMode=p[Keys.timestampMode]?.let { runCatching { SubtitleTimestampMode.valueOf(it) }.getOrNull() } ?: SubtitleTimestampMode.WITH_TIMESTAMP,
            useYouTubeDataApi=p[Keys.useApi] ?: false, youtubeDataApiKey=p[Keys.apiKey] ?: "",
            subtitleDelayMode=p[Keys.subtitleDelayMode] ?: "AUTO", subtitleBaseDelayMs=(p[Keys.subtitleBase] ?: 0).coerceAtLeast(0),
            subtitleJitterMinMs=(p[Keys.subtitleJitterMin] ?: 0).coerceAtLeast(0), subtitleJitterMaxMs=(p[Keys.subtitleJitterMax] ?: 0).coerceAtLeast(0),
            subtitleConcurrency=(p[Keys.subtitleConcurrency] ?: 1).coerceAtLeast(1),
            apiDelayMode=p[Keys.apiDelayMode] ?: "NONE", apiBaseDelayMs=(p[Keys.apiBase] ?: 0).coerceAtLeast(0),
            apiJitterMinMs=(p[Keys.apiJitterMin] ?: 0).coerceAtLeast(0), apiJitterMaxMs=(p[Keys.apiJitterMax] ?: 0).coerceAtLeast(0)
        )
    }
    suspend fun current(): AppSettings = settings.first()
    suspend fun update(value: AppSettings) {
        require(value.subtitleBaseDelayMs >= 0 && value.apiBaseDelayMs >= 0)
        require(value.subtitleJitterMinMs >= 0 && value.subtitleJitterMaxMs >= value.subtitleJitterMinMs)
        require(value.apiJitterMinMs >= 0 && value.apiJitterMaxMs >= value.apiJitterMinMs)
        require(value.subtitleConcurrency >= 1)
        context.settingsStore.edit { p ->
            p[Keys.languages]=value.languages.joinToString(","); p[Keys.formats]=value.formats.joinToString(","){it.name}
            p[Keys.outputDir]=value.outputDir; p[Keys.preferManual]=value.preferManualSub; p[Keys.skipNoSub]=value.skipNoSub
            p[Keys.timestampMode]=value.timestampMode.name; p[Keys.useApi]=value.useYouTubeDataApi; p[Keys.apiKey]=value.youtubeDataApiKey
            p[Keys.subtitleDelayMode]=value.subtitleDelayMode; p[Keys.subtitleBase]=value.subtitleBaseDelayMs
            p[Keys.subtitleJitterMin]=value.subtitleJitterMinMs; p[Keys.subtitleJitterMax]=value.subtitleJitterMaxMs; p[Keys.subtitleConcurrency]=value.subtitleConcurrency
            p[Keys.apiDelayMode]=value.apiDelayMode; p[Keys.apiBase]=value.apiBaseDelayMs; p[Keys.apiJitterMin]=value.apiJitterMinMs; p[Keys.apiJitterMax]=value.apiJitterMaxMs
        }
    }
}