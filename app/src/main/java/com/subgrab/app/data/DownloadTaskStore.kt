package com.subgrab.app.data

import android.content.Context
import java.io.File
import java.util.UUID

class DownloadTaskStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "download_tasks")
    private val currentFile = File(directory, "current.task")
    init { directory.mkdirs() }

    fun save(encodedTask: String, taskIndex: Int = 1, totalTasks: Int = 1): String {
        val id = UUID.randomUUID().toString()
        File(directory, "$taskIndex-$totalTasks-$id.task").writeText(encodedTask, Charsets.UTF_8)
        cleanupOld()
        return id
    }

    fun setCurrent(id: String) {
        if (id.matches(ID_ONLY_PATTERN) && find(id)?.exists() == true) {
            currentFile.writeText(id, Charsets.UTF_8)
        }
    }

    fun currentId(): String? =
        currentFile.takeIf { it.exists() }?.readText(Charsets.UTF_8)?.trim()?.takeIf { it.matches(ID_ONLY_PATTERN) }

    fun load(id: String): String? {
        if (!id.matches(ID_ONLY_PATTERN)) return null
        val f = find(id)
        return if (f?.exists() == true) f.readText(Charsets.UTF_8) else null
    }

    fun delete(id: String) {
        if (!id.matches(ID_ONLY_PATTERN)) return
        find(id)?.delete()
        if (currentId() == id) currentFile.delete()
    }

    fun pendingIds(): List<String> =
        directory.listFiles { f -> f.isFile && f.name.endsWith(".task") && f.name != currentFile.name }
            ?.sortedBy { f -> f.name.substringBefore("-").toIntOrNull() ?: Int.MAX_VALUE }
            ?.mapNotNull { ID_PATTERN.find(it.name)?.groupValues?.get(1) }
            ?: emptyList()

    private fun find(id: String) =
        directory.listFiles()?.firstOrNull { it.name.endsWith("-$id.task") }

    private fun cleanupOld() {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        directory.listFiles { f -> f.isFile && f.name.endsWith(".task") && f.name != currentFile.name }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach(File::delete)
    }

    companion object {
        private val ID_ONLY_PATTERN = Regex("^[0-9a-fA-F-]{36}$")
        private val ID_PATTERN = Regex("^(?:[0-9]+-[0-9]+-)?([0-9a-fA-F-]{36})[.]task$")
    }
}