package com.subgrab.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.subgrab.app.data.RecentItem
import com.subgrab.app.data.RecentKind
import com.subgrab.app.domain.YoutubeUrlParser

@Composable
fun HomeScreen(
    state: AnalysisState,
    recents: List<RecentItem>,
    notificationsEnabled: Boolean,
    onAnalyze: (String) -> Unit,
    onSearchKeyword: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenRecent: (RecentItem) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var keywordMode by rememberSaveable { mutableStateOf(false) }
    var url by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }
    val loading = state is AnalysisState.Loading
    val errorMessage = (state as? AnalysisState.Error)?.message

    // Gợi ý clipboard: chỉ đọc nội dung khi cửa sổ đang được focus (Android chặn đọc nền)
    // và chỉ đọc lại khi clipboard thật sự đổi, để tránh làm phiền người dùng.
    val windowInfo = LocalWindowInfo.current
    var clipText by rememberSaveable { mutableStateOf<String?>(null) }
    var lastClipStamp by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(windowInfo.isWindowFocused) {
        if (!windowInfo.isWindowFocused) return@LaunchedEffect
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val description = clipboard.primaryClipDescription
        if (description == null || !description.hasMimeType("text/*")) {
            clipText = null
            return@LaunchedEffect
        }
        if (description.timestamp == lastClipStamp) return@LaunchedEffect
        lastClipStamp = description.timestamp
        val text = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }
            .getOrNull().orEmpty()
        clipText = text.takeIf { YoutubeUrlParser.extractUrls(it).isNotEmpty() }
    }

    Column(modifier.fillMaxSize()) {
        AppTopBar("SubGrab") {
            if (!notificationsEnabled) {
                IconButton(onClick = onOpenNotificationSettings) {
                    Icon(Icons.Default.NotificationsOff, contentDescription = "Thông báo đang tắt")
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { ModeSwitch(keywordMode) { keywordMode = it } }
            item {
                if (!keywordMode) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = { Text("youtube.com/playlist?list=…") },
                        trailingIcon = {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                url = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Dán link", tint = MaterialTheme.colorScheme.secondary)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        maxLines = 4,
                        isError = errorMessage != null,
                        supportingText = { if (errorMessage != null) Text(errorMessage) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        placeholder = { Text("Ví dụ: du lịch Hà Nội") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { if (keyword.isNotBlank() && !loading) onSearchKeyword(keyword) }),
                        isError = errorMessage != null,
                        supportingText = { if (errorMessage != null) Text(errorMessage) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (!keywordMode && clipText != null && clipText != url) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { url = clipText.orEmpty() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            "Có link YouTube trong clipboard — chạm để dán",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            item {
                Column {
                    Button(
                        onClick = { if (keywordMode) onSearchKeyword(keyword) else onAnalyze(url) },
                        enabled = !loading && (!keywordMode || keyword.isNotBlank()),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            when {
                                loading && keywordMode -> "Đang tìm…"
                                loading -> "Đang phân tích…"
                                keywordMode -> "Tìm video"
                                else -> "Phân tích link"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (state is AnalysisState.Error && state.retryUrl != null) {
                        TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Thử lại") }
                    }
                }
            }
            if (recents.isNotEmpty()) {
                item {
                    Text(
                        "Gần đây",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(recents, key = { it.key }) { item ->
                    RecentRow(item) { onOpenRecent(item) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ModeSwitch(keywordMode: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)
    ) {
        ModeTab("Dán link", !keywordMode, Modifier.weight(1f)) { onChange(false) }
        ModeTab("Tìm từ khóa", keywordMode, Modifier.weight(1f)) { onChange(true) }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecentRow(item: RecentItem, onClick: () -> Unit) {
    val icon: ImageVector = when (item.kind) {
        RecentKind.PLAYLIST -> Icons.Default.VideoLibrary
        RecentKind.CHANNEL -> Icons.Default.Person
        RecentKind.VIDEO -> Icons.Default.PlayArrow
        RecentKind.KEYWORD -> Icons.Default.Search
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (item.kind == RecentKind.KEYWORD) "\"${item.title}\"" else item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${item.videoCount} video · ${Format.relativeWithTime(item.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
