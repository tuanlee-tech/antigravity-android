#!/usr/bin/env python3
"""
web/patch_main.py - Dynamic patcher for web/main.js (applied to staged copy).

Patches:
1. Appearance settings: injects Desktop Mode toggle and UI Scale selector.
2. Folder picker (New Project / Open Folder): defaults initialPath to
   /storage/emulated/0/Documents (with /storage/emulated/0 fallback) and
   adds a touch-friendly quick-jump bar (Documents, Downloads, Storage,
   System Picker).

Safe, idempotent, and resilient to obfuscated identifier changes.
"""

import os
import re
import sys


def _patch_appearance(content: str) -> tuple[str, bool]:
    if "Desktop Mode" in content and "UI Scale" in content:
        print("[web-patch] Desktop Mode and UI Scale already present in main.js")
        return content, True

    hook_pattern = re.compile(
        r'(var\s+[A-Za-z0-9_$]+=[A-Za-z0-9_$]+!==3,\s*[A-Za-z0-9_$]+=[A-Za-z0-9_$]+!==2,\s*[A-Za-z0-9_$]+=[A-Za-z0-9_$]+=>\s*[A-Za-z0-9_$]+=>\{.*?\};)'
    )
    hook_match = hook_pattern.search(content)

    theme_idx = content.find('{label:"Theme"')
    theme_match = None
    react_id = "A"
    theme_comp = "YKb"
    if theme_idx != -1:
        window_start = max(0, theme_idx - 100)
        window_end = min(len(content), theme_idx + 500)
        window_text = content[window_start:window_end]
        theme_pattern = re.compile(
            r'([A-Za-z0-9_$]+)\.createElement\(([A-Za-z0-9_$]+),\{label:"Theme",.*?onSelect:[A-Za-z0-9_$]+\}\)'
        )
        theme_match = theme_pattern.search(window_text)
        if theme_match:
            react_id = theme_match.group(1)
            theme_comp = theme_match.group(2)

    if not hook_match or not theme_match:
        print(
            f"[web-patch] appearance patterns not matched: hook={bool(hook_match)}, theme={bool(theme_match)}",
            file=sys.stderr,
        )
        return content, False

    orig_hook = hook_match.group(1)
    injected_hook = (
        f"var [isDesktop,setIsDesktop]=(0,{react_id}.useState)(()=>{{"
        "try{if(window.Android&&typeof window.Android.isDesktopMode==='function')return window.Android.isDesktopMode();"
        "var s=localStorage.getItem('ag_desktop_mode');return s===null?!1:s==='true'}catch(_){return !1}});"
        f"var onDesktopToggle=(0,{react_id}.useCallback)(v=>{{"
        "setIsDesktop(v);try{localStorage.setItem('ag_desktop_mode',String(v));"
        "if(window.Android&&typeof window.Android.setDesktopMode==='function')window.Android.setDesktopMode(v);"
        "if(typeof window.__agApplyUiScale==='function')window.__agApplyUiScale()}catch(_){}},[]);"
        f"var [uiScale,setUiScale]=(0,{react_id}.useState)(()=>{{"
        "try{if(typeof window.__agGetUiScale==='function')return window.__agGetUiScale();"
        "var u=parseInt(localStorage.getItem('ag_ui_scale')||'115',10);return u>=100&&u<=150?u:115}catch(_){return 115}});"
        f"var onUiScaleSelect=(0,{react_id}.useCallback)(v=>{{"
        "setUiScale(v);try{localStorage.setItem('ag_ui_scale',String(v));"
        "if(typeof window.__agApplyUiScale==='function')window.__agApplyUiScale(v)}catch(_){}},[]);"
        + orig_hook
    )

    item_comp = "v3"
    switch_comp = "dX"
    re_idx = content.find("rightElement:")
    if re_idx != -1:
        re_window = content[max(0, re_idx - 150):min(len(content), re_idx + 150)]
        item_m = re.search(
            r'([A-Za-z0-9_$]+)\.createElement\(([A-Za-z0-9_$]+),\{label:[^,]+,description:[^,]+,rightElement:',
            re_window,
        )
        if item_m:
            item_comp = item_m.group(2)
        sw_m = re.search(r'rightElement:[A-Za-z0-9_$]+\.createElement\(([A-Za-z0-9_$]+),\{checked:', re_window)
        if sw_m:
            switch_comp = sw_m.group(1)

    orig_theme = theme_match.group(0)
    injected_theme = (
        orig_theme
        + f',{react_id}.createElement({item_comp},{{label:"Desktop Mode",description:"Use desktop viewport and user agent for the workspace",'
        + f'rightElement:{react_id}.createElement({switch_comp},{{checked:isDesktop,onCheckedChange:onDesktopToggle}})}})'
        + f',{react_id}.createElement({theme_comp},{{label:"UI Scale",value:uiScale,options:[100,115,125,140],'
        + 'resolveOptionToDescription:v=>v===115?"115% (Mobile)":v+"%",onSelect:onUiScaleSelect})'
    )

    content = content.replace(orig_hook, injected_hook, 1)
    content = content.replace(orig_theme, injected_theme, 1)
    print("[web-patch] Successfully patched Desktop Mode toggle and UI Scale selector into main.js")
    return content, True


def _patch_folder_picker(content: str) -> tuple[str, bool]:
    if "agy-folder-shortcuts" in content and "/storage/emulated/0/Documents" in content:
        print("[web-patch] Folder picker Documents default & quick-jump bar already present in main.js")
        return content, True

    # 1. Default initialPath in interactive folder picker feature (wqc) to /storage/emulated/0/Documents
    init_pattern = re.compile(
        r'(label:"Open Folder \(Interactive\)",id:"add-folder"[\s\S]{0,220}?initialPath:)([A-Za-z0-9_$]+)\?([A-Za-z0-9_$]+)\.fsPath:[A-Za-z0-9_$]+\?"C:/":"/"'
    )
    init_match = init_pattern.search(content)
    if not init_match:
        print("[web-patch] folder picker initialPath pattern not matched", file=sys.stderr)
        return content, False

    b_var = init_match.group(2)
    initial_expr = (
        f'((u)=>{{var p=typeof u==="string"?u.replace(/^file:\\/\\//,""):u?.fsPath;'
        f'return p&&!p.startsWith("/data/")?p:"/storage/emulated/0/Documents"}})({b_var})'
    )
    content = (
        content[:init_match.start()]
        + init_match.group(1)
        + initial_expr
        + content[init_match.end():]
    )

    # 2. Fallback in iqb when /storage/emulated/0/Documents read fails + quick-jump bar in $K title
    iqb_head_pattern = re.compile(
        r'var\s+([A-Za-z0-9_$]+)=\(\{isOpen:([A-Za-z0-9_$]+),initialPath:([A-Za-z0-9_$]+),'
        r'onSelectFolder:([A-Za-z0-9_$]+),onClose:([A-Za-z0-9_$]+),fetchDirectoryContents:([A-Za-z0-9_$]+),'
        r'statPath:([A-Za-z0-9_$]+),createDirectory:([A-Za-z0-9_$]+)\}\)=>'
        r'\{var\s+\[([A-Za-z0-9_$]+),([A-Za-z0-9_$]+)\]=\(0,([A-Za-z0-9_$]+)\.useState\)\(\3\),'
        r'\[([A-Za-z0-9_$]+),([A-Za-z0-9_$]+)\]=\(0,\11\.useState\)\([A-Za-z0-9_$]+\(\3\)\)'
    )
    iqb_match = iqb_head_pattern.search(content)
    if not iqb_match:
        print("[web-patch] folder picker component (iqb) signature not matched", file=sys.stderr)
        return content, False

    on_select = iqb_match.group(4)
    on_close = iqb_match.group(5)
    path_var = iqb_match.group(9)
    set_path_var = iqb_match.group(10)
    react_id = iqb_match.group(11)
    set_input_var = iqb_match.group(13)

    iqb_start = iqb_match.start()
    iqb_window = content[iqb_start:iqb_start + 5200]

    # Patch fallback on read error when Documents doesn't exist
    catch_pattern = re.compile(
        r'\.catch\(([A-Za-z0-9_$]+)=>\{([A-Za-z0-9_$]+)&&\(([A-Za-z0-9_$]+)\("directory_read_failed",\1 instanceof Error\?`Failed to fetch directory contents: \$\{\1\.message\}`:\s*\1\),([A-Za-z0-9_$]+)\(\[\]\),([A-Za-z0-9_$]+)\(!1\)\)\}\)'
    )
    catch_match = catch_pattern.search(iqb_window)
    if not catch_match:
        print("[web-patch] folder picker catch block not matched", file=sys.stderr)
        return content, False

    err_v, guard_v, rec_v, set_items_v, set_load_v = catch_match.groups()
    catch_repl = (
        f'.catch({err_v}=>{{{guard_v}&&({path_var}==="/storage/emulated/0/Documents"?'
        f'({set_path_var}("/storage/emulated/0"),{set_input_var}("/storage/emulated/0/")):'
        f'({rec_v}("directory_read_failed",{err_v} instanceof Error?`Failed to fetch directory contents: ${{{err_v}.message}}`:{err_v}),'
        f'{set_items_v}([]),{set_load_v}(!1)))}})'
    )
    iqb_window = iqb_window[:catch_match.start()] + catch_repl + iqb_window[catch_match.end():]

    # Locate E (nav callback) and QuickPick render inside iqb_window
    nav_match = re.search(
        rf'var\s+([A-Za-z0-9_$]+)=\(0,{re.escape(react_id)}\.useCallback\)\(([A-Za-z0-9_$]+)=>\{{{re.escape(set_path_var)}\(\2\);{re.escape(set_input_var)}\([A-Za-z0-9_$]+\(\2\)\)\}},\[\]\)',
        iqb_window,
    )
    if not nav_match:
        print("[web-patch] folder picker nav callback not matched", file=sys.stderr)
        return content, False
    nav_fn = nav_match.group(1)

    qp_pattern = re.compile(
        rf'({re.escape(react_id)}\.createElement\(([A-Za-z0-9_$]+),\{{items:[A-Za-z0-9_$]+,zClassName:"z-\[5100\]",\s*selectOrClose:[A-Za-z0-9_$]+,inputBoxProps:\{{placeholder:"Type absolute path or navigate folders\.\.\.")'
    )
    qp_match = qp_pattern.search(iqb_window)
    if not qp_match:
        print("[web-patch] folder picker QuickPick render not matched", file=sys.stderr)
        return content, False

    shortcut_bar = (
        f'title:{react_id}.createElement("div",{{className:"agy-folder-shortcuts"}},'
        f'{react_id}.createElement("button",{{type:"button",className:"agy-folder-chip"+({path_var}==="/storage/emulated/0/Documents"||{path_var}.startsWith("/storage/emulated/0/Documents/")?" is-active":""),onMouseDown:ev=>ev.preventDefault(),onClick:()=>{nav_fn}("/storage/emulated/0/Documents")}},"Documents"),'
        f'{react_id}.createElement("button",{{type:"button",className:"agy-folder-chip"+({path_var}==="/storage/emulated/0/Download"||{path_var}.startsWith("/storage/emulated/0/Download/")?" is-active":""),onMouseDown:ev=>ev.preventDefault(),onClick:()=>{nav_fn}("/storage/emulated/0/Download")}},"Downloads"),'
        f'{react_id}.createElement("button",{{type:"button",className:"agy-folder-chip"+({path_var}==="/storage/emulated/0"?" is-active":""),onMouseDown:ev=>ev.preventDefault(),onClick:()=>{nav_fn}("/storage/emulated/0")}},"Storage"),'
        f'typeof window.dialog?.showOpenDialog==="function"&&{react_id}.createElement("button",{{type:"button",className:"agy-folder-chip is-system",onMouseDown:ev=>ev.preventDefault(),onClick:()=>{{window.dialog.showOpenDialog().then(p=>{{if(p){{{on_select}(p);{on_close}()}}}})}}}},"System Picker")'
        f'),'
    )

    qp_prefix = qp_match.group(1)
    marker = 'zClassName:"z-[5100]",'
    qp_replaced = qp_prefix.replace(marker, marker + shortcut_bar, 1)
    iqb_window = iqb_window[:qp_match.start()] + qp_replaced + iqb_window[qp_match.end():]
    content = content[:iqb_start] + iqb_window + content[iqb_start + 5200:]

    print("[web-patch] Successfully patched folder picker Documents default and quick-jump bar into main.js")
    return content, True


def patch_main(file_path):
    if not os.path.isfile(file_path):
        print(f"[web-patch] file not found: {file_path}", file=sys.stderr)
        return False

    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    content, ok_appearance = _patch_appearance(content)
    content, ok_folder = _patch_folder_picker(content)
    if not (ok_appearance and ok_folder):
        return False

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    return True


if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "main.js")
    if not patch_main(path):
        sys.exit(1)
