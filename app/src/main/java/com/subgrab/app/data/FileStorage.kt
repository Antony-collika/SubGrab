package com.subgrab.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.subgrab.app.domain.FileNameSanitizer
import java.io.File

class StorageFailure(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class FileStorage(private val context: Context) {
    private val stagingRoot: File get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SubGrab")

    fun defaultDirectory(): File = stagingRoot

    private val reservedFolders = mutableSetOf<String>()

    private fun outputParentRelativePath(outputDir: String): String {
        val root = outputDir.replace('\\', '/').removePrefix("Download").trim('/')
        return listOf(root, "SubGrab").filter(String::isNotBlank).joinToString("/")
    }

    @Synchronized
    private fun uniqueFolderName(parentRelativePath: String, requestedName: String): String {
        val safeName = sanitizeSegment(requestedName).ifBlank { "SubGrab" }
        val fullParent = "Download/" + parentRelativePath.trim('/')
        val existing = mutableSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads.RELATIVE_PATH),
                "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf("$fullParent/%"),
                null
            )?.use { cursor ->
                val column = cursor.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(column)?.replace('\\', '/')?.trimEnd('/').orEmpty()
                    val prefix = "$fullParent/"
                    if (path.startsWith(prefix)) {
                        val child = path.removePrefix(prefix).substringBefore('/')
                        if (child.isNotBlank()) existing += child
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val parent = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), parentRelativePath)
            parent.listFiles()?.filter { it.isDirectory }?.forEach { existing += it.name }
        }
        var candidate = safeName
        var suffix = 1
        while (candidate in existing || "$fullParent/$candidate" in reservedFolders) {
            candidate = "$safeName ($suffix)"
            suffix++
        }
        reservedFolders += "$fullParent/$candidate"
        return candidate
    }

    fun createTaskDirectory(folderName: String, outputDir: String = "Download"): File {
        val parent = outputParentRelativePath(outputDir)
        val uniqueName = uniqueFolderName(parent, "transcript_${sanitizeSegment(folderName)}")
        return File(stagingRoot, uniqueName).apply {
            mkdirs()
            listFiles()?.filter(::isSubtitleFile)?.forEach { file ->
                if (!file.delete() && file.exists()) throw StorageFailure("Không thể dọn file staging cũ: " + file.name)
            }
        }
    }

    fun outputRelativePath(outputDir: String, directory: File): String =
        outputParentRelativePath(outputDir) + "/" + directory.name

    fun createExportDirectory(folderName: String, outputDir: String = "Download"): String {
        val parent = outputParentRelativePath(outputDir)
        val uniqueName = uniqueFolderName(parent, "data_${sanitizeSegment(folderName)}")
        return "$parent/$uniqueName"
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
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw StorageFailure("Không thể tạo file trong Downloads: " + file.name)
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                published += uri.toString()
            }.onFailure {
                context.contentResolver.delete(uri, null, null)
                throw StorageFailure("Không thể xuất file: " + file.name, it)
            }
        }
        return published
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(directory: File, relativePath: String): List<String> {
        val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), relativePath).apply { mkdirs() }
        return directory.listFiles()?.filter(::isSubtitleFile)?.map { source ->
            runCatching {
                val destination = File(target, source.name)
                source.copyTo(destination, overwrite = true)
                destination.absolutePath
            }.getOrElse { throw StorageFailure("Không thể xuất file: " + source.name, it) }
        } ?: emptyList()
    }

    fun publishTextFile(fileName: String, content: String, relativePath: String): String {
        val safeName = FileNameSanitizer.sanitize(fileName).ifBlank { "subgrab_export" }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SubGrabExport").apply { mkdirs() }
        val file = File(dir, safeName).apply { writeText(content) }
        return publishGenericFile(file, relativePath)
    }

    private fun publishGenericFile(file: File, relativePath: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, when (file.extension.lowercase()) {
                    "json" -> "application/json"
                    "csv" -> "text/csv"
                    "md" -> "text/markdown"
                    else -> "text/plain"
                })
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw StorageFailure("Không thể tạo file trong Downloads: " + file.name)
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }.onFailure {
                context.contentResolver.delete(uri, null, null)
                throw StorageFailure("Không thể xuất file: " + file.name, it)
            }
            return uri.toString()
        }
        val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), relativePath).apply { mkdirs() }
        return file.copyTo(File(target, file.name), overwrite = true).absolutePath
    }

    private fun isSubtitleFile(file: File): Boolean = file.isFile && (file.extension.equals("srt", true) || file.extension.equals("txt", true))
    private fun mimeType(file: File): String = if (file.extension.equals("srt", true)) "application/x-subrip" else "text/plain"
    private fun sanitizeSegment(value: String): String = FileNameSanitizer.sanitize(value).trim().take(60)
}
