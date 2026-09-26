package com.subgrab.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subgrab.app.data.SubGrabDatabase
import com.subgrab.app.data.repository.KnowledgeRepository
import com.subgrab.app.data.repository.ResearchRepository
import com.subgrab.app.domain.ResearchFilters
import com.subgrab.app.domain.ResearchSort
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.VideoItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResearchRepositoryQueryTest {
    private lateinit var database: SubGrabDatabase
    private lateinit var knowledgeRepository: KnowledgeRepository
    private lateinit var researchRepository: ResearchRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SubGrabDatabase::class.java
        ).build()
        knowledgeRepository = KnowledgeRepository(database)
        researchRepository = ResearchRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun searchWithoutQueryReturnsAllIndexedVideosAndCorrectCount() = runBlocking {
        seedAnalysis()

        val (rows, count) = researchRepository.search("")

        assertEquals(3, count)
        assertEquals(listOf("video-3", "video-2", "video-1"), rows.map { it.videoId })
    }

    @Test
    fun ftsQueryWorksInsideDynamicResearchSearch() = runBlocking {
        seedAnalysis()

        val (rows, count) = researchRepository.search("Deep Dive")

        assertEquals(1, count)
        assertEquals(listOf("video-1"), rows.map { it.videoId })
    }

    @Test
    fun ftsQueryCanCombineWithPlaylistMatch() = runBlocking {
        seedAnalysis()

        val (rows, count) = researchRepository.search(
            "Stable Diffusion",
            ResearchFilters(playlistContains = "LLMs Research")
        )

        assertEquals(1, count)
        assertEquals(listOf("video-2"), rows.map { it.videoId })
    }

    @Test
    fun multipleLikeAndNumericFiltersComposeWithoutQueryErrors() = runBlocking {
        seedAnalysis()

        val (rows, count) = researchRepository.search(
            query = "",
            filters = ResearchFilters(
                channelContains = "Research Channel",
                minViews = 100,
                maxViews = 300,
                tagsContains = "ai",
                topicContains = "language"
            ),
            sort = ResearchSort.VIEWS_DESC
        )

        assertEquals(2, count)
        assertEquals(listOf("video-1", "video-3"), rows.map { it.videoId })
        assertTrue(rows.all { (it.viewCount ?: 0L) in 100L..300L })
    }

    @Test
    fun activityFiltersComposeWithFtsAndDateRange() = runBlocking {
        seedAnalysis()

        val (rows, count) = researchRepository.search(
            query = "Deep Dive",
            filters = ResearchFilters(
                activityTypes = setOf("ANALYZE_URL"),
                activityFrom = 1L,
                activityTo = Long.MAX_VALUE
            )
        )

        assertEquals(1, count)
        assertEquals(listOf("video-1"), rows.map { it.videoId })
    }

    @Test
    fun countAndPaginationUseTheSameFilteredQuery() = runBlocking {
        seedAnalysis()

        val (page0, count) = researchRepository.search("", page = 0, pageSize = 2)
        val (page1, secondCount) = researchRepository.search("", page = 1, pageSize = 2)

        assertEquals(3, count)
        assertEquals(3, secondCount)
        assertEquals(2, page0.size)
        assertEquals(1, page1.size)
        assertEquals(3, (page0.map { it.videoId } + page1.map { it.videoId }).toSet().size)
    }

    private suspend fun seedAnalysis() {
        val videos = listOf(
            VideoItem(
                index = 1,
                videoId = "video-1",
                title = "Deep Dive into LLMs",
                durationSec = 600,
                availableSubs = emptyList(),
                channelId = "channel-1",
                channelTitle = "Research Channel",
                publishedAt = "2026-09-01T10:00:00Z",
                viewCount = 200L,
                description = "Large language models and research",
                tags = listOf("ai", "llm"),
                topic = listOf("language")
            ),
            VideoItem(
                index = 2,
                videoId = "video-2",
                title = "Stable Diffusion Dreams",
                durationSec = 500,
                availableSubs = emptyList(),
                channelId = "channel-1",
                channelTitle = "Research Channel",
                publishedAt = "2026-09-02T10:00:00Z",
                viewCount = 300L,
                description = "Image generation",
                tags = listOf("ai", "diffusion"),
                topic = listOf("vision")
            ),
            VideoItem(
                index = 3,
                videoId = "video-3",
                title = "AI Research Notes",
                durationSec = 400,
                availableSubs = emptyList(),
                channelId = "channel-1",
                channelTitle = "Research Channel",
                publishedAt = "2026-09-03T10:00:00Z",
                viewCount = 100L,
                description = "Research notes",
                tags = listOf("ai"),
                topic = listOf("language")
            )
        )

        knowledgeRepository.saveAnalysis(
            Source(
                id = "video:seed",
                url = "https://www.youtube.com/watch?v=video-1",
                title = "Seed",
                originalTotalVideos = 3
            ),
            videos
        )

        knowledgeRepository.saveAnalysis(
            Source(
                id = "playlist:seed",
                url = "https://www.youtube.com/playlist?list=playlist-1",
                title = "LLMs Research",
                originalTotalVideos = 2
            ),
            videos.take(2)
        )
    }
}
