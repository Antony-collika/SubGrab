package com.subgrab.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.subgrab.app.domain.ResearchFilters
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private const val DOWNLOADED = "DOWNLOAD_SUBTITLE"

/** Số điều kiện lọc đang bật (mỗi nhóm điều kiện tính một lần). */
fun activeFilterCount(f: ResearchFilters): Int = listOf(
    f.publishedWithinHours != null || f.publishedFrom != null || f.publishedTo != null,
    f.minViews != null || f.maxViews != null,
    f.activityTypes.isNotEmpty() || f.activityFrom != null || f.activityTo != null,
    f.channelOrPlaylistContains.isNotBlank(),
    f.channelContains.isNotBlank(),
    f.playlistContains.isNotBlank(),
    f.titleContains.isNotBlank(),
    f.descriptionContains.isNotBlank(),
    f.tagsContains.isNotBlank(),
    f.categoryContains.isNotBlank(),
    f.topicContains.isNotBlank(),
    f.searchKeywordContext.isNotBlank(),
    f.minSubscribers != null || f.maxSubscribers != null,
    f.minLikes != null || f.maxLikes != null,
    f.minComments != null || f.maxComments != null,
    f.fetchedFrom != null || f.fetchedTo != null
).count { it }

fun presetLabel(f: ResearchFilters): String? = when {
    f.publishedWithinHours == 24L -> "24 giờ qua"
    f.publishedWithinHours == 168L -> "7 ngày qua"
    f.publishedWithinHours == 720L -> "30 ngày qua"
    f.publishedWithinHours != null -> "${f.publishedWithinHours / 24} ngày qua"
    f.publishedFrom != null || f.publishedTo != null ->
        (f.publishedFrom?.let { Format.dateOnly(it) } ?: "…") + "–" + (f.publishedTo?.let { Format.dateOnly(it) } ?: "…")
    else -> null
}

/** Bộ lọc nâng cao dạng bảng trượt từ dưới lên, có đếm trực tiếp số kết quả. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterSheet(
    initial: ResearchFilters,
    countFor: suspend (ResearchFilters) -> Int,
    onDismiss: () -> Unit,
    onApply: (ResearchFilters) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(initial) }
    var count by remember { mutableStateOf<Int?>(null) }
    var showRange by remember { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(draft) {
        count = null
        delay(250)
        count = countFor(draft)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bộ lọc", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { draft = ResearchFilters() }) { Text("Đặt lại") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Spacer(Modifier.height(2.dp))
                FilterSection("Thời gian đăng") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("24 giờ" to 24L, "7 ngày" to 168L, "30 ngày" to 720L).forEach { (label, hours) ->
                            ChoiceChip(label, draft.publishedWithinHours == hours, {
                                draft = draft.copy(
                                    publishedWithinHours = if (draft.publishedWithinHours == hours) null else hours,
                                    publishedFrom = null, publishedTo = null
                                )
                            })
                        }
                        val custom = draft.publishedFrom != null || draft.publishedTo != null
                        ChoiceChip(if (custom) presetLabel(draft).orEmpty() else "Tùy chọn…", custom, { showRange = true })
                    }
                }
                FilterSection("Lượt xem tối thiểu") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Bất kỳ" to null, "1 N" to 1_000L, "10 N" to 10_000L, "100 N" to 100_000L, "1 Tr" to 1_000_000L)
                            .forEach { (label, value) ->
                                ChoiceChip(label, draft.minViews == value, { draft = draft.copy(minViews = value) })
                            }
                    }
                }
                FilterSection("Hoạt động") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Đã xem" to "VIEW_VIDEO", "Đã tải phụ đề" to DOWNLOADED, "Đã phân tích" to "ANALYZE_URL")
                            .forEach { (label, type) ->
                                val on = type in draft.activityTypes
                                ChoiceChip(label, on, {
                                    draft = draft.copy(activityTypes = if (on) draft.activityTypes - type else draft.activityTypes + type)
                                })
                            }
                    }
                }
                FilterSection("Kênh hoặc playlist") {
                    OutlinedTextField(
                        value = draft.channelOrPlaylistContains,
                        onValueChange = { draft = draft.copy(channelOrPlaylistContains = it) },
                        placeholder = { Text("Nhập tên kênh hoặc playlist") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    Modifier.fillMaxWidth().clickable { more = !more }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Thêm điều kiện", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(if (more) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                if (more) MoreConditions(draft) { draft = it }
                Spacer(Modifier.height(4.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Hủy") }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1.6f).heightIn(min = 52.dp)) {
                    Text(count?.let { "Xem $it kết quả" } ?: "Xem kết quả")
                }
            }
        }
    }

    if (showRange) {
        DateRangeDialog(
            onDismiss = { showRange = false },
            onConfirm = { from, to ->
                showRange = false
                draft = draft.copy(publishedFrom = from, publishedTo = to, publishedWithinHours = null)
            }
        )
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** Các điều kiện ít dùng: giữ nguyên toàn bộ khả năng lọc của bản cũ. */
@Composable
private fun MoreConditions(draft: ResearchFilters, onChange: (ResearchFilters) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextCondition("Tên kênh chứa", draft.channelContains) { onChange(draft.copy(channelContains = it)) }
        TextCondition("Tên playlist chứa", draft.playlistContains) { onChange(draft.copy(playlistContains = it)) }
        TextCondition("Tiêu đề chứa", draft.titleContains) { onChange(draft.copy(titleContains = it)) }
        TextCondition("Mô tả chứa", draft.descriptionContains) { onChange(draft.copy(descriptionContains = it)) }
        TextCondition("Tags chứa", draft.tagsContains) { onChange(draft.copy(tagsContains = it)) }
        TextCondition("Danh mục chứa", draft.categoryContains) { onChange(draft.copy(categoryContains = it)) }
        TextCondition("Chủ đề chứa", draft.topicContains) { onChange(draft.copy(topicContains = it)) }
        TextCondition("Từ khóa đã tìm", draft.searchKeywordContext) { onChange(draft.copy(searchKeywordContext = it)) }
        NumberRange("Lượt xem tối đa", null, draft.maxViews, {}, { onChange(draft.copy(maxViews = it)) }, onlyMax = true)
        NumberRange("Người đăng ký", draft.minSubscribers, draft.maxSubscribers,
            { onChange(draft.copy(minSubscribers = it)) }, { onChange(draft.copy(maxSubscribers = it)) })
        NumberRange("Lượt thích", draft.minLikes, draft.maxLikes,
            { onChange(draft.copy(minLikes = it)) }, { onChange(draft.copy(maxLikes = it)) })
        NumberRange("Bình luận", draft.minComments, draft.maxComments,
            { onChange(draft.copy(minComments = it)) }, { onChange(draft.copy(maxComments = it)) })
        DateRangeText("Ngày lấy dữ liệu (yyyy-MM-dd)", draft.fetchedFrom, draft.fetchedTo,
            { onChange(draft.copy(fetchedFrom = it)) }, { onChange(draft.copy(fetchedTo = it)) })
        DateRangeText("Ngày hoạt động (yyyy-MM-dd)", draft.activityFrom, draft.activityTo,
            { onChange(draft.copy(activityFrom = it)) }, { onChange(draft.copy(activityTo = it)) })
    }
}

@Composable
private fun TextCondition(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NumberRange(
    label: String, min: Long?, max: Long?,
    onMin: (Long?) -> Unit, onMax: (Long?) -> Unit,
    onlyMax: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!onlyMax) NumberBox("Từ", min, onMin, Modifier.weight(1f))
            NumberBox(if (onlyMax) "Tối đa" else "Đến", max, onMax, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NumberBox(hint: String, value: Long?, onChange: (Long?) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { onChange(it.filter(Char::isDigit).take(12).toLongOrNull()) },
        placeholder = { Text(hint) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    )
}

@Composable
private fun DateRangeText(label: String, from: Long?, to: Long?, onFrom: (Long?) -> Unit, onTo: (Long?) -> Unit) {
    var fromText by remember { mutableStateOf(from?.let { Format.localDate(it).toString() }.orEmpty()) }
    var toText by remember { mutableStateOf(to?.let { Format.localDate(it).toString() }.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = fromText,
                onValueChange = { fromText = it; onFrom(parseDay(it, endOfDay = false)) },
                placeholder = { Text("Từ") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = toText,
                onValueChange = { toText = it; onTo(parseDay(it, endOfDay = true)) },
                placeholder = { Text("Đến") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun parseDay(text: String, endOfDay: Boolean): Long? = runCatching {
    val day = LocalDate.parse(text.trim())
    val zone = ZoneId.systemDefault()
    if (endOfDay) day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    else day.atStartOfDay(zone).toInstant().toEpochMilli()
}.getOrNull()

/** Chọn khoảng ngày đăng tùy ý bằng lịch. Trả về mốc đầu ngày bắt đầu và cuối ngày kết thúc theo giờ máy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(onDismiss: () -> Unit, onConfirm: (Long?, Long?) -> Unit) {
    val state = rememberDateRangePickerState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Đóng") }
                    Text("Chọn khoảng ngày đăng", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = state.selectedStartDateMillis != null,
                        onClick = {
                            val zone = ZoneId.systemDefault()
                            fun day(utcMillis: Long) = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
                            val start = state.selectedStartDateMillis?.let { day(it).atStartOfDay(zone).toInstant().toEpochMilli() }
                            val end = (state.selectedEndDateMillis ?: state.selectedStartDateMillis)
                                ?.let { day(it).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1 }
                            onConfirm(start, end)
                        }
                    ) { Text("Lưu") }
                }
                DateRangePicker(state = state, modifier = Modifier.weight(1f))
            }
        }
    }
}
