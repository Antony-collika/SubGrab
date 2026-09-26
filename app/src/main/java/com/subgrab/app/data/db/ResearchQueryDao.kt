package com.subgrab.app.data.db

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.subgrab.app.domain.ResearchActivityItem
import com.subgrab.app.domain.VideoSearchResult

@Dao
interface ResearchQueryDao {
    @RawQuery
    suspend fun searchResults(query: SupportSQLiteQuery): List<VideoSearchResult>

    @RawQuery
    suspend fun countResults(query: SupportSQLiteQuery): Int

    @RawQuery
    suspend fun activities(query: SupportSQLiteQuery): List<ResearchActivityItem>
}
