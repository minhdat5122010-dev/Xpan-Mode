# Xpan-Mode

Ứng dụng camera Android native thử nghiệm, giao diện chụp khung siêu rộng X-Pan, hai skin UI và đọc thông tin camera bằng Camera2.

## Mở project

1. Cài Android Studio bản ổn định và Android SDK 35.
2. Mở thư mục `Xpan-Mode` bằng Android Studio.
3. Chờ Gradle Sync, sau đó chọn thiết bị Android API 28 trở lên và Run.
4. Cấp quyền Camera khi ứng dụng hỏi.

## Tính năng trong bản 1.0.0

- Giao diện ngang, preview camera, nút chụp lớn và khung hình X-Pan 2.39:1.
- Nút góc trái chuyển hai skin giao diện: Classic và XPAN / Mode.
- Bộ lọc xử lý sau khi chụp: Natural, Classic, Vivid, Bright. Đây là các profile màu gốc của dự án, không phải LUT hoặc thuật toán màu độc quyền của Hasselblad, Leica hay ZEISS.
- Chụp JPEG từ luồng độ phân giải lớn nhất được Camera HAL công bố cho camera đang mở, `JPEG_QUALITY=100`.
- Đọc camera ID, physical camera ID thuộc logical camera, tiêu cự, khẩu độ, kích thước cảm biến, khoảng lấy nét và một số capability công khai.
- Có thể thử chuyển giữa các camera được Android công bố. Khi camera là physical camera nằm trong logical camera, app gắn physical ID vào output stream theo Camera2 API.
- Ảnh lưu tại `DCIM/Xpan-Mode`.

## Giới hạn quan trọng

Camera2 chỉ có thể đọc và sử dụng những camera/metadata mà ROM, Camera HAL và nhà sản xuất công khai cho ứng dụng bên thứ ba. Một số máy có thể chỉ cho app bên thứ ba thấy camera logical hoặc giới hạn việc mở luồng từ camera tele. Vì vậy, không thể cam kết chắc chắn mọi camera tele của Xiaomi 14 đều mở được, dù app đã liệt kê các physical ID được công bố và thử route stream qua logical camera.

Các profile màu hiện là hiệu ứng hậu kỳ cơ bản trên JPEG, chưa phải pipeline computational photography của ứng dụng camera gốc. Bản này chưa có RAW/DNG, HDR nhiều khung, chống rung điện tử, xử lý noise đa khung, chỉnh tay ISO/tốc độ/màu, lưu metadata EXIF đầy đủ sau khi render, hay hiệu chỉnh preview màu theo filter. Chế độ X-Pan cắt ảnh sau khi chụp, nên ảnh thành phẩm có ít chiều cao pixel hơn ảnh gốc; đây là bản crop khung hình chứ không phải ghép panorama.

Để giữ ảnh gốc không mất chi tiết do hậu kỳ, hãy tắt crop và chọn Natural. Bản tiếp theo nên lưu song song ảnh sensor JPEG gốc và bản X-Pan đã render, đồng thời thêm DNG khi sensor hỗ trợ RAW.

## Cấu trúc

- `MainActivity.kt`: giao diện, preview, chụp và lưu ảnh.
- `LensInfo.kt`: liệt kê camera và đọc thông tin ống kính.
- `app/build.gradle.kts`: cấu hình Android/Kotlin.

## Build

Android Gradle Plugin 8.7.3, Kotlin 2.0.21, JDK 17, compileSdk 35.

Khi bật X-Pan crop hoặc chọn profile màu khác Natural, ứng dụng lưu thêm một file `_ORIGINAL.jpg` giữ nguyên JPEG do camera trả về; file `_XPAN.jpg` là bản crop/màu để chia sẻ. Hai file nằm trong `DCIM/Xpan-Mode`.

## GitHub Actions

Workflow `.github/workflows/android.yml` tự build APK debug khi push hoặc tạo pull request. APK được đính kèm trong mục Actions → Artifacts của lần chạy tương ứng.
