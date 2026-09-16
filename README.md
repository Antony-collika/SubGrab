# SubGrab

Ứng dụng Android Kotlin + Jetpack Compose để phân tích link YouTube và tải phụ đề tiếng Việt/English.

## Bản nháp first-Draft

Bản hiện tại cung cấp package `com.subgrab.app`, màn hình Home tiếng Việt, clipboard paste, validate link YouTube, parser/runner yt-dlp với timeout, giới hạn 50 video, màn hình chọn video, cấu hình folder, FileStorage Downloads, chuyển SRT sang TXT, download orchestrator tuần tự có pause/resume/cancel và foreground service notification. yt-dlp chạy in-process qua thư viện `yt-dlp-android`/Chaquopy, không dùng `ProcessBuilder` để execute ELF Linux từ `filesDir`.

## Chạy local

Mở bằng Android Studio (Koala hoặc mới hơn), sync Gradle và chạy `app`. Có thể chạy kiểm thử bằng `./gradlew test` và build APK bằng `./gradlew assembleDebug`.

## Core implementation status

- `YtDlpOutputParser` parses flat-playlist JSON lines and subtitle availability.
- `YtDlpRunner` supports metadata fetch, per-video subtitle listing and subtitle download with timeouts.
- `DownloadOrchestrator` processes selected videos sequentially and exposes pause/resume/cancel state.
- `FileStorage` writes to `Download/Subtitles/{folder}` and converts generated SRT files to TXT.
- `DownloadService` registers a foreground service with notification pause/cancel actions.
- Unit tests cover URL validation, filename sanitization, SRT conversion and yt-dlp parser behavior.

The app uses `dev.ffmpegkit-maintained:yt-dlp-android:2.0.2`, which embeds Python 3.13 and yt-dlp in-process through Chaquopy. This fixes Android `error=13 Permission denied`: Android 10+ may mount app data with `noexec`, and the previous yt-dlp assets were Linux glibc executables rather than Android/Bionic binaries. The library supports `arm64-v8a` and `x86_64`; this project intentionally builds the supported `arm64` flavor only. The app requests notification permission on Android 13+ and legacy storage permission on Android 9 and below.

For YouTube subtitle extraction, the runner is subtitle-only (`--skip-download`) and retries a 403 at most across the supported yt-dlp clients `web_embedded`, `android_vr`, and `tv`. This is a bounded fallback, not a guarantee against YouTube changes, PO-token enforcement, consent, age, or geo restrictions. NewPipeExtractor is also included as the primary per-video subtitle provider; its GPL-3.0-or-later obligations are documented in `THIRD_PARTY_NOTICES.md`.

The current implementation now tries NewPipeExtractor `v0.26.5` first for per-video subtitle discovery and direct caption-track download, then falls back to the embedded yt-dlp runtime if extraction or caption download fails. NewPipeExtractor uses YouTube InnerTube and caption tracks rather than the media-download path which commonly returns HTTP 403. It is GPL-3.0-or-later; this project must retain the dependency's license and source-notice obligations when distributed.

## CI

Workflow `.github/workflows/android.yml` tự cài JDK, Android SDK và Gradle, sau đó chạy unit tests và build debug APK cho mọi branch/pull request.
