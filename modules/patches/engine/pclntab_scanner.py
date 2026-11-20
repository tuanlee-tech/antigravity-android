"""
pclntab_scanner.py - Resolves Go runtime functions via pclntab and encodes AArch64 MOVZ instructions.
"""
import struct
from typing import Optional, Tuple, Dict, Any, Union, Callable

import elf_scanner

PCLN_MAGICS = (b"\xf1\xff\xff\xff", b"\xf2\xff\xff\xff", b"\xf0\xff\xff\xff")
R_AARCH64_RELATIVE = 1027

def encode_movz(reg: int, imm: int) -> bytes:
    """Encode AArch64 MOVZ X<reg>, #imm"""
    return struct.pack("<I", 0xD2800000 | ((imm & 0xFFFF) << 5) | (reg & 0x1F))

def encode_movz_x0(imm: int) -> bytes:
    """Encode AArch64 MOVZ X0, #imm"""
    return encode_movz(0, imm)

def _resolve_pie_text_offset(
    data: Union[bytes, bytearray, memoryview],
    base: int,
    pcln_base: int,
    text_vaddr: int,
    text_rel_off: int,
    text_sz: int,
) -> Optional[int]:
    """Resolve Go 1.20+ PIE runtime.text offset via moduledata relocations in .rela.dyn."""
    pcln_vaddr = elf_scanner.offset_to_vaddr(data, base, pcln_base - base)
    rela_sec = elf_scanner.get_section_by_name(data, base, b".rela.dyn")
    if pcln_vaddr is None or rela_sec is None:
        return None

    _, rela_rel_off, rela_sz = rela_sec
    rela_start = base + rela_rel_off
    rela_end = rela_start + rela_sz
    needle = struct.pack("<Q", pcln_vaddr)
    pos = rela_start

    while pos < rela_end:
        pos = data.find(needle, pos, rela_end)
        if pos == -1:
            break
        entry_pos = pos - 16
        if (pos - rela_start) % 24 == 16 and entry_pos >= rela_start and entry_pos + 24 <= rela_end:
            r_offset, r_info, r_addend = struct.unpack_from("<QQq", data, entry_pos)
            if (r_info & 0xFFFFFFFF) == R_AARCH64_RELATIVE and r_addend == pcln_vaddr:
                # moduledata holds pcHeader at +0x0 and runtime.text within +0x100
                scan_end = min(rela_end, entry_pos + 64 * 24)
                for p2 in range(entry_pos, scan_end, 24):
                    r2_offset, r2_info, r2_addend = struct.unpack_from("<QQq", data, p2)
                    if (
                        r_offset <= r2_offset <= r_offset + 0x100
                        and (r2_info & 0xFFFFFFFF) == R_AARCH64_RELATIVE
                        and text_vaddr <= r2_addend < text_vaddr + text_sz
                    ):
                        return base + text_rel_off + (r2_addend - text_vaddr)
        pos += 1
    return None

def get_go_text_offset(data: Union[bytes, bytearray, memoryview], base: int, pcln: Dict[str, Any]) -> Optional[int]:
    """Resolve the true Go runtime.text file offset for pclntab entry_off values."""
    if "_go_text_off" in pcln:
        return pcln["_go_text_off"]

    text = elf_scanner.get_text_section(data, base)
    if text is None:
        return None
    go_text_off = text[0]

    text_sec = elf_scanner.get_section_by_name(data, base, b".text")
    if text_sec is not None and pcln["pcln_base"] + 32 <= len(data):
        text_vaddr, text_rel_off, text_sz = text_sec
        text_start = struct.unpack_from("<Q", data, pcln["pcln_base"] + 24)[0]
        if text_vaddr <= text_start < text_vaddr + text_sz:
            go_text_off = base + text_rel_off + (text_start - text_vaddr)
        elif text_start == 0:
            resolved = _resolve_pie_text_offset(data, base, pcln["pcln_base"], text_vaddr, text_rel_off, text_sz)
            if resolved is not None:
                go_text_off = resolved

    pcln["_go_text_off"] = go_text_off
    return go_text_off

def apply_function_patch(
    data: bytearray,
    patch_name: str,
    func_name: str,
    patch_cb: Callable[[bytearray, int, bytes, bool], Tuple[str, Optional[str]]],
    verify: bool = False,
    dry_run: bool = False,
    allow_missing: bool = False,
) -> Tuple[bool, Dict[str, Any]]:
    """Iterate embedded Go ELFs, locate func_name via pclntab, and apply/verify patch_cb."""
    bases = elf_scanner.find_elf_headers(data)
    if not bases:
        return False, {"name": patch_name, "error": "no_elf_found"}

    info: Dict[str, Any] = {"name": patch_name, "patched_sites": [], "already_sites": []}
    ok = True
    write = not verify and not dry_run

    for idx, base in enumerate(bases):
        region_end = bases[idx + 1] if idx + 1 < len(bases) else len(data)
        pcln = find_pclntab(data, base, region_end)
        if pcln is None:
            continue
        text_off = get_go_text_offset(data, base, pcln)
        if text_off is None:
            continue

        found = find_function(data, pcln, func_name)
        if found is None:
            if base == 0 and not allow_missing:
                ok = False
            continue

        entry_off, size = found
        start = text_off + entry_off
        end = start + size
        if size <= 0 or start < 0 or end > len(data):
            ok = False
            continue

        body = bytes(data[start:end])
        status, site_label = patch_cb(data, start, body, write)
        if status == "patch":
            info["patched_sites"].append(site_label)
        elif status == "already":
            info["already_sites"].append(site_label)
        else:
            ok = False

    if verify:
        v_ok = ok and len(info["already_sites"]) >= 1 and len(info["patched_sites"]) == 0
        info["status"] = "verified" if v_ok else "unverified"
        return v_ok, info

    if dry_run:
        info["status"] = "dry_run" if info["patched_sites"] else "already_patched"
    else:
        info["status"] = "patched" if info["patched_sites"] else "already_patched"
    return ok, info

def find_pclntab(data: Union[bytes, bytearray, memoryview], base: int, region_end: int) -> Optional[Dict[str, Any]]:
    for magic in PCLN_MAGICS:
        pos = base
        while True:
            pos = data.find(magic, pos, region_end)
            if pos < 0:
                break
            if pos + 72 <= len(data):
                try:
                    f = struct.unpack_from("<IBBBBQQQQQQQQ", data, pos)
                except struct.error:
                    f = None
                if f is not None:
                    min_lc, ptr_size, nfunc = f[3], f[4], f[5]
                    funcname_off, pcln_off = f[8], f[12]
                    ft = pos + funcname_off
                    pt = pos + pcln_off
                    if (
                        min_lc == 4
                        and ptr_size == 8
                        and 1 <= nfunc <= 1000000
                        and base <= ft < region_end
                        and base <= pt < region_end
                        and pt + nfunc * 8 <= len(data)
                    ):
                        return {
                            "pcln_base": pos,
                            "funcname_off": funcname_off,
                            "pcln_off": pcln_off,
                            "nfunc": nfunc
                        }
            pos += 4
    return None

def find_function(data: Union[bytes, bytearray, memoryview], pcln: Dict[str, Any], func_name: str) -> Optional[Tuple[int, int]]:
    cache = pcln.setdefault("_cache", {})
    if func_name in cache:
        return cache[func_name]

    ft = pcln["pcln_base"] + pcln["funcname_off"]
    pt = pcln["pcln_base"] + pcln["pcln_off"]
    nfunc = pcln["nfunc"]

    target_bytes = func_name.encode("utf-8") + b"\x00"
    name_offsets = set()
    pos = ft
    limit = pt if pt > ft else len(data)

    while True:
        pos = data.find(target_bytes, pos, limit)
        if pos == -1:
            break
        if pos == ft or data[pos - 1] == 0:
            name_offsets.add(pos - ft)
        pos += 1

    if not name_offsets:
        cache[func_name] = None
        return None

    for i in range(nfunc):
        entry_off, func_off = struct.unpack_from("<II", data, pt + i * 8)
        try:
            _entry, name_off = struct.unpack_from("<Ii", data, pt + func_off)
        except struct.error:
            continue
        if name_off in name_offsets:
            if i + 1 < nfunc:
                next_entry = struct.unpack_from("<II", data, pt + (i + 1) * 8)[0]
                size = next_entry - entry_off
            else:
                size = 0
            res = (entry_off, size)
            cache[func_name] = res
            return res

    cache[func_name] = None
    return None
