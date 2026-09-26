package com.subgrab.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subgrab.app.data.SubGrabDatabase
import com.subgrab.app.data.repository.KnowledgeRepository
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.VideoItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataFoundationInstrumentedTest {
    private lateinit var database: SubGrabDatabase
    private lateinit var repository: KnowledgeRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SubGrabDatabase::class.java
        ).build()
        repository = KnowledgeRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun analyzeCreatesIdentitySnapshotAndActivity() {
        val video = VideoItem(
            index = 1,
            videoId = "video-1",
            title = "Research video",
            durationSec = 60,
            availableSubs = emptyList(),
            channelId = "channel-1",
            channelTitle = "Channel",
            viewCount = 100L
        )

        database.run {
            kotlinx.coroutines.runBlocking {
                repository.saveAnalysis(
                    Source("video", "https://www.youtube.com/watch?v=video-1", "Research video", 1),
                    listOf(video)
                )
            }
        }

        kotlinx.coroutines.runBlocking {
            assertNotNull(repository.getVideo("video-1"))
            assertEquals(1, repository.getSnapshotHistory("video-1").size)
            assertEquals(1, repository.getRecentActivities().size)
        }
    }

    @Test
    fun repeatedAnalyzeWithinCacheWindowKeepsSingleSnapshot() {
        val video = VideoItem(1, "video-1", "Research video", 60, emptyList())
        val source = Source("video", "https://www.youtube.com/watch?v=video-1", "Research video", 1)

        kotlinx.coroutines.runBlocking {
            repository.saveAnalysis(source, listOf(video))
            repository.saveAnalysis(source, listOf(video.copy(viewCount = 200L)))
            assertEquals(1, repository.getSnapshotHistory("video-1").size)
            repository.saveAnalysis(source, listOf(video.copy(viewCount = 300L)), metadataCacheHours = 24, forceRefresh = true)
            assertEquals(2, repository.getSnapshotHistory("video-1").size)
        }
    }

    @Test
    fun transcriptIsStoredOnceAndDoesNotOverwriteDifferentLanguage() {
        val video = VideoItem(1, "video-1", "Transcript video", 60, emptyList())
        kotlinx.coroutines.runBlocking {
            repository.saveTranscript("video-1", "hello", "en")
            repository.saveTranscript("video-1", "xin chao", "vi")
            assertEquals("hello", repository.getTranscript("video-1")?.content)
            assertEquals("en", repository.getTranscript("video-1")?.language)
            repository.saveTranscript("video-1", "hello refreshed", "en", overwrite = true)
            assertEquals("hello refreshed", repository.getTranscript("video-1")?.content)
        }
    }

    @Test
    fun keywordSearchCreatesSessionAndLocalFtsDocument() {
        val video = VideoItem(1, "video-1", "Smartphone review", 60, emptyList(), channelTitle = "Tech Channel")

        kotlinx.coroutines.runBlocking {
            repository.saveKeywordSearch(
                "smartphone",
                Source("keyword:smartphone", "https://www.youtube.com/results?search_query=smartphone", "smartphone", 1),
                listOf(video)
            )
            assertEquals(listOf("video-1"), repository.searchVideos("Smartphone"))
        }
    }
}
