package com.subgrab.app.data

import android.content.Context
import android.os.Build
import java.io.File

class YtDlpBinaryProvider(private val context: Context) {
    fun executable(): File {
        val asset = when (Build.SUPPORTED_ABIS.firstOrNull()) {
            "arm64-v8a" -> "yt-dlp-arm64-v8a"
            "armeabi-v7a" -> "yt-dlp-armeabi-v7a"
            else -> error("Thiết bị không hỗ trợ ARM64 hoặc ARMv7")
        }
        return File(context.filesDir, "yt-dlp").also { target ->
            if (!target.exists()) context.assets.open(asset).use { input -> target.outputStream().use(input::copyTo) }
            check(target.setExecutable(true, false)) { "Không thể cấp quyền chạy yt-dlp" }
        }
    }
}
