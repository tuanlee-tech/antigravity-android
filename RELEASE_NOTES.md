# Antigravity cho Android v2.4.5

## Danh sách gói phát hành

| Tệp gói | Mã ứng dụng | Cổng runtime | Mô tả |
| :--- | :--- | :--- | :--- |
| **`antigravity.apk`** | `com.agy` | 45157 | Bản tiêu chuẩn giữ nguyên cơ chế kiểm tra tài khoản của Google. |
| **`antigravity-region-bypass.apk`** | `com.agy` | 45157 | Bản bỏ qua kiểm tra điều kiện tài khoản/vùng (yêu cầu VPN/tunnel nếu cần). |
| **`SHA256SUMS.txt`** | — | — | Mã kiểm tra toàn vẹn SHA-256 cho các tệp APK. |

---

## Thay đổi & Sửa lỗi (v2.4.5)

### 1. Sửa lỗi DNS cho `gh` và các công cụ Go chạy trên Bionic
* **Nguyên nhân:** Core glibc (`language_server`) xuất `GODEBUG=netdns=go`, khiến các tiến trình con viết bằng Go (như `gh`) kế thừa biến này và tra DNS về `[::1]:53` (`connection refused`).
* **Khắc phục:** Tự động xuất `GODEBUG="netdns=cgo"` trong các cầu nối `ShellBridgeInstaller` (tăng `BRIDGE_FORMAT` lên 4) và `env/overlay/etc/profile.d/00-env.sh`, đồng thời cập nhật sẵn `GODEBUG=netdns=cgo` trong skill `android-setup` và `README`.

### 2. Tự động cập nhật skill mặc định khi nâng cấp APK
* Nâng cấp `OnboardingSeeder.java` theo dõi SHA-256 của skill mặc định qua `.seed-hashes.properties`: tự động cập nhật `android-setup/SKILL.md` nếu là bản gốc chưa chỉnh sửa (bao gồm nâng cấp từ v2.4.4), và giữ nguyên nếu người dùng đã tự tùy chỉnh.

---

## Thay đổi & Sửa lỗi (v2.4.4)

### 1. Skill mặc định `android-setup` tích hợp sẵn
* Đóng gói sẵn skill `android-setup` và tự động nạp vào `~/.gemini/config/skills/android-setup/SKILL.md` qua `OnboardingSeeder` + `pack_seed.py`.
* Người dùng chỉ cần gõ **`/android-setup`** hoặc nhắn trong khung chat (ví dụ *"cài git python nodejs"* hoặc *"clone repo private"*), agent sẽ tự động kiểm tra `pkg`, cài môi trường và hướng dẫn đăng nhập GitHub (`gh` / PAT / SSH) để clone repo private mà không cần copy lệnh từ README.

### 2. Sửa TLS cho Git & nhận diện giả lập x86/x86_64
* Xuất sẵn biến `GIT_SSL_CAINFO="$PREFIX/etc/tls/cert.pem"` trong `ShellBridgeInstaller` (nâng `BRIDGE_FORMAT` lên 3).
* Phát hiện trường hợp chạy trên giả lập PC x86/x86_64 (`exit code 139 / SIGSEGV`) trong `CoreProcessSupervisor` để hiển thị thông báo yêu cầu thiết bị ARM64 gốc.

---

## Thay đổi & Sửa lỗi (v2.4.3)

### 1. Chọn thư mục Project trực tiếp bằng trình quản lý tệp Android
* Kết nối `window.dialog.showOpenDialog()` tới `AndroidBridge.openDirectoryPicker()` (`ACTION_OPEN_DOCUMENT_TREE`) qua `web/src/core/dialog-bridge.js`. Khi bấm **New Project** hoặc **Open Folder**, app tự mở bộ chọn thư mục Android và điền đường dẫn `/storage/emulated/0/...` tự động.

### 2. Cải thiện giao diện cảm ứng & Chọn Model trực quan hơn
* **Tăng kích thước nút bấm mặc định trên Mobile** (`min-height: 36–42px`) và thêm tùy chọn **UI Scale (`100% / 115% (Mobile) / 125% / 140%`)** ngay trong *Settings → Appearance* để chỉnh độ lớn giao diện trực tiếp.
* **Sửa UX chọn Model (`touch_model_menu`):** chạm vào tên model để chuyển model ngay lập tức; chạm vào nhãn thinking effort bên phải để mở menu chọn mức suy luận (`Low / Medium / High`).

### 3. Sửa lỗi không cài được `usr` / `pkg` trên Android 10
* Bổ sung giải mã header mở rộng POSIX PAX (`x` — `path`/`linkpath`) và GNU `K` trong `TarExtractor.java` để giải nén đầy đủ các đường dẫn dài trong `bootstrap.tar.gz` khi `toybox tar` trên Android 10 fallback về Java.
* Đảm bảo tạo sẵn thư mục `tmp` và `home` trước `bashSmokeTest`, đồng thời ghi log chi tiết vào `files/logs/env_install.log` (kèm xuất vào Diagnostics).

---

## Thay đổi & Sửa lỗi (v2.4.2)

### 1. Khắc phục lỗi sập `SIGSYS` trên Android 10 (API 29) & Android 10–13
* **Giải mã chính xác `runtime.text` của Go PIE binary:** Bổ sung `get_section_by_name`, `offset_to_vaddr` trong `elf_scanner.py` và `get_go_text_offset` trong `pclntab_scanner.py` để xác định đúng độ lệch `textStart` từ relocation `moduledata` trong `.rela.dyn`.
* **Bổ sung 3 mô-đun vá syscall trả về `ENOSYS` để kích hoạt fallback có sẵn của Go:**
  * `pidfd`: vá `internal/syscall/unix.PidFDOpen` (`SYS_pidfd_open`, #434) trả về `(-1, ENOSYS)` — ngăn `os.checkPidfd()` bị seccomp-bpf trên Android 10 chặn gây `SIGSYS`.
  * `copy_file_range`: vá `internal/syscall/unix.CopyFileRange` (`SYS_copy_file_range`, #285) trả về `(0, ENOSYS)` — kích hoạt fallback về `read`/`write` trên Android 10–13.
  * `openat2`: vá `unix.openat2` (`SYS_openat2`, #437) trả về `(-1, ENOSYS)` — kích hoạt fallback về `openat`.

### 2. Tự động phát hành Release mới nhất
* Cập nhật workflow GitHub Actions để tự động tạo **Latest Release** cho cả push tag `v*` và chạy tay `workflow_dispatch`.

---

## Các tính năng chính từ v2.4.0 – v2.4.1

* **Web Terminal & PTY tích hợp:** Vá nhị phân `/bin/bash` → `/bin///sh`, chuỗi fallback `$SHELL` (`$PREFIX/bin/bash` → `runtime/bin/bash` → `/system/bin/sh`) và cấu hình `TERM=xterm-256color` / `TERMINFO`.
* **Ổn định build AAPT trên Android ARM64:** Xuất biến `ANDROID_DATA` tránh lỗi `SIGABRT (134)`.
* **Tăng cường bảo mật:** Gỡ `android:debuggable="true"` khỏi bản phát hành chính thức.
* **Bộ chọn tệp gốc Android:** Thay thế SAF bằng bộ chọn hệ thống hỗ trợ Gallery, Google Photos và trình quản lý tệp.
* **Hỗ trợ CPU ARMv8.0-A:** Giả lập lệnh LSE, FEAT_RDM và FEAT_FCMA qua `liblse_compat.so`.
* **Tối ưu đăng nhập OAuth:** Bắt URL trực tiếp qua stdout thay vì quét tệp tạm.
* **Đèn pin (`torch on/off`), micro-daemon nền (`agy-daemon`) và công cụ xuất log chẩn đoán.**

---

## Nâng cấp & Tương thích

Tất cả bản build đều ký bằng cùng một keystore. Bạn có thể cài đè trực tiếp lên các phiên bản 2.x trước đó mà không cần gỡ ứng dụng hay mất dữ liệu dự án và phiên đăng nhập.
