package com.subgrab.app.ui

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subgrab.app.data.SettingsRepository
import com.subgrab.app.domain.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repository: SettingsRepository, onBack: () -> Unit, onDiagnostics: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val stored by repository.settings.collectAsState(initial = null)
    var draft by remember { mutableStateOf<AppSettings?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    // Do not initialize the draft from the artificial collectAsState default.
    // Wait for the real DataStore value, otherwise persisted settings can be
    // replaced by AppSettings() before the first real emission arrives.
    LaunchedEffect(stored) {
        if (draft == null && stored != null) {
            draft = stored
        }
    }

    val value = draft ?: stored ?: AppSettings()

    fun save(v: AppSettings) {
        // Keep the UI responsive while editing. Normalize only the fields that
        // have hard persistence bounds; never reject unrelated settings such as
        // the API toggle/key because of a pacing value.
        val normalized = v.copy(
            subtitleBaseDelayMs = v.subtitleBaseDelayMs.coerceAtLeast(0L),
            subtitleJitterMinMs = v.subtitleJitterMinMs.coerceAtLeast(0L),
            subtitleJitterMaxMs = v.subtitleJitterMaxMs.coerceAtLeast(v.subtitleJitterMinMs.coerceAtLeast(0L)),
            subtitleConcurrency = v.subtitleConcurrency.coerceAtLeast(1),
            maxSubtitlesPerTask = v.maxSubtitlesPerTask.coerceIn(1, 50),
            apiBaseDelayMs = v.apiBaseDelayMs.coerceAtLeast(0L),
            apiJitterMinMs = v.apiJitterMinMs.coerceAtLeast(0L),
            apiJitterMaxMs = v.apiJitterMaxMs.coerceAtLeast(v.apiJitterMinMs.coerceAtLeast(0L))
        )
        draft = normalized
        saveError = null
        scope.launch {
            runCatching { repository.update(normalized) }
                .onFailure { saveError = "Không thể lưu thiết lập: ${it.message ?: "lỗi không xác định"}" }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val documentId = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
        val relative = documentId?.removePrefix("primary:")
        if (relative == "Download" || relative?.startsWith("Download/") == true) {
            save(value.copy(outputDir = relative))
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Thiết lập phụ đề và thư mục tải xuống", style = MaterialTheme.typography.bodyMedium)
        Text("Ngôn ngữ phụ đề", style = MaterialTheme.typography.titleMedium)
        LanguageToggle("Tiếng Việt", "vi", value.languages) { save(value.copy(languages = it)) }
        LanguageToggle("English", "en", value.languages) { save(value.copy(languages = it)) }

        Text("Định dạng", style = MaterialTheme.typography.titleMedium)
        FormatToggle("TXT", OutputFormat.TXT, value.formats) { save(value.copy(formats = it)) }
        FormatToggle("SRT", OutputFormat.SRT, value.formats) { save(value.copy(formats = it)) }

        if (OutputFormat.SRT in value.formats) {
            Text("Timestamp", style = MaterialTheme.typography.titleMedium)
            TimestampToggle("Có timestamp", SubtitleTimestampMode.WITH_TIMESTAMP, value.timestampMode) {
                save(value.copy(timestampMode = it))
            }
            TimestampToggle("Không timestamp", SubtitleTimestampMode.WITHOUT_TIMESTAMP, value.timestampMode) {
                save(value.copy(timestampMode = it))
            }
        }

        HorizontalDivider()
        Button(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) { Text("DIAGNOSTICS") }

        Text("YouTube Data API", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bật API")
            Switch(checked = value.useYouTubeDataApi, onCheckedChange = { save(value.copy(useYouTubeDataApi = it)) })
        }
        OutlinedTextField(
            value = value.youtubeDataApiKey,
            onValueChange = { save(value.copy(youtubeDataApiKey = it)) },
            label = { Text("YouTube Data API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (value.useYouTubeDataApi && value.youtubeDataApiKey.isBlank()) {
            Text("API đang bật nhưng chưa có API key.", style = MaterialTheme.typography.bodySmall)
        }
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Text("Giới hạn task", style = MaterialTheme.typography.titleMedium)
        NumberField(
            "Số subtitle tối đa trong 1 task",
            value.maxSubtitlesPerTask.toLong()
        ) { save(value.copy(maxSubtitlesPerTask = it.toInt().coerceIn(1, 50))) }
        Text(
            "Mỗi task xử lý tối đa số video này; các video còn lại sẽ chờ task kế tiếp.",
            style = MaterialTheme.typography.bodySmall
        )

        PacingSection(
            "Subtitle Requests",
            value.subtitleDelayMode,
            value.subtitleBaseDelayMs,
            value.subtitleJitterMinMs,
            value.subtitleJitterMaxMs,
            value.subtitleConcurrency,
            { save(value.copy(subtitleDelayMode = it)) },
            { save(value.copy(subtitleBaseDelayMs = it)) },
            { save(value.copy(subtitleJitterMinMs = it)) },
            { save(value.copy(subtitleJitterMaxMs = it)) },
            { save(value.copy(subtitleConcurrency = it.coerceAtLeast(1))) },
            true
        )

        PacingSection(
            "API Requests",
            value.apiDelayMode,
            value.apiBaseDelayMs,
            value.apiJitterMinMs,
            value.apiJitterMaxMs,
            null,
            { save(value.copy(apiDelayMode = it)) },
            { save(value.copy(apiBaseDelayMs = it)) },
            { save(value.copy(apiJitterMinMs = it)) },
            { save(value.copy(apiJitterMaxMs = it)) },
            {},
            false
        )

        HorizontalDivider()
        Text("Thư mục lưu kết quả", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value.outputDir,
                readOnly = true,
                onValueChange = {},
                label = { Text("Thư mục trong Downloads") },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                val initial = DocumentsContract.buildTreeDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Download"
                )
                folderPicker.launch(initial)
            }) { Text("Chọn thư mục") }
        }
        Text(
            "Thư mục được chọn bên trong Download; ví dụ Download/Subtitles.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ưu tiên phụ đề chính thức")
            Switch(checked = value.preferManualSub, onCheckedChange = { save(value.copy(preferManualSub = it)) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bỏ qua video không có sub")
            Switch(checked = value.skipNoSub, onCheckedChange = { save(value.copy(skipNoSub = it)) })
        }
    }
}

@Composable
private fun PacingSection(
    title: String,
    mode: String,
    base: Long,
    jmin: Long,
    jmax: Long,
    concurrency: Int?,
    setMode: (String) -> Unit,
    setBase: (Long) -> Unit,
    setMin: (Long) -> Unit,
    setMax: (Long) -> Unit,
    setConcurrency: (Int) -> Unit,
    auto: Boolean
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = mode == "NONE", onClick = { setMode("NONE") }, label = { Text("None") })
        if (auto) {
            FilterChip(selected = mode == "AUTO", onClick = { setMode("AUTO") }, label = { Text("Auto") })
        }
    }
    NumberField("BaseDelay", base, setBase)
    NumberField("Jitter min", jmin, setMin)
    NumberField("Jitter max", jmax, setMax)
    concurrency?.let { NumberField("Concurrency", it.toLong()) { setConcurrency(it.toInt()) } }
}

@Composable
private fun NumberField(label: String, value: Long, onChange: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (value == 0L) "" else value.toString()) }

    // Keep the field's editing buffer independent from the numeric setting.
    // The old remember(value) recreated the buffer after every keystroke.
    LaunchedEffect(value) {
        val numericText = text.toLongOrNull()
        if (numericText != value && !(value == 0L && text.isEmpty())) {
            text = if (value == 0L) "" else value.toString()
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            when {
                input.isEmpty() -> {
                    text = ""
                    onChange(0L)
                }
                input.all(Char::isDigit) -> {
                    input.toLongOrNull()?.let { n ->
                        text = input
                        onChange(n)
                    }
                }
            }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LanguageToggle(label: String, code: String, selected: List<String>, onChange: (List<String>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Checkbox(
            checked = code in selected,
            onCheckedChange = { checked ->
                val n = if (checked) (selected + code).distinct() else selected - code
                if (n.isNotEmpty()) onChange(n)
            }
        )
    }
}

@Composable
private fun TimestampToggle(
    label: String,
    mode: SubtitleTimestampMode,
    selected: SubtitleTimestampMode,
    onChange: (SubtitleTimestampMode) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        RadioButton(selected = mode == selected, onClick = { onChange(mode) })
    }
}

@Composable
private fun FormatToggle(
    label: String,
    format: OutputFormat,
    selected: Set<OutputFormat>,
    onChange: (Set<OutputFormat>) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(".$label")
        Checkbox(
            checked = format in selected,
            onCheckedChange = { checked ->
                val n = if (checked) selected + format else selected - format
                if (n.isNotEmpty()) onChange(n)
            }
        )
    }
}