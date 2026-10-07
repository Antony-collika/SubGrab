package com.subgrab.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.subgrab.app.data.DownloadState
import com.subgrab.app.data.HistoryRepository
import com.subgrab.app.data.RecentItem
import com.subgrab.app.data.RecentKind
import com.subgrab.app.data.RecentRepository
import com.subgrab.app.data.SettingsRepository
import com.subgrab.app.data.SubGrabDatabase
import com.subgrab.app.domain.AppSettings
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.VideoItem
import com.subgrab.app.domain.YoutubeUrlParser
import com.subgrab.app.service.DownloadWorker
import com.subgrab.app.ui.AnalysisState
import com.subgrab.app.ui.ApiSettingsScreen
import com.subgrab.app.ui.AppTopBar
import com.subgrab.app.ui.DownloadProgressScreen
import com.subgrab.app.ui.DownloadViewModel
import com.subgrab.app.ui.DownloadViewModelFactory
import com.subgrab.app.ui.HomeScreen
import com.subgrab.app.ui.LibraryScreen
import com.subgrab.app.ui.LogsScreen
import com.subgrab.app.ui.MiniDownloadStrip
import com.subgrab.app.ui.PacingSettingsScreen
import com.subgrab.app.ui.ResearchFeatureViewModelFactory
import com.subgrab.app.ui.ResearchSearchViewModel
import com.subgrab.app.ui.SelectVideoScreen
import com.subgrab.app.ui.SettingsScreen
import com.subgrab.app.ui.VideoDetailScreen
import com.subgrab.app.ui.VideoDetailViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SubGrabTheme { SubGrabApp() } }
        requestRequiredPermissions()
    }

    private fun requestRequiredPermissions() {
        val permissions = buildList {
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (android.os.Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), 100)
    }
}

private data class AppTab(val route: String, val label: String, val icon: ImageVector)

private val appTabs = listOf(
    AppTab("home", "Tải", Icons.Default.Download),
    AppTab("library", "Thư viện", Icons.Default.VideoLibrary),
    AppTab("settings", "Cài đặt", Icons.Default.Settings)
)

@Composable
fun SubGrabApp() {
    val context = LocalContext.current
    val factory = remember(context) { DownloadViewModelFactory(context) }
    val vm: DownloadViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsState()
    val downloadState by vm.downloadState.collectAsState()
    val settingsRepo = remember(context) { SettingsRepository(context) }
    val historyRepo = remember(context) { HistoryRepository(context) }
    val recentRepo = remember(context) { RecentRepository(context) }
    val researchFactory = remember(context) { ResearchFeatureViewModelFactory(context) }
    val researchSearchVm: ResearchSearchViewModel = viewModel(factory = researchFactory)
    val videoDetailVm: VideoDetailViewModel = viewModel(factory = researchFactory)
    val settings by settingsRepo.settings.collectAsState(initial = AppSettings())
    val recents by recentRepo.entries.collectAsState(initial = emptyList())
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: "home"
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Thông báo bị tắt? Kiểm tra lại mỗi lần quay về app (người dùng có thể vừa bật trong Cài đặt hệ thống).
    var notificationsEnabled by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Chỉ chạy khi nguồn phân tích đổi (không chạy lại mỗi lần tick chọn video).
    val readySource = (state as? AnalysisState.Ready)?.source
    LaunchedEffect(readySource) {
        val ready = state as? AnalysisState.Ready ?: return@LaunchedEffect
        ready.toRecent()?.let { recentRepo.add(it) }
        if (route == "home") navController.navigate("results")
        ready.persistenceWarning?.let { snackbarHostState.showSnackbar(it) }
    }
    val errorState = state as? AnalysisState.Error
    LaunchedEffect(errorState) {
        errorState?.let { snackbarHostState.showSnackbar(it.message) }
    }

    val goBack: () -> Unit = {
        if (!navController.popBackStack()) navController.navigate("home")
    }
    BackHandler(enabled = route != "home") { goBack() }

    fun navigateTab(target: String) {
        navController.navigate(target) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isTabRoute = appTabs.any { it.route == route }
    val downloadActive = downloadState is DownloadState.Running || downloadState is DownloadState.Paused

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val showStrip = downloadActive && route != "progress"
            if (isTabRoute || showStrip) {
                Column {
                    if (showStrip) {
                        MiniDownloadStrip(
                            state = downloadState,
                            onClick = { navController.navigate("progress") },
                            modifier = if (isTabRoute) Modifier else Modifier.navigationBarsPadding()
                        )
                    }
                    if (isTabRoute) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                            appTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = route == tab.route,
                                    onClick = { navigateTab(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.secondary,
                                        selectedTextColor = MaterialTheme.colorScheme.secondary,
                                        indicatorColor = Color.Transparent,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(pad)
        ) {
            composable("home") {
                HomeScreen(
                    state = state,
                    recents = recents,
                    notificationsEnabled = notificationsEnabled,
                    onAnalyze = { vm.analyze(it) },
                    onSearchKeyword = { vm.searchKeyword(it) },
                    onRetry = vm::retryAnalysis,
                    onOpenRecent = { item ->
                        vm.openRecent(item)
                    },
                    onOpenNotificationSettings = {
                        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable("results") {
                val ready = state as? AnalysisState.Ready
                SelectVideoScreen(
                    title = ready?.source?.title.orEmpty(),
                    videos = ready?.videos.orEmpty(),
                    folder = ready?.folder.orEmpty(),
                    settings = settings,
                    downloadActive = downloadActive,
                    onBack = goBack,
                    onRefresh = vm::refreshMetadata,
                    onToggle = vm::toggle,
                    onSelectAll = vm::selectAll,
                    onClearSelection = vm::clearSelection,
                    total = ready?.total,
                    hasMore = ready?.hasMore == true,
                    loadingMore = ready?.loadingMore == true,
                    fromCache = ready?.fromCache == true,
                    notice = ready?.notice,
                    loadMoreCost = ready?.loadMoreCost,
                    onLoadMore = vm::loadMore,
                    onStopLoading = vm::stopLoading,
                    onNewAnalysis = {
                        vm.resetAnalysis()
                        navController.navigate("home") { popUpTo("home") { inclusive = true } }
                    },
                    onStartDownload = { config, folderName ->
                        vm.updateFolder(folderName)
                        vm.startDownload(config) { navController.navigate("progress") }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable("progress") {
                DownloadProgressScreen(
                    state = downloadState,
                    historyRepository = historyRepo,
                    batchLimit = settings.maxSubtitlesPerTask,
                    onBack = goBack,
                    onPause = vm::pauseDownload,
                    onResume = vm::resumeDownload,
                    onCancel = vm::cancelDownload,
                    onContinueNext = vm::continueNextTask,
                    onOpenFileManager = { openDownloadFolder(context) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable("library") {
                LibraryScreen(
                    viewModel = researchSearchVm,
                    historyRepository = historyRepo,
                    settings = settings,
                    onOpenDetail = { id -> navController.navigate("video-detail/$id") },
                    onDownloadSelected = { selected, config, folder ->
                        val videos = selected.mapIndexed { index, v ->
                            VideoItem(
                                index = index + 1,
                                videoId = v.videoId,
                                title = v.title,
                                durationSec = (v.durationSeconds ?: 0L).toInt(),
                                availableSubs = emptyList(),
                                // Phải đánh dấu đã chọn, nếu không bộ lập kế hoạch tải sẽ bỏ qua toàn bộ video.
                                isSelected = true,
                                channelTitle = v.channelName.orEmpty(),
                                viewCount = v.viewCount,
                                thumbnailUrl = v.thumbnail.orEmpty()
                            )
                        }
                        scope.launch {
                            DownloadWorker.enqueueBatch(
                                context,
                                Source("research-selection", "research", "Thư viện", videos.size),
                                videos,
                                folder,
                                config
                            )
                            navController.navigate("progress")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable("video-detail/{videoId}") { entry ->
                Column(Modifier.fillMaxSize()) {
                    AppTopBar("Chi tiết video", onBack = goBack)
                    VideoDetailScreen(
                        viewModel = videoDetailVm,
                        videoId = entry.arguments?.getString("videoId").orEmpty(),
                        defaultDownloadFolder = settings.outputDir,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            composable("settings") {
                SettingsScreen(
                    repository = settingsRepo,
                    onOpenApi = { navController.navigate("settings/api") },
                    onOpenPacing = { navController.navigate("settings/pacing") },
                    onOpenLogs = { navController.navigate("settings/logs") },
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable("settings/api") { ApiSettingsScreen(settingsRepo, goBack, Modifier.fillMaxSize()) }
            composable("settings/pacing") { PacingSettingsScreen(settingsRepo, goBack, Modifier.fillMaxSize()) }
            composable("settings/logs") { LogsScreen(SubGrabDatabase.get(context), goBack, Modifier.fillMaxSize()) }
        }
    }

    // Đang lấy nhiều trang (kênh/playlist, chọn "Tất cả" hoặc nhiều video): hiện tiến trình và nút Dừng.
    (state as? AnalysisState.Loading)?.takeIf { it.loaded > 0 }?.let { loading ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Đang lấy video") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val totalCount = loading.total
                    if (totalCount != null && totalCount > 0) {
                        LinearProgressIndicator(
                            progress = { (loading.loaded.toFloat() / totalCount).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Đã lấy ${loading.loaded}/$totalCount video")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Đã lấy ${loading.loaded} video")
                    }
                    Text(
                        "Bấm Dừng để giữ phần đã lấy. Phần còn lại có thể lấy tiếp bằng nút Tải thêm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = vm::stopLoading) { Text("Dừng") } }
        )
    }
}

/** Chuyển kết quả phân tích thành mục "Gần đây". Trả về null nếu không thể chạy lại từ link đó. */
private fun AnalysisState.Ready.toRecent(): RecentItem? {
    val isKeyword = source.id.startsWith("keyword:")
    if (!isKeyword && !YoutubeUrlParser.isValid(source.url)) return null
    val kind = when {
        isKeyword -> RecentKind.KEYWORD
        YoutubeUrlParser.isPlaylistUrl(source.url) -> RecentKind.PLAYLIST
        YoutubeUrlParser.isChannelUrl(source.url) -> RecentKind.CHANNEL
        else -> RecentKind.VIDEO
    }
    return RecentItem(
        kind = kind,
        input = if (isKeyword) source.title else source.url,
        title = source.title,
        videoCount = videos.size,
        timestamp = System.currentTimeMillis()
    )
}

private fun openDownloadFolder(context: Context) {
    val folderUri = DocumentsContract.buildTreeDocumentUri(
        "com.android.externalstorage.documents",
        "primary:Download"
    )

    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        data = folderUri
        type = DocumentsContract.Document.MIME_TYPE_DIR
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    runCatching {
        context.startActivity(viewIntent)
    }
}
