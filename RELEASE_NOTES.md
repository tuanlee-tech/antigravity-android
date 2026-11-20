# Antigravity cho Android v2.4.7

## Danh sách gói phát hành

| Tệp gói | Mã ứng dụng | Cổng runtime | Mô tả |
| :--- | :--- | :--- | :--- |
| **`antigravity.apk`** | `com.agy` | 45157 | Bản tiêu chuẩn giữ nguyên cơ chế kiểm tra tài khoản của Google. |
| **`antigravity-region-bypass.apk`** | `com.agy` | 45157 | Bản bỏ qua kiểm tra điều kiện tài khoản/vùng (yêu cầu VPN/tunnel nếu cần). |
| **`SHA256SUMS.txt`** | — | — | Mã kiểm tra toàn vẹn SHA-256 cho các tệp APK. |

---

## Thay đổi & Sửa lỗi (v2.4.7)

* **Thông báo nền khi Agent hoàn thành hoặc cần phản hồi:**
  * Thêm `AgentNotifier` (kênh thông báo ưu tiên cao `agy_agent_alerts`) và `AgentStatusWatcher` chạy trong `CoreServerService`, theo dõi trạng thái trajectory từ Language Server (`GetAllCascadeTrajectories`) để gửi thông báo ngay cả khi bạn đang ở màn hình chính hoặc dùng ứng dụng khác, kèm chống trùng lặp (15s TTL).
  * Bổ sung cầu nối `window.Notification` (`web/src/core/notification-bridge.js` -> `AndroidBridge.notifyAgentEvent`) có xác thực token và xin quyền `POST_NOTIFICATIONS` trên Android 13+ trong `MainActivity`; chạm thông báo để quay lại ứng dụng (`ForegroundNotification` & `AgentNotifier`) và tự xóa thông báo agent khi mở lại app.
* **Thu gọn form đăng nhập trên mobile:**
  * Cập nhật CSS mobile-only trong `ui_polish.js` (không ảnh hưởng Desktop Mode) để giới hạn kích thước `.entrance-auth-panel`, `.entrance-title`, `[class~="w-[480px]"]` (`calc(100vw - 40px)`, `max-width: 340px`, `padding: 16px`), và `#advanced-sso-panel`, khắc phục lỗi form đăng nhập bị tràn viền màn hình điện thoại.

---

## Thay đổi & Sửa lỗi (v2.4.6)

### 1. Vá ELF `git` không phụ thuộc Python & đồng bộ bridge của `pkg`
* **Tận gốc nhiều lớp (Java + Bash thuần):** Bổ sung `TermuxElfShellPatcher.java` (gọi khi cài/nâng cấp APK trong `EnvInstaller`), hàm `patch_elf_shell` trong `pkg` (hook ngay sau `pkg install`) và Bước 3 trong `agy-fix-paths` dùng `grep -aobF` + `dd conv=notrunc` thay in-place `/data/data/com.termux/files/usr/bin/sh\0` → `/system/bin/sh\0` (giữ nguyên 39 byte) cho `git` & `git-remote-http` mà **không cần cài `python3`**.
* **Sửa template bridge của `pkg`:** Bổ sung `GODEBUG="netdns=cgo"`, `CURL_CA_BUNDLE`, `GIT_SSL_CAINFO`, `GIT_CONFIG_NOSYSTEM=1`, `GIT_ATTR_NOSYSTEM=1`, `GIT_TEMPLATE_DIR`, `GIT_EXEC_PATH` và `PAGER="cat"` ngay khi `pkg install` tạo wrapper trong `runtime/bin`.

### 2. Nâng cấp `/android-setup` v2 & bổ sung `/github-clone`, `/load-projects`, `agy-projects`
* **`/android-setup` v2 (Fast Track):** Gom bước cài gói vào 1 lệnh duy nhất (chỉ cài gói còn thiếu), chạy `gh auth login` ngầm và ghim mã qua `agy-notify --tag gh-auth`, đồng bộ token sang `~/.git-credentials` (`credential.helper store`), loại bỏ các lượt thử `am start`/`rish` lỗi.
* **`/github-clone`:** Duyệt nhanh top 10 repo trên GitHub bằng `ask_question` (kèm lọc theo từ khóa/org hoặc nhập URL tùy chỉnh), tự `git fetch` nếu repo đã tồn tại, clone vào `/storage/emulated/0/Documents/<repo>` và nạp thẳng vào Sidebar.
* **`/load-projects` & CLI `agy-projects`:** Công cụ dòng lệnh thuần Bash + AWK (`agy-projects list | scan | add | remove`) ghi trực tiếp tệp JSON theo schema `exa.project_pb.Project` vào `~/.gemini/config/projects/` để cập nhật **Projects Sidebar tức thì (live)** không cần khởi động lại ứng dụng.
* **`OnboardingSeeder` & `AGENTS.md`:** Sửa ví dụ `agy-notify cancel <tag>`, cập nhật mô tả 3 skill + `agy-projects` trên cả 3 bản `AGENTS.md`, và nâng cấp `OnboardingSeeder` tự động cập nhật `AGENTS.md` lẫn `android-setup/SKILL.md` khi nâng cấp APK nếu người dùng chưa chỉnh sửa thủ công.

### 3. Sửa nút `New Project` mặc định mở `Documents` & làm đẹp giao diện Splash / Mobile UI
* **Folder Picker thông minh hơn:** Nút **New Project** mặc định mở thẳng `/storage/emulated/0/Documents` (thay vì `/data/user/0/com.agy/files/`), đặt modal cao thoáng phía trên bàn phím, bổ sung thanh phím tắt nhanh (`Documents`, `Downloads`, `Storage`, `System Picker`) và gắn `DocumentsContract.EXTRA_INITIAL_URI` cho Android SAF Picker trên API 26+.
* **Splash Screen mới có số phiên bản:** Hiển thị logo Antigravity, 3 chấm loading mượt và badge phiên bản (`v2.4.6`) ở đáy màn hình chờ (hỗ trợ cả Dark & Light).
* **Mobile UI Polish:** Làm sâu bảng màu Dark (`#0e1014`) và Light (`#ffffff`) sạch sẽ sắc nét theo phong cách v2.33.0 cùng viền mảnh và thanh cuộn gọn gàng qua `web/src/patches/web_optimizations/ui_polish.js`.

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
