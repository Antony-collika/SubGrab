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
fun HistoryScreen(repository: HistoryRepository, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val entries by repository.entries.collectAsState(initial = emptyList())
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Lịch sử tải", style = MaterialTheme.typography.headlineSmall)
            if (entries.isNotEmpty()) TextButton(onClick = onClear) { Text("Xóa lịch sử") }
        }
        if (entries.isEmpty()) {
            Text("Chưa có tác vụ tải hoàn tất.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { "${it.timestamp}-${it.title}" }) { entry -> HistoryRow(entry) }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: DownloadHistoryEntry) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium)
            Text("Thư mục: ${entry.folder}", style = MaterialTheme.typography.bodySmall)
            Text("${entry.saved} file · bỏ qua ${entry.skipped} · ${DateFormat.getDateTimeInstance().format(Date(entry.timestamp))}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
