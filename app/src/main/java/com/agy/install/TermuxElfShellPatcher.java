package com.agy.install;

import android.util.Log;

import com.agy.util.AtomicFileWriter;
import com.agy.util.IoUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Patches prebuilt Termux ELF binaries (such as {@code git} and
 * {@code git-remote-http}) whose compiled {@code SHELL_PATH} points at
 * {@code /data/data/com.termux/files/usr/bin/sh\0} (39 bytes), replacing it
 * in-place with NUL-padded {@code /system/bin/sh\0} of identical byte length.
 *
 * Works purely in Java with zero external interpreter dependencies.
 */
public final class TermuxElfShellPatcher {

    private static final String TAG = "TermuxElfShellPatcher";
    private static final long MAX_ELF_BYTES = 32L * 1024L * 1024L;

    private static final byte[] ELF_MAGIC = new byte[]{0x7f, 'E', 'L', 'F'};
    private static final byte[] OLD_SHELL =
            "/data/data/com.termux/files/usr/bin/sh\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] NEW_SHELL = buildReplacementShell();

    private TermuxElfShellPatcher() {
    }

    private static byte[] buildReplacementShell() {
        byte[] replacement = new byte[OLD_SHELL.length];
        byte[] sysSh = "/system/bin/sh".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(sysSh, 0, replacement, 0, sysSh.length);
        // Remaining bytes are zero (NUL padding) preserving exact 39-byte C-string slot.
        return replacement;
    }

    /**
     * Scans known Termux git binaries plus immediate ELF files in {@code bin/}
     * and {@code libexec/git-core/} under {@code prefix}.
     *
     * @return number of ELF binaries patched.
     */
    public static int patch(File prefix) {
        if (prefix == null || !prefix.isDirectory()) {
            return 0;
        }
        Set<String> seen = new HashSet<>();
        int patched = 0;

        File[] priority = new File[]{
                new File(prefix, "bin/git"),
                new File(prefix, "libexec/git-core/git-remote-http")
        };
        for (File target : priority) {
            if (patchCandidate(target, seen)) {
                patched++;
            }
        }

        File[] scanDirs = new File[]{
                new File(prefix, "bin"),
                new File(prefix, "libexec/git-core")
        };
        for (File dir : scanDirs) {
            File[] children = dir.listFiles();
            if (children == null) {
                continue;
            }
            for (File child : children) {
                if (patchCandidate(child, seen)) {
                    patched++;
                }
            }
        }

        if (patched > 0) {
            Log.i(TAG, "patched hardcoded Termux shell path in " + patched + " ELF binary(ies)");
        }
        return patched;
    }

    private static boolean patchCandidate(File file, Set<String> seen) {
        if (file == null || !file.isFile() || TextFileDetector.isSymlink(file)) {
            return false;
        }
        String key;
        try {
            key = file.getCanonicalPath();
        } catch (IOException e) {
            key = file.getAbsolutePath();
        }
        if (!seen.add(key)) {
            return false;
        }
        long length = file.length();
        if (length < OLD_SHELL.length || length > MAX_ELF_BYTES) {
            return false;
        }
        if (!hasElfMagic(file)) {
            return false;
        }
        return patchElfFile(file);
    }

    private static boolean hasElfMagic(File file) {
        byte[] header = new byte[4];
        try (InputStream in = new FileInputStream(file)) {
            if (IoUtil.readFully(in, header) < 4) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        return header[0] == ELF_MAGIC[0]
                && header[1] == ELF_MAGIC[1]
                && header[2] == ELF_MAGIC[2]
                && header[3] == ELF_MAGIC[3];
    }

    static boolean patchElfFile(File file) {
        byte[] data;
        try (InputStream in = new FileInputStream(file)) {
            data = IoUtil.readAllBytes(in);
        } catch (IOException e) {
            Log.w(TAG, "cannot read ELF " + file + ": " + e.getMessage());
            return false;
        }
        int replacements = replaceAllInPlace(data, OLD_SHELL, NEW_SHELL);
        if (replacements <= 0) {
            return false;
        }
        if (AtomicFileWriter.writeBytes(file, data)) {
            Log.i(TAG, "patched ELF shell (" + replacements + "x) -> " + file);
            return true;
        }
        Log.w(TAG, "failed writing patched ELF: " + file);
        return false;
    }

    static int replaceAllInPlace(byte[] data, byte[] needle, byte[] replacement) {
        if (data == null || needle == null || replacement == null
                || needle.length == 0 || needle.length != replacement.length
                || data.length < needle.length) {
            return 0;
        }
        int count = 0;
        int limit = data.length - needle.length;
        byte first = needle[0];
        for (int i = 0; i <= limit; i++) {
            if (data[i] != first) {
                continue;
            }
            boolean match = true;
            for (int j = 1; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                System.arraycopy(replacement, 0, data, i, replacement.length);
                count++;
                i += needle.length - 1;
            }
        }
        return count;
    }
}
