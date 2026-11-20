<div align="center">

  <p>
    <b>English</b> &nbsp;&bull;&nbsp; <a href="README.vi.md">Tiếng Việt</a>
  </p>

  <br/>

  <img src="docs/assets/logo.png" alt="Antigravity" width="88" height="88" style="border-radius: 20px;" />

  # Antigravity for Android

**Autonomous AI development environment running natively on Android & ARM64.**

  [![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
  [![Arch](https://img.shields.io/badge/ARM64-v8.0--A%2B-blue?style=flat-square)](https://arm.com)
  [![Release](https://img.shields.io/github/v/release/tuanlee-tech/antigravity-android?style=flat-square&color=blueviolet&label=Latest%20Release)](https://github.com/tuanlee-tech/antigravity-android/releases/latest)
  [![License](https://img.shields.io/badge/License-Apache_2.0-orange?style=flat-square)](LICENSE)

  <br/>

  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/hero_dark.png">
    <source media="(prefers-color-scheme: light)" srcset="docs/assets/hero_light.png">
    <img alt="Antigravity Interface" src="docs/assets/hero_dark.png" width="100%" style="border-radius: 12px; border: 1px solid rgba(128,128,128,0.2);" />
  </picture>

</div>

---

### Overview

**Antigravity for Android** brings autonomous agentic coding directly to mobile hardware.

No PRoot, chroot, or virtualization overhead. Antigravity runs **bare-metal inside native Android userspace (Bionic)**, unleashing the full read/write speed of UFS storage and physical CPU cores. Driven by Gemini models, the agent inspects repositories, authors code across multiple files, compiles binaries, and runs tests completely on-device.

---

### Highlights in v2.4

* **Modular Core:** Clean separation into 6 standalone subsystems (`modules/*`) with independent test suites.
* **Native Splash:** Canvas-based dot animation saving ~40 MB RAM with instant startup.
* **Desktop Mode:** Instant toggle in Appearance settings for tablets, external keyboards, and Samsung DeX.
* **Universal ARMv8.0-A Support:** Built-in transparent atomic & SIMD emulation (`liblse_compat.so`) for older CPUs.
* **Background Micro-Daemon:** Ultra-lightweight `agy-daemon` (<10 MB RAM) with cron scheduler, file watcher, and AI task queue.

---

### Key Features

* **Autonomous Agent:** Plans tasks, edits code across project trees, executes shell tools, and fixes build errors.
* **Elevated Control (Shizuku):** Direct ADB-level operations (`uid=2000`) without root for package installs and system diagnostics.
* **Built-in `pkg` Manager:** Fast package manager installing development runtimes (`git`, `python`, `node`, `clang`) from Termux repos.
* **Adaptive Viewports:** Seamless touch-first mobile UI with optional desktop layout.
* **In-Place Upgrades:** Consistent keystore guarantees updating APKs without losing sessions or workspace data.

---

### Quick Start

> [!IMPORTANT]
> Requires a **native ARM64 (`arm64-v8a`) Android device** running Android 7.0+. **x86/x86_64 PC emulators** (MuMuPlayer, BlueStacks, LDPlayer, or x86_64 AVD) relying on ARM64 binary translation (`houdini`/`libndk_translation`) are **not supported** and will crash on startup (`exit code 139`).

1. **Download Latest APK** from [Latest Release](https://github.com/tuanlee-tech/antigravity-android/releases/latest):
   * [`antigravity.apk`](https://github.com/tuanlee-tech/antigravity-android/releases/latest/download/antigravity.apk) — standard release (original Google account checks).
   * [`antigravity-region-bypass.apk`](https://github.com/tuanlee-tech/antigravity-android/releases/latest/download/antigravity-region-bypass.apk) — release with account eligibility check bypass.
2. **Grant Storage Permission** (`All Files Access`). *(Optional: enable Shizuku for ADB access)*.
3. **Install Runtimes** via terminal or simply ask the agent in chat (`/android-setup` or *"install git python nodejs"*):
   ```bash
   pkg update && pkg install git python nodejs
   ```

---

### Built-in Agent Skills & Live Sidebar CLI

Antigravity seeds three interactive skills into `~/.gemini/config/skills/` and ships `agy-projects`:

- **`/android-setup`:** One-shot Bionic toolchain setup (`git`, `python`, `nodejs`, `gh`), automated headless GitHub device auth, `~/.git-credentials` sync, and initial clone.
- **`/github-clone`:** Interactive GitHub repository browser/filter (using `ask_question`) that clones or fetches directly into `/storage/emulated/0/Documents/<repo>` and registers it in the sidebar.
- **`/load-projects`:** Batch-scans `/storage/emulated/0/Documents` (or custom folders) and adds/removes entries live in the **Projects** sidebar via `agy-projects` (`list`, `scan`, `add`, `remove`).

### Cloning Private GitHub Repositories

> [!TIP]
> You don't need to copy these commands manually — just type **`/android-setup`** (to install tools & log in) or **`/github-clone`** (to browse & clone repos), and **`/load-projects`** to load existing folders into the sidebar.

Because non-interactive commands run without terminal prompts (`GIT_TERMINAL_PROMPT=0`), authenticate Git once inside the built-in terminal before cloning or pushing private repositories (choose one method):

1. **GitHub CLI (`gh`) *(Recommended)*:**
   ```bash
   pkg install gh
   GODEBUG=netdns=cgo gh auth login
   ```
   *(v2.4.5+ exports `GODEBUG=netdns=cgo` automatically in shell bridges so Go CLIs like `gh` use Android's system DNS.)*
   Select **`GitHub.com`** &rarr; **`HTTPS`** &rarr; **`Y`** (*Authenticate Git with your GitHub credentials*) &rarr; **`Login with a web browser`** and complete authorization in the browser.

2. **Personal Access Token (PAT):**
   Create a token with `repo` access at <https://github.com/settings/tokens>, then store it:
   ```bash
   git config --global credential.helper store
   printf 'https://<USERNAME>:<TOKEN>@github.com\n' > ~/.git-credentials
   chmod 600 ~/.git-credentials
   ```

3. **SSH Key:**
   ```bash
   pkg install openssh
   ssh-keygen -t ed25519 -C "antigravity-android"
   cat ~/.ssh/id_ed25519.pub
   ```
   Add the printed public key at <https://github.com/settings/keys>, then clone using `git@github.com:<user>/<repo>.git`.

> [!NOTE]
> When cloning into Android shared storage (`/storage/emulated/0/...`), configure the bundled TLS CA bundle and safe directory once if needed:
> ```bash
> git config --global http.sslCAInfo "$HOME/usr/etc/tls/cert.pem"
> git config --global --add safe.directory '*'
> ```

---

### Building & Testing

```bash
# Build standard release APK (dist/antigravity.apk):
bash build.sh

# Build release APK with region bypass (dist/antigravity-region-bypass.apk):
bash build.sh --region-bypass

# Run all test suites:
bash test_all_modules.sh
```

---

### Repository Structure

```text
antigravity/
├── config/             # App constants, environment paths, and AI rules
├── modules/            # Decoupled core modules (v2.4+)
│   ├── core/           # Binary unpacking, glibc loader, shims, SHA-256 manifest
│   ├── patches/        # Binary patch engine (syscall emulation, resolv, SIGILL)
│   ├── io/             # High-performance I/O Bus: event broker, FIFO pipes, sockets
│   ├── env/            # Path resolver, diagnostics, Flight Recorder logs, CLI
│   ├── builder/        # Gradle-free APK builder (javac, D8, aapt, zipalign, apksigner)
│   └── runner/         # Standalone runtime preparation, port probe, E2E tests
├── app/                # Android host app (native splash, WebView, system bridges)
├── web/                # Local web frontend, desktop mode toggle, themes
├── env/                # Bionic userspace, Termux overlay, pkg manager, liblse_compat
├── docs/assets/        # Screenshots, banners, and diagrams
├── build.sh            # Root APK build script
└── test_all_modules.sh # Test suite for all 6 modules
```

> [!TIP]
> Each module in `modules/`, `app/`, `web/`, and `env/` includes its own dedicated `README.md` with in-depth technical documentation.

---

<div align="center">
  <sub>Licensed under the <a href="LICENSE">Apache 2.0 License</a></sub>
</div>
