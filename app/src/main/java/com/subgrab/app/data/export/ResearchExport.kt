package com.subgrab.app.data.export

import com.subgrab.app.domain.VideoSearchResult
import org.json.JSONArray
import org.json.JSONObject

object ResearchExport {
    fun json(results: List<VideoSearchResult>): String {
        val array = JSONArray()
        results.forEach { v ->
            array.put(JSONObject()
                .put("videoId", v.videoId)
                .put("title", v.title)
                .put("description", v.description)
                .put("channelId", v.channelId)
                .put("channelName", v.channelName)
                .put("thumbnail", v.thumbnail)
                .put("publishedAt", v.publishedAt)
                .put("fetchedAt", v.fetchedAt)
                .put("durationSeconds", v.durationSeconds)
                .put("subscriberCount", v.subscriberCount)
                .put("viewCount", v.viewCount)
                .put("likeCount", v.likeCount)
                .put("commentCount", v.commentCount)
                .put("tags", v.tags)
                .put("category", v.category)
                .put("topic", v.topic)
                .put("playlistTitles", v.playlistTitles)
                .put("searchKeywords", v.searchKeywords))
        }
        return array.toString(2)
    }

    fun csv(results: List<VideoSearchResult>): String {
        val header = listOf("videoId","title","channelName","publishedAt","fetchedAt","durationSeconds","subscriberCount","viewCount","likeCount","commentCount","category","tags","topic","playlistTitles","searchKeywords")
        return buildString {
            appendLine(header.joinToString(",") { csvCell(it) })
            results.forEach { v ->
                val row = listOf(
                    v.videoId, v.title, v.channelName, v.publishedAt, v.fetchedAt,
                    v.durationSeconds, v.subscriberCount, v.viewCount, v.likeCount,
                    v.commentCount, v.category, v.tags, v.topic, v.playlistTitles, v.searchKeywords
                )
                appendLine(row.joinToString(",") { csvCell(it?.toString().orEmpty()) })
            }
        }
    }

    fun markdown(results: List<VideoSearchResult>): String = buildString {
        appendLine("# SubGrab Research Results")
        appendLine()
        appendLine("| Title | Channel | Views | Likes | Published | Video ID |")
        appendLine("|---|---|---:|---:|---|---|")
        results.forEach { v ->
            appendLine("| " + mdCell(v.title) + " | " + mdCell(v.channelName) + " | " +
                (v.viewCount ?: 0) + " | " + (v.likeCount ?: 0) + " | " +
                mdCell(v.publishedAt) + " | `" + mdCell(v.videoId) + "` |")
        }
    }

    private fun csvCell(value: String): String = """ + value.replace(""", """").replace("\n", " ").replace("\r", " ") + """
    private fun mdCell(value: String?): String = value.orEmpty().replace("|", "\\|").replace("\n", " ").replace("\r", " ")
}
