package com.subgrab.app.data

import com.subgrab.app.domain.VideoItem

object DownloadTaskPlanner {
    fun plan(videos: List<VideoItem>, maxPerTask: Int = 10): List<List<VideoItem>> {
        val taskSize = maxPerTask.coerceIn(1, MAX_VIDEOS)
        return videos.filter { it.isSelected }.take(MAX_VIDEOS).chunked(taskSize).ifEmpty { listOf(emptyList()) }
    }

    private const val MAX_VIDEOS = 50
}
