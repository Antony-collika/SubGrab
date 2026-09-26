package com.subgrab.app.domain

data class VideoSearchResult(
    val videoId: String,
    val title: String,
    val description: String?,
    val channelId: String?,
    val channelName: String?,
    val thumbnail: String?,
    val publishedAt: String?,
    val fetchedAt: Long,
    val durationSeconds: Long?,
    val subscriberCount: Long?,
    val viewCount: Long?,
    val likeCount: Long?,
    val commentCount: Long?,
    val tags: String,
    val category: String?,
    val topic: String,
    val playlistTitles: String?,
    val searchKeywords: String?
)

data class ResearchActivityItem(
    val id: String,
    val type: String,
    val timestamp: Long,
    val videoId: String?,
    val title: String?,
    val channelName: String?,
    val searchSessionId: String?,
    val query: String?,
    val context: String?
)

enum class ResearchSort(val sql: String, val label: String) {
    PUBLISHED_DESC("m.publishedAt DESC", "Mới xuất bản"),
    FETCHED_DESC("m.fetchedAt DESC", "Mới cập nhật"),
    VIEWS_DESC("COALESCE(m.viewCount, 0) DESC", "Lượt xem"),
    LIKES_DESC("COALESCE(m.likeCount, 0) DESC", "Lượt thích"),
    COMMENTS_DESC("COALESCE(m.commentCount, 0) DESC", "Bình luận"),
    DURATION_DESC("COALESCE(m.durationSeconds, 0) DESC", "Thời lượng"),
    TITLE_ASC("LOWER(m.title) ASC", "Tiêu đề"),
    CHANNEL_ASC("LOWER(COALESCE(m.channelName, '')) ASC", "Kênh")
}

data class ResearchFilters(
    val titleContains: String = "",
    val descriptionContains: String = "",
    val tagsContains: String = "",
    val categoryContains: String = "",
    val topicContains: String = "",
    val channelContains: String = "",
    val minSubscribers: Long? = null,
    val maxSubscribers: Long? = null,
    val minViews: Long? = null,
    val maxViews: Long? = null,
    val minLikes: Long? = null,
    val maxLikes: Long? = null,
    val minComments: Long? = null,
    val maxComments: Long? = null,
    val publishedFrom: Long? = null,
    val publishedTo: Long? = null,
    val fetchedFrom: Long? = null,
    val fetchedTo: Long? = null,
    val playlistContains: String = "",
    val searchKeywordContext: String = "",
    val activityTypes: Set<String> = emptySet(),
    val activityFrom: Long? = null,
    val activityTo: Long? = null
)

data class SearchState(
    val query: String = "",
    val filters: ResearchFilters = ResearchFilters(),
    val sort: ResearchSort = ResearchSort.FETCHED_DESC,
    val selectedVideos: Set<String> = emptySet(),
    val results: List<VideoSearchResult> = emptyList(),
    val resultCount: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val page: Int = 0
) {
    val selectedResults: List<VideoSearchResult>
        get() = results.filter { it.videoId in selectedVideos }
}
