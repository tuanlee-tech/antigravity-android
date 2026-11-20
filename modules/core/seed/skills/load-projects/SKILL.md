---
name: load-projects
description: >-
  Quickly scan folders in `/storage/emulated/0/Documents` (or custom directories)
  and register/remove them in Antigravity's Projects sidebar via `agy-projects`.
  Use whenever the user invokes `/load-projects` or asks to add, import, load, or
  manage project folders in the sidebar ("nạp folder vào sidebar", "thêm project
  vào sidebar", "quản lý projects", "add folders to sidebar").
---

# Quick Sidebar Project Loader (`/load-projects`)

Batch-imports existing project folders from `/storage/emulated/0/Documents`
(or another directory) directly into Antigravity's **Projects** sidebar without
manually picking them one by one. Changes are written as `exa.project_pb.Project`
JSON files in `$HOME/.gemini/config/projects/` and appear live via `language_server`'s
file watcher (no app restart required).

---

## Step 1: Scan Unloaded Folders & Currently Loaded Projects

Run **one command** to inspect folders in `/storage/emulated/0/Documents` that
are not yet registered in the sidebar, alongside currently loaded projects:

```bash
echo "=== UNLOADED IN DOCUMENTS ==="
agy-projects scan /storage/emulated/0/Documents 2>&1 || true
echo "=== CURRENTLY LOADED ==="
agy-projects list 2>&1 || true
```

---

## Step 2: Interactive Multi-Select (`ask_question`)

### Case A — Adding Folders to Sidebar (Default)
1. If there are unloaded folders under `/storage/emulated/0/Documents`, present
   them in `ask_question` with `is_multi_select: true`:
   - Label git repos clearly as `<folder-name> [git]` and plain folders as `<folder-name>`.
   - Include optional utility choices at the end:
     - `Scan another folder (e.g. /storage/emulated/0/Download)`
     - *(If loaded projects exist)* `Remove project(s) from sidebar`
2. If `/storage/emulated/0/Documents` has no unloaded folders, inform the user
   which folders are already loaded and offer via `ask_question` to:
   - `Scan another folder (e.g. /storage/emulated/0/Download)`
   - `Remove project(s) from sidebar`
3. If the user chooses `Scan another folder`, ask which directory path they want
   to scan (suggest `/storage/emulated/0/Download` and `/storage/emulated/0`),
   run `agy-projects scan "<DIR>"`, and present the results with `ask_question(is_multi_select: true)`.

### Case B — Removing Projects from Sidebar
If the user asks to remove/unlink projects from the sidebar:
1. Present currently loaded projects from `agy-projects list` via `ask_question`
   with `is_multi_select: true`.
2. Explicitly clarify that removing from the sidebar **only unlinks the sidebar
   entry and does NOT delete any files on disk**.

---

## Step 3: Apply Selection in One Command

- **Add selected folders:**
  ```bash
  agy-projects add "<dir1>" "<dir2>" ...
  agy-projects list
  ```
- **Remove selected projects:**
  ```bash
  agy-projects remove "<id_or_path_1>" "<id_or_path_2>" ...
  agy-projects list
  ```

---

## Step 4: Confirm Live Update

Report the exact projects added (or removed) along with their filesystem paths,
and let the user know they will appear in the **Projects** sidebar automatically
(no restart needed).
