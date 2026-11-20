"""
pidfd.py - Stubs internal/syscall/unix.PidFDOpen to return (-1, ENOSYS) on Android 10+
where Zygote seccomp-bpf traps SYS_pidfd_open (434) with SIGSYS.
"""
import os
import struct
import sys
from typing import Any, Dict, Optional, Tuple

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PATCHES_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))
sys.path.insert(0, os.path.join(PATCHES_ROOT, "engine"))

import pclntab_scanner

PIDFD_OPEN_IMM = 434
ENOSYS_IMM = 38
FUNC_NAME = "internal/syscall/unix.PidFDOpen"

MOVZ_X0_PIDFD = pclntab_scanner.encode_movz_x0(PIDFD_OPEN_IMM)
MOVZ_X2_ENOSYS = pclntab_scanner.encode_movz(2, ENOSYS_IMM)
NOP_INSN = struct.pack("<I", 0xD503201F)
PATCH_BYTES = MOVZ_X2_ENOSYS + NOP_INSN


def patch_in_body(body: bytes) -> Tuple[str, int, int, int]:
    """Locate the SYS_pidfd_open call site inside PidFDOpen and determine patch state."""
    raw = []
    pat = []
    for i in range(0, len(body) - 11, 4):
        i0 = body[i:i + 4]
        i1 = struct.unpack_from("<I", body, i + 4)[0]
        i2 = struct.unpack_from("<I", body, i + 8)[0]
        is_cbz_x2 = (i2 & 0xFF00001F) == 0xB4000002
        is_bl = (i1 & 0xFC000000) == 0x94000000
        if i0 == MOVZ_X0_PIDFD and is_bl and is_cbz_x2:
            raw.append(i)
        elif body[i:i + 8] == PATCH_BYTES and is_cbz_x2:
            pat.append(i)

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
            data[file_off:file_off + len(PATCH_BYTES)] = PATCH_BYTES
        return "patch", hex(file_off)
    if status == "already":
        return "already", hex(start + rel_off)
    return "ambiguous", None


def apply(data: bytearray, verify: bool = False, dry_run: bool = False, allow_missing: bool = False) -> Tuple[bool, Dict[str, Any]]:
    """Apply, dry-run, or verify the PidFDOpen ENOSYS patch across all embedded ELF images."""
    return pclntab_scanner.apply_function_patch(
        data, "pidfd", FUNC_NAME, _apply_to_body,
        verify=verify, dry_run=dry_run, allow_missing=allow_missing,
    )
