---
name: github-clone
description: >-
  Interactive GitHub repository browser & cloner using `gh` CLI and `ask_question`
  for Antigravity for Android. Use whenever the user invokes `/github-clone`, asks
  to browse/list/pick/clone repositories from their GitHub account or organization
  ("clone github", "duyệt repo github", "chọn repo để clone", "tải repo từ github").
---

# Interactive GitHub Repository Browser & Cloner (`/github-clone`)

Provides a touch-friendly `ask_question` flow (inspired by `/grill-me`) so the
user can browse, filter, and clone GitHub repositories into
`/storage/emulated/0/Documents/<repo>` and automatically add them to the
Projects sidebar without typing long URLs or paths on mobile.

---

## Step 1: Fast Tool & Auth Check (Single Command)

Check `git`, `gh`, and current GitHub login status in one call:
```bash
command -v git >/dev/null 2>&1 || { echo "NEED_GIT"; exit 0; }
command -v gh >/dev/null 2>&1  || { echo "NEED_GH"; exit 0; }
GODEBUG=netdns=cgo gh auth status 2>&1 || echo "NEED_AUTH"
```

- If `NEED_GIT` or `NEED_GH`, offer via `ask_question` to install missing packages right now (`pkg update && pkg install ...` + `agy-fix-paths`) or hand off to `/android-setup`.
- If `NEED_AUTH`, use the headless device login flow (background `printf "\n" | GODEBUG=netdns=cgo gh auth login --hostname github.com -p https --web --insecure-storage`, extract the 8-char code, pin via `agy-notify post -t "GitHub Auth Code" -m "Code: <CODE>" --tag gh-auth`, share `https://github.com/login/device`, wait passively for completion, then `agy-notify cancel gh-auth`).

---

## Step 2: Fetch Recent Repositories

Run a single command to list the user's top 10 recently updated repositories:
```bash
GODEBUG=netdns=cgo gh repo list --limit 10 \
  --json nameWithOwner,isPrivate,defaultBranchRef \
  --jq '.[] | "\(.nameWithOwner) [\(.defaultBranchRef.name // "main") • \(if .isPrivate then "Private" else "Public" end)]"'
```

---

## Step 3: Interactive Selection (`ask_question` Grill-Me Flow)

Present the repositories in `ask_question`, appending two discovery actions:
- `<owner>/<repo> [<branch> • Private|Public]` (top repos, most recently pushed first)
- `Search / Filter by keyword or organization`
- `Enter a custom GitHub URL`

### Branch Handling
- **If user picks `Search / Filter by keyword or organization`:**
  1. Ask for the keyword or GitHub org/owner name (e.g. `org:my-team` or `react`).
  2. Query GitHub in one command:
     - If an org/owner was given (e.g. `org:<name>` or single org slug):
       ```bash
       GODEBUG=netdns=cgo gh repo list "<ORG>" --limit 10 \
         --json nameWithOwner,isPrivate,defaultBranchRef \
         --jq '.[] | "\(.nameWithOwner) [\(.defaultBranchRef.name // "main") • \(if .isPrivate then "Private" else "Public" end)]"'
       ```
     - If a keyword was given:
       ```bash
       GODEBUG=netdns=cgo gh repo list --limit 40 \
         --json nameWithOwner,isPrivate,defaultBranchRef \
         --jq '.[] | "\(.nameWithOwner) [\(.defaultBranchRef.name // "main") • \(if .isPrivate then "Private" else "Public" end)]"' \
         | grep -iF "<KEYWORD>" | head -n 10 || true
       ```
       *(If empty, also try `GODEBUG=netdns=cgo gh search repos "<KEYWORD>" --limit 10 --json fullName,isPrivate --jq '.[] | "\(.fullName) [\(if .isPrivate then "Private" else "Public" end)]"'`.)*
  3. Present the filtered matches with `ask_question` so the user can tap one.
- **If user picks `Enter a custom GitHub URL` or types a custom answer:** accept `https://github.com/...` or `<owner>/<repo>`.

---

## Step 4: Clone / Sync into `Documents` & Register in Sidebar

Run a **single command** that:
1. Ensures `~/.git-credentials` is synced from `gh auth token` (`chmod 600`) and Git TLS CA / `safe.directory '*'` / `credential.helper store` are set.
2. Clones into `/storage/emulated/0/Documents/<repo-name>` — or if `<TARGET>/.git` already exists, runs `git fetch -v` and `git status` instead of failing.
3. Adds the repository immediately to the Projects sidebar with `agy-projects add "$TARGET"`.

```bash
set -e
DOCS="/storage/emulated/0/Documents"
mkdir -p "$DOCS"
TARGET="$DOCS/<repo-name>"
REPO_URL="https://github.com/<owner>/<repo>.git"

if [ ! -s "$HOME/.git-credentials" ] || ! grep -q '@github.com' "$HOME/.git-credentials" 2>/dev/null; then
  USER_LOGIN="$(GODEBUG=netdns=cgo gh api user --jq .login)"
  TOKEN="$(GODEBUG=netdns=cgo gh auth token)"
  printf 'https://%s:%s@github.com\n' "$USER_LOGIN" "$TOKEN" > "$HOME/.git-credentials"
  chmod 600 "$HOME/.git-credentials"
fi

GIT_CONFIG_NOSYSTEM=1 git config --global http.sslCAInfo "$PREFIX/etc/tls/cert.pem"
GIT_CONFIG_NOSYSTEM=1 git config --global --add safe.directory '*'
GIT_CONFIG_NOSYSTEM=1 git config --global credential.helper store

if [ -d "$TARGET/.git" ]; then
  GIT_CONFIG_NOSYSTEM=1 GIT_TERMINAL_PROMPT=0 git -C "$TARGET" fetch -v
  GIT_CONFIG_NOSYSTEM=1 git -C "$TARGET" status
else
  GIT_CONFIG_NOSYSTEM=1 GIT_TERMINAL_PROMPT=0 git clone "$REPO_URL" "$TARGET"
  GIT_CONFIG_NOSYSTEM=1 git -C "$TARGET" status
fi

command -v agy-projects >/dev/null 2>&1 && agy-projects add "$TARGET" || true
```

---

## Step 5: Report Result

Summarize clearly:
- Target path: `/storage/emulated/0/Documents/<repo-name>`
- Current branch & working tree status
- Confirmation that the folder is now registered in the **Projects** sidebar
- Reminder: `/storage/emulated/0` is mounted `noexec`; invoke scripts via `bash`, `python3`, or `node`.
