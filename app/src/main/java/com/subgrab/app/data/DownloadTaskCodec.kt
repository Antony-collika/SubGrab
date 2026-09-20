package com.subgrab.app.data

import android.util.Base64
import com.subgrab.app.domain.DownloadConfig
import com.subgrab.app.domain.OutputFormat
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.SubtitleLanguage
import com.subgrab.app.domain.SubtitleTimestampMode
import com.subgrab.app.domain.VideoItem
import org.json.JSONArray
import org.json.JSONObject

object DownloadTaskCodec {
    fun encode(source: Source, videos: List<VideoItem>, folder: String, config: DownloadConfig, taskIndex:Int=1, totalTasks:Int=1): String {
        val root = JSONObject()
        root.put("source", JSONObject().apply {
            put("id", source.id)
            put("url", source.url)
            put("title", source.title)
            put("total", source.originalTotalVideos)
        })
        root.put("folder", folder)
        root.put("taskIndex", taskIndex)
        root.put("totalTasks", totalTasks)
        root.put("config", JSONObject().apply {
            val languages = JSONArray()
            config.languages.forEach(languages::put)
            val formats = JSONArray()
            config.formats.forEach { formats.put(it.name) }
            put("languages", languages)
            put("formats", formats)
            put("preferManual", config.preferManual)
            put("skipNoSub", config.skipNoSub)
            put("outputDir", config.outputDir)
            put("timestampMode", config.timestampMode.name)
        })
        val videosJson = JSONArray()
        videos.filter { it.isSelected }.take(50).forEach { video ->
            val subs = JSONArray()
            video.availableSubs.forEach { sub ->
                subs.put(JSONObject().apply {
                    put("code", sub.code)
                    put("auto", sub.isAuto)
                    put("name", sub.name)
                })
            }
            videosJson.put(JSONObject().apply {
                put("index", video.index)
                put("videoId", video.videoId)
                put("title", video.title)
                put("durationSec", video.durationSec)
                put("selected", video.isSelected)
                put("checked", video.subtitleChecked)
                put("channelTitle", video.channelTitle)
                put("publishedAt", video.publishedAt)
                video.viewCount?.let { put("viewCount", it) }
                put("thumbnailUrl", video.thumbnailUrl)
                put("subs", subs)
            })
        }
        root.put("videos", videosJson)
        return Base64.encodeToString(root.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun decode(encoded: String): Task {
        val root = JSONObject(String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8))
        val sourceJson = root.getJSONObject("source")
        val source = Source(sourceJson.getString("id"), sourceJson.getString("url"), sourceJson.getString("title"), sourceJson.getInt("total"))
        val configJson = root.getJSONObject("config")
        val languagesJson = configJson.getJSONArray("languages")
        val languages = List(languagesJson.length()) { languagesJson.getString(it) }
        val formatsJson = configJson.getJSONArray("formats")
        val formats = List(formatsJson.length()) { OutputFormat.valueOf(formatsJson.getString(it)) }.toSet()
        val config = DownloadConfig(
            languages = languages,
            formats = formats,
            preferManual = configJson.getBoolean("preferManual"),
            skipNoSub = configJson.getBoolean("skipNoSub"),
            outputDir = configJson.getString("outputDir"),
            timestampMode = configJson.optString("timestampMode")
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { SubtitleTimestampMode.valueOf(it) }.getOrNull() }
                ?: SubtitleTimestampMode.WITH_TIMESTAMP
        )
        val videosJson = root.getJSONArray("videos")
        val videos = List(videosJson.length()) { i ->
            val json = videosJson.getJSONObject(i)
            val subsJson = json.getJSONArray("subs")
            val subs = List(subsJson.length()) { j ->
                val sub = subsJson.getJSONObject(j)
                SubtitleLanguage(sub.getString("code"), sub.getBoolean("auto"), sub.getString("name"))
            }
            VideoItem(
                index = json.getInt("index"),
                videoId = json.getString("videoId"),
                title = json.getString("title"),
                durationSec = json.getInt("durationSec"),
                availableSubs = subs,
                isSelected = json.optBoolean("selected", true),
                subtitleChecked = json.optBoolean("checked", false),
                channelTitle = json.optString("channelTitle"),
                publishedAt = json.optString("publishedAt"),
                viewCount = if (json.has("viewCount")) json.getLong("viewCount") else null,
                thumbnailUrl = json.optString("thumbnailUrl")
            )
        }
        return Task(source, videos, root.getString("folder"), config, root.optInt("taskIndex",1), root.optInt("totalTasks",1))
    }

    data class Task(val source: Source, val videos: List<VideoItem>, val folder: String, val config: DownloadConfig, val taskIndex:Int = 1, val totalTasks:Int = 1)
}
