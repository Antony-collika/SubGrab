package com.subgrab.app.data.export

import com.subgrab.app.domain.VideoSearchResult

object ResearchExport {
    enum class ExportFormat { JSON, CSV, MD }
    
    data class ExportOptions(
        val metadata: Boolean = true,
        val transcript: Boolean = false,
        val comments: Boolean = false,
        val history: Boolean = false,
        val format: ExportFormat = ExportFormat.JSON
    )
    
    data class VideoExportData(
        val videoId: String,
        val title: String,
        val metadata: List<com.subgrab.app.data.db.VideoMetadataSnapshotEntity>,
        val transcript: com.subgrab.app.data.db.TranscriptEntity?,
        val comments: List<com.subgrab.app.data.db.CommentEntity>
    )
    
    fun exportVideo(data: VideoExportData, options: ExportOptions): String = when (options.format) {
        ExportFormat.JSON -> buildString {
            appendLine("{")
            appendLine("  \"videoId\": " + jsonString(data.videoId) + ",")
            appendLine("  \"title\": " + jsonString(data.title) + ",")
            appendLine("  \"metadata\": " + if (options.metadata) data.metadata.joinToString(prefix="[", postfix="]") { "{\"fetchedAt\":" + it.fetchedAt + ",\"title\":" + jsonString(it.title) + "}" } else "[]")
            if (options.transcript) appendLine(",  \"transcript\": " + (data.transcript?.let { "{\"language\":" + jsonString(it.language) + ",\"fetchedAt\":" + (it.fetchedAt ?: 0L) + ",\"content\":" + jsonString(it.content) + "}" } ?: "null"))
            if (options.comments) appendLine(",  \"comments\": " + data.comments.joinToString(prefix="[", postfix="]") { "{\"commentId\":" + jsonString(it.commentId) + ",\"fetchedAt\":" + (it.fetchedAt ?: 0L) + ",\"text\":" + jsonString(it.text) + "}" })
            appendLine("}")
        }
        ExportFormat.CSV -> buildString {
            appendLine("videoId,section,fetchedAt,key,value")
            if (options.metadata) data.metadata.forEach { appendLine(listOf(data.videoId,"metadata",it.fetchedAt,"title",it.title).joinToString(",") { v -> csvCell(v.toString()) }) }
            if (options.transcript) data.transcript?.let { appendLine(listOf(data.videoId,"transcript",it.fetchedAt ?: 0L,"content",it.content.orEmpty()).joinToString(",") { v -> csvCell(v.toString()) }) }
            if (options.comments) data.comments.forEach { appendLine(listOf(data.videoId,"comment",it.fetchedAt ?: 0L,it.commentId,it.text).joinToString(",") { v -> csvCell(v.toString()) }) }
        }
        ExportFormat.MD -> buildString {
            appendLine("# SubGrab Export")
            appendLine("\n- Video ID: " + data.videoId + "\n- Title: " + data.title)
            if (options.metadata) { appendLine("\n## Metadata"); data.metadata.forEach { appendLine("- ${it.fetchedAt}: ${it.title}") } }
            if (options.transcript) { appendLine("\n## Transcript"); appendLine(data.transcript?.content.orEmpty()) }
            if (options.comments) { appendLine("\n## Comments"); data.comments.forEach { appendLine("- " + it.author.orEmpty() + ": " + it.text) } }
        }
    }
    
    private fun jsonString(value: String?): String {
        if (value == null) return "null"
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
    }
    private fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ") + "\""
    
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


    private fun mdCell(value: String?): String = value.orEmpty().replace("|", "\\|").replace("\n", " ").replace("\r", " ")
}
