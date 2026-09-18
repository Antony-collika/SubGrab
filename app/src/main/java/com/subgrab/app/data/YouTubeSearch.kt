package com.subgrab.app.data

import android.net.Uri
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class YouTubeSearchClient {
    suspend fun search(query: String, maxResults: Int = 20): Result<Pair<Source, List<VideoItem>>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanQuery = query.trim()
            require(cleanQuery.isNotBlank()) { "Vui lòng nhập từ khóa" }

            // Do not call SearchQueryHandlerFactory.fromQuery() here.
            // The pinned NewPipeExtractor calls URLEncoder.encode(String, Charset),
            // which is unavailable on Android API < 33.
            val encodedQuery = Uri.encode(cleanQuery)
            val searchUrl = "https://www.youtube.com/results?search_query=" + encodedQuery
            val searchHandler = SearchQueryHandler(
                searchUrl,
                searchUrl,
                cleanQuery,
                emptyList(),
                ""
            )

            val service = ServiceList.YouTube
            val extractor = service.getSearchExtractor(searchHandler)
            extractor.fetchPage()

            val items = extractor.getInitialPage().items
                .filterIsInstance<StreamInfoItem>()
                .take(maxResults.coerceIn(1, 50))

            val videos = items.mapIndexed { index, item ->
                VideoItem(
                    index = index + 1,
                    videoId = youtubeVideoId(item.getUrl()),
                    title = item.getName(),
                    durationSec = item.getDuration().coerceAtLeast(0L)
                        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    availableSubs = emptyList(),
                    isSelected = false,
                    subtitleChecked = false,
                    channelTitle = item.getUploaderName().orEmpty(),
                    publishedAt = item.getTextualUploadDate().orEmpty(),
                    viewCount = item.getViewCount().takeIf { it >= 0 },
                    thumbnailUrl = item.getThumbnails().firstOrNull()?.getUrl().orEmpty()
                )
            }

            val source = Source(
                id = "keyword:" + cleanQuery,
                url = searchUrl,
                title = cleanQuery,
                originalTotalVideos = videos.size
            )
            source to videos
        }
    }

    private fun youtubeVideoId(url: String): String {
        val uri = Uri.parse(url)
        return uri.getQueryParameter("v")
            ?: uri.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() }
            ?: url
    }
}
