package com.subgrab.app.ui

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subgrab.app.domain.OutputFormat
import com.subgrab.app.domain.SubtitleLanguage

@Composable
fun BulkSubtitleDownloadDialog(
    initialFolder: String,
    initialLanguage: String = "en",
    initialPreferOfficial: Boolean = true,
    initialFormat: OutputFormat = OutputFormat.TXT,
    initialTimestampMode: com.subgrab.app.domain.SubtitleTimestampMode = com.subgrab.app.domain.SubtitleTimestampMode.WITH_TIMESTAMP,
    onDismiss: () -> Unit,
    onConfirm: (language: String, preferOfficial: Boolean, format: OutputFormat, folder: String, timestampMode: com.subgrab.app.domain.SubtitleTimestampMode) -> Unit
) {
    var language by rememberSaveable { mutableStateOf(initialLanguage) }
    var preferOfficial by rememberSaveable { mutableStateOf(initialPreferOfficial) }
    var format by rememberSaveable { mutableStateOf(initialFormat) }
    var timestampMode by rememberSaveable { mutableStateOf(initialTimestampMode) }
    var folder by rememberSaveable { mutableStateOf(initialFolder) }
    var folderError by rememberSaveable { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val relative = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
            ?.removePrefix("primary:")
        if (relative == "Download" || relative?.startsWith("Download/") == true) {
            folder = relative
            folderError = null
        } else if (uri != null) {
            folderError = "Chỉ được chọn thư mục bên trong Download."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tải phụ đề hàng loạt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ngôn ngữ ưu tiên", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(selected = language == "en", onClick = { language = "en" })
                    Text("English", Modifier.padding(top = 12.dp))
                }
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(selected = language == "vi", onClick = { language = "vi" })
                    Text("Tiếng Việt", Modifier.padding(top = 12.dp))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ưu tiên phụ đề chính thức", Modifier.padding(top = 8.dp))
                    Switch(checked = preferOfficial, onCheckedChange = { preferOfficial = it })
                }

                Text("Định dạng", style = MaterialTheme.typography.titleMedium)
                Row {
                    RadioButton(selected = format == OutputFormat.TXT, onClick = { format = OutputFormat.TXT })
                    Text("TXT", Modifier.padding(top = 12.dp))
                }
                Row {
                    RadioButton(selected = format == OutputFormat.SRT, onClick = { format = OutputFormat.SRT })
                    Text("SRT", Modifier.padding(top = 12.dp))
                }

                if (format == OutputFormat.SRT) {
                    Text("Timestamp", style = MaterialTheme.typography.titleMedium)
                    Row {
                        RadioButton(
                            selected = timestampMode == com.subgrab.app.domain.SubtitleTimestampMode.WITH_TIMESTAMP,
                            onClick = { timestampMode = com.subgrab.app.domain.SubtitleTimestampMode.WITH_TIMESTAMP }
                        )
                        Text("Có timestamp", Modifier.padding(top = 12.dp))
                    }
                    Row {
                        RadioButton(
                            selected = timestampMode == com.subgrab.app.domain.SubtitleTimestampMode.WITHOUT_TIMESTAMP,
                            onClick = { timestampMode = com.subgrab.app.domain.SubtitleTimestampMode.WITHOUT_TIMESTAMP }
                        )
                        Text("Không timestamp", Modifier.padding(top = 12.dp))
                    }
                }

                Text("Thư mục lưu", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = folder,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    TextButton(onClick = {
                        val initial = DocumentsContract.buildTreeDocumentUri(
                            "com.android.externalstorage.documents", "primary:Download"
                        )
                        picker.launch(initial)
                    }) { Text("Chọn") }
                }
                folderError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (folder.isBlank()) folderError = "Vui lòng chọn thư mục."
                else onConfirm(language, preferOfficial, format, folder, timestampMode)
            }) { Text("Tải") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
fun SingleSubtitleDownloadDialog(
    languages: List<SubtitleLanguage>,
    initialFolder: String,
    initialFormat: OutputFormat = OutputFormat.TXT,
    onDismiss: () -> Unit,
    onConfirm: (language: SubtitleLanguage, format: OutputFormat, folder: String) -> Unit
) {
    var selectedCode by rememberSaveable { mutableStateOf(languages.firstOrNull()?.code.orEmpty()) }
    var selectedAuto by rememberSaveable { mutableStateOf(languages.firstOrNull()?.isAuto ?: false) }
    var format by rememberSaveable { mutableStateOf(initialFormat) }
    var folder by rememberSaveable { mutableStateOf(initialFolder) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var folderError by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(languages) {
        if (languages.none { it.code == selectedCode && it.isAuto == selectedAuto }) {
            languages.firstOrNull()?.let {
                selectedCode = it.code
                selectedAuto = it.isAuto
            }
        }
    }

    val selected = languages.firstOrNull { it.code == selectedCode && it.isAuto == selectedAuto }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val relative = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
            ?.removePrefix("primary:")
        if (relative == "Download" || relative?.startsWith("Download/") == true) {
            folder = relative
            folderError = null
        } else if (uri != null) {
            folderError = "Chỉ được chọn thư mục bên trong Download."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tải phụ đề") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ngôn ngữ", style = MaterialTheme.typography.titleMedium)
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selected?.let { subtitleLabel(it) } ?: "Không có phụ đề")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        languages.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(subtitleLabel(item)) },
                                onClick = {
                                    selectedCode = item.code
                                    selectedAuto = item.isAuto
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Text("Định dạng", style = MaterialTheme.typography.titleMedium)
                Row {
                    RadioButton(selected = format == OutputFormat.TXT, onClick = { format = OutputFormat.TXT })
                    Text("TXT", Modifier.padding(top = 12.dp))
                }
                Row {
                    RadioButton(selected = format == OutputFormat.SRT, onClick = { format = OutputFormat.SRT })
                    Text("SRT", Modifier.padding(top = 12.dp))
                }

                Text("Thư mục lưu", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(folder, {}, readOnly = true, modifier = Modifier.weight(1f), singleLine = true)
                    TextButton(onClick = {
                        val initial = DocumentsContract.buildTreeDocumentUri(
                            "com.android.externalstorage.documents", "primary:Download"
                        )
                        picker.launch(initial)
                    }) { Text("Chọn") }
                }
                folderError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    selected == null -> Unit
                    folder.isBlank() -> folderError = "Vui lòng chọn thư mục."
                    else -> onConfirm(selected, format, folder)
                }
            }) { Text("Tải") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
fun TranscriptRefreshDialog(
    languages: List<SubtitleLanguage>,
    currentLanguage: String?,
    onDismiss: () -> Unit,
    onConfirm: (SubtitleLanguage) -> Unit
) {
    var selectedCode by rememberSaveable {
        mutableStateOf(languages.firstOrNull()?.code.orEmpty())
    }
    var selectedAuto by rememberSaveable {
        mutableStateOf(languages.firstOrNull()?.isAuto ?: false)
    }
    var expanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(languages) {
        if (languages.none { it.code == selectedCode && it.isAuto == selectedAuto }) {
            languages.firstOrNull()?.let {
                selectedCode = it.code
                selectedAuto = it.isAuto
            }
        }
    }

    val selected = languages.firstOrNull { it.code == selectedCode && it.isAuto == selectedAuto }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Làm mới Transcript") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                currentLanguage?.let { Text("Ngôn ngữ hiện tại: $it", style = MaterialTheme.typography.bodySmall) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selected?.let { subtitleLabel(it) } ?: "Không có phụ đề")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        languages.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(subtitleLabel(item)) },
                                onClick = {
                                    selectedCode = item.code
                                    selectedAuto = item.isAuto
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onConfirm) }) { Text("Làm mới") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

private fun subtitleLabel(language: SubtitleLanguage): String =
    language.code + " — " + language.name + if (language.isAuto) " (Auto-generated)" else ""
