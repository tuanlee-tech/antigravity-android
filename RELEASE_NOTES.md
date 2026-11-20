# Antigravity cho Android v2.4.2

## Danh sách gói phát hành

| Tệp gói | Mã ứng dụng | Cổng runtime | Mô tả |
| :--- | :--- | :--- | :--- |
| **`antigravity.apk`** | `com.agy` | 45157 | Bản tiêu chuẩn giữ nguyên cơ chế kiểm tra tài khoản của Google. |
| **`antigravity-region-bypass.apk`** | `com.agy` | 45157 | Bản bỏ qua kiểm tra điều kiện tài khoản/vùng (yêu cầu VPN/tunnel nếu cần). |
| **`SHA256SUMS.txt`** | — | — | Mã kiểm tra toàn vẹn SHA-256 cho các tệp APK. |

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
