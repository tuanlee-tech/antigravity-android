<div align="center">

  <p>
    <a href="README.md">English</a> &nbsp;&bull;&nbsp; <b>Tiếng Việt</b>
  </p>

  <br/>

  <img src="docs/assets/logo.png" alt="Antigravity" width="88" height="88" style="border-radius: 20px;" />

  # Antigravity cho Android

**Môi trường phát triển AI tự động chạy trực tiếp (native) trên Android & ARM64.**

  [![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
  [![Arch](https://img.shields.io/badge/ARM64-v8.0--A%2B-blue?style=flat-square)](https://arm.com)
  [![Release](https://img.shields.io/github/v/release/tuanlee-tech/antigravity-android?style=flat-square&color=blueviolet&label=Phi%C3%AAn%20b%E1%BA%A3n%20m%E1%BB%9Bi%20nh%E1%BA%A5t)](https://github.com/tuanlee-tech/antigravity-android/releases/latest)
  [![License](https://img.shields.io/badge/Gi%E1%BA%A5y%20ph%C3%A9p-Apache_2.0-orange?style=flat-square)](LICENSE)

  <br/>

  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/hero_dark.png">
    <source media="(prefers-color-scheme: light)" srcset="docs/assets/hero_light.png">
    <img alt="Giao diện Antigravity" src="docs/assets/hero_dark.png" width="100%" style="border-radius: 12px; border: 1px solid rgba(128,128,128,0.2);" />
  </picture>

</div>

---

### Tổng quan

**Antigravity cho Android** mang môi trường lập trình AI tự động lên thẳng thiết bị di động.

Không dùng PRoot, chroot hay lớp ảo hóa. Antigravity chạy **trực tiếp trong không gian người dùng gốc của Android (Bionic)**, khai thác tối đa tốc độ đọc/ghi bộ nhớ UFS và các nhân CPU vật lý. Được hỗ trợ bởi các mô hình Gemini, agent có thể đọc kho mã nguồn, chỉnh sửa nhiều tệp, biên dịch binary và chạy kiểm thử ngay trên điện thoại.

---

### Điểm nổi bật trong v2.4

* **Tương thích Android 10+ (Seccomp):** Bổ sung các bản vá syscall `ENOSYS` dạng mô-đun (`pidfd_open`, `copy_file_range`, `openat2`) giúp khắc phục hoàn toàn lỗi sập `SIGSYS` trên Android 10–13.
* **Kiến trúc mô-đun:** Tách lõi thành 6 phân hệ độc lập (`modules/*`) đi kèm bộ kiểm thử riêng.
* **Màn hình chờ gốc (Native Splash):** Hoạt ảnh vẽ bằng Canvas giúp tiết kiệm ~40 MB RAM và khởi động tức thì.
* **Chế độ Desktop:** Bật/tắt nhanh trong cài đặt *Appearance* cho máy tính bảng, bàn phím rời và Samsung DeX.
* **Hỗ trợ ARMv8.0-A:** Tích hợp giả lập lệnh nguyên tử & SIMD (`liblse_compat.so`) cho các dòng CPU đời cũ.
* **Micro-Daemon nền:** `agy-daemon` siêu nhẹ (<10 MB RAM) hỗ trợ hẹn giờ cron, theo dõi tệp và hàng đợi tác vụ AI.

---

### Tính năng chính

* **Agent tự động:** Lập kế hoạch, sửa mã nguồn trên toàn bộ dự án, chạy công cụ dòng lệnh và tự sửa lỗi biên dịch.
* **Quyền nâng cao (Shizuku):** Thực thi thao tác cấp ADB (`uid=2000`) không cần root để cài gói và chẩn đoán hệ thống.
* **Trình quản lý gói `pkg`:** Cài nhanh các môi trường phát triển (`git`, `python`, `node`, `clang`) từ kho Termux.
* **Giao diện linh hoạt:** Tối ưu cảm ứng trên điện thoại, hỗ trợ chuyển sang bố cục máy tính.
* **Cập nhật liền mạch:** Giữ cố định keystore ký APK giúp nâng cấp đè mà không mất phiên đăng nhập hay dữ liệu dự án.

---

### Bắt đầu nhanh

1. **Tải APK bản mới nhất** tại [Latest Release](https://github.com/tuanlee-tech/antigravity-android/releases/latest):
   * [`antigravity.apk`](https://github.com/tuanlee-tech/antigravity-android/releases/latest/download/antigravity.apk) — bản tiêu chuẩn (giữ nguyên kiểm tra tài khoản Google).
   * [`antigravity-region-bypass.apk`](https://github.com/tuanlee-tech/antigravity-android/releases/latest/download/antigravity-region-bypass.apk) — bản bỏ qua kiểm tra giới hạn vùng/tài khoản.
2. **Cấp quyền truy cập bộ nhớ** (`Truy cập tất cả tệp`). *(Tùy chọn: bật Shizuku nếu cần quyền ADB)*.
3. **Cài công cụ lập trình** trong terminal hoặc qua agent:
   ```bash
   pkg update && pkg install git python nodejs
   ```

---

### Biên dịch & Kiểm thử

```bash
# Build APK tiêu chuẩn (dist/antigravity.apk):
bash build.sh

# Build APK có region bypass (dist/antigravity-region-bypass.apk):
bash build.sh --region-bypass

# Chạy toàn bộ bộ kiểm thử:
bash test_all_modules.sh
```

---

### Cấu trúc kho mã nguồn

```text
antigravity/
├── config/             # Hằng số ứng dụng, đường dẫn môi trường và quy tắc AI
├── modules/            # Các phân hệ lõi độc lập (v2.4+)
│   ├── core/           # Giải nén binary, glibc loader, shims, manifest SHA-256
│   ├── patches/        # Bộ vá nhị phân (seccomp syscall, resolv, SIGILL, PTY)
│   ├── io/             # Bus I/O hiệu năng cao: event broker, FIFO pipes, sockets
│   ├── env/            # Định tuyến đường dẫn, chẩn đoán, Flight Recorder logs, CLI
│   ├── builder/        # Bộ build APK không cần Gradle (javac, D8, aapt, zipalign, apksigner)
│   └── runner/         # Chuẩn bị runtime độc lập, kiểm tra cổng, kiểm thử E2E
├── app/                # Ứng dụng Android host (native splash, WebView, system bridges)
├── web/                # Giao diện web cục bộ, chế độ desktop, chủ đề giao diện
├── env/                # Môi trường Bionic, Termux overlay, trình quản lý pkg, liblse_compat
├── docs/assets/        # Ảnh chụp màn hình, banner và sơ đồ
├── build.sh            # Script build APK gốc
└── test_all_modules.sh # Bộ kiểm thử cho cả 6 phân hệ
```

> [!TIP]
> Mỗi thư mục trong `modules/`, `app/`, `web/`, và `env/` đều có tệp `README.md` riêng với tài liệu kỹ thuật chi tiết.

---

<div align="center">
  <sub>Phát hành theo <a href="LICENSE">Giấy phép Apache 2.0</a></sub>
</div>
