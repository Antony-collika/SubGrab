package com.subgrab.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subgrab.app.SubGrabExtras
import com.subgrab.app.data.DownloadHistoryEntry
import com.subgrab.app.data.HistoryRepository
import com.subgrab.app.data.export.ResearchExport
import com.subgrab.app.domain.AppSettings
import com.subgrab.app.domain.DownloadConfig
import com.subgrab.app.domain.ResearchFilters
import com.subgrab.app.domain.ResearchSort
import com.subgrab.app.domain.SearchState
import com.subgrab.app.domain.VideoOutcome
import com.subgrab.app.domain.VideoSearchResult
import kotlinx.coroutines.launch

private const val DOWNLOADED_TYPE = "DOWNLOAD_SUBTITLE"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: ResearchSearchViewModel,
    historyRepository: HistoryRepository,
    settings: AppSettings,
    onOpenDetail: (String) -> Unit,
    onDownloadSelected: (List<VideoSearchResult>, DownloadConfig, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val history by historyRepository.entries.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var downloadSheetOpen by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var commentConfirmOpen by rememberSaveable { mutableStateOf(false) }
    var exportSheetOpen by rememberSaveable { mutableStateOf(false) }
    val batch by viewModel.batch.collectAsState()
    val selecting = state.selectedVideos.isNotEmpty()

    LaunchedEffect(Unit) { viewModel.search() }
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    Column(modifier.fillMaxSize()) {
        if (selecting) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.clearSelection() }) { Icon(Icons.Default.Close, contentDescription = "Hủy chọn") }
                Text(
                    "${state.selectedVideos.size} đã chọn",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.selectAllVisible() }) { Text("Chọn tất cả") }
            }
        } else {
            AppTopBar("Thư viện") {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Thêm tùy chọn") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (tab == 0) {
                            DropdownMenuItem(text = { Text("Xuất Markdown") }, enabled = state.results.isNotEmpty(),
                                onClick = { menuOpen = false; exportList(context, state, "md") })
                            DropdownMenuItem(text = { Text("Xuất JSON") }, enabled = state.results.isNotEmpty(),
                                onClick = { menuOpen = false; exportList(context, state, "json") })
                            DropdownMenuItem(text = { Text("Xuất CSV") }, enabled = state.results.isNotEmpty(),
                                onClick = { menuOpen = false; exportList(context, state, "csv") })
                        } else {
                            DropdownMenuItem(text = { Text("Xóa lịch sử tải") }, enabled = history.isNotEmpty(),
                                onClick = { menuOpen = false; confirmClear = true })
                        }
                    }
                }
            }
        }

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Video") })
            Tab(selected = tab == 1, onClick = { tab = 1; viewModel.clearSelection() }, text = { Text("Đã tải") })
        }

        if (tab == 0) {
            VideoTab(state, viewModel, onOpenDetail, onOpenFilter = { filterOpen = true }, modifier = Modifier.weight(1f))
        } else {
            DownloadedTab(history, Modifier.weight(1f))
        }

        if (selecting && tab == 0) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val barPadding = PaddingValues(horizontal = 8.dp)
                Button(
                    onClick = { downloadSheetOpen = true },
                    contentPadding = barPadding,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) { Text("Phụ đề", maxLines = 1) }
                Button(
                    onClick = { commentConfirmOpen = true },
                    contentPadding = barPadding,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) { Text("Comments", maxLines = 1) }
                Button(
                    onClick = { exportSheetOpen = true },
                    contentPadding = barPadding,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) { Text("Data", maxLines = 1) }
            }
        }
    }

    if (filterOpen) {
        LibraryFilterSheet(
            initial = state.filters,
            countFor = { viewModel.countFor(it) },
            onDismiss = { filterOpen = false },
            onApply = { filterOpen = false; viewModel.applyFilters(it) }
        )
    }
    if (downloadSheetOpen) {
        DownloadConfirmSheet(
            videoCount = state.selectedVideos.size,
            settings = settings,
            initialFolderName = "Thư viện",
            onDismiss = { downloadSheetOpen = false },
            onConfirm = { config, folder ->
                downloadSheetOpen = false
                val chosen = state.selectedResults
                viewModel.clearSelection()
                onDownloadSelected(chosen, config, folder)
            }
        )
    }
    if (commentConfirmOpen) {
        AlertDialog(
            onDismissRequest = { commentConfirmOpen = false },
            title = { Text("Lấy comment") },
            text = {
                Text(
                    "Lấy toàn bộ comment của ${state.selectedVideos.size} video đã chọn và lưu vào máy.\n\n" +
                        "Cần nhập YouTube API key trong Cài đặt. Video có nhiều comment sẽ tốn nhiều hạn mức API trong ngày."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    commentConfirmOpen = false
                    viewModel.fetchComments(state.selectedResults)
                }) { Text("Lấy comment") }
            },
            dismissButton = { TextButton(onClick = { commentConfirmOpen = false }) { Text("Hủy") } }
        )
    }
    if (exportSheetOpen) {
        ExportDataSheet(
            videoCount = state.selectedVideos.size,
            initialFolder = settings.outputDir,
            onDismiss = { exportSheetOpen = false },
            onConfirm = { options, folder ->
                exportSheetOpen = false
                viewModel.exportData(state.selectedResults, options, folder)
            }
        )
    }
    if (batch.running) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(batch.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { if (batch.total == 0) 0f else batch.done.toFloat() / batch.total },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Video ${(batch.done + 1).coerceAtMost(batch.total)}/${batch.total}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        batch.currentTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.cancelBatch() }) { Text("Dừng") } }
        )
    }
    batch.summary?.let { summary ->
        if (!batch.running) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBatchSummary() },
                title = { Text("Kết quả") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(summary) } },
                confirmButton = { TextButton(onClick = { viewModel.dismissBatchSummary() }) { Text("Đóng") } }
            )
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Xóa lịch sử tải?") },
            text = { Text("Toàn bộ danh sách các lượt tải sẽ bị xóa. Các file phụ đề đã lưu trong máy không bị ảnh hưởng.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; scope.launch { historyRepository.clear() } }) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Hủy") } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Bảng "Xuất data" cho nhiều video: gộp thành 1 file
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExportDataSheet(
    videoCount: Int,
    initialFolder: String,
    onDismiss: () -> Unit,
    onConfirm: (ResearchExport.ExportOptions, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var metadata by remember { mutableStateOf(true) }
    var transcript by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(ResearchExport.ExportFormat.JSON) }
    var folder by remember { mutableStateOf(initialFolder) }
    val pickFolder = rememberDownloadFolderPicker { folder = it }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Xuất data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "$videoCount video · gộp thành 1 file",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ExportSection("Nội dung") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Metadata", metadata, { metadata = !metadata })
                    ChoiceChip("Transcript", transcript, { transcript = !transcript })
                    ChoiceChip("Comments", comments, { comments = !comments })
                    ChoiceChip("Lịch sử", history, { history = !history })
                }
            }
            ExportSection("Định dạng") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("JSON", format == ResearchExport.ExportFormat.JSON, { format = ResearchExport.ExportFormat.JSON })
                    ChoiceChip("CSV", format == ResearchExport.ExportFormat.CSV, { format = ResearchExport.ExportFormat.CSV })
                    ChoiceChip("Markdown", format == ResearchExport.ExportFormat.MD, { format = ResearchExport.ExportFormat.MD })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lưu trong $folder",
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
                            ResearchExport.ExportOptions(
                                metadata = metadata,
                                transcript = transcript,
                                comments = comments,
                                history = history,
                                format = format
                            ),
                            folder
                        )
                    },
                    enabled = metadata || transcript || comments,
                    modifier = Modifier.weight(1.4f).heightIn(min = 52.dp)
                ) { Text("Xuất") }
            }
        }
    }
}

@Composable
private fun ExportSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Tab "Video"
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoTab(
    state: SearchState,
    viewModel: ResearchSearchViewModel,
    onOpenDetail: (String) -> Unit,
    onOpenFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sortOpen by remember { mutableStateOf(false) }
    val selecting = state.selectedVideos.isNotEmpty()
    val hasFilter = state.query.isNotBlank() || state.filters != ResearchFilters()

    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::updateQuery,
            placeholder = { Text("Tìm trong thư viện") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateQuery("") }) { Icon(Icons.Default.Close, contentDescription = "Xóa từ khóa") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        )
        QuickFilterRow(state.filters, onChange = viewModel::applyFilters, onOpenFilter = onOpenFilter)

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { sortOpen = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.resultCount} kết quả · Sắp xếp: ${state.sort.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    ResearchSort.entries.forEach { sort ->
                        DropdownMenuItem(text = { Text(sort.label) }, onClick = { sortOpen = false; viewModel.updateSort(sort) })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (hasFilter) TextButton(onClick = { viewModel.clearAll() }) { Text("Xóa lọc") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (state.loading && state.results.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
        }

        LazyColumn(Modifier.weight(1f)) {
            if (state.results.isEmpty() && !state.loading) {
                item {
                    EmptyHint(
                        if (hasFilter) "Không có video nào khớp với bộ lọc."
                        else "Thư viện còn trống. Hãy phân tích một link hoặc tìm theo từ khóa ở tab Tải."
                    )
                }
            }
            items(state.results, key = { it.videoId }) { result ->
                LibraryRow(
                    result = result,
                    query = state.query,
                    selecting = selecting,
                    selected = result.videoId in state.selectedVideos,
                    onClick = { if (selecting) viewModel.toggleSelection(result.videoId) else onOpenDetail(result.videoId) },
                    onLongClick = { viewModel.toggleSelection(result.videoId) },
                    onKeywordClick = { kw -> viewModel.applyFilters(state.filters.copy(searchKeywordContext = kw)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (state.hasMore) {
                item {
                    LaunchedEffect(state.page, state.results.size) { viewModel.loadMore() }
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun pillColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
)

@Composable
private fun QuickFilterRow(filters: ResearchFilters, onChange: (ResearchFilters) -> Unit, onOpenFilter: () -> Unit) {
    var dateMenu by remember { mutableStateOf(false) }
    var channelDialog by remember { mutableStateOf(false) }
    val pill = RoundedCornerShape(50)
    val datePreset = presetLabel(filters)
    val advanced = activeFilterCount(filters)

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            val on = DOWNLOADED_TYPE in filters.activityTypes
            FilterChip(
                selected = on,
                onClick = {
                    onChange(filters.copy(activityTypes = if (on) filters.activityTypes - DOWNLOADED_TYPE else filters.activityTypes + DOWNLOADED_TYPE))
                },
                label = { Text("Đã tải phụ đề") },
                shape = pill,
                colors = pillColors()
            )
        }
        item {
            Box {
                FilterChip(
                    selected = datePreset != null,
                    onClick = { dateMenu = true },
                    label = { Text(datePreset ?: "Thời gian") },
                    trailingIcon = if (datePreset != null) ({
                        Icon(
                            Icons.Default.Close, contentDescription = "Bỏ lọc thời gian",
                            modifier = Modifier.size(18.dp).clickable {
                                onChange(filters.copy(publishedWithinHours = null, publishedFrom = null, publishedTo = null))
                            }
                        )
                    }) else null,
                    shape = pill,
                    colors = pillColors()
                )
                DropdownMenu(expanded = dateMenu, onDismissRequest = { dateMenu = false }) {
                    listOf("24 giờ qua" to 24L, "7 ngày qua" to 168L, "30 ngày qua" to 720L).forEach { (label, hours) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            dateMenu = false
                            onChange(filters.copy(publishedWithinHours = hours, publishedFrom = null, publishedTo = null))
                        })
                    }
                }
            }
        }
        item {
            val active = filters.channelContains.isNotBlank()
            FilterChip(
                selected = active,
                onClick = { channelDialog = true },
                label = { Text(if (active) "Kênh: ${filters.channelContains}" else "Kênh", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = if (active) ({
                    Icon(
                        Icons.Default.Close, contentDescription = "Bỏ lọc kênh",
                        modifier = Modifier.size(18.dp).clickable { onChange(filters.copy(channelContains = "")) }
                    )
                }) else null,
                shape = pill,
                colors = pillColors()
            )
        }
        item {
            FilterChip(
                selected = advanced > 0,
                onClick = onOpenFilter,
                label = { Text(if (advanced > 0) "Bộ lọc ($advanced)" else "Bộ lọc") },
                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = pill,
                colors = pillColors()
            )
        }
    }

    if (channelDialog) {
        var text by remember { mutableStateOf(filters.channelContains) }
        AlertDialog(
            onDismissRequest = { channelDialog = false },
            title = { Text("Lọc theo kênh") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text("Nhập tên kênh") }, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { channelDialog = false; onChange(filters.copy(channelContains = text.trim())) }) { Text("Áp dụng") }
            },
            dismissButton = { TextButton(onClick = { channelDialog = false }) { Text("Hủy") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    result: VideoSearchResult,
    query: String,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onKeywordClick: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VideoThumbnail(result.thumbnail, result.durationSeconds)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                highlightTerms(result.title, query),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildList {
                result.channelName?.takeIf { it.isNotBlank() }?.let { add(it) }
                result.viewCount?.let { add(Format.count(it) + " lượt xem") }
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val keyword = result.searchKeywords?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            when {
                result.lastDownloadedAt != null -> StatusChip("Đã tải phụ đề", ChipKind.SUCCESS)
                result.lastViewedAt != null -> StatusChip("Đã xem ${Format.relativeDay(result.lastViewedAt)}", ChipKind.NEUTRAL)
                keyword != null -> StatusChip(
                    "Từ tìm kiếm “$keyword”", ChipKind.NEUTRAL,
                    Modifier.clip(RoundedCornerShape(50)).clickable { onKeywordClick(keyword) }
                )
            }
        }
        if (selecting) Checkbox(checked = selected, onCheckedChange = null)
    }
}

/** Tô nền các từ khóa tìm kiếm trong tiêu đề (không phân biệt hoa thường). */
@Composable
private fun highlightTerms(title: String, query: String): AnnotatedString {
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (terms.isEmpty()) return AnnotatedString(title)
    val color = SubGrabExtras.highlight
    return buildAnnotatedString {
        append(title)
        terms.forEach { term ->
            var i = title.indexOf(term, ignoreCase = true)
            while (i >= 0) {
                addStyle(SpanStyle(background = color), i, i + term.length)
                i = title.indexOf(term, i + term.length, ignoreCase = true)
            }
        }
    }
}

private fun exportList(context: Context, state: SearchState, kind: String) {
    val list = state.selectedResults.ifEmpty { state.results }
    if (list.isEmpty()) return
    val (mime, body, title) = when (kind) {
        "md" -> Triple("text/markdown", ResearchExport.markdown(list), "Xuất Markdown")
        "json" -> Triple("application/json", ResearchExport.json(list), "Xuất JSON")
        else -> Triple("text/csv", ResearchExport.csv(list), "Xuất CSV")
    }
    context.startActivity(
        Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_TEXT, body) }, title)
    )
}

// ---------------------------------------------------------------------------------------------
// Tab "Đã tải": theo lượt, mở ra thấy từng video và lý do
// ---------------------------------------------------------------------------------------------

@Composable
private fun DownloadedTab(entries: List<DownloadHistoryEntry>, modifier: Modifier = Modifier) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    if (entries.isEmpty()) {
        EmptyHint("Chưa có lượt tải nào.", modifier)
        return
    }
    LazyColumn(modifier.fillMaxWidth()) {
        items(entries, key = { it.id }) { entry ->
            val expanded = expandedId == entry.id
            Row(
                Modifier.fillMaxWidth().clickable { expandedId = if (expanded) null else entry.id }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(Format.relativeWithTime(entry.timestamp))
                            append(" · ${entry.saved} đã lưu · ${entry.skipped} bỏ qua")
                            if (entry.failed > 0) append(" · ${entry.failed} lỗi")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when (entry.status) {
                    "CANCELLED" -> StatusChip("Đã hủy", ChipKind.NEUTRAL)
                    "ERROR" -> StatusChip("Thất bại", ChipKind.ERROR)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Thu gọn" else "Mở rộng",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Thư mục: ${entry.folder}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entry.results.isNotEmpty()) {
                        // Video có vấn đề hiện trước để dễ thấy.
                        entry.results.sortedBy { it.outcome == VideoOutcome.SAVED }.forEach { VideoResultRow(it) }
                    } else {
                        Text(
                            "Lượt tải này được ghi từ phiên bản cũ nên chưa có chi tiết từng video.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        entry.logs.takeLast(30).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
