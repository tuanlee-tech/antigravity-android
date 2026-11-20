"""
copy_file_range.py - Stubs internal/syscall/unix.CopyFileRange to return (0, ENOSYS)
so Go falls back to read/write when Android 10-13 seccomp-bpf traps SYS_copy_file_range (285).
"""
import os
import struct
import sys
from typing import Any, Dict, Optional, Tuple

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PATCHES_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))
sys.path.insert(0, os.path.join(PATCHES_ROOT, "engine"))

import pclntab_scanner

COPY_FILE_RANGE_IMM = 285
ENOSYS_IMM = 38
FUNC_NAME = "internal/syscall/unix.CopyFileRange"

MOVZ_X0_CFR = pclntab_scanner.encode_movz_x0(COPY_FILE_RANGE_IMM)
MOVZ_X0_ZERO = pclntab_scanner.encode_movz_x0(0)
MOVZ_X2_ENOSYS = pclntab_scanner.encode_movz(2, ENOSYS_IMM)


def patch_in_body(body: bytes) -> Tuple[str, int, int, int, int]:
    """Locate the SYS_copy_file_range call site inside CopyFileRange and determine patch state."""
    raw = []
    pat = []
    for i in range(0, len(body) - 11, 4):
        op_i = body[i:i + 4]
        if op_i != MOVZ_X0_CFR and op_i != MOVZ_X0_ZERO:
            continue
        for j in range(i + 4, min(i + 32, len(body) - 7), 4):
            insn_j = struct.unpack_from("<I", body, j)[0]
            insn_next = struct.unpack_from("<I", body, j + 4)[0]
            is_cbnz_x2 = (insn_next & 0xFF00001F) == 0xB5000002

            if op_i == MOVZ_X0_CFR and (insn_j & 0xFC000000) == 0x94000000 and is_cbnz_x2:
                raw.append((i, j))
                break
            if op_i == MOVZ_X0_ZERO and body[j:j + 4] == MOVZ_X2_ENOSYS and is_cbnz_x2:
                pat.append((i, j))
                break

            # Intermediate instructions must be register MOVs that do not clobber X0
            if (insn_j & 0xFFE0FFE0) != 0xAA0003E0 or (insn_j & 0x1F) == 0:
                break

    if len(raw) == 1 and len(pat) == 0:
        return "patch", raw[0][0], raw[0][1], 1, 0
    if len(raw) == 0 and len(pat) == 1:
        return "already", pat[0][0], pat[0][1], 0, 1
    return "ambiguous", -1, -1, len(raw), len(pat)


def _apply_to_body(data: bytearray, start: int, body: bytes, write: bool) -> Tuple[str, Optional[str]]:
    status, mov_off, bl_off, _, _ = patch_in_body(body)
    if status == "patch":
        if write:
            data[start + mov_off:start + mov_off + 4] = MOVZ_X0_ZERO
            data[start + bl_off:start + bl_off + 4] = MOVZ_X2_ENOSYS
        return "patch", f"{hex(start + mov_off)}/{hex(start + bl_off)}"
    if status == "already":
        return "already", f"{hex(start + mov_off)}/{hex(start + bl_off)}"
    return "ambiguous", None


def apply(data: bytearray, verify: bool = False, dry_run: bool = False, allow_missing: bool = False) -> Tuple[bool, Dict[str, Any]]:
    """Apply, dry-run, or verify the CopyFileRange ENOSYS patch across all embedded ELF images."""
    return pclntab_scanner.apply_function_patch(
        data, "copy_file_range", FUNC_NAME, _apply_to_body,
        verify=verify, dry_run=dry_run, allow_missing=allow_missing,
    )
