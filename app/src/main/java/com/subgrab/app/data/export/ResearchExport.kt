package com.subgrab.app.data.export

import com.subgrab.app.domain.VideoSearchResult

object ResearchExport {
    fun json(results: List<VideoSearchResult>): String = buildString {
        appendLine("[")
        results.forEachIndexed { index, v ->
            appendLine("  {")
            appendLine("    \"videoId\": " + jsonString(v.videoId) + ",")
            appendLine("    \"title\": " + jsonString(v.title) + ",")
            appendLine("    \"description\": " + jsonString(v.description) + ",")
            appendLine("    \"channelId\": " + jsonString(v.channelId) + ",")
            appendLine("    \"channelName\": " + jsonString(v.channelName) + ",")
            appendLine("    \"thumbnail\": " + jsonString(v.thumbnail) + ",")
            appendLine("    \"publishedAt\": " + jsonString(v.publishedAt) + ",")
            appendLine("    \"fetchedAt\": " + v.fetchedAt + ",")
            appendLine("    \"durationSeconds\": " + jsonNumber(v.durationSeconds) + ",")
            appendLine("    \"subscriberCount\": " + jsonNumber(v.subscriberCount) + ",")
            appendLine("    \"viewCount\": " + jsonNumber(v.viewCount) + ",")
            appendLine("    \"likeCount\": " + jsonNumber(v.likeCount) + ",")
            appendLine("    \"commentCount\": " + jsonNumber(v.commentCount) + ",")
            appendLine("    \"tags\": " + jsonString(v.tags) + ",")
            appendLine("    \"category\": " + jsonString(v.category) + ",")
            appendLine("    \"topic\": " + jsonString(v.topic) + ",")
            appendLine("    \"playlistTitles\": " + jsonString(v.playlistTitles) + ",")
            appendLine("    \"searchKeywords\": " + jsonString(v.searchKeywords))
            append("  }")
            if (index < results.lastIndex) append(",")
            appendLine()
        }
        append("]")
    }

    private fun jsonNumber(value: Long?): String = value?.toString() ?: "null"

    private fun jsonString(value: String?): String =
        value?.let {
            "\"" + it
                .replace("\\\\", "\\\\\\\\")
                .replace("\"", "\\\\"")
                .replace("\\b", "\\\\b")
                .replace("\\u000C", "\\\\f")
                .replace("\\n", "\\\\n")
                .replace("\\r", "\\\\r")
                .replace("\\t", "\\\\t")
                .replace(Regex("[\\u0000-\\u001F]")) { match ->
                    "\\\\u%04x".format(match.value[0].code)
                } + "\""
        } ?: "null"

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

    private fun csvCell(value: String): String = 34.toChar() + value.replace(34.toChar().toString(), 34.toChar().toString() + 34.toChar()).replace("\n", " ").replace("\r", " ") + 34.toChar()
    private fun mdCell(value: String?): String = value.orEmpty().replace("|", "\\|").replace("\n", " ").replace("\r", " ")
}
