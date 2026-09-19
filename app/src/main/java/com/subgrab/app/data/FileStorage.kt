package com.subgrab.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.subgrab.app.domain.FileNameSanitizer
import java.io.File

class FileStorage(private val context: Context) {
    private val stagingRoot: File get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SubGrab")

    fun defaultDirectory(): File = stagingRoot

    fun createTaskDirectory(folderName: String, outputDir: String = "Download/Subtitles"): File {
        val safeBase = outputDir.split('/', '\\').map(::sanitizeSegment).filter(String::isNotBlank).joinToString(File.separator).ifBlank { "Download/Subtitles" }
        return File(stagingRoot, safeBase + File.separator + FileNameSanitizer.sanitize(folderName).ifBlank { "SubGrab" }).apply { mkdirs() }
    }

    fun publishToDownloads(directory: File, relativePath: String): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) publishWithMediaStore(directory, relativePath) else publishLegacy(directory, relativePath)
    }

    private fun publishWithMediaStore(directory: File, relativePath: String): List<String> {
        val published = mutableListOf<String>()
        directory.listFiles()?.filter(::isSubtitleFile)?.forEach { file ->
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mimeType(file))
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$relativePath")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@forEach
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                published += uri.toString()
            }.onFailure { context.contentResolver.delete(uri, null, null) }
        }
        return published
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(directory: File, relativePath: String): List<String> {
        val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), relativePath).apply { mkdirs() }
        return directory.listFiles()?.filter(::isSubtitleFile)?.map { source ->
            val destination = File(target, source.name); source.copyTo(destination, overwrite = true); destination.absolutePath
        } ?: emptyList()
    }

    private fun isSubtitleFile(file: File): Boolean = file.isFile && (file.extension.equals("srt", true) || file.extension.equals("txt", true))
    private fun mimeType(file: File): String = if (file.extension.equals("srt", true)) "application/x-subrip" else "text/plain"
    private fun sanitizeSegment(value: String): String = FileNameSanitizer.sanitize(value).take(60)
}
