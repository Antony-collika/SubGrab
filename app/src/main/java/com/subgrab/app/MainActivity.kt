package com.subgrab.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable private fun DebugLogScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(DebugLog.snapshot()) }

    LaunchedEffect(Unit) {
        while (true) {
            logs = DebugLog.snapshot()
            delay(300)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Keep the copy action in its own full-width row so it cannot be pushed
        // out of the visible area on small screens or narrow orientations.
        Button(
            onClick = {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("SubGrab debug log", DebugLog.text())
                )
            },
            enabled = logs.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Copy log")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { logs = DebugLog.snapshot() },
                modifier = Modifier.weight(1f)
            ) { Text("Làm mới") }

            OutlinedButton(
                onClick = {
                    DebugLog.clear()
                    logs = emptyList()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Xóa") }

            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(1f)
            ) { Text("Đóng") }
        }

        Text(
            "Network debug — " + logs.size + " dòng",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Ghi từng request/response của NewPipeDownloader. Query/token được che để tránh lộ thông tin nhạy cảm.",
            style = MaterialTheme.typography.bodySmall
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(Modifier.padding(10.dp)) {
                items(logs) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
