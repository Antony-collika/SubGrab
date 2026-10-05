package com.subgrab.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.subgrab.app.data.DebugLog
import com.subgrab.app.data.SubGrabDatabase

/** Gộp "Nhật ký debug" (mạng) và "Diagnostics" (chẩn đoán) thành một màn có 2 tab. */
@Composable
fun LogsScreen(database: SubGrabDatabase, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        AppTopBar("Nhật ký và chẩn đoán", onBack = onBack)
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Mạng") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Chẩn đoán") })
        }
        Box(Modifier.weight(1f)) {
            if (tab == 0) NetworkLogTab() else DiagnosticsScreen(database, onBack)
        }
    }
}

@Composable
private fun NetworkLogTab() {
    val context = LocalContext.current
    val debugVersion by DebugLog.updates.collectAsState()
    val logs = remember(debugVersion) { DebugLog.snapshot() }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = DebugLog::refresh, modifier = Modifier.weight(1f)) { Text("Làm mới") }
            OutlinedButton(onClick = DebugLog::clear, modifier = Modifier.weight(1f)) { Text("Xóa") }
        }
        Button(onClick = { copyDebugLog(context) }, modifier = Modifier.fillMaxWidth()) { Text("Sao chép nhật ký") }
        Text("Nhật ký mạng — ${logs.size} dòng", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ghi từng request/response. Query/token được che để tránh lộ thông tin nhạy cảm.",
            style = MaterialTheme.typography.bodySmall
        )
        Card(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(Modifier.padding(10.dp)) {
                items(logs) { line -> Text(line, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private fun copyDebugLog(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("SubGrab debug log", DebugLog.text()))
}
