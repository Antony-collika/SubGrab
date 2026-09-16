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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.subgrab.app.data.DownloadState
import com.subgrab.app.data.SettingsRepository
import com.subgrab.app.domain.AppSettings
import com.subgrab.app.domain.VideoItem
import com.subgrab.app.service.DownloadService
import com.subgrab.app.ui.AnalysisState
import com.subgrab.app.ui.DownloadViewModel
import com.subgrab.app.ui.DownloadViewModelFactory
import com.subgrab.app.ui.SettingsScreen

class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { SubGrabTheme { SubGrabApp() } } } }
@Composable fun SubGrabTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF006C4C))) { content() } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SubGrabApp() {
    val context = LocalContext.current
    val factory = remember(context) { DownloadViewModelFactory(context) }
    val vm: DownloadViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsState()
    val downloadState by vm.downloadState.collectAsState()
    val settingsRepo = remember(context) { SettingsRepository(context) }
    val settings by settingsRepo.settings.collectAsState(initial = AppSettings())
    var showSettings by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("SubGrab") }, actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Cài đặt") } }) }) { pad ->
        if (showSettings) SettingsScreen(settingsRepo) { showSettings = false }
        else when (val current = state) {
            is AnalysisState.Ready -> SelectVideoScreen(current.videos, current.folder, vm, settings, downloadState, Modifier.padding(pad))
            else -> HomeScreen(state, vm, downloadState, Modifier.padding(pad))
        }
    }
}

@Composable private fun HomeScreen(state: AnalysisState, vm: DownloadViewModel, downloadState: DownloadState, modifier: Modifier) {
    val context = LocalContext.current; var url by remember { mutableStateOf("") }
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Tải phụ đề YouTube hàng loạt", style = MaterialTheme.typography.headlineSmall); Text("Dán link channel, playlist hoặc video. Tối đa 50 video mỗi lần.") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Link YouTube") }, modifier = Modifier.weight(1f), isError = state is AnalysisState.Error, supportingText = { if (state is AnalysisState.Error) Text((state as AnalysisState.Error).message) }); IconButton(onClick = { val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; url = clip.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty() }) { Icon(Icons.Default.ContentPaste, "Dán link") } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { Button(onClick = { vm.analyze(url) }, modifier = Modifier.weight(1f), enabled = state !is AnalysisState.Loading) { Text(if (state is AnalysisState.Loading) "ĐANG PHÂN TÍCH..." else "PHÂN TÍCH") }; if (state is AnalysisState.Error && (state as AnalysisState.Error).retryUrl != null) OutlinedButton(onClick = vm::retryAnalysis) { Text("Thử lại") } } }
        if (downloadState !is DownloadState.Idle) item { DownloadProgressCard(downloadState, vm) }
        item { Text("yt-dlp sẽ phân tích tối đa 50 video và lấy subtitle vi/en nếu có.") }
    }
}

@Composable private fun SelectVideoScreen(videos: List<VideoItem>, folder: String, vm: DownloadViewModel, settings: AppSettings, downloadState: DownloadState, modifier: Modifier) {
    val context = LocalContext.current; var folderName by remember(folder) { mutableStateOf(folder) }; val selected = videos.count { it.isSelected }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Chọn video", style = MaterialTheme.typography.headlineSmall); Text("Đã chọn $selected/${videos.size} · Tối đa 50 video/lần")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { vm.selectAll() }) { Text("Chọn tất cả") }; OutlinedButton(onClick = { vm.clearSelection() }) { Text("Bỏ chọn") } }
        OutlinedTextField(value = folderName, onValueChange = { folderName = it; vm.updateFolder(it) }, label = { Text("Tên folder") }, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { items(videos, key = { it.index }) { video -> VideoRow(video) { vm.toggle(video.index) } } }
        if (downloadState !is DownloadState.Idle) DownloadProgressCard(downloadState, vm)
        Button(onClick = { ContextCompat.startForegroundService(context, android.content.Intent(context, DownloadService::class.java)); vm.startDownload(settings) }, enabled = selected > 0 && folderName.isNotBlank() && downloadState !is DownloadState.Running, modifier = Modifier.fillMaxWidth()) { Text("TẢI PHỤ ĐỀ ($selected)") }
    }
}

@Composable private fun DownloadProgressCard(state: DownloadState, vm: DownloadViewModel) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            is DownloadState.Running -> { Text("Đang tải ${state.current}/${state.total}", style = MaterialTheme.typography.titleMedium); Text(state.title); LinearProgressIndicator(progress = { state.current.toFloat() / state.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth()); Text("Đã lưu ${state.saved} file · Bỏ qua ${state.skipped}"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = vm::pauseDownload) { Text("Tạm dừng") }; OutlinedButton(onClick = vm::cancelDownload) { Text("Hủy") } } }
            is DownloadState.Paused -> { Text("Đã tạm dừng ${state.current}/${state.total}", style = MaterialTheme.typography.titleMedium); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = vm::resumeDownload) { Text("Tiếp tục") }; OutlinedButton(onClick = vm::cancelDownload) { Text("Hủy") } } }
            is DownloadState.Done -> Text("Hoàn tất: ${state.saved} file, bỏ qua ${state.skipped}", color = MaterialTheme.colorScheme.primary)
            is DownloadState.Cancelled -> Text("Đã hủy: ${state.saved} file đã lưu", color = MaterialTheme.colorScheme.error)
            else -> Text("Đang chuẩn bị tải")
        }
    } }
}

@Composable private fun VideoRow(video: VideoItem, onToggle: () -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Checkbox(checked = video.isSelected, onCheckedChange = { onToggle() }, enabled = video.hasSub); Column { Text("%03d · %s".format(video.index, video.title)); Text(if (video.hasSub) "Có phụ đề" else "Không có phụ đề", style = MaterialTheme.typography.bodySmall) } } }
