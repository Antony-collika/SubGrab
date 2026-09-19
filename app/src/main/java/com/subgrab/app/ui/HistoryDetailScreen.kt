package com.subgrab.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subgrab.app.data.DownloadHistoryEntry
import com.subgrab.app.data.HistoryRepository
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryDetailScreen(
    repository: HistoryRepository,
    entryId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entry by produceState<DownloadHistoryEntry?>(initialValue = null, repository, entryId) {
        value = repository.getById(entryId)
    }

    if (entry == null) {
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Không tìm thấy tác vụ", style = MaterialTheme.typography.headlineSmall)
            Text("Bản ghi có thể đã bị xóa khỏi lịch sử.")
            OutlinedButton(onClick = onBack) { Text("Quay lại") }
        }
        return
    }

    val current = entry!!
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(current.title, style = MaterialTheme.typography.headlineSmall)
            Text(DateFormat.getDateTimeInstance().format(Date(current.timestamp)), style = MaterialTheme.typography.bodySmall)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Trạng thái: ${current.status}")
                    Text("Video: ${current.total}")
                    Text("Đã lưu: ${current.saved}")
                    Text("Bỏ qua/lỗi: ${current.skipped}")
                    Text("Thư mục: ${current.folder}")
                    if (current.sourceUrl.isNotBlank()) Text("Nguồn: ${current.sourceUrl}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (current.logs.isNotEmpty()) {
            item { Text("Nhật ký", style = MaterialTheme.typography.titleMedium) }
            items(current.logs) { line -> Text(line, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
