---
name: android-setup
description: >-
  Default built-in skill for Antigravity for Android. Use whenever the user asks
  to install/setup dev tools (`pkg update`, `pkg install git python nodejs`, `gh`,
  `openssh`), authenticate GitHub, or clone/push private GitHub repositories on
  Android — or when the user invokes `/android-setup` or asks in Vietnamese
  ("cài môi trường", "cài git python node", "clone repo private", "đăng nhập github").
---

# Android Setup & Private GitHub Helper

Interactive guide for setting up the Android Bionic toolchain (`$HOME/usr`, `pkg`)
and cloning/authenticating private GitHub repositories without manual copy-pasting.

## Step 0: Determine User Intent

If the user invokes `/android-setup` without specifying a task, use `ask_question`
to ask what they want to do:
1. **Install Dev Environment** (`git`, `python`, `nodejs`)
2. **Setup GitHub Auth & Clone Private Repo**
3. **Both (Install Tools + Clone Private Repo)**

---

## Task A: Install Dev Environment (`pkg`)

1. **Verify `pkg` is ready**:
   ```bash
   which pkg && ls -la "$HOME/usr/bin/pkg"
   ```
   - If `pkg` is missing, check `$HOME/logs/env_install.log`:
     ```bash
     cat "$HOME/logs/env_install.log" 2>&1 || true
     ```
   - On a fresh app launch, the background Bionic installer takes ~10–20 seconds.
     Wait briefly and re-check, or report the error in `env_install.log`.

2. **Install requested packages** (do **NOT** pass `-y` to `pkg`):
   ```bash
   pkg update && pkg install git python nodejs
   ```
   *(Add any extra packages the user asked for, such as `gh`, `openssh`, `clang`, `curl`.)*

3. **Fix shebangs & verify binaries**:
   ```bash
   command -v agy-fix-paths >/dev/null 2>&1 && agy-fix-paths || true
   git --version && python3 --version && node -v
   ```

---

## Task B: Authenticate GitHub & Clone Private Repository

1. **Ensure `git` is installed**:
   If `which git` fails, run `pkg update && pkg install git` first.

2. **Configure TLS CA & safe.directory for Android shared storage** (run once):
   ```bash
   GIT_CONFIG_NOSYSTEM=1 git config --global http.sslCAInfo "$HOME/usr/etc/tls/cert.pem"
   GIT_CONFIG_NOSYSTEM=1 git config --global --add safe.directory '*'
   ```

3. **Check existing GitHub authentication** (always pass `GODEBUG=netdns=cgo` for Go-based Bionic CLIs like `gh` so they use Android's system DNS instead of `[::1]:53`):
   ```bash
   GODEBUG=netdns=cgo gh auth status 2>&1 || true
   ls -la ~/.git-credentials ~/.ssh/id_ed25519 2>&1 || true
   ```

4. **If not authenticated**, ask the user via `ask_question` which login method
   they prefer:
   - **GitHub CLI (`gh`) (Recommended)**:
     1. Install `gh`: `pkg install gh`
     2. Instruct the user to run `GODEBUG=netdns=cgo gh auth login` in the built-in terminal and choose:
        `GitHub.com` → `HTTPS` → `Y` (*Authenticate Git with your GitHub credentials*) → `Login with a web browser`, then complete browser login and reply when done.
     3. Verify with `GODEBUG=netdns=cgo gh auth status`.
   - **Personal Access Token (PAT)**:
     1. Ask the user to create a token with `repo` scope at `https://github.com/settings/tokens`
        and run the following inside the built-in terminal (**never** ask them to paste raw tokens into chat or embed tokens inside `.git/config`):
        ```bash
        git config --global credential.helper store
        printf 'https://<USERNAME>:<TOKEN>@github.com\n' > ~/.git-credentials
        chmod 600 ~/.git-credentials
        ```
   - **SSH Key**:
     1. Install OpenSSH and generate a key if missing:
        ```bash
        pkg install openssh
        [ -f ~/.ssh/id_ed25519 ] || ssh-keygen -t ed25519 -N "" -C "antigravity-android" -f ~/.ssh/id_ed25519
        cat ~/.ssh/id_ed25519.pub
        ```
     2. Show the printed public key and ask the user to add it at `https://github.com/settings/keys`, then verify with `ssh -T -o StrictHostKeyChecking=no git@github.com`.
     3. Clone using the SSH URL (`git@github.com:<owner>/<repo>.git`).

5. **Clone the repository**:
   - Ask for the repository URL (and destination folder; default to `/storage/emulated/0/Documents/<repo-name>`).
   - Run:
     ```bash
     GIT_CONFIG_NOSYSTEM=1 GIT_TERMINAL_PROMPT=0 git clone "<REPO_URL>" "<TARGET_DIR>"
     ```
   - Verify with `git -C "<TARGET_DIR>" status` and `ls -la "<TARGET_DIR>"`.
   - **Reminder**: `/storage/emulated/0` is mounted `noexec` on Android; invoke scripts explicitly via `bash script.sh` / `python3 script.py` / `node script.js`.
