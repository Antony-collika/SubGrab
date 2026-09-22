package com.subgrab.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subgrab.app.data.DownloadState

@Composable
fun DownloadProgressScreen(state: DownloadState, vm: DownloadViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Tiến độ tải", style = MaterialTheme.typography.headlineSmall)
        when (state) {
            DownloadState.Idle -> { Text("Chưa có tác vụ tải đang hoạt động."); OutlinedButton(onClick = onDone) { Text("Quay lại") } }
            is DownloadState.Running -> {
                Text("Task " + state.taskIndex + "/" + state.totalTasks + " · " + state.current + "/" + state.total, style = MaterialTheme.typography.titleLarge)
                Text(state.title, style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { state.current.toFloat() / state.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                Text("Đã lưu " + state.saved + " file · Bỏ qua " + state.skipped)
                state.etaSeconds?.let { Text("Ước tính còn " + formatEta(it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::pauseDownload) { Text("Tạm dừng") }
                    OutlinedButton(onClick = vm::cancelDownload) { Text("Hủy") }
                }
                DownloadLogList(state.logs)
            }
            is DownloadState.Paused -> {
                Text("Task " + state.taskIndex + "/" + state.totalTasks + " · Đã tạm dừng " + state.current + "/" + state.total, style = MaterialTheme.typography.titleLarge)
                state.etaSeconds?.let { Text("Ước tính còn " + formatEta(it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::resumeDownload) { Text("Tiếp tục") }
                    OutlinedButton(onClick = vm::cancelDownload) { Text("Hủy") }
                }
                DownloadLogList(state.logs)
            }
            is DownloadState.Done -> {
                Text("Hoàn tất", style = MaterialTheme.typography.titleLarge)
                Text("Đã lưu " + state.saved + " file · Bỏ qua " + state.skipped)
                if (state.totalTasks > 1) {
                    val remainingTasks = state.totalTasks - state.taskIndex
                    Text("Lượt ${state.taskIndex}/${state.totalTasks} đã hoàn tất. ${if (remainingTasks > 0) "Còn $remainingTasks lượt chưa tải." else "Đã hoàn tất toàn bộ các lượt."}")
                }
                DownloadLogList(state.logs)
                if (state.taskIndex < state.totalTasks) {
                    Button(onClick = vm::continueNextTask, modifier = Modifier.fillMaxWidth()) { Text("TIẾP TỤC LƯỢT KẾ TIẾP") }
                }
                Button(onClick = onDone) { Text("Xem kết quả") }
            }
            is DownloadState.Cancelled -> {
                Text("Đã hủy", style = MaterialTheme.typography.titleLarge)
                Text("Đã lưu " + state.saved + " file trước khi hủy.")
                DownloadLogList(state.logs)
                OutlinedButton(onClick = onDone) { Text("Quay lại") }
            }
            is DownloadState.Error -> {
                Text("Tác vụ thất bại", style = MaterialTheme.typography.titleLarge)
                Text(state.message)
                Text("Đã lưu " + state.saved + " file · Bỏ qua " + state.skipped)
                DownloadLogList(state.logs)
                OutlinedButton(onClick = onDone) { Text("Quay lại") }
            }
        }
    }
}

private fun formatEta(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val remaining = safe % 60
    return if (minutes > 0) minutes.toString() + " phút " + remaining + " giây" else remaining.toString() + " giây"
}

@Composable
private fun DownloadLogList(logs: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        LazyColumn(Modifier.padding(12.dp).heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(logs.takeLast(50)) { line -> Text(line, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
