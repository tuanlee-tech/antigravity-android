---
name: android-setup
description: >-
  Default built-in skill for Antigravity for Android. Use whenever the user asks
  to install/setup dev tools (`pkg update`, `pkg install git python nodejs`, `gh`,
  `openssh`), authenticate GitHub, or clone/push private GitHub repositories on
  Android — or when the user invokes `/android-setup` or asks in Vietnamese
  ("cài môi trường", "cài git python node", "clone repo private", "đăng nhập github").
---

# Android Setup & Private GitHub Helper (Fast Track)

Deterministic, low-latency workflow for setting up the Android Bionic toolchain
(`$PREFIX`, `pkg`) and authenticating/cloning private GitHub repositories with zero
terminal typing required from the user.

## Hard-Earned Android Facts (Do NOT waste turns re-discovering)
- **Go DNS on Android:** Always prefix `gh` commands with `GODEBUG=netdns=cgo` (shell bridges already export it, and keeping it explicit guarantees success in subshells).
- **No Browser Intent from Sandbox:** `am start` is blocked by Android SELinux (`INTERACT_ACROSS_USERS_FULL`). **NEVER** try `am start` or `rish` to open URLs; print a clickable markdown link and send the code via `agy-notify`.
- **Termux `git` ELF Shell Path:** Prebuilt Termux `git` & `git-remote-http` hardcode `/data/data/com.termux/files/usr/bin/sh`. `pkg install` and `agy-fix-paths` patch ELF binaries automatically to `/system/bin/sh` without requiring `python3`; always ensure `agy-fix-paths` has run if any package/script paths were modified.
- **Fastest Git Credential Helper:** Sync `gh auth token` into `~/.git-credentials` (`chmod 600`) with `credential.helper store` instead of `gh auth setup-git` (avoids spawning the Go binary on every `git fetch/push`).
- **Shared Storage `noexec`:** `/storage/emulated/0` is mounted `noexec`; always invoke scripts via `bash`, `python3`, or `node`.
- **Live Sidebar Sync:** Register cloned folders into the Projects sidebar automatically using `agy-projects add "<dir>"`.

---

## Step 0: Determine User Intent

If the user invokes `/android-setup` without specifying a task, use `ask_question`:
1. `(Recommended) Both (Install Tools + GitHub Auth & Clone Repo)`
2. `Install Dev Environment (git, python, nodejs)`
3. `Setup GitHub Auth & Clone Private Repo`

---

## Step 1: One-Shot Bootstrap (Install Missing Only + Patch + Config + Status Check)

Run **a single combined command** tailored to the user's choice:
- Verify `pkg` exists (if missing, inspect `$HOME/logs/env_install.log` — on a fresh app launch the background Bionic installer takes ~10–20s).
- Install **ONLY missing packages** from the requested set (`git python nodejs gh` for Both; `git python nodejs` for Dev Env; `git gh` for GitHub Auth — **never** pass `-y` to `pkg`).
- Run `agy-fix-paths` (patches shebangs + Termux ELF shell paths).
- Configure Git global TLS CA, `safe.directory '*'`, and `credential.helper store`.
- Print installed versions and `gh auth status`.

```bash
set -e
command -v pkg >/dev/null || { cat "$HOME/logs/env_install.log" 2>&1 || true; exit 1; }

# Adjust checked tools if user only asked for Dev Env (omit gh) or GitHub Auth (git + gh)
MISSING=""
command -v git >/dev/null 2>&1     || MISSING="$MISSING git"
command -v python3 >/dev/null 2>&1 || MISSING="$MISSING python"
command -v node >/dev/null 2>&1    || MISSING="$MISSING nodejs"
command -v gh >/dev/null 2>&1      || MISSING="$MISSING gh"

if [ -n "$MISSING" ]; then
  pkg update && pkg install $MISSING
fi

command -v agy-fix-paths >/dev/null 2>&1 && agy-fix-paths || true

GIT_CONFIG_NOSYSTEM=1 git config --global http.sslCAInfo "$PREFIX/etc/tls/cert.pem"
GIT_CONFIG_NOSYSTEM=1 git config --global --add safe.directory '*'
GIT_CONFIG_NOSYSTEM=1 git config --global credential.helper store

git --version
command -v python3 >/dev/null 2>&1 && python3 --version || true
command -v node >/dev/null 2>&1 && node -v || true
command -v gh >/dev/null 2>&1 && GODEBUG=netdns=cgo gh --version || true
command -v gh >/dev/null 2>&1 && GODEBUG=netdns=cgo gh auth status 2>&1 || true
```

If the user only requested **Install Dev Environment**, summarize the installed versions and stop here.

---

## Step 2: Automated Headless GitHub Login (If Not Logged In)

Do **NOT** ask the user to open the built-in terminal. Run the device flow directly:

1. Start `gh auth login` in background (set `WaitMsBeforeAsync: 4000` so it detaches into a background task while waiting for browser approval):
   ```bash
   printf "\n" | GODEBUG=netdns=cgo gh auth login --hostname github.com -p https --web --insecure-storage
   ```
2. Read the background task output log to extract the 8-character one-time code (`XXXX-XXXX`).
3. Pin the code to Android's status bar notification for easy copy:
   ```bash
   agy-notify post -t "GitHub Auth Code" -m "Code: <CODE>" --tag gh-auth 2>&1 || true
   ```
4. Reply to the user with:
   - The bold code **`<CODE>`**
   - Clickable link: `https://github.com/login/device`
   - Stop calling tools and wait passively for the background task completion notification (do **NOT** poll in a loop).

5. Once the background task completes with exit code `0` (or if already logged in from Step 1), sync the token into `~/.git-credentials`, cancel the notification, and list recent repos in **one command**:
   ```bash
   USER_LOGIN="$(GODEBUG=netdns=cgo gh api user --jq .login)"
   TOKEN="$(GODEBUG=netdns=cgo gh auth token)"
   printf 'https://%s:%s@github.com\n' "$USER_LOGIN" "$TOKEN" > "$HOME/.git-credentials"
   chmod 600 "$HOME/.git-credentials"
   GIT_CONFIG_NOSYSTEM=1 git config --global --unset-all credential.https://github.com.helper 2>/dev/null || true
   GIT_CONFIG_NOSYSTEM=1 git config --global --unset-all credential.https://gist.github.com.helper 2>/dev/null || true
   agy-notify cancel gh-auth 2>&1 || true
   GODEBUG=netdns=cgo gh repo list "$USER_LOGIN" --limit 8 --json nameWithOwner,isPrivate --jq '.[] | "\(.nameWithOwner) (\(if .isPrivate then "Private" else "Public" end))"'
   ```

*(Only if the user explicitly requests PAT or SSH Key instead of `gh`, store the PAT in `~/.git-credentials` via terminal or install `openssh` + generate `~/.ssh/id_ed25519`.)*

---

## Step 3: Clone / Sync Repository & Add to Sidebar

1. Use `ask_question` presenting the user's recent repos from Step 2 (the built-in write-in option lets them enter any custom URL if needed).
2. Clone to `/storage/emulated/0/Documents/<repo-name>` (or `git fetch` + `git status` if `.git` already exists) and register in the Projects sidebar in **one command**:
   ```bash
   mkdir -p "/storage/emulated/0/Documents"
   TARGET="/storage/emulated/0/Documents/<repo-name>"
   REPO_URL="https://github.com/<owner>/<repo>.git"
   if [ -d "$TARGET/.git" ]; then
     GIT_CONFIG_NOSYSTEM=1 GIT_TERMINAL_PROMPT=0 git -C "$TARGET" fetch -v
     GIT_CONFIG_NOSYSTEM=1 git -C "$TARGET" status
   else
     GIT_CONFIG_NOSYSTEM=1 GIT_TERMINAL_PROMPT=0 git clone "$REPO_URL" "$TARGET"
     GIT_CONFIG_NOSYSTEM=1 git -C "$TARGET" status
   fi
   command -v agy-projects >/dev/null 2>&1 && agy-projects add "$TARGET" || true
   ```
3. Report completion clearly, remind the user about `/storage/emulated/0` `noexec` (`bash` / `python3` / `node`), and mention `/github-clone` (browse & clone repos anytime) and `/load-projects` (batch-add existing folders to the Projects sidebar).
