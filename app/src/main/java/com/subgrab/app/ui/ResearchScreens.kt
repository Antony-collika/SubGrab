package com.subgrab.app.ui

import android.content.Intent
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
fun VideoDetailScreen(
    viewModel: VideoDetailViewModel,
    videoId: String,
    defaultDownloadFolder: String = "Download/Subtitles",
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var exportOpen by rememberSaveable { mutableStateOf(false) }
    var metadata by rememberSaveable { mutableStateOf(true) }
    var transcript by rememberSaveable { mutableStateOf(false) }
    var comments by rememberSaveable { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    var format by rememberSaveable { mutableStateOf("JSON") }
    var exportFolder by rememberSaveable { mutableStateOf(defaultDownloadFolder) }
    var exportFormatMenuOpen by rememberSaveable { mutableStateOf(false) }
    var subtitleDownloadOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val exportFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val documentId = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
        val relative = documentId?.removePrefix("primary:")
        if (relative == "Download" || relative?.startsWith("Download/") == true) exportFolder = relative
        else if (uri != null) Toast.makeText(context, "Chỉ được chọn thư mục trong Download", Toast.LENGTH_SHORT).show()
    }
    var transcriptRefreshOpen by rememberSaveable { mutableStateOf(false) }
    var subtitleLoading by rememberSaveable { mutableStateOf(false) }
    var subtitleError by rememberSaveable { mutableStateOf<String?>(null) }
    var subtitleLanguages by remember { mutableStateOf<List<com.subgrab.app.domain.SubtitleLanguage>>(emptyList()) }

    LaunchedEffect(videoId) { viewModel.load(videoId) }

    fun loadSubtitleLanguages(afterLoad: () -> Unit) {
        subtitleLoading = true
        subtitleError = null
        viewModel.listSubtitles(videoId) { result ->
            result.onSuccess {
                if (it.isEmpty()) {
                    subtitleLoading = false
                    subtitleError = "Video này không có phụ đề."
                } else {
                    subtitleLanguages = it
                    subtitleLoading = false
                    afterLoad()
                }
            }.onFailure {
                subtitleLoading = false
                subtitleError = it.message ?: "Không thể lấy danh sách phụ đề"
            }
        }
    }

    if (exportOpen) {
        AlertDialog(
            onDismissRequest = { exportOpen = false },
            title = { Text("Xuất dữ liệu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row { Checkbox(metadata, { metadata = it }); Text("metadata") }
                    Row { Checkbox(transcript, { transcript = it }); Text("transcript") }
                    Row { Checkbox(comments, { comments = it }); Text("comments") }
                    Row { RadioButton(history, { history = !history }); Text("dữ liệu lịch sử") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Định dạng", modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { exportFormatMenuOpen = true }) { Text(if (format == "MD") "Markdown" else format + " ▾") }
                            DropdownMenu(expanded = exportFormatMenuOpen, onDismissRequest = { exportFormatMenuOpen = false }) {
                                DropdownMenuItem(text = { Text("JSON") }, onClick = { format = "JSON"; exportFormatMenuOpen = false })
                                DropdownMenuItem(text = { Text("CSV") }, onClick = { format = "CSV"; exportFormatMenuOpen = false })
                                DropdownMenuItem(text = { Text("Markdown") }, onClick = { format = "MD"; exportFormatMenuOpen = false })
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = exportFolder,
                            readOnly = true,
                            onValueChange = {},
                            label = { Text("Thư mục trong Downloads") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = {
                            val initial = DocumentsContract.buildTreeDocumentUri(
                                "com.android.externalstorage.documents", "primary:Download"
                            )
                            exportFolderPicker.launch(initial)
                        }) { Text("Chọn thư mục") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    exportOpen = false
                    viewModel.exportData(
                        videoId,
                        state.result?.title.orEmpty(),
                        ResearchExport.ExportOptions(
                            metadata = metadata,
                            transcript = transcript,
                            comments = comments,
                            history = history,
                            format = ResearchExport.ExportFormat.valueOf(format)
                        ),
                        exportFolder
                    ) { path -> Toast.makeText(context, "Exported to $path", Toast.LENGTH_LONG).show() }
                }) { Text("Xuất") }
            },
            dismissButton = { TextButton(onClick = { exportOpen = false }) { Text("Hủy") } }
        )
    }

    if (subtitleLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Phụ đề") },
            text = { Text("Đang lấy danh sách phụ đề…") },
            confirmButton = {}
        )
    } else if (subtitleError != null) {
        AlertDialog(
            onDismissRequest = { subtitleError = null },
            title = { Text("Phụ đề") },
            text = { Text(subtitleError.orEmpty()) },
            confirmButton = { TextButton(onClick = { subtitleError = null }) { Text("Đóng") } }
        )
    } else if (subtitleDownloadOpen && subtitleLanguages.isNotEmpty()) {
        SingleSubtitleDownloadDialog(
            languages = subtitleLanguages,
            initialFolder = defaultDownloadFolder,
            onDismiss = { subtitleDownloadOpen = false },
            onConfirm = { language, selectedFormat, folder ->
                subtitleDownloadOpen = false
                viewModel.downloadSubtitles(videoId, state.result?.title.orEmpty(), language, selectedFormat, folder)
            }
        )
    } else if (transcriptRefreshOpen && subtitleLanguages.isNotEmpty()) {
        TranscriptRefreshDialog(
            languages = subtitleLanguages,
            currentLanguage = state.transcript?.language,
            onDismiss = { transcriptRefreshOpen = false },
            onConfirm = { language ->
                transcriptRefreshOpen = false
                viewModel.refreshTranscript(videoId, language)
            }
        )
    }

    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        state.result?.let { result ->
            item { Text(result.title, style = MaterialTheme.typography.headlineSmall) }
            item { Text("Kênh: " + result.channelName.orEmpty()) }
            item { Text("Views: " + (result.viewCount ?: 0) + " · Likes: " + (result.likeCount ?: 0) + " · Comments: " + (result.commentCount ?: 0)) }
            result.description?.let { item { Text(it) } }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        subtitleDownloadOpen = true
                        transcriptRefreshOpen = false
                        loadSubtitleLanguages {}
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Tải phụ đề") }
                OutlinedButton(onClick = { exportOpen = true }, modifier = Modifier.weight(1f)) {
                    Text("Xuất data")
                }
            }
        }

        state.result?.let { result ->
            item {
                Text(
                    "Video create by ${result.channelName.orEmpty()} at ${formatPublishedDate(result.publishedAt)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Text(
                    "Fetched at " + DateFormat.getDateTimeInstance().format(Date(result.fetchedAt)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Transcript", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { viewModel.toggleTranscriptExpanded() }) {
                        Text(if (state.transcriptExpanded) "Thu gọn" else "Mở rộng")
                    }
                }
                IconButton(
                    onClick = {
                        transcriptRefreshOpen = true
                        subtitleDownloadOpen = false
                        loadSubtitleLanguages {}
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Làm mới transcript")
                }
            }
        }
        item {
            Text(
                state.transcript?.content ?: "Chưa có transcript.",
                maxLines = if (state.transcriptExpanded) Int.MAX_VALUE else 2
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Comments", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { viewModel.refreshComments(videoId) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Làm mới comments")
                }
            }
        }
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
