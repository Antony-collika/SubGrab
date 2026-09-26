package com.subgrab.app

import com.subgrab.app.data.export.ResearchExport
import com.subgrab.app.domain.VideoSearchResult
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchExportTest {
    private val result = VideoSearchResult(
        videoId = "abc",
        title = "A, B",
        description = "Description",
        channelId = "channel",
        channelName = "Channel",
        thumbnail = null,
        publishedAt = "2026-01-01T00:00:00Z",
        fetchedAt = 1L,
        durationSeconds = 60L,
        subscriberCount = 10L,
        viewCount = 20L,
        likeCount = 3L,
        commentCount = 2L,
        tags = "one two",
        category = "Education",
        topic = "topic",
        playlistTitles = "Playlist",
        searchKeywords = "keyword"
    )

    @Test fun json_contains_current_snapshot_fields() {
        val output = ResearchExport.json(listOf(result))
        assertTrue(output.contains("\"videoId\": \"abc\""))
        assertTrue(output.contains("\"title\": \"A, B\""))
    }

    @Test fun csv_quotes_commas() {
        val output = ResearchExport.csv(listOf(result))
        assertTrue(output.contains("\"A, B\""))
    }
}
