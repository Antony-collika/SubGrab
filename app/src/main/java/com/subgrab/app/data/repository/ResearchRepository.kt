package com.subgrab.app.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.subgrab.app.data.SubGrabDatabase
import com.subgrab.app.data.db.UserActivityEntity
import java.util.UUID
import com.subgrab.app.domain.ResearchActivityItem
import com.subgrab.app.domain.ResearchFilters
import com.subgrab.app.domain.ResearchSort
import com.subgrab.app.domain.VideoSearchResult

class ResearchRepository(private val database: SubGrabDatabase) {
    suspend fun getActivityPage(limit: Int = 50, offset: Int = 0): List<ResearchActivityItem> {
        val sql = """
            SELECT a.id AS id, a.type AS type, a.timestamp AS timestamp,
                a.videoId AS videoId, m.title AS title, m.channelName AS channelName,
                a.searchSessionId AS searchSessionId, a.query AS query, a.context AS context
            FROM user_activities a
            LEFT JOIN video_metadata_snapshots m ON m.id = (
                SELECT ms.id FROM video_metadata_snapshots ms
                WHERE ms.videoId = a.videoId ORDER BY ms.fetchedAt DESC LIMIT 1
            )
            ORDER BY a.timestamp DESC LIMIT ? OFFSET ?
        """.trimIndent()
        return database.researchQueryDao().activities(
            SimpleSQLiteQuery(sql, arrayOf(limit.coerceAtLeast(1), offset.coerceAtLeast(0)))
        )
    }

    suspend fun search(query: String, filters: ResearchFilters = ResearchFilters(),
                        sort: ResearchSort = ResearchSort.FETCHED_DESC,
                        page: Int = 0, pageSize: Int = 50): Pair<List<VideoSearchResult>, Int> {
        val args = mutableListOf<Any>()
        val conditions = mutableListOf<String>()
        if (query.trim().isNotBlank()) {
            conditions += "(m.videoId IN (SELECT videoId FROM video_search(?)) OR EXISTS (SELECT 1 FROM playlist_video pvq JOIN playlists pq ON pq.playlistId = pvq.playlistId WHERE pvq.videoId = m.videoId AND LOWER(pq.title) LIKE LOWER(?)))"
            args += ftsQuery(query)
            args += "%" + query.trim() + "%"
        }
        addLike(conditions, args, "m.title", filters.titleContains)
        addLike(conditions, args, "m.description", filters.descriptionContains)
        addLike(conditions, args, "m.tags", filters.tagsContains)
        addLike(conditions, args, "m.category", filters.categoryContains)
        addLike(conditions, args, "m.topic", filters.topicContains)
        addLike(conditions, args, "m.channelName", filters.channelContains)
        addLike(conditions, args, "playlistTitles", filters.playlistContains)
        filters.minSubscribers?.let { conditions += "COALESCE(m.subscriberCount, 0) >= ?"; args += it }
        filters.maxSubscribers?.let { conditions += "COALESCE(m.subscriberCount, 0) <= ?"; args += it }
        filters.minViews?.let { conditions += "COALESCE(m.viewCount, 0) >= ?"; args += it }
        filters.maxViews?.let { conditions += "COALESCE(m.viewCount, 0) <= ?"; args += it }
        filters.minLikes?.let { conditions += "COALESCE(m.likeCount, 0) >= ?"; args += it }
        filters.maxLikes?.let { conditions += "COALESCE(m.likeCount, 0) <= ?"; args += it }
        filters.minComments?.let { conditions += "COALESCE(m.commentCount, 0) >= ?"; args += it }
        filters.maxComments?.let { conditions += "COALESCE(m.commentCount, 0) <= ?"; args += it }
        filters.publishedFrom?.let { conditions += publishedEpochSql() + " >= ?"; args += it }
        filters.publishedTo?.let { conditions += publishedEpochSql() + " <= ?"; args += it }
        filters.fetchedFrom?.let { conditions += "m.fetchedAt >= ?"; args += it }
        filters.fetchedTo?.let { conditions += "m.fetchedAt <= ?"; args += it }
        if (filters.searchKeywordContext.isNotBlank()) {
            conditions += "EXISTS (SELECT 1 FROM search_session_video ssv JOIN search_sessions ss ON ss.id = ssv.searchSessionId WHERE ssv.videoId = m.videoId AND LOWER(ss.query) LIKE LOWER(?))"
            args += "%" + filters.searchKeywordContext.trim() + "%"
        }
        if (filters.activityTypes.isNotEmpty() || filters.activityFrom != null || filters.activityTo != null) {
            conditions += buildString {
                append("EXISTS (SELECT 1 FROM user_activities ua WHERE ua.videoId = m.videoId")
                if (filters.activityTypes.isNotEmpty()) {
                    append(" AND ua.type IN (")
                    append(filters.activityTypes.joinToString(",") { "?" })
                    append(")")
                    filters.activityTypes.forEach { args += it }
                }
                filters.activityFrom?.let { append(" AND ua.timestamp >= ?"); args += it }
                filters.activityTo?.let { append(" AND ua.timestamp <= ?"); args += it }
                append(")")
            }
        }

        val where = if (conditions.isEmpty()) "" else " AND " + conditions.joinToString(" AND ")
        val base = baseSql() + where + " GROUP BY m.videoId"
        val count = database.researchQueryDao().countResults(
            SimpleSQLiteQuery("SELECT COUNT(*) FROM (" + base + ")", args.toTypedArray())
        )
        val pageSizeSafe = pageSize.coerceIn(1, 100)
        val dataArgs = args.toMutableList()
        dataArgs += pageSizeSafe
        dataArgs += page.coerceAtLeast(0) * pageSizeSafe
        val rows = database.researchQueryDao().searchResults(
            SimpleSQLiteQuery(base + " ORDER BY " + sort.sql + " LIMIT ? OFFSET ?", dataArgs.toTypedArray())
        )
        return rows to count
    }

    suspend fun getSearchSession(sessionId: String) = database.searchDao().getSession(sessionId)

    suspend fun getSessionResults(sessionId: String, page: Int = 0, pageSize: Int = 50): Pair<List<VideoSearchResult>, Int> {
        val base = """
            SELECT m.videoId AS videoId, m.title AS title, m.description AS description,
                m.channelId AS channelId, m.channelName AS channelName, m.thumbnail AS thumbnail,
                m.publishedAt AS publishedAt, m.fetchedAt AS fetchedAt, m.durationSeconds AS durationSeconds,
                m.subscriberCount AS subscriberCount, m.viewCount AS viewCount, m.likeCount AS likeCount,
                m.commentCount AS commentCount, s.tags AS tags, s.category AS category, s.topic AS topic,
                (SELECT group_concat(DISTINCT p.title) FROM playlist_video pv JOIN playlists p ON p.playlistId = pv.playlistId WHERE pv.videoId = m.videoId) AS playlistTitles,
                (SELECT group_concat(DISTINCT ss.query) FROM search_session_video ssv JOIN search_sessions ss ON ss.id = ssv.searchSessionId WHERE ssv.videoId = m.videoId) AS searchKeywords
            FROM search_session_video member
            JOIN video_metadata_snapshots m ON m.id = (
                SELECT ms.id FROM video_metadata_snapshots ms
                WHERE ms.videoId = member.videoId ORDER BY ms.fetchedAt DESC LIMIT 1
            )
            JOIN video_search s ON s.videoId = member.videoId
            WHERE member.searchSessionId = ?
            GROUP BY m.videoId
        """.trimIndent()
        val pageSizeSafe = pageSize.coerceIn(1, 100)
        val count = database.researchQueryDao().countResults(
            SimpleSQLiteQuery("SELECT COUNT(*) FROM (" + base + ")", arrayOf(sessionId))
        )
        val rows = database.researchQueryDao().searchResults(
            SimpleSQLiteQuery(base + " ORDER BY member.position ASC LIMIT ? OFFSET ?",
                arrayOf(sessionId, pageSizeSafe, page.coerceAtLeast(0) * pageSizeSafe))
        )
        return rows to count
    }

    suspend fun recordView(videoId: String) {
        database.userActivityDao().insert(
            UserActivityEntity(UUID.randomUUID().toString(), "VIEW_VIDEO", System.currentTimeMillis(), videoId, null, null, null, null, "RESEARCH")
        )
    }

    suspend fun getVideo(videoId: String) = database.videoDao().get(videoId)
    suspend fun getLatestSnapshot(videoId: String) = database.metadataSnapshotDao().latest(videoId)
    suspend fun getSnapshotHistory(videoId: String) = database.metadataSnapshotDao().history(videoId)
    suspend fun getTranscript(videoId: String) = database.transcriptDao().get(videoId)
    suspend fun getCommentThreads(videoId: String) = database.commentDao().getThreads(videoId)
    suspend fun getComments(threadId: String) = database.commentDao().getComments(threadId)

    private fun baseSql(): String = """
        SELECT m.videoId AS videoId, m.title AS title, m.description AS description,
            m.channelId AS channelId, m.channelName AS channelName, m.thumbnail AS thumbnail,
            m.publishedAt AS publishedAt, m.fetchedAt AS fetchedAt, m.durationSeconds AS durationSeconds,
            m.subscriberCount AS subscriberCount, m.viewCount AS viewCount, m.likeCount AS likeCount,
            m.commentCount AS commentCount, s.tags AS tags, s.category AS category, s.topic AS topic,
            (SELECT group_concat(DISTINCT p.title) FROM playlist_video pv JOIN playlists p ON p.playlistId = pv.playlistId WHERE pv.videoId = m.videoId) AS playlistTitles,
            (SELECT group_concat(DISTINCT ss.query) FROM search_session_video ssv JOIN search_sessions ss ON ss.id = ssv.searchSessionId WHERE ssv.videoId = m.videoId) AS searchKeywords
        FROM video_metadata_snapshots m
        JOIN video_search s ON s.videoId = m.videoId
        WHERE m.id = (SELECT ms.id FROM video_metadata_snapshots ms WHERE ms.videoId = m.videoId ORDER BY ms.fetchedAt DESC LIMIT 1)
    """.trimIndent()

    private fun addLike(conditions: MutableList<String>, args: MutableList<Any>, column: String, value: String) {
        if (value.isNotBlank()) {
            conditions += "LOWER(COALESCE(" + column + ", '')) LIKE LOWER(?)"
            args += "%" + value.trim() + "%"
        }
    }

    private fun publishedEpochSql(): String =
        "CAST(strftime('%s', replace(substr(m.publishedAt, 1, 19), 'T', ' ')) AS INTEGER) * 1000"

    private fun ftsQuery(input: String): String =
        input.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .joinToString(" AND ") { "\"" + it.replace("\"", "") + "\"" }
}
