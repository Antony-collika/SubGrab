# SubGrab

Ứng dụng Android Kotlin + Jetpack Compose để phân tích link YouTube và tải phụ đề tiếng Việt/English.

## Bản nháp first-Draft

Bản hiện tại cung cấp package `com.subgrab.app`, màn hình Home tiếng Việt, clipboard paste, validate link YouTube, parser/runner yt-dlp với timeout, giới hạn 50 video, màn hình chọn video, cấu hình folder, FileStorage Downloads, chuyển SRT sang TXT, download orchestrator tuần tự có pause/resume/cancel và foreground service notification. yt-dlp executable vẫn cần được đóng gói vào `app/src/main/assets` theo ABI trước khi chạy tải thật trên thiết bị.

## Chạy local

Mở bằng Android Studio (Koala hoặc mới hơn), sync Gradle và chạy `app`. Có thể chạy kiểm thử bằng `./gradlew test` và build APK bằng `./gradlew assembleDebug`.

## Core implementation status

- `YtDlpOutputParser` parses flat-playlist JSON lines and subtitle availability.
- `YtDlpRunner` supports metadata fetch, per-video subtitle listing and subtitle download with timeouts.
- `DownloadOrchestrator` processes selected videos sequentially and exposes pause/resume/cancel state.
- `FileStorage` writes to `Download/Subtitles/{folder}` and converts generated SRT files to TXT.
- `DownloadService` registers a foreground service with notification pause/cancel actions.
- Unit tests cover URL validation, filename sanitization, SRT conversion and yt-dlp parser behavior.

The bundled release is yt-dlp `2026.08.19`: `yt-dlp-arm64-v8a` and `yt-dlp-armeabi-v7a`. Because both native assets are bundled in one universal APK, the debug APK is approximately 97 MB, above the SRS target of 40 MB. A production build should use ABI splits or Android App Bundles to avoid shipping both binaries to every device.

## CI

Workflow `.github/workflows/android.yml` tự cài JDK, Android SDK và Gradle, sau đó chạy unit tests và build debug APK cho mọi branch/pull request.
