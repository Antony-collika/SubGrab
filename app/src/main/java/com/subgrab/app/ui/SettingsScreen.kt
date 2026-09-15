package com.subgrab.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subgrab.app.data.SettingsRepository
import com.subgrab.app.domain.AppSettings
import com.subgrab.app.domain.OutputFormat
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repository: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope(); val stored by repository.settings.collectAsState(initial = AppSettings()); var value by remember(stored) { mutableStateOf(stored) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Cài đặt", style = MaterialTheme.typography.headlineSmall); TextButton(onClick = onBack) { Text("Xong") } }
        Text("Ngôn ngữ phụ đề", style = MaterialTheme.typography.titleMedium)
        LanguageToggle("Tiếng Việt", "vi", value.languages) { value = value.copy(languages = it); scope.launch { repository.update(value) } }
        LanguageToggle("English", "en", value.languages) { value = value.copy(languages = it); scope.launch { repository.update(value) } }
        Text("Định dạng file", style = MaterialTheme.typography.titleMedium)
        FormatToggle("TXT", OutputFormat.TXT, value.formats) { value = value.copy(formats = it); scope.launch { repository.update(value) } }
        FormatToggle("SRT", OutputFormat.SRT, value.formats) { value = value.copy(formats = it); scope.launch { repository.update(value) } }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Ưu tiên phụ đề chính thức"); Switch(checked = value.preferManualSub, onCheckedChange = { value = value.copy(preferManualSub = it); scope.launch { repository.update(value) } }) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Bỏ qua video không có sub"); Switch(checked = value.skipNoSub, onCheckedChange = { value = value.copy(skipNoSub = it); scope.launch { repository.update(value) } }) }
        Text("Thư mục mặc định: ${value.outputDir}", style = MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun LanguageToggle(label: String, code: String, selected: List<String>, onChange: (List<String>) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Checkbox(checked = code in selected, onCheckedChange = { val next = if (it) (selected + code).distinct() else selected - code; if (next.isNotEmpty()) onChange(next) }) } }
@Composable private fun FormatToggle(label: String, format: OutputFormat, selected: Set<OutputFormat>, onChange: (Set<OutputFormat>) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(".$label"); Checkbox(checked = format in selected, onCheckedChange = { val next = if (it) selected + format else selected - format; if (next.isNotEmpty()) onChange(next) }) } }
