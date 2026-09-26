package com.subgrab.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.subgrab.app.data.export.ResearchExport
import com.subgrab.app.domain.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.text.DateFormat
import java.util.Date

@Composable
fun ResearchHistoryScreen(state: ResearchHistoryState, onLoadMore: () -> Unit, onOpenVideo: (String) -> Unit,
                          onOpenSession: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Lịch sử nghiên cứu", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onSearch) { Text("Tìm kiếm") }
        }
        Text("Activity Timeline", style = MaterialTheme.typography.titleMedium)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.items.isEmpty() && !state.loading) Text("Chưa có hoạt động nghiên cứu.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().clickable {
                    item.searchSessionId?.let(onOpenSession) ?: item.videoId?.let(onOpenVideo)
                }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(activityLabel(item), style = MaterialTheme.typography.titleMedium)
                        item.title?.let { Text(it) }
                        item.query?.let { Text("Từ khóa: " + it, style = MaterialTheme.typography.bodySmall) }
                        item.channelName?.let { Text("Kênh: " + it, style = MaterialTheme.typography.bodySmall) }
                        Text(DateFormat.getDateTimeInstance().format(Date(item.timestamp)), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (state.hasMore) item {
                OutlinedButton(onClick = onLoadMore, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Tải thêm") }
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
private fun activityLabel(item: ResearchActivityItem): String = when (item.type) {
    "SEARCH" -> "Tìm kiếm"
    "ANALYZE_URL" -> "Phân tích URL"
    "VIEW_VIDEO" -> "Đã xem video"
    "DOWNLOAD_SUBTITLE" -> "Tải phụ đề"
    else -> item.type
}

@Composable
fun ResearchSearchScreen(viewModel: ResearchSearchViewModel, sessionId: String?, onOpenDetail: (String) -> Unit,
                         onDownloadSelected: (List<VideoSearchResult>) -> Unit = {}, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sessionId) { if (!sessionId.isNullOrBlank()) viewModel.loadSession(sessionId) else viewModel.reset() }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tìm kiếm dữ liệu", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(state.query, viewModel::updateQuery, label = { Text("Từ khóa") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::search, modifier = Modifier.weight(1f)) { Text("Tìm trong History") }
            OutlinedButton(onClick = { filterOpen = true }, modifier = Modifier.weight(1f)) { Text("Bộ lọc") }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ResearchResultsContent(state, viewModel, onOpenDetail, onDownloadSelected, Modifier.weight(1f))
    }
    if (filterOpen) ResearchFilterDialog(state.filters, { filterOpen = false }) {
        viewModel.updateFilters(it); filterOpen = false; viewModel.search()
    }
}

@Composable
private fun ResearchResultsContent(state: SearchState, viewModel: ResearchSearchViewModel, onOpenDetail: (String) -> Unit,
                                   onDownloadSelected: (List<VideoSearchResult>) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(state.resultCount.toString() + " kết quả")
            Row {
                TextButton(onClick = viewModel::selectAllVisible) { Text("Chọn tất cả") }
                TextButton(onClick = viewModel::clearSelection) { Text("Bỏ chọn") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { viewModel.search() }) { Text("Làm mới") }
            TextButton(onClick = { viewModel.updateSort(nextSort(state.sort)); viewModel.search() }) { Text("Sắp xếp: " + state.sort.label) }
            TextButton(onClick = { if (state.selectedResults.isNotEmpty()) onDownloadSelected(state.selectedResults) }) { Text("Tải phụ đề") }
            TextButton(onClick = {
                if (state.results.isNotEmpty()) {
                    val body = ResearchExport.markdown(if (state.selectedResults.isNotEmpty()) state.selectedResults else state.results)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/markdown"; putExtra(Intent.EXTRA_TEXT, body)
                    }, "Xuất Markdown"))
                }
            }) { Text("Xuất MD") }
            TextButton(onClick = {
                if (state.results.isNotEmpty()) {
                    val body = ResearchExport.json(if (state.selectedResults.isNotEmpty()) state.selectedResults else state.results)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"; putExtra(Intent.EXTRA_TEXT, body)
                    }, "Xuất JSON"))
                }
            }) { Text("Xuất JSON") }
            TextButton(onClick = {
                if (state.results.isNotEmpty()) {
                    val body = ResearchExport.csv(if (state.selectedResults.isNotEmpty()) state.selectedResults else state.results)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"; putExtra(Intent.EXTRA_TEXT, body)
                    }, "Xuất CSV"))
                }
            }) { Text("Xuất CSV") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.results, key = { it.videoId }) { result ->
                Card(Modifier.fillMaxWidth().clickable { onOpenDetail(result.videoId) }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(result.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Checkbox(result.videoId in state.selectedVideos, { viewModel.toggleSelection(result.videoId) })
                        }
                        Text(result.channelName.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        Text("Views: " + (result.viewCount ?: 0) + " · Likes: " + (result.likeCount ?: 0) +
                            " · Comments: " + (result.commentCount ?: 0), style = MaterialTheme.typography.bodySmall)
                        result.playlistTitles?.takeIf { it.isNotBlank() }?.let { Text("Playlist: " + it, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            if (state.hasMore) item {
                OutlinedButton(onClick = viewModel::loadMore, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Tải thêm") }
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun ResearchFilterDialog(initial: ResearchFilters, onDismiss: () -> Unit, onApply: (ResearchFilters) -> Unit) {
    var title by remember { mutableStateOf(initial.titleContains) }
    var description by remember { mutableStateOf(initial.descriptionContains) }
    var channel by remember { mutableStateOf(initial.channelContains) }
    var tags by remember { mutableStateOf(initial.tagsContains) }
    var category by remember { mutableStateOf(initial.categoryContains) }
    var playlist by remember { mutableStateOf(initial.playlistContains) }
    var keyword by remember { mutableStateOf(initial.searchKeywordContext) }
    var minSubscribers by remember { mutableStateOf(initial.minSubscribers?.toString().orEmpty()) }
    var maxSubscribers by remember { mutableStateOf(initial.maxSubscribers?.toString().orEmpty()) }
    var minViews by remember { mutableStateOf(initial.minViews?.toString().orEmpty()) }
    var maxViews by remember { mutableStateOf(initial.maxViews?.toString().orEmpty()) }
    var minLikes by remember { mutableStateOf(initial.minLikes?.toString().orEmpty()) }
    var maxLikes by remember { mutableStateOf(initial.maxLikes?.toString().orEmpty()) }
    var minComments by remember { mutableStateOf(initial.minComments?.toString().orEmpty()) }
    var maxComments by remember { mutableStateOf(initial.maxComments?.toString().orEmpty()) }
    var publishedFrom by remember { mutableStateOf(initial.publishedFrom?.let(::formatDate).orEmpty()) }
    var publishedTo by remember { mutableStateOf(initial.publishedTo?.let(::formatDate).orEmpty()) }
    var fetchedFrom by remember { mutableStateOf(initial.fetchedFrom?.let(::formatDate).orEmpty()) }
    var fetchedTo by remember { mutableStateOf(initial.fetchedTo?.let(::formatDate).orEmpty()) }
    var searchActivity by remember { mutableStateOf("SEARCH" in initial.activityTypes) }
    var analyzeActivity by remember { mutableStateOf("ANALYZE_URL" in initial.activityTypes) }
    var viewActivity by remember { mutableStateOf("VIEW_VIDEO" in initial.activityTypes) }
    var activityFrom by remember { mutableStateOf(initial.activityFrom?.let(::formatDate).orEmpty()) }
    var activityTo by remember { mutableStateOf(initial.activityTo?.let(::formatDate).orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Advanced Filter") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterField("Tiêu đề", title) { title = it } }
                item { FilterField("Mô tả", description) { description = it } }
                item { FilterField("Kênh", channel) { channel = it } }
                item { FilterField("Tags", tags) { tags = it } }
                item { FilterField("Category / topic", category) { category = it } }
                item { FilterField("Playlist", playlist) { playlist = it } }
                item { FilterField("SearchSession keyword", keyword) { keyword = it } }
                item { FilterPair("Subscribers", minSubscribers, maxSubscribers, { minSubscribers = it }, { maxSubscribers = it }) }
                item { FilterPair("Views", minViews, maxViews, { minViews = it }, { maxViews = it }) }
                item { FilterPair("Likes", minLikes, maxLikes, { minLikes = it }, { maxLikes = it }) }
                item { FilterPair("Comments", minComments, maxComments, { minComments = it }, { maxComments = it }) }
                item { FilterPair("Published yyyy-MM-dd", publishedFrom, publishedTo, { publishedFrom = it }, { publishedTo = it }) }
                item { FilterPair("Fetched yyyy-MM-dd", fetchedFrom, fetchedTo, { fetchedFrom = it }, { fetchedTo = it }) }
                item { FilterPair("Activity yyyy-MM-dd", activityFrom, activityTo, { activityFrom = it }, { activityTo = it }) }
                item { Row { Checkbox(searchActivity, { searchActivity = it }); Text("Đã tìm kiếm") } }
                item { Row { Checkbox(analyzeActivity, { analyzeActivity = it }); Text("Đã phân tích") } }
                item { Row { Checkbox(viewActivity, { viewActivity = it }); Text("Đã xem") } }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(initial.copy(titleContains = title, descriptionContains = description, channelContains = channel, tagsContains = tags,
                    playlistContains = playlist, categoryContains = category, searchKeywordContext = keyword,
                    minSubscribers = minSubscribers.toLongOrNull(), maxSubscribers = maxSubscribers.toLongOrNull(),
                    minViews = minViews.toLongOrNull(), maxViews = maxViews.toLongOrNull(),
                    minLikes = minLikes.toLongOrNull(), maxLikes = maxLikes.toLongOrNull(),
                    minComments = minComments.toLongOrNull(), maxComments = maxComments.toLongOrNull(),
                    publishedFrom = parseDate(publishedFrom), publishedTo = parseDate(publishedTo, true),
                    fetchedFrom = parseDate(fetchedFrom), fetchedTo = parseDate(fetchedTo, true),
                    activityFrom = parseDate(activityFrom), activityTo = parseDate(activityTo, true),
                    activityTypes = buildSet { if (searchActivity) add("SEARCH"); if (analyzeActivity) add("ANALYZE_URL"); if (viewActivity) add("VIEW_VIDEO") }
                ))
            }) { Text("Áp dụng") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}
private fun parseDate(value: String, end: Boolean = false): Long? = runCatching {
    val date = LocalDate.parse(value)
    val instant = if (end) date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        else date.atStartOfDay(ZoneId.systemDefault()).toInstant()
    instant.toEpochMilli() - if (end) 1 else 0
}.getOrNull()

private fun formatDate(value: Long): String =
    LocalDate.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault()).toString()

@Composable private fun FilterField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}
@Composable private fun FilterPair(label: String, first: String, second: String, onFirst: (String) -> Unit, onSecond: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(first, onFirst, label = { Text(label + " từ") }, modifier = Modifier.weight(1f), singleLine = true)
        OutlinedTextField(second, onSecond, label = { Text("đến") }, modifier = Modifier.weight(1f), singleLine = true)
    }
}

@Composable
fun VideoDetailScreen(viewModel: VideoDetailViewModel, videoId: String, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(videoId) { viewModel.load(videoId) }
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        state.result?.let { result ->
            item { Text(result.title, style = MaterialTheme.typography.headlineSmall) }
            item { Text("Kênh: " + result.channelName.orEmpty()) }
            item { Text("Views: " + (result.viewCount ?: 0) + " · Likes: " + (result.likeCount ?: 0) + " · Comments: " + (result.commentCount ?: 0)) }
            result.description?.let { item { Text(it) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.refreshTranscript(videoId) }) { Text("Làm mới transcript") }
                    OutlinedButton(onClick = { viewModel.refreshComments(videoId) }) { Text("Làm mới comments") }
                }
            }
        }
        item { Text("Metadata history", style = MaterialTheme.typography.titleMedium) }
        items(state.snapshotHistory.take(10)) { snapshot ->
            Text(DateFormat.getDateTimeInstance().format(Date(snapshot.fetchedAt)) + " · " + snapshot.title,
                style = MaterialTheme.typography.bodySmall)
        }
        item { Text("Transcript", style = MaterialTheme.typography.titleMedium) }
        item { Text(state.transcript?.content ?: "Chưa có transcript.") }
        item { Text("Comments", style = MaterialTheme.typography.titleMedium) }
        if (state.commentThreads.isEmpty()) item { Text("Chưa có comments.") }
        items(state.commentThreads) { thread ->
            val replies by produceState<List<com.subgrab.app.data.db.CommentEntity>>(emptyList(), thread.threadId) {
                value = viewModel.comments(thread.threadId)
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(thread.topLevelComment.orEmpty())
                    Text(thread.replyCount.toString() + " replies", style = MaterialTheme.typography.labelSmall)
                    replies.filter { it.parentCommentId != null }.forEach { reply ->
                        Text("↳ " + reply.author.orEmpty() + ": " + reply.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun nextSort(sort: ResearchSort): ResearchSort = when (sort) {
    ResearchSort.PUBLISHED_DESC -> ResearchSort.FETCHED_DESC
    ResearchSort.FETCHED_DESC -> ResearchSort.VIEWS_DESC
    ResearchSort.VIEWS_DESC -> ResearchSort.LIKES_DESC
    ResearchSort.LIKES_DESC -> ResearchSort.COMMENTS_DESC
    ResearchSort.COMMENTS_DESC -> ResearchSort.DURATION_DESC
    ResearchSort.DURATION_DESC -> ResearchSort.TITLE_ASC
    ResearchSort.TITLE_ASC -> ResearchSort.CHANNEL_ASC
    ResearchSort.CHANNEL_ASC -> ResearchSort.PUBLISHED_DESC
}
