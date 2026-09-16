# Báo cáo tiến độ SubGrab

**Ngày báo cáo:** 15/09/2026  
**Nhánh đánh giá:** `first-Draft`  
**Commit đánh giá:** `57458af` (`ci: remove duplicate Android SDK install step`)  
**Tài liệu đối chiếu:** SRS SubGrab phiên bản 1.0 được cung cấp trong tệp yêu cầu.

## 1. Kết luận điều hành

Mã nguồn hiện tại là một **bản scaffold Android có thể build được**, chưa phải bản triển khai đầy đủ theo SRS. Ứng dụng đã có project Kotlin/Jetpack Compose, package đúng, màn hình Home cơ bản bằng tiếng Việt, kiểm tra link YouTube, một số domain models và unit tests cho các tiện ích lõi. Tuy nhiên, luồng nghiệp vụ chính vẫn chưa hoạt động: ứng dụng chưa gọi yt-dlp, chưa fetch metadata, chưa hiển thị danh sách video, chưa tải phụ đề, chưa có foreground service, chưa có DataStore settings, chưa có navigation và chưa có pause/resume/cancel.

Build đã được xác nhận thành công trên GitHub Actions ở run [34996815432](https://github.com/Antony-collika/SubGrab/actions/runs/34996815432), gồm cả unit tests và `assembleDebug`. Kết quả build xanh chỉ xác nhận project biên dịch và test hiện có đạt; nó **không chứng minh toàn bộ yêu cầu chức năng trong SRS đã hoàn thành**.

Theo đánh giá thực tế, tiến độ hiện ở mức **khoảng 15–20% phạm vi v1.0**, nếu đo theo chức năng bắt buộc có thể sử dụng được. Con số này là ước lượng quản trị, không phải tỷ lệ nghiệm thu chính thức, vì SRS chưa quy định trọng số cho từng yêu cầu.

## 2. Bằng chứng từ repository

Các thành phần hiện có:

| Thành phần | Trạng thái thực tế |
|---|---|
| `app/build.gradle.kts` | Có Android application, Kotlin, Compose, Material 3, Navigation Compose, DataStore dependency và coroutines dependency. |
| `MainActivity.kt` | Có một màn hình Home Compose duy nhất. |
| `domain/Models.kt` | Có model cơ bản cho video, source, subtitle, download config và task status; có `UrlValidator`, `FileNameSanitizer`, `SrtToTxtConverter`. |
| `CoreTest.kt` | Có 5 unit tests cho URL, sanitizer, SRT converter và giới hạn 50 phần tử. |
| `AndroidManifest.xml` | Có quyền Internet, foreground service và foreground service data sync; chưa đăng ký `DownloadService` vì service chưa tồn tại. |
| `.github/workflows/android.yml` | Có CI cài SDK, chạy `./gradlew test` và `./gradlew assembleDebug`. |
| Gradle Wrapper | Có `gradlew`, `gradlew.bat` và wrapper version 8.10.2. |
| yt-dlp binary | Chưa có trong `app/src/main/assets`. |

Dependency được khai báo nhưng chưa đồng nghĩa với chức năng đã hoàn thành. Cụ thể, Navigation Compose, DataStore và coroutines hiện chưa được dùng để tạo navigation, lưu cài đặt hoặc điều phối download.

## 3. Đối chiếu yêu cầu chức năng

### 3.1. FR-01 — Nhập link

| Yêu cầu | Đánh giá | Bằng chứng và thiếu sót |
|---|---|---|
| FR-01.1 Ô nhập link | **Đạt một phần** | Có `OutlinedTextField` trong `MainActivity.kt`. |
| FR-01.2 Nút Dán clipboard | **Chưa đạt** | Không có quyền truy cập hoặc thao tác ClipboardManager; giao diện không có nút `Dán`. |
| FR-01.3 Validate realtime | **Đạt một phần** | `UrlValidator` được gọi khi người dùng nhập; lỗi được hiển thị. Chưa có test UI. |
| FR-01.4 Các dạng URL YouTube | **Đạt một phần** | Có kiểm tra `@handle`, `/c/`, `/channel/`, `/playlist?`, `/watch?v=` và `youtu.be`. Chưa kiểm tra đầy đủ query edge cases và URL malformed. |
| FR-01.5 Thông báo link sai | **Đạt** | Có `isError` và thông báo tiếng Việt. |

**Kết luận FR-01:** phần nhập và validate cơ bản có, nhưng chưa có clipboard và chưa có kiểm thử giao diện.

### 3.2. FR-02 — Fetch metadata

**Chưa đạt.** Không có `YtDlpWrapper`, `YtDlpOutputParser`, `FetchVideosUseCase`, ViewModel hoặc process execution. Nút `PHÂN TÍCH` chỉ đổi trạng thái nút và hiển thị câu “Đã nhận link. Sẵn sàng phân tích tối đa 50 video.” Nó không thực hiện phân tích, không fetch video/subtitle, không hiển thị progress, không có timeout 30 giây và không xử lý lỗi mạng.

### 3.3. FR-03 — Chọn video

**Chưa đạt.** Chưa có màn hình chọn video, checkbox, danh sách video, nhãn ngôn ngữ, search filter, chọn tất cả, bộ đếm, disabled state hoặc nút `Tải phụ đề (N)`. Model `VideoItem` đã tồn tại nhưng chưa được sử dụng trong UI hoặc luồng nghiệp vụ.

### 3.4. FR-04 — Cấu hình tải

**Chưa đạt.** Chưa có ô nhập tên folder, validate tên folder, mặc định theo source, cũng chưa kết nối `DownloadConfig` với giao diện. `FileNameSanitizer` chỉ là hàm tiện ích unit-tested, chưa được dùng để tạo thư mục hoặc file thực tế.

### 3.5. FR-05 — Tải phụ đề

**Chưa đạt toàn bộ.** Chưa có yt-dlp binary, wrapper, lệnh fetch/download, retry, timeout theo video, chuyển SRT/TXT trong pipeline, MediaStore/FileStorage, lưu Downloads, foreground service hoặc notification. Manifest có khai báo một số permission nhưng không có service để thực thi.

### 3.6. FR-06 — Điều khiển task

**Chưa đạt.** Enum `TaskStatus` có các giá trị `PAUSED`, `CANCELLED` và các trạng thái liên quan, nhưng không có orchestrator hoặc task runtime. Chưa có pause/resume/cancel, confirm dialog, notification actions hoặc lưu resume state.

### 3.7. FR-07 — Hiển thị tiến độ

**Chưa đạt.** Chưa có progress bar tổng, X/Y video, video hiện tại, realtime log, bottom sheet hoặc auto-scroll. Không có `ProgressSheet.kt` hay `DownloadViewModel.kt`.

### 3.8. FR-08 — Settings

**Chưa đạt.** Chưa có Settings screen và chưa có `SettingsRepository`. `datastore-preferences` chỉ được khai báo trong Gradle, chưa có DataStore instance, serializer/key definitions, Flow hoặc persistence. Nút bánh răng hiện chỉ đặt một chuỗi message cố định: “Cài đặt: Tiếng Việt + English · TXT”.

### 3.9. FR-09 — Kết quả

**Chưa đạt.** Chưa có notification hoàn tất, nút mở folder, task card ở Home hoặc tải lại. Home hiện chỉ có card mô tả quy trình, không có task state thật.

## 4. Đối chiếu yêu cầu phi chức năng

| Nhóm SRS | Đánh giá |
|---|---|
| Min SDK 26 | **Đạt** — `minSdk = 26`. |
| Target SDK 34 | **Chưa khớp tài liệu** — code dùng `targetSdk = 35`; đây là cấu hình mới hơn nhưng khác SRS. `compileSdk = 35`. |
| Kotlin + Compose + Material 3 | **Đạt ở mức nền tảng**. |
| MVVM + Clean Architecture | **Chưa đạt** — chưa có ViewModel, use case, repository hoặc các layer theo cấu trúc SRS. |
| Hilt | **Chưa đạt** — chưa có plugin, dependency, Application class hoặc module Hilt. |
| Coroutines + Flow | **Chưa đạt về sử dụng** — dependency có, chưa có pipeline bất đồng bộ. |
| Foreground service | **Chưa đạt** — permission có, service chưa có. |
| ARM64/ARMv7 ABI filters | **Chưa đạt** — chưa cấu hình `ndk.abiFilters`. |
| Light + Dark theme | **Chưa đạt** — chỉ có `lightColorScheme`, chưa có dark scheme hoặc system theme switching. |
| Không analytics/quảng cáo/đăng nhập | **Đạt theo code hiện tại** — chưa có các thành phần này. |
| Chỉ dùng Internet cho YouTube | **Chưa thể nghiệm thu** — chưa có network implementation. |
| Accessibility | **Chưa kiểm thử** — một số Material components có semantics mặc định, nhưng chưa có UI test hoặc đánh giá TalkBack/contrast/font scale. |
| Hiệu năng, RAM, APK < 40 MB | **Chưa đo** — chưa có benchmark, profiling hoặc release APK size report. |
| Độ tin cậy, resume sau kill, crash rate | **Chưa đạt/chưa đo** — chưa có service, persistence, integration test hoặc crash monitoring. |

## 5. Đối chiếu testing plan

### Đã có

Năm unit tests hiện có kiểm tra URL hợp lệ, URL không hợp lệ, loại bỏ dấu tiếng Việt và ký tự đặc biệt, chuyển SRT sang TXT, và tạo danh sách 50 video. CI chạy được các test này.

### Chưa có

Chưa có test cho ViewModel state transitions, parser yt-dlp, DataStore, FileStorage, Fetch integration, download một hoặc nhiều video, pause/resume, cancel, service restart, Compose UI, clipboard, Settings hoặc các manual scenarios trong SRS. Cũng chưa có binary yt-dlp và public playlist test fixture để chạy integration test thực tế.

## 6. So sánh lộ trình P0–P8

| Giai đoạn SRS | Trạng thái thực tế |
|---|---|
| P0 — Setup project, Hilt, Compose, yt-dlp bundle | **Khoảng 60%**: project và Compose có; Hilt và yt-dlp bundle chưa có. |
| P1 — YtDlpWrapper, FileStorage, SettingsRepo | **0%**: chưa có implementation. |
| P2 — Home fetch và validate | **Khoảng 35%**: Home và validate có; fetch chưa có, clipboard chưa có. |
| P3 — Select screen | **0%**. |
| P4 — Download service/orchestrator/notification | **0%**. |
| P5 — Progress/pause/resume/log | **0%**. |
| P6 — Settings/DataStore | **Khoảng 5%**: dependency đã khai báo, chức năng chưa có. |
| P7 — Unit/integration/manual tests | **Khoảng 15%**: chỉ có unit tests tiện ích; CI đã có. |
| P8 — Polish/release | **Khoảng 10%**: Material 3 và app label có; chưa có icon, splash, release validation hoặc edge-case UX. |

## 7. Khoảng cách ưu tiên để đạt MVP sử dụng được

Thứ tự triển khai nên là một luồng dọc hoàn chỉnh thay vì tiếp tục mở rộng giao diện tĩnh. Trước hết cần tích hợp yt-dlp và parser để biến nút phân tích thành fetch thật. Tiếp theo cần tạo Select screen với giới hạn 50 video, lựa chọn ngôn ngữ và folder. Sau đó cần triển khai FileStorage cùng SRT/TXT output và một DownloadOrchestrator chạy tuần tự. Khi download cơ bản hoạt động, cần bọc orchestrator trong foreground service, thêm notification, pause/resume/cancel và lưu state. Cuối cùng mới hoàn thiện Settings/DataStore, task history card, accessibility, integration tests và release packaging.

Các blocker kỹ thuật hiện tại là việc đóng gói yt-dlp cho arm64-v8a và armeabi-v7a, xử lý quyền/lưu file theo MediaStore trên Android 10+, parse ổn định output yt-dlp, và bảo đảm service resume sau khi process bị kill.

## 8. Trạng thái bàn giao

Repository đang sạch và đồng bộ với remote branch `first-Draft`. CI đã được cấu hình và đã chạy thành công ở commit `57458af`. Artefact hiện chứng minh được là **debug APK build được**, không phải một ứng dụng đã đáp ứng toàn bộ SRS.

Để tránh hiểu sai trạng thái, nên gắn nhãn bản hiện tại là **Technical Scaffold / P0–P2 partial**, thay vì “SubGrab v1.0 hoàn thành”.

## References

[1]: https://github.com/Antony-collika/SubGrab/tree/first-Draft "SubGrab first-Draft source branch"
[2]: https://github.com/Antony-collika/SubGrab/actions/runs/34996815432 "SubGrab Android CI successful run"
