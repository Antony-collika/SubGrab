package com.subgrab.app.data

import android.content.Context
import android.os.Environment
import com.subgrab.app.domain.FileNameSanitizer
import com.subgrab.app.domain.SrtToTxtConverter
import java.io.File

class FileStorage(private val context: Context) {
    fun defaultDirectory(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Subtitles")
    fun createTaskDirectory(folderName: String): File = File(defaultDirectory(), FileNameSanitizer.sanitize(folderName).ifBlank { "SubGrab" }).apply { mkdirs() }
    fun convertSrtToTxt(directory: File): List<File> = directory.listFiles()?.filter { it.extension.equals("srt", true) }?.map { srt -> File(srt.parentFile, srt.nameWithoutExtension + ".txt").also { it.writeText(SrtToTxtConverter.convert(srt.readText())) } } ?: emptyList()
}
