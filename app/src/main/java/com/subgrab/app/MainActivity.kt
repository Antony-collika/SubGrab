package com.subgrab.app

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.subgrab.app.data.DebugLog
import com.subgrab.app.data.DownloadState
import com.subgrab.app.data.SettingsRepository
import com.subgrab.app.domain.AppSettings
import com.subgrab.app.domain.VideoItem
import com.subgrab.app.service.DownloadService
import com.subgrab.app.ui.AnalysisState
import com.subgrab.app.ui.DownloadViewModel
import com.subgrab.app.ui.DownloadViewModelFactory
import com.subgrab.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SubGrabTheme { SubGrabApp() } }
        requestRequiredPermissions()
    }
    private fun requestRequiredPermissions() {
        val permissions = buildList {
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
            if (android.os.Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), 100)
    }
}
@Composable fun SubGrabTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xFF63DBA8)) else lightColorScheme(primary = Color(0xFF006C4C))
    MaterialTheme(colorScheme = colors) { content() }
}

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
    var showDebug by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("SubGrab") }, actions = { IconButton(onClick = { showDebug = true }) { Icon(Icons.Default.BugReport, "Log debug") }; IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Cài đặt") } }) }) { pad ->
        if (showSettings) SettingsScreen(settingsRepo) { showSettings = false }
        else if (showDebug) DebugLogScreen { showDebug = false }
        else when (val current = state) {
            is AnalysisState.Ready -> SelectVideoScreen(current.videos, current.folder, vm, settings, downloadState, Modifier.padding(pad))
            else -> HomeScreen(state, vm, downloadState, Modifier.padding(pad))
        }
    }
}

@Composable private fun HomeScreen(state: AnalysisState, vm: DownloadViewModel, downloadState: DownloadState, modifier: Modifier) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Tải phụ đề YouTube hàng loạt", style = MaterialTheme.typography.headlineSmall)
            Text("Nhập link như trước, hoặc tìm video bằng từ khóa.")
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !mode, onClick = { mode = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Nhập URL") }
                SegmentedButton(selected = mode, onClick = { mode = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Từ khóa") }
            }
        }
        if (!mode) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Link YouTube") }, modifier = Modifier.weight(1f), isError = state is AnalysisState.Error, supportingText = { if (state is AnalysisState.Error) Text((state as AnalysisState.Error).message) }); IconButton(onClick = { val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; url = clip.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty() }) { Icon(Icons.Default.ContentPaste, "Dán link") } } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { Button(onClick = { vm.analyze(url) }, modifier = Modifier.weight(1f), enabled = state !is AnalysisState.Loading) { Text(if (state is AnalysisState.Loading) "ĐANG PHÂN TÍCH..." else "PHÂN TÍCH") }; if (state is AnalysisState.Error && (state as AnalysisState.Error).retryUrl != null) OutlinedButton(onClick = vm::retryAnalysis) { Text("Thử lại") } } }
        } else {
            item {
                OutlinedTextField(value = keyword, onValueChange = { keyword = it }, label = { Text("Từ khóa YouTube") }, placeholder = { Text("Ví dụ: AI agents") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = state is AnalysisState.Error, supportingText = { if (state is AnalysisState.Error) Text((state as AnalysisState.Error).message) })
            }
            item {
                Button(onClick = { vm.searchKeyword(keyword) }, modifier = Modifier.fillMaxWidth(), enabled = state !is AnalysisState.Loading && keyword.isNotBlank()) { Text(if (state is AnalysisState.Loading) "ĐANG TÌM..." else "TÌM VIDEO") }
            }
        }
        if (downloadState !is DownloadState.Idle) item { DownloadProgressCard(downloadState, vm) }
        item { Text(if (mode) "NewPipe tìm trực tiếp trên YouTube, sau đó trả về danh sách video để bạn chọn." else "yt-dlp/NewPipe sẽ phân tích tối đa 50 video và lấy subtitle vi/en nếu có.") }
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
            is DownloadState.Done -> { Text("Hoàn tất: ${state.saved} file, bỏ qua ${state.skipped}", color = MaterialTheme.colorScheme.primary); state.logs.takeLast(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall) } }
            is DownloadState.Cancelled -> { Text("Đã hủy: ${state.saved} file đã lưu", color = MaterialTheme.colorScheme.error); state.logs.takeLast(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall) } }
            else -> Text("Đang chuẩn bị tải")
        }
    } }
}

@Composable private fun VideoRow(video: VideoItem, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = video.isSelected, onCheckedChange = { onToggle() }, enabled = video.canSelect)
        Column {
            Text("%03d · %s".format(video.index, video.title))
            val metadata = buildList {
                if (video.channelTitle.isNotBlank()) add(video.channelTitle)
                video.viewCount?.let { add("${it} lượt xem") }
            }.joinToString(" · ")
            if (metadata.isNotBlank()) Text(metadata, style = MaterialTheme.typography.bodySmall)
            Text(
                when {
                    !video.subtitleChecked -> "Chưa kiểm tra phụ đề · sẽ kiểm tra khi tải"
                    video.hasSub -> "Có phụ đề"
                    else -> "Không có phụ đề"
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


@Composable private fun DebugLogScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(DebugLog.snapshot()) }
    LaunchedEffect(Unit) {
        while (true) {
            logs = DebugLog.snapshot()
            delay(300)
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { logs = DebugLog.snapshot() }) { Text("Làm mới") }
            Button(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("SubGrab debug log", DebugLog.text()))
            }, enabled = logs.isNotEmpty()) { Text("Copy log") }
            OutlinedButton(onClick = { DebugLog.clear(); logs = emptyList() }) { Text("Xóa") }
            OutlinedButton(onClick = onClose) { Text("Đóng") }
        }
        Text("Network debug — " + logs.size + " dòng", style = MaterialTheme.typography.titleMedium)
        Text("Ghi từng request/response của NewPipeDownloader. Query/token được che để tránh lộ thông tin nhạy cảm.", style = MaterialTheme.typography.bodySmall)
        Card(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.padding(10.dp)) { items(logs) { line -> Text(line, style = MaterialTheme.typography.bodySmall) } }
        }
    }
}
