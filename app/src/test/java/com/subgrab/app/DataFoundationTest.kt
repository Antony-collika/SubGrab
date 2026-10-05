package com.subgrab.app

import com.subgrab.app.data.MetadataNormalizer
import com.subgrab.app.domain.SubtitleLanguage
import com.subgrab.app.domain.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataFoundationTest {
    @Test
    fun normalizerPreservesLevelOneMetadata() {
        val item = VideoItem(
            index = 1,
            videoId = "abc",
            title = "Video",
            durationSec = 60,
            availableSubs = listOf(SubtitleLanguage("vi")),
            channelTitle = "Channel",
            publishedAt = "2026-09-25T00:00:00Z",
            viewCount = 123L,
            thumbnailUrl = "https://example.test/thumb",
            description = "Description",
            durationSeconds = 60L,
            likeCount = 10L,
            channelId = "channel-id",
            subscriberCount = 456L,
            commentCount = 7L,
            tags = listOf("one", "two"),
            category = "10",
            topic = listOf("topic")
        )

        val normalized = MetadataNormalizer.normalize(item)

        assertEquals("abc", normalized.videoId)
        assertEquals("Channel", normalized.channelName)
        assertEquals("channel-id", normalized.channelId)
        assertEquals(456L, normalized.subscriberCount)
        assertEquals(7L, normalized.commentCount)
        assertEquals(listOf("one", "two"), normalized.tags)
        assertEquals("10", normalized.category)
        assertEquals(listOf("topic"), normalized.topic)
    }

    @Test
    fun normalizerUsesDurationSecAsFallback() {
        val item = VideoItem(1, "abc", "Video", 42, emptyList())
        assertEquals(42L, MetadataNormalizer.normalize(item).durationSeconds)
        assertNull(MetadataNormalizer.normalize(item).channelId)
    }
}
