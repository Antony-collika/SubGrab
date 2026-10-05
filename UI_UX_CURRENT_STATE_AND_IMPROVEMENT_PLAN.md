# Báo cáo hiện trạng và phương án cải thiện UI/UX của SubGrab

**Nhánh khảo sát:** `keyword`  
**Nhánh triển khai tài liệu:** `UI/UX`  
**Commit khảo sát:** `ac7391f59b3a81a5e8eb1b8df524d745e9b87d82`  
**Phạm vi:** Luồng màn hình chính, cài đặt, nhật ký debug, phân tích URL/từ khóa, chọn video và tải phụ đề.

## 1. Kết luận điều hành

Ứng dụng hiện có đầy đủ các composable cho màn hình chính, cài đặt, nhật ký debug và chọn video, nhưng chưa có một cơ chế điều hướng thực sự. `SubGrabApp` chỉ dùng hai biến Boolean là `showSettings` và `showDebug` để quyết định nội dung hiển thị. Khi phân tích thành công, `AnalysisState.Ready` tự động thay thế màn hình chính bằng màn hình chọn video. Màn hình này không nhận callback quay lại và cũng không có xử lý nút Back của Android.

Đây là nguyên nhân trực tiếp của trải nghiệm mà người dùng mô tả: sau khi bấm **Phân tích** hoặc **Tìm video**, người dùng bị đưa vào màn hình chọn video nhưng không có đường quay về màn hình nhập liệu. Khi mở **Cài đặt** hoặc **Debug**, giao diện có nút đóng nội bộ, nhưng việc chuyển màn hình vẫn chỉ là thay đổi biến trạng thái cục bộ. Khi xảy ra một trạng thái không có lối thoát, người dùng phải đóng và mở lại ứng dụng.

Khuyến nghị ưu tiên là thay thế các cờ Boolean bằng một mô hình điều hướng có trạng thái màn hình rõ ràng. Có thể dùng `Navigation Compose`, vì dependency này đã có trong `app/build.gradle.kts` nhưng hiện chưa được sử dụng. Ở bước đầu, cần bổ sung đường quay lại cho màn hình chọn video, cài đặt và debug; sau đó tách trạng thái phiên phân tích khỏi trạng thái hiển thị để giữ dữ liệu khi chuyển màn hình hoặc khi Activity bị tái tạo.

## 2. Hiện trạng kiến trúc giao diện

### 2.1. Các màn hình đang tồn tại

| Màn hình | Cách hiển thị hiện tại | Có đường quay lại? | Trạng thái chính |
|---|---|---:|---|
| Màn hình chính | Hiển thị khi không ở `Ready`, không mở cài đặt/debug | Không áp dụng | URL, từ khóa và mode được giữ bằng `remember` cục bộ |
| Chọn video | Tự động hiển thị khi `AnalysisState.Ready` | **Không** | Danh sách video nằm trong `DownloadViewModel` |
| Cài đặt | Được mở khi `showSettings = true` | Có nút `Xong` | Lưu trực tiếp qua `SettingsRepository` |
| Debug log | Được mở khi `showDebug = true` | Có nút `Đóng` | Đọc snapshot log mỗi 300 ms |
| Tiến trình tải | Card nhúng trong màn hình chính hoặc chọn video | Không có màn hình riêng | `DownloadState` từ orchestrator/service |

### 2.2. Cơ chế quyết định màn hình

Trong `SubGrabApp`, thứ tự ưu tiên hiện tại là:

```kotlin
if (showSettings) SettingsScreen(...)
else if (showDebug) DebugLogScreen(...)
else when (val current = state) {
    is AnalysisState.Ready -> SelectVideoScreen(...)
    else -> HomeScreen(...)
}
```

Cách này tạo ra một cây điều kiện thay vì một back stack. Nó có các hạn chế sau:

1. Không có `NavController`, `NavHost` hoặc route. Người dùng không thể quay lại theo lịch sử màn hình.
2. `SelectVideoScreen` không nhận `onBack`. Trạng thái `Ready` không có thao tác chuyển về `Idle` hoặc màn hình chính.
3. Nút Back vật lý hoặc gesture Back của Android không được xử lý trong `MainActivity`. Hành vi mặc định có thể là kết thúc Activity.
4. `showSettings` và `showDebug` là hai cờ độc lập. Nếu một cờ đã bật rồi sự kiện khác bật cờ còn lại, thứ tự `if` sẽ che màn hình trước đó; không có quy tắc ngăn trạng thái chồng lấn.
5. Top app bar vẫn được tạo bên ngoài các màn hình con. Vì vậy các màn hình cài đặt, debug và chọn video không có tiêu đề, nút Back hoặc hành vi điều hướng nhất quán trong cùng một thanh điều hướng.

## 3. Phân tích theo từng luồng người dùng

### 3.1. URL hoặc từ khóa → phân tích → chọn video

`DownloadViewModel.analyze()` và `searchKeyword()` cập nhật `AnalysisState` từ `Idle` sang `Loading`, rồi sang `Ready` hoặc `Error`. Khi state là `Ready`, `SubGrabApp` lập tức thay `HomeScreen` bằng `SelectVideoScreen`.

Điểm tốt là danh sách video và lựa chọn video được giữ trong ViewModel, nên các thao tác chọn/bỏ chọn không chỉ nằm trong composable. Tuy nhiên, ViewModel chưa cung cấp thao tác `goBackToHome`, `resetAnalysis` hoặc `clearResults`. Do đó UI không có cách hợp lệ để trả state về màn hình nhập liệu.

**Mức độ:** Cao. Đây là lỗi chặn luồng sử dụng chính và khớp trực tiếp với phản hồi của người dùng.

### 3.2. Chọn video → tải phụ đề

Màn hình chọn video cho phép chọn tất cả, bỏ chọn, chỉnh tên folder và bắt đầu tải. Tiến trình tải được hiển thị ngay trong cùng màn hình dưới dạng card. ViewModel có các lệnh tạm dừng, tiếp tục và hủy.

Luồng tải có khả năng hoạt động tiếp tục qua service, nhưng UI chưa có màn hình lịch sử hoặc màn hình tiến trình độc lập. Nếu người dùng rời khỏi màn hình hiện tại sau khi tải bắt đầu, việc xem lại công việc và kết quả chưa được mô hình hóa thành một route riêng.

**Mức độ:** Trung bình. Không nhất thiết chặn tải, nhưng làm giảm khả năng quan sát và phục hồi thao tác.

### 3.3. Màn hình chính → cài đặt

Cài đặt được mở bằng `showSettings`. `SettingsScreen` có callback `onBack`, và nút `Xong` đặt lại cờ về `false`. Vì vậy luồng này có đường quay lại nội bộ.

Tuy nhiên, trạng thái màn hình chính không được mô hình hóa trong back stack. Nút Back hệ thống chưa được liên kết với callback. Ngoài ra, việc cập nhật repository được gọi trực tiếp sau mỗi thay đổi checkbox, switch hoặc từng ký tự trong trường folder. Điều này giúp lưu ngay nhưng có thể tạo nhiều thao tác ghi liên tiếp và chưa có trạng thái lưu lỗi/thành công rõ ràng.

**Mức độ:** Trung bình.

### 3.4. Màn hình chính → debug log

Debug log có nút `Đóng`, nhưng dùng vòng lặp `while (true)` trong `LaunchedEffect` để đọc snapshot mỗi 300 ms. Khi màn hình bị thay thế hoặc Activity bị hủy, coroutine sẽ được hủy theo composition; trong quá trình tồn tại, cách polling này vẫn tiêu tốn chu kỳ xử lý dù không có log mới.

Màn hình này cũng không có xử lý Back hệ thống. Tên nút và ngôn ngữ đang trộn giữa tiếng Việt và tiếng Anh, khác với phần lớn giao diện còn lại.

**Mức độ:** Thấp đến trung bình. Không phải nguyên nhân chính của việc kẹt sau phân tích, nhưng là dấu hiệu giao diện chưa nhất quán.

## 4. Các vấn đề kỹ thuật cần xử lý

### P0 — Không thể quay lại sau khi phân tích

`SelectVideoScreen` chỉ nhận dữ liệu và các callback thao tác video. Không có `onBack`, `onNewAnalysis` hoặc nút làm mới kết quả. Cần bổ sung ngay một hành động **Quay lại** hoặc **Phân tích mới**, đồng thời đưa ViewModel về trạng thái phù hợp.

### P0 — Thiếu back stack và xử lý Back hệ thống

Ứng dụng đã khai báo `androidx.navigation:navigation-compose:2.8.5`, nhưng không dùng dependency này. Cần chọn một trong hai hướng:

- **Hướng khuyến nghị:** dùng `NavHost` với các route `home`, `results`, `settings`, `debug` và `download`.
- **Hướng sửa tối thiểu:** dùng một sealed class `AppScreen` duy nhất thay cho hai Boolean, kết hợp `BackHandler` và các callback chuyển màn hình.

Hướng sửa tối thiểu phù hợp cho bản vá khẩn cấp. Hướng `Navigation Compose` phù hợp hơn để tránh lặp lại lỗi khi thêm màn hình.

### P1 — State giao diện và state nghiệp vụ đang trộn lẫn

`AnalysisState.Ready` vừa biểu thị dữ liệu phân tích vừa quyết định màn hình nào được hiển thị. Đây là lý do việc “quay lại” khó thực hiện: muốn đổi màn hình phải xóa hoặc thay thế kết quả phân tích. Nên tách thành:

```text
UiRoute: HOME | RESULTS | SETTINGS | DEBUG | DOWNLOAD
AnalysisState: IDLE | LOADING | READY | ERROR
```

Khi quay về Home, có thể giữ kết quả phân tích trong ViewModel để người dùng quay lại mà không cần tải lại dữ liệu, hoặc xóa kết quả theo một hành động rõ ràng **Phân tích mới**.

### P1 — Input URL và từ khóa nằm trong composable

`url`, `keyword` và `mode` được khai báo bằng `remember` trong `HomeScreen`. Chúng có thể mất khi composition bị tái tạo hoặc khi cần dùng lại dữ liệu sau điều hướng. Nên đưa chúng vào `HomeUiState` trong ViewModel hoặc tối thiểu dùng `rememberSaveable`.

### P1 — Chưa có trạng thái phiên làm việc rõ ràng

Khi người dùng chuyển giữa cài đặt và kết quả, ứng dụng chưa quy định rõ dữ liệu nào được giữ. Cần xác định chính sách:

- Kết quả và lựa chọn video được giữ khi mở cài đặt.
- Tải đang chạy không bị hủy khi đổi màn hình.
- Nút **Phân tích mới** phải cảnh báo nếu đang có lựa chọn chưa tải.
- Công việc tải đang chạy phải tiếp tục qua service và hiển thị lại khi quay về màn hình tiến trình.

### P2 — Trải nghiệm và khả năng quan sát chưa nhất quán

Cần chuẩn hóa tiêu đề, ngôn ngữ, icon và hành vi Back. Các nút hành động nên có thứ bậc rõ ràng: **Phân tích**, **Phân tích mới**, **Quay lại**, **Tải phụ đề**. Lỗi nên hiển thị bằng snackbar hoặc banner có hành động thử lại, thay vì chỉ gắn vào `OutlinedTextField`.

## 5. Phương án cải thiện đề xuất

### Phương án A — Bản vá nhanh, ít thay đổi

Bổ sung sealed class cho màn hình hiện tại, ví dụ `Home`, `Results`, `Settings`, `Debug`. Thêm callback `onBack` vào `SelectVideoScreen`. Khi người dùng quay lại, đặt route về `Home` nhưng giữ `AnalysisState.Ready` trong ViewModel; `HomeScreen` cần được phép hiển thị khi state vẫn là `Ready`, hoặc ViewModel cung cấp state hiển thị kết quả độc lập với state dữ liệu.

Đồng thời thêm `BackHandler` trong `SubGrabApp` để xử lý theo thứ tự: đóng debug, đóng cài đặt, từ results về home, rồi mới cho Activity kết thúc. Phương án này giải quyết nhanh lỗi chặn chính nhưng vẫn là điều hướng thủ công.

### Phương án B — Kiến trúc khuyến nghị

Dùng `Navigation Compose` với một `NavHost` duy nhất. Đề xuất route:

```text
home
results
settings
 debug
progress
```

`MainActivity` chỉ tạo theme, ViewModel và root navigation. Mỗi màn hình nhận dữ liệu tối thiểu và callback rõ ràng. `NavController` quản lý back stack, deep link nội bộ và nút Back hệ thống. `DownloadViewModel` giữ state nghiệp vụ, còn route quyết định màn hình hiện tại.

Đối với kết quả phân tích, có thể lưu source và danh sách video trong ViewModel hoặc một state holder dùng chung. Route `results` đọc state đó. Khi nhấn **Phân tích mới**, gọi một lệnh reset có chủ đích rồi điều hướng về `home`.

### Phương án C — Hoàn thiện UX sau khi có navigation

Sau khi có route ổn định, nên bổ sung:

1. Thanh điều hướng nhất quán với nút Back ở màn hình con.
2. Nút **Phân tích mới** ở màn hình chọn video.
3. Snackbar cho lỗi, hoàn tất và thao tác bị từ chối.
4. Khôi phục input bằng `SavedStateHandle` hoặc `rememberSaveable`.
5. Màn hình tiến trình tải độc lập để người dùng có thể quay về Home trong khi tải tiếp tục.
6. Kiểm thử Compose cho các kịch bản: mở cài đặt rồi quay lại, mở debug rồi quay lại, phân tích rồi quay lại, Back hệ thống từ từng màn hình, và quay lại khi tải đang chạy.

## 6. Kế hoạch triển khai theo giai đoạn

| Giai đoạn | Công việc | Kết quả mong đợi |
|---|---|---|
| 1. Sửa lỗi chặn | Thêm route/back action cho kết quả, cài đặt và debug; xử lý Back hệ thống | Không cần thoát app để thực hiện phân tích mới |
| 2. Tách state | Tách route khỏi `AnalysisState`; đưa input quan trọng vào state có khả năng khôi phục | Chuyển màn hình không làm mất phiên làm việc |
| 3. Chuẩn hóa UX | Thống nhất top bar, tiêu đề, button, lỗi và thông báo | Luồng sử dụng dễ hiểu và nhất quán |
| 4. Tách tiến trình tải | Mô hình hóa màn hình progress và trạng thái service | Có thể rời Home nhưng vẫn theo dõi tải |
| 5. Kiểm thử | Bổ sung UI tests và kiểm tra process recreation | Giảm nguy cơ tái phát lỗi điều hướng |

## 7. Tiêu chí nghiệm thu đề xuất

Một bản sửa đạt yêu cầu khi người dùng có thể thực hiện liên tục các thao tác sau mà không đóng ứng dụng:

1. Mở ứng dụng, phân tích URL và quay lại màn hình chính.
2. Chuyển sang tìm bằng từ khóa, tìm video và quay lại màn hình nhập liệu.
3. Mở cài đặt, thay đổi tùy chọn và quay lại đúng màn hình trước đó.
4. Mở debug log, đóng bằng nút trên màn hình hoặc bằng Back hệ thống.
5. Từ màn hình chọn video, bắt đầu một lượt tải rồi quay về màn hình chính mà tiến trình vẫn tiếp tục.
6. Khi phân tích lỗi, dùng **Thử lại** hoặc **Phân tích mới** mà không cần khởi động lại ứng dụng.
7. Khi xoay màn hình hoặc Activity bị tái tạo, input và trạng thái cần thiết không bị mất ngoài chính sách đã định.

## 8. Kết luận

Vấn đề hiện tại không nằm ở riêng nút **Phân tích** mà nằm ở mô hình điều hướng chưa tồn tại. Màn hình chọn video được tạo bằng một nhánh điều kiện dựa trên `AnalysisState.Ready`, nhưng không có hành động đảo chiều. Cài đặt và debug có callback đóng, song chưa được tích hợp vào back stack và chưa xử lý Back hệ thống.

Nên triển khai bản vá P0 trước để loại bỏ việc phải thoát ứng dụng. Sau đó chuyển sang `Navigation Compose` và tách route khỏi state nghiệp vụ. Đây là cách giảm nợ kỹ thuật, bảo toàn trạng thái tải và tạo nền tảng để bổ sung lịch sử tải, màn hình tiến trình hoặc các tính năng UI/UX khác mà không tiếp tục mở rộng chuỗi `if/else` trong `SubGrabApp`.

## References

[1]: https://github.com/Antony-collika/SubGrab/blob/keyword/app/src/main/java/com/subgrab/app/MainActivity.kt "SubGrab MainActivity and root Compose UI"
[2]: https://github.com/Antony-collika/SubGrab/blob/keyword/app/src/main/java/com/subgrab/app/ui/DownloadViewModel.kt "SubGrab DownloadViewModel and AnalysisState"
[3]: https://github.com/Antony-collika/SubGrab/blob/keyword/app/src/main/java/com/subgrab/app/ui/SettingsScreen.kt "SubGrab SettingsScreen"
[4]: https://github.com/Antony-collika/SubGrab/blob/keyword/app/build.gradle.kts "SubGrab Android dependencies and Navigation Compose configuration"
[5]: https://developer.android.com/develop/ui/compose/navigation "Android Navigation with Compose"
[6]: https://developer.android.com/develop/ui/compose/touch-input/pointer-input/back-gestures "Android predictive back and Compose back handling"

> Các nhận định mã nguồn trong báo cáo được đối chiếu với [MainActivity][1], [DownloadViewModel][2], [SettingsScreen][3] và cấu hình dependency [build.gradle.kts][4]. Phương án kiến trúc tham chiếu hướng dẫn chính thức về [Navigation Compose][5] và xử lý Back trong Compose [6].
