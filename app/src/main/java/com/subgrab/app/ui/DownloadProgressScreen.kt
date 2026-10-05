package com.subgrab.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subgrab.app.SubGrabExtras
import com.subgrab.app.data.DownloadHistoryEntry
import com.subgrab.app.data.DownloadState
import com.subgrab.app.data.HistoryRepository
import com.subgrab.app.data.RequestGovernor
import com.subgrab.app.domain.GovernorState
import com.subgrab.app.domain.RequestLane
import com.subgrab.app.domain.VideoDownloadResult
import com.subgrab.app.domain.VideoOutcome

private enum class Phase { RUNNING, PAUSED, DONE, CANCELLED, ERROR }

/** Gom mọi trạng thái tải về một dạng chung để màn hình chỉ cần vẽ một lần. */
private data class ProgressView(
    val phase: Phase,
    val current: Int,
    val total: Int,
    val etaSeconds: Long?,
    val itemTitle: String,
    val taskIndex: Int,
    val totalTasks: Int,
    val saved: Int,
    val skipped: Int,
    val failed: Int,
    val logs: List<String>,
    val message: String?,
    val historyId: String?
)

private fun DownloadState.toView(): ProgressView? = when (this) {
    is DownloadState.Running -> ProgressView(Phase.RUNNING, current, total, etaSeconds, title, taskIndex, totalTasks, saved, skipped, failed, logs, null, null)
    is DownloadState.Paused -> ProgressView(Phase.PAUSED, current, total, etaSeconds, "", taskIndex, totalTasks, saved, skipped, failed, logs, null, null)
    is DownloadState.Done -> ProgressView(Phase.DONE, 0, 0, null, "", taskIndex, totalTasks, saved, skipped, failed, logs, null, historyId)
    is DownloadState.Cancelled -> ProgressView(Phase.CANCELLED, 0, 0, null, "", taskIndex, totalTasks, saved, skipped, failed, logs, null, historyId)
    is DownloadState.Error -> ProgressView(Phase.ERROR, 0, 0, null, "", taskIndex, totalTasks, saved, skipped, failed, logs, message, historyId)
    DownloadState.Idle -> null
}

@Composable
fun DownloadProgressScreen(
    state: DownloadState,
    historyRepository: HistoryRepository,
    batchLimit: Int,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onContinueNext: () -> Unit,
    onOpenFileManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = state.toView()
    val title = when (view?.phase) {
        Phase.RUNNING -> "Đang tải phụ đề"
        Phase.PAUSED -> "Đã tạm dừng"
        Phase.DONE -> "Hoàn tất"
        Phase.CANCELLED -> "Đã hủy"
        Phase.ERROR -> "Tải thất bại"
        null -> "Tiến độ tải"
    }

    Column(modifier.fillMaxSize()) {
        AppTopBar(title, onBack = onBack)
        if (view == null) {
            EmptyHint("Hiện không có tác vụ tải nào.")
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val active = view.phase == Phase.RUNNING || view.phase == Phase.PAUSED
            if (active) {
                ProgressRing(view.current, view.total, Format.etaLabel(view.etaSeconds))
                if (view.phase == Phase.RUNNING && view.itemTitle.isNotBlank()) {
                    Text(
                        "Đang tải: " + view.itemTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "Lượt ${view.taskIndex}/${view.totalTasks} · mỗi lượt tối đa $batchLimit video",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FinishedHeader(view)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(view.saved, "Đã lưu", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                StatTile(view.skipped, "Bỏ qua", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                StatTile(
                    view.failed, "Lỗi",
                    if (view.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    Modifier.weight(1f)
                )
            }

            if (active) SpeedStatus(view)

            if (!active) ProblemVideos(view.historyId, historyRepository)

            LogCard(view.logs)
            Spacer(Modifier.height(4.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val hasNext = view.taskIndex < view.totalTasks
            when (view.phase) {
                Phase.RUNNING -> {
                    OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Tạm dừng") }
                    CancelButton(onCancel, Modifier.weight(1f))
                }
                Phase.PAUSED -> {
                    Button(onClick = onResume, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Tiếp tục") }
                    CancelButton(onCancel, Modifier.weight(1f))
                }
                Phase.DONE -> {
                    if (hasNext) {
                        Button(onClick = onContinueNext, modifier = Modifier.weight(1.4f).heightIn(min = 52.dp)) { Text("Tiếp tục lượt kế tiếp") }
                        OutlinedButton(onClick = onOpenFileManager, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Mở thư mục") }
                    } else {
                        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Quay lại") }
                        Button(onClick = onOpenFileManager, modifier = Modifier.weight(1.4f).heightIn(min = 52.dp)) { Text("Mở thư mục") }
                    }
                }
                Phase.CANCELLED, Phase.ERROR -> {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Quay lại") }
                    if (hasNext) {
                        Button(onClick = onContinueNext, modifier = Modifier.weight(1.4f).heightIn(min = 52.dp)) { Text("Tiếp tục lượt kế tiếp") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CancelButton(onCancel: () -> Unit, modifier: Modifier) {
    OutlinedButton(
        onClick = onCancel,
        modifier = modifier.heightIn(min = 52.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) { Text("Hủy") }
}

@Composable
private fun ProgressRing(current: Int, total: Int, etaLabel: String) {
    val fraction by animateFloatAsState(
        targetValue = if (total <= 0) 0f else (current.toFloat() / total).coerceIn(0f, 1f),
        label = "progress"
    )
    val track = MaterialTheme.colorScheme.outlineVariant
    val color = MaterialTheme.colorScheme.secondary
    Box(Modifier.padding(top = 8.dp).size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke, size = Size(size.width, size.height))
            if (fraction > 0f) {
                drawArc(color, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, style = stroke, size = Size(size.width, size.height))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$current/$total", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(etaLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FinishedHeader(view: ProgressView) {
    val (icon, tint, text) = when (view.phase) {
        Phase.DONE -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.tertiary, "Hoàn tất lượt ${view.taskIndex}/${view.totalTasks}")
        Phase.CANCELLED -> Triple(Icons.Default.Info, MaterialTheme.colorScheme.onSurfaceVariant, "Đã hủy lượt ${view.taskIndex}/${view.totalTasks}")
        else -> Triple(Icons.Default.Close, MaterialTheme.colorScheme.error, "Lượt ${view.taskIndex}/${view.totalTasks} thất bại")
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(72.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        view.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun StatTile(value: Int, label: String, valueColor: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpeedStatus(view: ProgressView) {
    // Đọc trạng thái giảm tốc hiện tại; tính lại mỗi khi tiến độ đổi.
    val slowdown = remember(view.current, view.logs.size) {
        RequestGovernor.runtime().state(RequestLane.SUBTITLE_EXTRACTOR) == GovernorState.SLOWDOWN
    }
    val dot = if (slowdown) SubGrabExtras.onWarningContainer else MaterialTheme.colorScheme.tertiary
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Text(
            if (slowdown) "Đang giảm tốc — YouTube đang giới hạn request" else "Tốc độ ổn định — YouTube chưa giới hạn",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Danh sách video của lượt vừa xong mà chưa được lưu, kèm lý do cụ thể. */
@Composable
private fun ProblemVideos(historyId: String?, historyRepository: HistoryRepository) {
    val entry by produceState<DownloadHistoryEntry?>(initialValue = null, historyId) {
        value = historyId?.let { historyRepository.getById(it) }
    }
    val problems = entry?.results.orEmpty().filter { it.outcome != VideoOutcome.SAVED }
    if (problems.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).padding(vertical = 4.dp)
    ) {
        Text(
            "Video chưa lưu được (${problems.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        problems.forEachIndexed { i, result ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            VideoResultRow(result, Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

/** Một dòng kết quả video: biểu tượng trạng thái, tiêu đề và (nếu có) lý do. */
@Composable
fun VideoResultRow(result: VideoDownloadResult, modifier: Modifier = Modifier) {
    val (icon, tint) = when (result.outcome) {
        VideoOutcome.SAVED -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.tertiary
        VideoOutcome.SKIPPED -> Icons.Default.Remove to MaterialTheme.colorScheme.onSurfaceVariant
        VideoOutcome.FAILED -> Icons.Default.Close to MaterialTheme.colorScheme.error
        VideoOutcome.NOT_RUN -> Icons.Default.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(result.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            result.reason?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.outcome == VideoOutcome.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LogCard(logs: List<String>) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Nhật ký", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(if (expanded) "Thu gọn" else "Mở rộng", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            if (logs.isEmpty()) {
                Text(
                    "Chưa có nhật ký.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            logs.forEach { raw ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LogLine(raw)
            }
        }
    }
}

@Composable
private fun LogLine(raw: String) {
    val (marker, tone) = when {
        raw.startsWith("✅") -> "✓" to MaterialTheme.colorScheme.tertiary
        raw.startsWith("❌") -> "✕" to MaterialTheme.colorScheme.error
        else -> "–" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = raw.trimStart { !it.isLetterOrDigit() && it != '"' }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(marker, color = tone, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (marker == "–") tone else MaterialTheme.colorScheme.onSurface)
    }
}

/** Thanh nhỏ hiện phía trên thanh điều hướng khi đang có tác vụ tải; chạm để mở màn Tiến độ. */
@Composable
fun MiniDownloadStrip(state: DownloadState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val view = state.toView() ?: return
    if (view.phase != Phase.RUNNING && view.phase != Phase.PAUSED) return
    Column(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (view.phase == Phase.PAUSED) "Đã tạm dừng " else "Đang tải ") + "${view.current}/${view.total} · Lượt ${view.taskIndex}/${view.totalTasks}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            Text("Xem", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
        LinearProgressIndicator(
            progress = { if (view.total <= 0) 0f else (view.current.toFloat() / view.total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.secondaryContainer
        )
    }
}
