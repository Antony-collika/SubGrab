package com.subgrab.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subgrab.app.domain.AppSettings
import com.subgrab.app.domain.DownloadConfig
import com.subgrab.app.domain.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectVideoScreen(
    title: String,
    videos: List<VideoItem>,
    folder: String,
    settings: AppSettings,
    downloadActive: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onNewAnalysis: () -> Unit,
    onStartDownload: (DownloadConfig, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val selected = videos.count { it.isSelected }
    val batchLimit = settings.maxSubtitlesPerTask.coerceIn(1, 50)

    Column(modifier.fillMaxSize()) {
        AppTopBar(title, onBack = onBack) {
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Làm mới dữ liệu") }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(onClick = onSelectAll, label = { Text("Chọn tất cả") }, shape = androidx.compose.foundation.shape.RoundedCornerShape(50))
            AssistChip(onClick = onClearSelection, label = { Text("Bỏ chọn") }, shape = androidx.compose.foundation.shape.RoundedCornerShape(50))
            Spacer(Modifier.weight(1f))
            Text(
                "Đã chọn $selected/${videos.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected > batchLimit) {
            val lots = (selected + batchLimit - 1) / batchLimit
            Text(
                "Sẽ chia thành $lots lượt, mỗi lượt tối đa $batchLimit video. Lượt sau chỉ bắt đầu khi bạn bấm tiếp tục.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.weight(1f)) {
            items(videos, key = { it.index }) { video ->
                SelectableVideoRow(video) { onToggle(video.index) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onNewAnalysis, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Phân tích mới") }
            Button(
                onClick = { sheetOpen = true },
                enabled = selected > 0 && !downloadActive,
                modifier = Modifier.weight(1.3f).heightIn(min = 52.dp)
            ) { Text("Tải phụ đề ($selected)") }
        }
    }

    if (sheetOpen) {
        DownloadConfirmSheet(
            videoCount = selected,
            settings = settings,
            initialFolderName = folder,
            onDismiss = { sheetOpen = false },
            onConfirm = { config, folderName ->
                sheetOpen = false
                onStartDownload(config, folderName)
            }
        )
    }
}

@Composable
private fun SelectableVideoRow(video: VideoItem, onToggle: () -> Unit) {
    val rowColor = if (video.isSelected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(rowColor)
            .clickable(enabled = video.canSelect, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VideoThumbnail(video.thumbnailUrl, video.durationSeconds ?: video.durationSec.toLong())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                video.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildList {
                if (video.channelTitle.isNotBlank()) add(video.channelTitle)
                video.viewCount?.let { add(Format.count(it) + " lượt xem") }
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when {
                !video.subtitleChecked -> StatusChip("Chưa kiểm tra", ChipKind.WARNING)
                video.hasSub -> StatusChip("Có phụ đề", ChipKind.SUCCESS)
                else -> StatusChip("Không có phụ đề", ChipKind.NEUTRAL)
            }
        }
        Checkbox(checked = video.isSelected, onCheckedChange = null, enabled = video.canSelect)
    }
}
