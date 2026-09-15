package com.subgrab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.subgrab.app.domain.UrlValidator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { SubGrabTheme { SubGrabHome() } } }
}

@Composable fun SubGrabTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF006C4C))) { content() } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SubGrabHome() {
    var url by remember { mutableStateOf("") }; var message by remember { mutableStateOf<String?>(null) }; var analyzing by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("SubGrab") }, actions = { IconButton(onClick = { message = "Cài đặt: Tiếng Việt + English · TXT" }) { Icon(Icons.Default.Settings, "Cài đặt") } }) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Tải phụ đề YouTube hàng loạt", style = MaterialTheme.typography.headlineSmall); Text("Dán link channel, playlist hoặc video. Tối đa 50 video mỗi lần.", style = MaterialTheme.typography.bodyMedium) }
            item { OutlinedTextField(value = url, onValueChange = { url = it; message = if (it.isNotBlank() && !UrlValidator.isValid(it)) "Link YouTube không hợp lệ" else null }, label = { Text("Link YouTube") }, placeholder = { Text("https://youtube.com/playlist?list=...") }, modifier = Modifier.fillMaxWidth(), isError = message != null, supportingText = { message?.let { Text(it) } }) }
            item { Button(onClick = { if (UrlValidator.isValid(url)) { analyzing = true; message = "Đã nhận link. Sẵn sàng phân tích tối đa 50 video." } else message = "Link không hợp lệ. Vui lòng kiểm tra lại" }, modifier = Modifier.fillMaxWidth(), enabled = !analyzing) { Text(if (analyzing) "ĐANG PHÂN TÍCH..." else "PHÂN TÍCH") } }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Quy trình tải", style = MaterialTheme.typography.titleMedium); Text("1. Phân tích metadata bằng yt-dlp"); Text("2. Chọn video và ngôn ngữ vi/en"); Text("3. Lưu .txt/.srt vào Download/Subtitles") } } }
        }
    }
}
