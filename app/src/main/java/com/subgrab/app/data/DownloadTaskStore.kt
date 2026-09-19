package com.subgrab.app.data

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Persists the complete download task outside WorkManager Data.
 *
 * WorkManager Data is limited to 10 KiB when serialized. A multi-video
 * download task can exceed that limit because each VideoItem contains
 * titles, thumbnails and subtitle metadata.
 */
class DownloadTaskStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "download_tasks")

    init {
        directory.mkdirs()
    }

    fun save(encodedTask: String): String {
        val id = UUID.randomUUID().toString()
        File(directory, "$id.task").writeText(encodedTask, Charsets.UTF_8)
        cleanupOld()
        return id
    }

    fun load(id: String): String? {
        if (!TASK_ID_PATTERN.matches(id)) return null
        val file = File(directory, "$id.task")
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    fun delete(id: String) {
        if (!TASK_ID_PATTERN.matches(id)) return
        File(directory, "$id.task").delete()
    }

    private fun cleanupOld() {
        val cutoff = System.currentTimeMillis() - MAX_TASK_AGE_MS
        directory.listFiles { file -> file.isFile && file.name.endsWith(".task") }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach(File::delete)
    }

    private companion object {
        val TASK_ID_PATTERN = Regex("[0-9a-fA-F-]{36}")
        const val MAX_TASK_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
