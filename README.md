# SubGrab

Ứng dụng Android Kotlin + Jetpack Compose để phân tích link YouTube và chuẩn bị tải phụ đề tiếng Việt/English.

## Bản nháp first-Draft

Bản đầu tiên cung cấp project Android chạy được, package `com.subgrab.app`, màn hình Home tiếng Việt, xác thực link YouTube, domain models và unit tests cho URL/sanitize/SRT. Kiến trúc được tổ chức để mở rộng yt-dlp wrapper, foreground service, DataStore và các màn hình chọn video/cài đặt theo SRS.

## Chạy local

Mở bằng Android Studio (Koala hoặc mới hơn), sync Gradle và chạy `app`. Có thể chạy kiểm thử bằng `./gradlew test` và build APK bằng `./gradlew assembleDebug`.

## CI

Workflow `.github/workflows/android.yml` tự cài JDK, Android SDK và Gradle, sau đó chạy unit tests và build debug APK cho mọi branch/pull request.
