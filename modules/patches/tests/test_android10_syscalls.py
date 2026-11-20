#!/usr/bin/env python3
"""
test_android10_syscalls.py - Unit tests for Android 10 seccomp syscall patches and Go textStart resolution.
"""
import os
import struct
import sys
import unittest

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
PATCHES_ROOT = os.path.dirname(TESTS_DIR)
sys.path.insert(0, os.path.join(PATCHES_ROOT, "engine"))
sys.path.insert(0, os.path.join(PATCHES_ROOT, "groups", "syscalls"))

import copy_file_range
import openat2
import pclntab_scanner
import pidfd


def _pack_insns(*words: int) -> bytes:
    return struct.pack(f"<{len(words)}I", *words)


class TestAndroid10Syscalls(unittest.TestCase):
    def test_01_encode_movz(self):
        self.assertEqual(pclntab_scanner.encode_movz(0, 434), bytes.fromhex("403680d2"))
        self.assertEqual(pclntab_scanner.encode_movz(1, 38), bytes.fromhex("c10480d2"))
        self.assertEqual(pclntab_scanner.encode_movz(2, 38), bytes.fromhex("c20480d2"))
        self.assertEqual(pclntab_scanner.encode_movz(2, 437), bytes.fromhex("a23680d2"))
        self.assertEqual(pclntab_scanner.encode_movz_x0(285), bytes.fromhex("a02380d2"))

    def test_02_pidfd_patch_in_body(self):
        bl_insn = 0x97FF757E
        cbz_x2 = 0xB4000142
        unpatched = _pack_insns(0xD10023FD, 0xD2803640, bl_insn, cbz_x2, 0xD65F03C0)
        status, off, c_old, c_new = pidfd.patch_in_body(unpatched)
        self.assertEqual((status, off, c_old, c_new), ("patch", 4, 1, 0))

        patched = bytearray(unpatched)
        patched[off:off + len(pidfd.PATCH_BYTES)] = pidfd.PATCH_BYTES
        status2, off2, c_old2, c_new2 = pidfd.patch_in_body(bytes(patched))
        self.assertEqual((status2, off2, c_old2, c_new2), ("already", 4, 0, 1))

        # Ambiguous / unrelated body
        bad = _pack_insns(0xD2803640, bl_insn, 0xB4000140)  # CBZ X0 instead of X2
        status3, _, _, _ = pidfd.patch_in_body(bad)
        self.assertEqual(status3, "ambiguous")

    def test_03_copy_file_range_patch_in_body(self):
        bl_insn = 0x97FF7669
        cbnz_x2 = 0xB5000082
        unpatched = _pack_insns(
            0xD28023A0,  # MOVZ X0, #285
            0xAA0403E5,  # MOV X5, X4
            0xAA0303E4,  # MOV X4, X3
            0xAA0203E3,  # MOV X3, X2
            0xAA0703E2,  # MOV X2, X7
            bl_insn,
            cbnz_x2,
        )
        status, mov_off, bl_off, c_old, c_new = copy_file_range.patch_in_body(unpatched)
        self.assertEqual((status, mov_off, bl_off, c_old, c_new), ("patch", 0, 20, 1, 0))

        patched = bytearray(unpatched)
        patched[mov_off:mov_off + 4] = copy_file_range.MOVZ_X0_ZERO
        patched[bl_off:bl_off + 4] = copy_file_range.MOVZ_X2_ENOSYS
        status2, mov_off2, bl_off2, c_old2, c_new2 = copy_file_range.patch_in_body(bytes(patched))
        self.assertEqual((status2, mov_off2, bl_off2, c_old2, c_new2), ("already", 0, 20, 0, 1))

        # Clobbering X0 in between (MOV X0, X4 = 0xAA0403E0) must be rejected
        clobbered = _pack_insns(0xD28023A0, 0xAA0403E0, bl_insn, cbnz_x2)
        status3, _, _, _, _ = copy_file_range.patch_in_body(clobbered)
        self.assertEqual(status3, "ambiguous")

    def test_04_openat2_patch_in_body(self):
        bl_insn = 0x940004FB
        ldr_x0 = 0xF94023E0
        ldr_x1 = 0xF9402BE1
        cbz_x1 = 0xB4000381
        unpatched = _pack_insns(
            0xD28036A2,  # MOVZ X2, #437
            0xF9404FE3,
            0xA9008FE2,
            0xA90183E1,
            bl_insn,
            ldr_x0,
            ldr_x1,
            cbz_x1,
        )
        status, off, c_old, c_new = openat2.patch_in_body(unpatched)
        self.assertEqual((status, off, c_old, c_new), ("patch", 16, 1, 0))

        patched = bytearray(unpatched)
        patched[off:off + len(openat2.PATCH_SEQ)] = openat2.PATCH_SEQ
        status2, off2, c_old2, c_new2 = openat2.patch_in_body(bytes(patched))
        self.assertEqual((status2, off2, c_old2, c_new2), ("already", 16, 0, 1))

        # Missing MOVZ X2, #437 must fail closed
        missing = _pack_insns(0xD503201F, bl_insn, ldr_x0, ldr_x1, cbz_x1)
        status3, _, _, _ = openat2.patch_in_body(missing)
        self.assertEqual(status3, "ambiguous")

    def _build_synthetic_elf(self, pie_text_start_zero: bool, stripped: bool = False) -> bytearray:
        buf = bytearray(4096)
        buf[0:4] = b"\x7fELF"
        buf[4] = 2  # ELFCLASS64
        buf[5] = 1  # ELFDATA2LSB
        buf[6] = 1  # EV_CURRENT
        struct.pack_into("<H", buf, 16, 3 if pie_text_start_zero else 2)  # ET_DYN or ET_EXEC
        struct.pack_into("<H", buf, 18, 183)  # EM_AARCH64

        # Program header at 64: PT_LOAD PF_R|PF_X at offset 0x400, size 0x200
        struct.pack_into("<Q", buf, 32, 64)
        struct.pack_into("<H", buf, 54, 56)
        struct.pack_into("<H", buf, 56, 1)
        struct.pack_into("<II", buf, 64, 1, 5)
        struct.pack_into("<Q", buf, 64 + 8, 0x400)
        struct.pack_into("<Q", buf, 64 + 32, 0x200)

        # pclntab at 0x200 (72 bytes header + minimal tables)
        pcln_off = 0x200
        pcln_vaddr = 0x10200
        text_off = 0x400
        text_vaddr = 0x20400
        text_sz = 0x200
        go_text_vaddr = text_vaddr + 0xB0

        struct.pack_into(
            "<IBBBBQQQQQQQQ",
            buf,
            pcln_off,
            0xFFFFFFF1, 0, 0, 4, 8,
            1, 1,
            0 if pie_text_start_zero else go_text_vaddr,
            72, 72, 72, 72, 80,
        )

        if stripped:
            return buf

        # shstrtab at 0x300
        shstr = b"\x00.lrodata\x00.text\x00.rela.dyn\x00.shstrtab\x00"
        buf[0x300:0x300 + len(shstr)] = shstr

        # .rela.dyn at 0x600 (3 entries * 24 bytes = 72 bytes)
        rela_off = 0x600
        rela_vaddr = 0x30600
        mod_vaddr = 0x50000
        struct.pack_into("<QQq", buf, rela_off + 0, mod_vaddr + 0x00, 1027, pcln_vaddr)
        struct.pack_into("<QQq", buf, rela_off + 24, mod_vaddr + 0x08, 1027, pcln_vaddr + 0x48)
        struct.pack_into("<QQq", buf, rela_off + 48, mod_vaddr + 0xA0, 1027, go_text_vaddr)

        # Section headers at 0x800 (5 sections)
        shoff = 0x800
        struct.pack_into("<Q", buf, 40, shoff)
        struct.pack_into("<HHH", buf, 58, 64, 5, 4)

        def write_sh(idx: int, name_idx: int, sh_type: int, addr: int, off: int, size: int) -> None:
            p = shoff + idx * 64
            struct.pack_into("<II", buf, p, name_idx, sh_type)
            struct.pack_into("<QQQ", buf, p + 16, addr, off, size)

        write_sh(1, 1, 1, pcln_vaddr, pcln_off, 0x100)       # .lrodata
        write_sh(2, 10, 1, text_vaddr, text_off, text_sz)    # .text
        write_sh(3, 16, 4, rela_vaddr, rela_off, 72)         # .rela.dyn
        write_sh(4, 26, 3, 0, 0x300, len(shstr))             # .shstrtab
        return buf

    def test_05_get_go_text_offset(self):
        # 1. PIE with pcHeader.textStart == 0 resolved via .rela.dyn moduledata
        pie_buf = self._build_synthetic_elf(pie_text_start_zero=True)
        pcln_pie = {"pcln_base": 0x200}
        self.assertEqual(pclntab_scanner.get_go_text_offset(pie_buf, 0, pcln_pie), 0x4B0)
        self.assertEqual(pcln_pie["_go_text_off"], 0x4B0)

        # 2. Non-PIE with pcHeader.textStart != 0
        nonpie_buf = self._build_synthetic_elf(pie_text_start_zero=False)
        pcln_nonpie = {"pcln_base": 0x200}
        self.assertEqual(pclntab_scanner.get_go_text_offset(nonpie_buf, 0, pcln_nonpie), 0x4B0)

        # 3. Stripped ELF falls back to PT_LOAD start
        stripped_buf = self._build_synthetic_elf(pie_text_start_zero=True, stripped=True)
        pcln_stripped = {"pcln_base": 0x200}
        self.assertEqual(pclntab_scanner.get_go_text_offset(stripped_buf, 0, pcln_stripped), 0x400)


if __name__ == "__main__":
    unittest.main()
