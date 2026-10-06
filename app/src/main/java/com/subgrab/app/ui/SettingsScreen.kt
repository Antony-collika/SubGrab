package com.subgrab.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.subgrab.app.data.SettingsRepository
import com.subgrab.app.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Giữ bản nháp cài đặt trong lúc người dùng chỉnh và lưu ngay xuống DataStore.
 * Dùng chung cho màn Cài đặt chính và các màn con (API, Tốc độ).
 */
@Stable
private class SettingsEditor(private val repository: SettingsRepository, private val scope: CoroutineScope) {
    var draft by mutableStateOf<AppSettings?>(null)
    var saveError by mutableStateOf<String?>(null)
    val value: AppSettings get() = draft ?: AppSettings()

    fun save(v: AppSettings) {
        // Chưa đọc xong giá trị thật từ DataStore thì không ghi đè, tránh mất cài đặt đã lưu.
        if (draft == null) return
        // Chuẩn hóa các trường có giới hạn cứng; không từ chối các cài đặt khác chỉ vì một giá trị sai.
        val normalized = v.copy(
            subtitleBaseDelayMs = v.subtitleBaseDelayMs.coerceAtLeast(0L),
            subtitleJitterMinMs = v.subtitleJitterMinMs.coerceAtLeast(0L),
            subtitleJitterMaxMs = v.subtitleJitterMaxMs.coerceAtLeast(v.subtitleJitterMinMs.coerceAtLeast(0L)),
            subtitleConcurrency = v.subtitleConcurrency.coerceAtLeast(1),
            maxSubtitlesPerTask = v.maxSubtitlesPerTask.coerceIn(1, 50),
            apiBaseDelayMs = v.apiBaseDelayMs.coerceAtLeast(0L),
            apiJitterMinMs = v.apiJitterMinMs.coerceAtLeast(0L),
            apiJitterMaxMs = v.apiJitterMaxMs.coerceAtLeast(v.apiJitterMinMs.coerceAtLeast(0L)),
            videosPerSource = v.videosPerSource.coerceAtLeast(0),
            commentsPerVideo = v.commentsPerVideo.coerceAtLeast(0)
        )
        draft = normalized
        saveError = null
        scope.launch {
            runCatching { repository.update(normalized) }
                .onFailure { saveError = "Không thể lưu thiết lập: ${it.message ?: "lỗi không xác định"}" }
        }
    }
}

@Composable
private fun rememberSettingsEditor(repository: SettingsRepository): SettingsEditor {
    val scope = rememberCoroutineScope()
    val editor = remember(repository) { SettingsEditor(repository, scope) }
    val stored by repository.settings.collectAsState(initial = null)
    // Chờ giá trị thật từ DataStore rồi mới khởi tạo bản nháp (không dùng giá trị mặc định tạm).
    LaunchedEffect(stored) {
        if (editor.draft == null && stored != null) editor.draft = stored
    }
    return editor
}

// ---------------------------------------------------------------------------------------------
// Màn Cài đặt chính
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onOpenApi: () -> Unit,
    onOpenPacing: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editor = rememberSettingsEditor(repository)
    val value = editor.value
    val pickFolder = rememberDownloadFolderPicker { editor.save(value.copy(outputDir = it)) }
    var dialog by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize()) {
        AppTopBar("Cài đặt")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingsGroup("Tải phụ đề") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ngôn ngữ ưu tiên", style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val first = value.languages.firstOrNull()
                        ChoiceChip("English", first == "en", { editor.save(value.copy(languages = listOf("en"))) })
                        ChoiceChip("Tiếng Việt", first == "vi", { editor.save(value.copy(languages = listOf("vi"))) })
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Định dạng", style = MaterialTheme.typography.bodyLarge)
                    val format = value.formats.firstOrNull() ?: OutputFormat.TXT
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("TXT", format == OutputFormat.TXT, { editor.save(value.copy(formats = setOf(OutputFormat.TXT))) })
                        ChoiceChip("SRT", format == OutputFormat.SRT, { editor.save(value.copy(formats = setOf(OutputFormat.SRT))) })
                    }
                    if (format == OutputFormat.SRT) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip("Có timestamp", value.timestampMode == SubtitleTimestampMode.WITH_TIMESTAMP,
                                { editor.save(value.copy(timestampMode = SubtitleTimestampMode.WITH_TIMESTAMP)) })
                            ChoiceChip("Không timestamp", value.timestampMode == SubtitleTimestampMode.WITHOUT_TIMESTAMP,
                                { editor.save(value.copy(timestampMode = SubtitleTimestampMode.WITHOUT_TIMESTAMP)) })
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow("Ưu tiên phụ đề chính thức", "Dùng bản do người đăng tải nếu có", value.preferManualSub) {
                    editor.save(value.copy(preferManualSub = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow("Bỏ qua video không có phụ đề", null, value.skipNoSub) { editor.save(value.copy(skipNoSub = it)) }
            }

            SettingsGroup("Lưu trữ") {
                ValueRow("Thư mục lưu", "Bên trong Download", value.outputDir, pickFolder)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ValueRow("Thời gian lưu tạm dữ liệu", "0 = luôn làm mới. Chỉ áp dụng cho video lẻ và từ khóa", "${value.metadataCacheHours} giờ") { dialog = "cache" }
            }

            SettingsGroup("Giới hạn lấy dữ liệu") {
                ValueRow("Video mỗi nguồn", "Kênh/playlist. Từ khóa luôn lấy 1 trang đầu", limitLabel(value.videosPerSource, "video")) { dialog = "videos" }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ValueRow("Comment mỗi video", "Số comment gốc tối đa mỗi video", limitLabel(value.commentsPerVideo, "comment")) { dialog = "comments" }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ValueRow("Phụ đề mỗi lượt", "Số video tối đa trong 1 lượt tải", "${value.maxSubtitlesPerTask} video") { dialog = "batch" }
            }

            SettingsGroup("Nâng cao") {
                NavRow("YouTube Data API", onOpenApi)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavRow("Tốc độ và giới hạn request", onOpenPacing)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavRow("Nhật ký và chẩn đoán", onOpenLogs)
            }

            editor.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }

    when (dialog) {
        "videos" -> LimitDialog(
            title = "Video mỗi nguồn",
            hint = "Số video tự lấy mỗi lần phân tích kênh/playlist. Chọn Tất cả sẽ lấy đến hết, có thanh tiến trình và nút Dừng. " +
                "Luôn có nút Tải thêm ở màn kết quả. Từ khóa chỉ lấy 1 trang đầu vì mỗi lần tìm tốn khoảng 100 đơn vị hạn mức API.",
            options = listOf(50, 100, 500, 0),
            unit = "video",
            selected = value.videosPerSource,
            onDismiss = { dialog = null },
            onSelect = { dialog = null; editor.save(value.copy(videosPerSource = it)) }
        )
        "comments" -> LimitDialog(
            title = "Comment mỗi video",
            hint = "Số comment gốc tối đa lấy cho mỗi video (kèm phần trả lời của các comment đó). " +
                "Mỗi 100 comment tốn khoảng 1 đơn vị hạn mức YouTube API.",
            options = listOf(100, 500, 0),
            unit = "comment",
            selected = value.commentsPerVideo,
            onDismiss = { dialog = null },
            onSelect = { dialog = null; editor.save(value.copy(commentsPerVideo = it)) }
        )
        "batch" -> NumberDialog(
            title = "Phụ đề mỗi lượt",
            hint = "Từ 1 đến 50 video. Các video còn lại sẽ chờ lượt kế tiếp.",
            initial = value.maxSubtitlesPerTask.toLong(),
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; editor.save(value.copy(maxSubtitlesPerTask = it.toInt().coerceIn(1, 50))) }
        )
        "cache" -> NumberDialog(
            title = "Thời gian lưu tạm dữ liệu",
            hint = "Tính bằng giờ. 0 nghĩa là luôn làm mới. Transcript vẫn được lưu lâu dài.",
            initial = value.metadataCacheHours,
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; editor.save(value.copy(metadataCacheHours = it.coerceAtLeast(0L))) }
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))) {
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ValueRow(title: String, subtitle: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NumberDialog(title: String, hint: String, initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(6) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotEmpty(), onClick = { text.toLongOrNull()?.let(onConfirm) }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

private fun limitLabel(value: Int, unit: String): String = if (value <= 0) "Tất cả" else "$value $unit"

@Composable
private fun LimitDialog(
    title: String,
    hint: String,
    options: List<Int>,
    unit: String,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(limitLabel(option, unit), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

// ---------------------------------------------------------------------------------------------
// Màn con: YouTube Data API
// ---------------------------------------------------------------------------------------------

@Composable
fun ApiSettingsScreen(repository: SettingsRepository, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val editor = rememberSettingsEditor(repository)
    val value = editor.value
    Column(modifier.fillMaxSize()) {
        AppTopBar("YouTube Data API", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsGroup("Kết nối") {
                SwitchRow("Dùng YouTube Data API", "Lấy thêm thông tin video (lượt thích, bình luận, số người đăng ký)", value.useYouTubeDataApi) {
                    editor.save(value.copy(useYouTubeDataApi = it))
                }
            }
            OutlinedTextField(
                value = value.youtubeDataApiKey,
                onValueChange = { editor.save(value.copy(youtubeDataApiKey = it)) },
                label = { Text("YouTube Data API key") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (value.useYouTubeDataApi && value.youtubeDataApiKey.isBlank()) {
                Text("API đang bật nhưng chưa có API key.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            editor.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Màn con: Tốc độ và giới hạn request
// ---------------------------------------------------------------------------------------------

@Composable
fun PacingSettingsScreen(repository: SettingsRepository, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val editor = rememberSettingsEditor(repository)
    val value = editor.value
    Column(modifier.fillMaxSize()) {
        AppTopBar("Tốc độ và giới hạn request", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PacingSection(
                "Khi tải phụ đề",
                value.subtitleDelayMode, value.subtitleBaseDelayMs, value.subtitleJitterMinMs, value.subtitleJitterMaxMs,
                value.subtitleConcurrency,
                { editor.save(value.copy(subtitleDelayMode = it)) },
                { editor.save(value.copy(subtitleBaseDelayMs = it)) },
                { editor.save(value.copy(subtitleJitterMinMs = it)) },
                { editor.save(value.copy(subtitleJitterMaxMs = it)) },
                { editor.save(value.copy(subtitleConcurrency = it.coerceAtLeast(1))) },
                true
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PacingSection(
                "Khi gọi YouTube Data API",
                value.apiDelayMode, value.apiBaseDelayMs, value.apiJitterMinMs, value.apiJitterMaxMs,
                null,
                { editor.save(value.copy(apiDelayMode = it)) },
                { editor.save(value.copy(apiBaseDelayMs = it)) },
                { editor.save(value.copy(apiJitterMinMs = it)) },
                { editor.save(value.copy(apiJitterMaxMs = it)) },
                {},
                false
            )
            editor.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun PacingSection(
    title: String,
    mode: String,
    base: Long,
    jmin: Long,
    jmax: Long,
    concurrency: Int?,
    setMode: (String) -> Unit,
    setBase: (Long) -> Unit,
    setMin: (Long) -> Unit,
    setMax: (Long) -> Unit,
    setConcurrency: (Int) -> Unit,
    auto: Boolean
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("Không giãn cách", mode == "NONE", { setMode("NONE") })
        if (auto) ChoiceChip("Tự động", mode == "AUTO", { setMode("AUTO") })
    }
    NumberField("Độ trễ cơ bản (ms)", base, setBase)
    NumberField("Dao động tối thiểu (ms)", jmin, setMin)
    NumberField("Dao động tối đa (ms)", jmax, setMax)
    concurrency?.let { NumberField("Số luồng đồng thời", it.toLong()) { n -> setConcurrency(n.toInt()) } }
}

@Composable
private fun NumberField(label: String, value: Long, onChange: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (value == 0L) "" else value.toString()) }

    // Giữ bộ đệm đang gõ độc lập với giá trị số để không bị tạo lại sau mỗi phím.
    LaunchedEffect(value) {
        val numericText = text.toLongOrNull()
        if (numericText != value && !(value == 0L && text.isEmpty())) {
            text = if (value == 0L) "" else value.toString()
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            when {
                input.isEmpty() -> {
                    text = ""
                    onChange(0L)
                }
                input.all(Char::isDigit) -> {
                    input.toLongOrNull()?.let { n ->
                        text = input
                        onChange(n)
                    }
                }
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    )
}
