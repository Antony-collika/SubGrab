package com.subgrab.app.ui

import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.subgrab.app.SubGrabExtras

/** Thanh tiêu đề dùng chung: tiêu đề đậm căn trái, nút quay lại (nếu có) và các nút hành động bên phải. */
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại") }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

enum class ChipKind { SUCCESS, WARNING, NEUTRAL, ERROR }

/** Nhãn trạng thái nhỏ, bo tròn (ví dụ "Có phụ đề", "Chưa kiểm tra"). */
@Composable
fun StatusChip(text: String, kind: ChipKind, modifier: Modifier = Modifier) {
    val container: Color
    val content: Color
    var border: BorderStroke? = null
    when (kind) {
        ChipKind.SUCCESS -> { container = MaterialTheme.colorScheme.tertiaryContainer; content = MaterialTheme.colorScheme.onTertiaryContainer }
        ChipKind.WARNING -> { container = SubGrabExtras.warningContainer; content = SubGrabExtras.onWarningContainer }
        ChipKind.ERROR -> { container = MaterialTheme.colorScheme.errorContainer; content = MaterialTheme.colorScheme.onErrorContainer }
        ChipKind.NEUTRAL -> {
            container = Color.Transparent
            content = MaterialTheme.colorScheme.onSurfaceVariant
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    }
    Surface(shape = RoundedCornerShape(50), color = container, border = border, modifier = modifier) {
        Text(
            text,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

/** Ảnh thu nhỏ video 16:9 kèm nhãn thời lượng ở góc dưới bên phải. */
@Composable
fun VideoThumbnail(url: String?, durationSeconds: Long?, modifier: Modifier = Modifier) {
    Box(
        modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (durationSeconds != null && durationSeconds > 0) {
            Text(
                Format.duration(durationSeconds),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color(0xCC1F1F1F))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

/** Mở bộ chọn thư mục, chỉ chấp nhận thư mục nằm trong Download. Trả về hàm để gọi khi bấm nút. */
@Composable
fun rememberDownloadFolderPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val documentId = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
        val relative = documentId?.removePrefix("primary:")
        if (relative == "Download" || relative?.startsWith("Download/") == true) {
            onPicked(relative)
        } else if (uri != null) {
            Toast.makeText(context, "Chỉ chọn được thư mục nằm trong Download", Toast.LENGTH_SHORT).show()
        }
    }
    return {
        launcher.launch(DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Download"))
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Chip lựa chọn bo tròn: xanh nhạt khi được chọn, viền xám khi chưa chọn. */
@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = modifier
    )
}
