package com.subgrab.app.data

import android.net.Uri
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration

class YouTubeSearchClient {
    suspend fun search(query: String, apiKey: String, maxResults: Int = 20): Result<Pair<Source, List<VideoItem>>> = withContext(Dispatchers.IO) {
        runCatching {
            require(query.isNotBlank()) { "Vui lòng nhập từ khóa" }
            require(apiKey.isNotBlank()) { "Chưa có YouTube Data API key. Vào Cài đặt để thêm API key." }
            val searchUrl = Uri.parse("https://www.googleapis.com/youtube/v3/search").buildUpon()
                .appendQueryParameter("part", "snippet")
                .appendQueryParameter("q", query.trim())
                .appendQueryParameter("type", "video")
                .appendQueryParameter("order", "relevance")
                .appendQueryParameter("maxResults", maxResults.coerceIn(1, 50).toString())
                .appendQueryParameter("regionCode", "VN")
                .appendQueryParameter("key", apiKey.trim())
                .build().toString()
            val searchJson = getJson(searchUrl)
            val searchItems = searchJson.getJSONArray("items")
            val ids = buildList {
                for (i in 0 until searchItems.length()) {
                    val id = searchItems.getJSONObject(i).getJSONObject("id").optString("videoId")
                    if (id.isNotBlank()) add(id)
                }
            }
            if (ids.isEmpty()) return@runCatching Source(query, "https://www.youtube.com/results?search_query=${Uri.encode(query)}", query, 0) to emptyList()

            val detailsUrl = Uri.parse("https://www.googleapis.com/youtube/v3/videos").buildUpon()
                .appendQueryParameter("part", "snippet,contentDetails,statistics")
                .appendQueryParameter("id", ids.joinToString(","))
                .appendQueryParameter("key", apiKey.trim())
                .build().toString()
            val detailsJson = getJson(detailsUrl)
            val byId = mutableMapOf<String, JSONObject>()
            val detailItems = detailsJson.getJSONArray("items")
            for (i in 0 until detailItems.length()) {
                val item = detailItems.getJSONObject(i)
                byId[item.optString("id")] = item
            }
            val videos = ids.mapIndexed { index, id ->
                val searchItem = searchItems.getJSONObject((0 until searchItems.length()).first { searchItems.getJSONObject(it).getJSONObject("id").optString("videoId") == id })
                val snippet = byId[id]?.optJSONObject("snippet") ?: searchItem.optJSONObject("snippet") ?: JSONObject()
                val content = byId[id]?.optJSONObject("contentDetails")
                val stats = byId[id]?.optJSONObject("statistics")
                VideoItem(
                    index = index + 1,
                    videoId = id,
                    title = snippet.optString("title", "Video $id"),
                    durationSec = parseDuration(content?.optString("duration")),
                    availableSubs = emptyList(),
                    isSelected = false,
                    subtitleChecked = false,
                    channelTitle = snippet.optString("channelTitle"),
                    publishedAt = snippet.optString("publishedAt"),
                    viewCount = stats?.optString("viewCount")?.toLongOrNull(),
                    thumbnailUrl = snippet.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url").orEmpty()
                )
            }
            val source = Source("keyword:${query.trim()}", "https://www.youtube.com/results?search_query=${Uri.encode(query.trim())}", query.trim(), videos.size)
            source to videos
        }
    }

    private fun getJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        return connection.use { c ->
            val body = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (c.responseCode !in 200..299) {
                val reason = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                error(reason?.takeIf { it.isNotBlank() } ?: "YouTube API lỗi HTTP ${c.responseCode}")
            }
            JSONObject(body)
        }
    }

    private fun parseDuration(value: String?): Int = runCatching { Duration.parse(value ?: "PT0S").seconds.toInt() }.getOrDefault(0)
}
