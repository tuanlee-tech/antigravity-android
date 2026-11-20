"""
openat2.py - Stubs google3/third_party/golang/go_sys/unix/unix.openat2 to return (-1, ENOSYS)
so safeopen falls back to openat when Android seccomp-bpf traps SYS_openat2 (437).
"""
import os
import struct
import sys
from typing import Any, Dict, Optional, Tuple

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PATCHES_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))
sys.path.insert(0, os.path.join(PATCHES_ROOT, "engine"))

import pclntab_scanner

OPENAT2_IMM = 437
ENOSYS_IMM = 38
FUNC_NAME = "google3/third_party/golang/go_sys/unix/unix.openat2"

MOVZ_X2_OPENAT2 = pclntab_scanner.encode_movz(2, OPENAT2_IMM)
NOP_INSN = struct.pack("<I", 0xD503201F)
MOVN_X0_NEG1 = struct.pack("<I", 0x92800000)
MOVZ_X1_ENOSYS = pclntab_scanner.encode_movz(1, ENOSYS_IMM)
PATCH_SEQ = NOP_INSN + MOVN_X0_NEG1 + MOVZ_X1_ENOSYS


def patch_in_body(body: bytes) -> Tuple[str, int, int, int]:
    """Locate the SYS_openat2 Syscall6 sequence inside unix.openat2 and determine patch state."""
    mov_sites = [m for m in range(0, len(body) - 3, 4) if body[m:m + 4] == MOVZ_X2_OPENAT2]
    if len(mov_sites) != 1:
        return "ambiguous", -1, 0, 0

    m = mov_sites[0]
    raw = []
    pat = []
    for j in range(m + 4, min(m + 48, len(body) - 15), 4):
        i0 = struct.unpack_from("<I", body, j)[0]
        i1 = struct.unpack_from("<I", body, j + 4)[0]
        i2 = struct.unpack_from("<I", body, j + 8)[0]
        i3 = struct.unpack_from("<I", body, j + 12)[0]
        is_cbz_x1 = (i3 & 0xFF00001F) == 0xB4000001

        if (
            (i0 & 0xFC000000) == 0x94000000
            and (i1 & 0xFFC003FF) == 0xF94003E0
            and (i2 & 0xFFC003FF) == 0xF94003E1
            and is_cbz_x1
        ):
            raw.append(j)
        elif body[j:j + 12] == PATCH_SEQ and is_cbz_x1:
            pat.append(j)

    if len(raw) == 1 and len(pat) == 0:
        return "patch", raw[0], 1, 0
    if len(raw) == 0 and len(pat) == 1:
        return "already", pat[0], 0, 1
    return "ambiguous", -1, len(raw), len(pat)


def _apply_to_body(data: bytearray, start: int, body: bytes, write: bool) -> Tuple[str, Optional[str]]:
    status, rel_off, _, _ = patch_in_body(body)
    if status == "patch":
        file_off = start + rel_off
        if write:
            data[file_off:file_off + len(PATCH_SEQ)] = PATCH_SEQ
        return "patch", hex(file_off)
    if status == "already":
        return "already", hex(start + rel_off)
    return "ambiguous", None


def apply(data: bytearray, verify: bool = False, dry_run: bool = False, allow_missing: bool = False) -> Tuple[bool, Dict[str, Any]]:
    """Apply, dry-run, or verify the unix.openat2 ENOSYS patch across all embedded ELF images."""
    return pclntab_scanner.apply_function_patch(
        data, "openat2", FUNC_NAME, _apply_to_body,
        verify=verify, dry_run=dry_run, allow_missing=allow_missing,
    )
