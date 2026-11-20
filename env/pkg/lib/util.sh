#!/data/data/com.agy/files/usr/bin/bash
# util.sh -- shared helpers for pkg.

PKG_VERSION="0.1.0"

: "${PREFIX:?PREFIX must be exported by the AGY shell profile or pkg wrapper}"
PKG_ROOT="${PKG_ROOT:-$PREFIX/var/lib/pkg}"
PKG_DB="$PKG_ROOT/installed"
PKG_CACHE="$PKG_ROOT/cache"
PKG_INDEX="$PKG_ROOT/index"
PKG_ETC="$PREFIX/etc/pkg"

# TLS trust: the overlay ships the bundle at $PREFIX/etc/tls/cert.pem. Termux's
# curl is compiled with a /data/data/com.termux/... default, so point it at ours.
PKG_CERT="$PREFIX/etc/tls/cert.pem"
export SSL_CERT_FILE="$PKG_CERT"
export CURL_CA_BUNDLE="$PKG_CERT"
export OPENSSL_CONF="${OPENSSL_CONF:-$PREFIX/etc/tls/openssl.cnf}"

log()  { printf 'pkg: %s\n' "$*" >&2; }
warn() { printf 'pkg: warn: %s\n' "$*" >&2; }
die()  { printf 'pkg: error: %s\n' "$*" >&2; exit 1; }

ensure_dirs() { mkdir -p "$PKG_DB" "$PKG_CACHE" "$PKG_INDEX" "$PKG_ROOT/tmp"; }

# rewrite_prefix [FILES_OR_DIRS...] -- rewrite legacy /data/data and modern
# /data/user/<id> app-private paths to the active $PREFIX. Every occurrence is
# replaced, not only the shebang, so helper bodies and generated .list files
# work after extraction. Text-only, no symlink traversal, idempotent.
# Accepts file/dir paths as arguments, or list of file paths via stdin (newline
# or null-separated). If no targets are provided or stdin is empty, scans $PREFIX.
rewrite_prefix() {
    local f esc pattern='/data/(data|user/[0-9]+)/[^/]*/files/usr'
    # Escape sed-replacement metacharacters in $PREFIX (never expected, cheap).
    esc="$(printf '%s' "$PREFIX" | sed 's/[\\&@]/\\&/g')"

    local targets=("$@")
    if [ "${#targets[@]}" -eq 0 ]; then
        if [ ! -t 0 ]; then
            # Read targets from stdin
            local item
            while IFS= read -r item; do
                [ -n "$item" ] && targets+=("$item")
            done
        fi
    fi

    # Fall back to $PREFIX if no arguments and stdin was empty
    if [ "${#targets[@]}" -eq 0 ]; then
        targets=("$PREFIX")
    fi

    # Separate existing regular files from directories/other
    local files=() dirs=() t
    for t in "${targets[@]}"; do
        if [ -f "$t" ] && [ ! -L "$t" ]; then
            files+=("$t")
        elif [ -d "$t" ]; then
            dirs+=("$t")
        elif [ -e "$t" ]; then
            # Symlink or special file, skip
            continue
        fi
    done

    # 1. Process explicit regular files directly
    if [ "${#files[@]}" -gt 0 ]; then
        grep -IlZ -E -e "$pattern" -- "${files[@]}" 2>/dev/null \
            | while IFS= read -r -d '' f; do
                sed -i -E "s@$pattern@$esc@g" "$f" 2>/dev/null || true
            done
    fi

    # 2. Process directories recursively
    if [ "${#dirs[@]}" -gt 0 ]; then
        grep -rIlZ -E -e "$pattern" -- "${dirs[@]}" 2>/dev/null \
            | while IFS= read -r -d '' f; do
                sed -i -E "s@$pattern@$esc@g" "$f" 2>/dev/null || true
            done
    fi
}

# patch_elf_shell [FILES_OR_DIRS...] -- patch Termux's hardcoded SHELL_PATH
# (/data/data/com.termux/files/usr/bin/sh\0, 39 bytes) inside ELF binaries
# in-place to NUL-padded /system/bin/sh\0 (39 bytes). Pure Bash + grep + dd,
# zero Python dependency. Accepts paths as args or newline-separated via stdin.
patch_elf_shell() {
    local old_str="/data/data/com.termux/files/usr/bin/sh"
    local old_len=39
    local sys_sh="/system/bin/sh"
    local pad_len=25
    local targets=("$@")

    if [ "${#targets[@]}" -eq 0 ] && [ ! -t 0 ]; then
        local item
        while IFS= read -r item; do
            [ -n "$item" ] && targets+=("$item")
        done
    fi
    if [ "${#targets[@]}" -eq 0 ]; then
        targets=("$PREFIX/bin" "$PREFIX/libexec/git-core")
    fi

    local files=() t f
    for t in "${targets[@]}"; do
        if [ -f "$t" ] && [ ! -L "$t" ]; then
            files+=("$t")
        elif [ -d "$t" ]; then
            while IFS= read -r -d '' f; do
                [ -n "$f" ] && files+=("$f")
            done < <(find "$t" -type f 2>/dev/null | tr '\n' '\0')
        fi
    done

    local elf_magic=$'\x7fELF'
    for f in "${files[@]}"; do
        [ -f "$f" ] && [ ! -L "$f" ] || continue
        local magic
        magic="$(LC_ALL=C dd if="$f" bs=4 count=1 2>/dev/null)" || continue
        [ "$magic" = "$elf_magic" ] || continue

        local offsets
        offsets="$(LC_ALL=C grep -aobF "$old_str" "$f" 2>/dev/null | cut -d: -f1)" || continue
        [ -n "$offsets" ] || continue

        local size_before
        size_before="$(wc -c < "$f" | tr -d ' ')"
        [ -n "$size_before" ] && [ "$size_before" -ge "$old_len" ] || continue

        local tmp="$f.agyelf.$$"
        cp -f "$f" "$tmp" 2>/dev/null || { rm -f "$tmp"; continue; }

        local off patched_any=0
        while IFS= read -r off; do
            [ -n "$off" ] || continue
            [ $((off + old_len)) -le "$size_before" ] || continue
            local nul_check
            nul_check="$(LC_ALL=C dd if="$tmp" bs=1 skip=$((off + 38)) count=1 2>/dev/null | tr -d '\000' | wc -c | tr -d ' ')"
            [ "$nul_check" = "0" ] || continue
            {
                printf '%s' "$sys_sh"
                dd if=/dev/zero bs=1 count="$pad_len" 2>/dev/null
            } | dd of="$tmp" bs=1 seek="$off" conv=notrunc 2>/dev/null || continue
            patched_any=1
        done <<< "$offsets"

        if [ "$patched_any" -eq 1 ]; then
            local size_after
            size_after="$(wc -c < "$tmp" | tr -d ' ')"
            if [ "$size_after" = "$size_before" ]; then
                local mode
                mode="$(stat -c '%a' "$f" 2>/dev/null)" || mode=""
                [ -n "$mode" ] && chmod "$mode" "$tmp" 2>/dev/null || true
                mv -f "$tmp" "$f" 2>/dev/null || rm -f "$tmp"
                continue
            fi
        fi
        rm -f "$tmp"
    done
}


load_conf() {
    [ -f "$PKG_ETC/repo.conf" ] && . "$PKG_ETC/repo.conf"
    : "${PKG_REPOS:=https://packages.termux.dev/apt/termux-main}"
    : "${PKG_CHANNEL:=stable}"
    : "${PKG_COMPONENT:=main}"
    : "${PKG_ARCH:=aarch64}"
    # data.tar paths are ./data/data/com.<id>/files/usr/... -> 6 components.
    # deb_files strips by component count, not by a hardcoded prefix string.
    : "${PKG_DEB_STRIP:=6}"
}
