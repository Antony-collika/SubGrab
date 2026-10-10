package com.subgrab.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.subgrab.app.domain.AppSettings
import com.subgrab.app.domain.DownloadConfig
import com.subgrab.app.domain.OutputFormat
import com.subgrab.app.domain.SubtitleTimestampMode

/**
 * Bảng trượt xác nhận trước khi tải. Mọi lựa chọn được điền sẵn theo Cài đặt,
 * người dùng chỉ cần bấm "Bắt đầu tải" hoặc chỉnh nhanh cho riêng lần này.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadConfirmSheet(
    videoCount: Int,
    settings: AppSettings,
    initialFolderName: String,
    onDismiss: () -> Unit,
    onConfirm: (DownloadConfig, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var language by remember { mutableStateOf(settings.languages.firstOrNull() ?: "en") }
    var format by remember { mutableStateOf(settings.formats.firstOrNull() ?: OutputFormat.TXT) }
    var timestamp by remember { mutableStateOf(settings.timestampMode) }
    var official by remember { mutableStateOf(settings.preferManualSub) }
    var folderName by remember { mutableStateOf(initialFolderName) }
    var outputDir by remember { mutableStateOf(settings.outputDir.ifBlank { "Download" }) }
    val pickFolder = rememberDownloadFolderPicker { outputDir = it }

    val batchLimit = settings.maxSubtitlesPerTask.coerceIn(1, 50)
    val batches = (videoCount + batchLimit - 1) / batchLimit

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Tải phụ đề", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (batches > 1) "$videoCount video · chia thành $batches lượt, mỗi lượt tối đa $batchLimit video"
                    else "$videoCount video · 1 lượt",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SheetSection("Ngôn ngữ") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("English", language == "en", { language = "en" })
                    ChoiceChip("Tiếng Việt", language == "vi", { language = "vi" })
                }
            }
            SheetSection("Định dạng") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("TXT", format == OutputFormat.TXT, { format = OutputFormat.TXT })
                    ChoiceChip("SRT", format == OutputFormat.SRT, { format = OutputFormat.SRT })
                }
                if (format == OutputFormat.SRT) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("Có timestamp", timestamp == SubtitleTimestampMode.WITH_TIMESTAMP,
                            { timestamp = SubtitleTimestampMode.WITH_TIMESTAMP })
                        ChoiceChip("Không timestamp", timestamp == SubtitleTimestampMode.WITHOUT_TIMESTAMP,
                            { timestamp = SubtitleTimestampMode.WITHOUT_TIMESTAMP })
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ưu tiên phụ đề chính thức", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Dùng bản do người đăng tải nếu có",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = official, onCheckedChange = { official = it })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("transcript_", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Tên") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lưu trong $outputDir",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = pickFolder) { Text("Đổi") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Hủy") }
                Button(
                    onClick = {
                        onConfirm(
                            DownloadConfig(
                                languages = listOf(language),
                                formats = setOf(format),
                                preferManual = official,
                                skipNoSub = settings.skipNoSub,
                                outputDir = outputDir,
                                timestampMode = timestamp,
                                subtitleConcurrency = settings.subtitleConcurrency,
                                maxSubtitlesPerTask = settings.maxSubtitlesPerTask
                            ),
                            folderName.trim()
                        )
                    },
                    enabled = folderName.isNotBlank(),
                    modifier = Modifier.weight(1.4f).heightIn(min = 52.dp)
                ) { Text("Bắt đầu tải") }
            }
        }
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
