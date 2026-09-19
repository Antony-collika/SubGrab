package com.subgrab.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subgrab.app.data.SettingsRepository
import com.subgrab.app.domain.AppSettings
import com.subgrab.app.domain.OutputFormat
import com.subgrab.app.domain.SubtitleTimestampMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repository: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val stored by repository.settings.collectAsState(initial = AppSettings())
    var value by remember(stored) { mutableStateOf(stored) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Thiết lập phụ đề và thư mục tải xuống", style = MaterialTheme.typography.bodyMedium)
        Text("Ngôn ngữ phụ đề", style = MaterialTheme.typography.titleMedium)
        LanguageToggle("Tiếng Việt", "vi", value.languages) { next -> value = value.copy(languages = next); scope.launch { repository.update(value) } }
        LanguageToggle("English", "en", value.languages) { next -> value = value.copy(languages = next); scope.launch { repository.update(value) } }
        Text("Định dạng", style = MaterialTheme.typography.titleMedium)
        FormatToggle("TXT", OutputFormat.TXT, value.formats) { next -> value = value.copy(formats = next); scope.launch { repository.update(value) } }
        FormatToggle("SRT", OutputFormat.SRT, value.formats) { next -> value = value.copy(formats = next); scope.launch { repository.update(value) } }
        if (OutputFormat.SRT in value.formats) {
            Text("Timestamp", style = MaterialTheme.typography.titleMedium)
            TimestampToggle("Có timestamp", SubtitleTimestampMode.WITH_TIMESTAMP, value.timestampMode) { next ->
                value = value.copy(timestampMode = next)
                scope.launch { repository.update(value) }
            }
            TimestampToggle("Không timestamp", SubtitleTimestampMode.WITHOUT_TIMESTAMP, value.timestampMode) { next ->
                value = value.copy(timestampMode = next)
                scope.launch { repository.update(value) }
            }
        }
        HorizontalDivider()
        Text("Tìm kiếm từ khóa sử dụng NewPipeExtractor, không cần API key.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = value.outputDir,
            onValueChange = { next -> value = value.copy(outputDir = next); scope.launch { repository.update(value) } },
            label = { Text("Thư mục trong Downloads") },
            supportingText = { Text("Ví dụ: Download/Subtitles hoặc SubGrab/Exports") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ưu tiên phụ đề chính thức")
            Switch(checked = value.preferManualSub, onCheckedChange = { next -> value = value.copy(preferManualSub = next); scope.launch { repository.update(value) } })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bỏ qua video không có sub")
            Switch(checked = value.skipNoSub, onCheckedChange = { next -> value = value.copy(skipNoSub = next); scope.launch { repository.update(value) } })
        }
    }
}

@Composable private fun LanguageToggle(label: String, code: String, selected: List<String>, onChange: (List<String>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Checkbox(checked = code in selected, onCheckedChange = { checked -> val next = if (checked) (selected + code).distinct() else selected - code; if (next.isNotEmpty()) onChange(next) })
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

@Composable private fun FormatToggle(label: String, format: OutputFormat, selected: Set<OutputFormat>, onChange: (Set<OutputFormat>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(".$label")
        Checkbox(checked = format in selected, onCheckedChange = { checked -> val next = if (checked) selected + format else selected - format; if (next.isNotEmpty()) onChange(next) })
    }
}
