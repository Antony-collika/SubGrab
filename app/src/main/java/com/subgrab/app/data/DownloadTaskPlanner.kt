package com.subgrab.app.data

import com.subgrab.app.domain.VideoItem

object DownloadTaskPlanner {
    fun plan(videos: List<VideoItem>, maxPerTask: Int = 10): List<List<VideoItem>> {
        val taskSize = maxPerTask.coerceAtLeast(1)
        return videos.filter { it.isSelected }.chunked(taskSize).ifEmpty { listOf(emptyList()) }
    }
}
