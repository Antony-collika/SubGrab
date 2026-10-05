package com.subgrab.app

import com.subgrab.app.domain.ResearchFilters
import com.subgrab.app.domain.ResearchSort
import com.subgrab.app.domain.SearchState
import com.subgrab.app.domain.VideoSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchModelsTest {
    private fun result(id: String) = VideoSearchResult(id, "Title", null, null, "Channel", null, null, 1L,
        60L, null, 10L, 2L, 1L, "", null, "", null, null)

    @Test fun selectedResults_follow_selectedIds() {
        val state = SearchState(results = listOf(result("a"), result("b")), selectedVideos = setOf("b"))
        assertEquals(listOf("b"), state.selectedResults.map { it.videoId })
    }

    @Test fun defaultFilter_isEmpty_andSortIsStable() {
        assertEquals(ResearchFilters(), SearchState().filters)
        assertEquals(ResearchSort.FETCHED_DESC, SearchState().sort)
        assertTrue(SearchState().selectedResults.isEmpty())
    }
}
