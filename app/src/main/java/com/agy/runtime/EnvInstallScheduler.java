package com.agy.runtime;

import android.util.Log;

import com.agy.install.EnvBundleManifest;
import com.agy.install.EnvInstaller;
import com.agy.install.SeedPackageInstaller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 2: extract the bionic toolchain and regenerate runtime/bin bridges on a
 * daemon thread, in parallel with core startup. Best-effort; a failed attempt
 * clears the guard so a later retry can run again.
 */
public final class EnvInstallScheduler {

    private static final String TAG = "EnvInstallScheduler";

    private EnvInstallScheduler() {
    }

    public static void schedule(final RuntimeManager rm, final AtomicBoolean started) {
        if (!started.compareAndSet(false, true)) {
            Log.i(TAG, "env install already started; skipping duplicate");
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                File logFile = new File(rm.appContext().getFilesDir(), "logs/env_install.log");
                writeLog(logFile, "Starting Bionic env installation...\n", false);
                try {
                    boolean fresh = EnvInstaller.install(rm.appContext());
                    EnvBundleManifest manifest = EnvBundleManifest.load(rm.appContext().getAssets());
                    boolean seeded = SeedPackageInstaller.installDefaults(
                            rm.appContext(), EnvInstaller.prefix(rm.appContext()), manifest.seedPackages);
                    // The core is glibc and must not inherit bionic's LD_* vars,
                    // so bridges set the bionic env for each child only. Seeded
                    // packages can add commands, so refresh the bridge inventory after them.
                    ShellBridgeInstaller.install(rm, fresh || seeded);
                    String okMsg = "env ready; fresh=" + fresh + " bundle=" + manifest.bundleId
                            + " seeded=" + seeded + "; runtime/bin bridges refreshed";
                    Log.i(TAG, okMsg);
                    writeLog(logFile, okMsg + "\n", true);
                } catch (Throwable e) {
                    started.set(false);
                    String errMsg = "env install failed; core continues without bionic tools: "
                            + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    Log.e(TAG, errMsg, e);
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    writeLog(logFile, errMsg + "\n" + sw.toString() + "\n", true);
                }
            }
        }, "EnvInstallThread");
        thread.setDaemon(true);
        thread.start();
    }

    private static void writeLog(File file, String text, boolean append) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(file, append)) {
                out.write(text.getBytes(Charset.forName("UTF-8")));
            }
        } catch (Throwable ignored) {
        }
    }
}
