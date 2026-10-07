package com.subgrab.app.data
import com.subgrab.app.domain.VideoItem
class ApiDiscoveryClient(private val api:YouTubeDataApiClient):DiscoveryClient{
 // ---- Các hàm tương thích cũ ----
 override suspend fun discoverPlaylist(source:String)=api.listPlaylistItems(source)
 suspend fun discoverPlaylistWithSource(source:String):Pair<com.subgrab.app.domain.Source,List<VideoItem>>{
  val videos=api.listPlaylistItems(source)
  val title=api.getPlaylistTitle(source)
  return com.subgrab.app.domain.Source(source,source,title,videos.size) to videos
 }
 override suspend fun discoverKeyword(query:String)=api.searchKeyword(query)
 /** Lấy comment của video. [maxThreads] = số comment gốc tối đa (0 = tất cả); null = theo Cài đặt. */
 suspend fun fetchComments(videoId:String,maxThreads:Int?=null)=api.fetchCommentThreads(videoId,maxThreads)
 override suspend fun discoverVideo(source:String)=api.getVideoMetadata(listOf(videoId(source)))
 override suspend fun discoverChannel(source:String)=api.listChannelUploads(source)
 suspend fun discoverChannelWithSource(source:String):Pair<com.subgrab.app.domain.Source,List<VideoItem>>{
  val videos=api.listChannelUploads(source)
  val title=api.getChannelTitle(source)
  return com.subgrab.app.domain.Source(source,source,title,videos.size) to videos
 }

 // ---- Lấy theo trang: trang đầu + biết còn nữa hay không + hàm lấy trang kế tiếp ----
 suspend fun discoverPlaylistPaged(source:String):Pair<com.subgrab.app.domain.Source,DiscoveryPage>{
  val page=api.playlistPageFor(source)
  val title=api.getPlaylistTitle(source)
  return com.subgrab.app.domain.Source(source,source,title,page.videos.size) to page
 }
 suspend fun discoverChannelPaged(source:String):Pair<com.subgrab.app.domain.Source,DiscoveryPage>{
  val page=api.channelUploadsPage(source)
  val title=api.getChannelTitle(source)
  return com.subgrab.app.domain.Source(source,source,title,page.videos.size) to page
 }
 /** Từ khóa: chỉ lấy 1 trang đầu (mỗi lần tìm tốn khoảng 100 đơn vị hạn mức). Trang kế tiếp do người dùng chủ động bấm. */
 suspend fun discoverKeywordPaged(query:String):Pair<com.subgrab.app.domain.Source,DiscoveryPage>{
  val clean=query.trim()
  val page=api.searchPage(clean)
  return com.subgrab.app.domain.Source("keyword:"+clean,"https://www.youtube.com/results?search_query="+android.net.Uri.encode(clean),clean,page.videos.size) to page
 }

 suspend fun discoverVideoCollectionWithSource(sources:List<String>):Pair<com.subgrab.app.domain.Source,List<VideoItem>>{
  val ids=sources.mapNotNull{videoId(it)}.distinct()
  require(ids.size==sources.distinct().size) { "Chuỗi nhiều URL chỉ hỗ trợ URL video YouTube hợp lệ" }
  val videos=api.getVideoMetadataInOrder(ids)
  return com.subgrab.app.domain.Source("collection",sources.joinToString("\n"),"Collection",videos.size) to videos
 }
 private fun videoId(source:String):String = android.net.Uri.parse(source).getQueryParameter("v")
  ?: android.net.Uri.parse(source).pathSegments.lastOrNull()?.takeIf{it.isNotBlank()}
  ?: error("Không tìm thấy video id")
}
