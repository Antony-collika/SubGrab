package com.subgrab.app

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.subgrab.app.domain.VideoItem
import com.subgrab.app.ui.AnalysisState
import com.subgrab.app.ui.DownloadViewModel
import com.subgrab.app.ui.DownloadViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { SubGrabTheme { SubGrabApp() } } }
}

@Composable fun SubGrabTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF006C4C))) { content() } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SubGrabApp() {
    val vm: DownloadViewModel = viewModel(factory = DownloadViewModelFactory(null))
    val state by vm.state.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("SubGrab") }, actions = { IconButton(onClick = {}) { Icon(Icons.Default.Settings, "Cài đặt") } }) }) { pad ->
        when (val current = state) {
            is AnalysisState.Ready -> SelectVideoScreen(current.videos, current.folder, vm, Modifier.padding(pad))
            else -> HomeScreen(state, vm, Modifier.padding(pad))
        }
    }
}

@Composable private fun HomeScreen(state: AnalysisState, vm: DownloadViewModel, modifier: Modifier) {
    val context = LocalContext.current; var url by remember { mutableStateOf("") }
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Tải phụ đề YouTube hàng loạt", style = MaterialTheme.typography.headlineSmall); Text("Dán link channel, playlist hoặc video. Tối đa 50 video mỗi lần.") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Link YouTube") }, modifier = Modifier.weight(1f), isError = state is AnalysisState.Error, supportingText = { if (state is AnalysisState.Error) Text(state.message) }); IconButton(onClick = { val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; url = clip.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty() }) { Icon(Icons.Default.ContentPaste, "Dán link") } } }
        item { Button(onClick = { vm.analyze(url) }, modifier = Modifier.fillMaxWidth(), enabled = state !is AnalysisState.Loading) { Text(if (state is AnalysisState.Loading) "ĐANG PHÂN TÍCH..." else "PHÂN TÍCH") } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Trạng thái", style = MaterialTheme.typography.titleMedium); Text(if (state is AnalysisState.Error) "Kiểm tra cấu hình yt-dlp và thử lại." else "Phân tích sẽ lấy tối đa 50 video đầu tiên.") } } }
    }
}

@Composable private fun SelectVideoScreen(videos: List<VideoItem>, folder: String, vm: DownloadViewModel, modifier: Modifier) {
    var folderName by remember(folder) { mutableStateOf(folder) }; val selected = videos.count { it.isSelected }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Chọn video", style = MaterialTheme.typography.headlineSmall); Text("Đã chọn $selected/${videos.size} · Tối đa 50 video/lần")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { vm.selectAll() }) { Text("Chọn tất cả") }; OutlinedButton(onClick = { vm.clearSelection() }) { Text("Bỏ chọn") } }
        OutlinedTextField(value = folderName, onValueChange = { folderName = it; vm.updateFolder(it) }, label = { Text("Tên folder") }, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { items(videos, key = { it.index }) { video -> VideoRow(video) { vm.toggle(video.index) } } }
        Button(onClick = {}, enabled = selected > 0 && folderName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("TẢI PHỤ ĐỀ ($selected)") }
    }
}

@Composable private fun VideoRow(video: VideoItem, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Checkbox(checked = video.isSelected, onCheckedChange = { onToggle() }, enabled = video.hasSub); Column { Text("%03d · %s".format(video.index, video.title)); Text(if (video.hasSub) "Có phụ đề" else "Không có phụ đề", style = MaterialTheme.typography.bodySmall) } }
}
