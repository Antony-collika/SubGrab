# SubGrab

Ứng dụng Android Kotlin + Jetpack Compose để phân tích link YouTube và tải phụ đề tiếng Việt/English.

## Kiến trúc hiện tại

Luồng chính sử dụng NewPipeExtractor làm extraction engine duy nhất:

URL / tìm kiếm / playlist
→ NewPipeExtractorClient
→ SubtitleDownloader
→ SubtitleParser
→ SubtitleNormalizer
→ SubtitleFormatter
→ FileStorage

Không còn runtime hoặc fallback yt-dlp.

SubtitleParser đọc WebVTT thành các cue có start/end/text. SubtitleNormalizer xử lý inline timestamp như Good<00:00:11.599><c> day.</c> và loại bỏ WebVTT markup trước khi format.

SRT hỗ trợ hai chế độ timestamp: có timestamp và không timestamp. TXT luôn là văn bản thuần, không có timestamp.

## Chạy local

Mở bằng Android Studio, sync Gradle và chạy app. Có thể chạy kiểm thử bằng ./gradlew test và build APK bằng ./gradlew assembleDebug.

## CI

Workflow .github/workflows/android.yml tự cài JDK, Android SDK và Gradle, sau đó chạy unit tests và build debug APK cho mọi branch/pull request.

NewPipeExtractor không phải YouTube API chính thức; extraction phụ thuộc vào cách YouTube cung cấp dữ liệu và có thể cần cập nhật khi YouTube thay đổi.