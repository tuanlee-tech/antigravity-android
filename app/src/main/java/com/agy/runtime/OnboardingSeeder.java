package com.agy.runtime;

import android.content.Context;
import android.util.Log;

import com.agy.util.AtomicFileWriter;
import com.agy.util.Sha256;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Materializes a fully-onboarded state before the core starts, so the Welcome
 * wizard is skipped. State files are seeded non-destructively when absent;
 * bundled skills are refreshed on upgrades only when unmodified by the user.
 */
public final class OnboardingSeeder {

    private static final String TAG = "OnboardingSeeder";
    private static final String SEED_HASHES_FILE = ".seed-hashes.properties";
    private static final String V244_ANDROID_SETUP_SHA =
            "f95c1ad79bff39a797ad1d871525eb0cea0bb4861517c2882ef9214cf64214eb";
    private static final String V245_ANDROID_SETUP_SHA =
            "f135c648678a4d14323790ca4b29b02216ae3a7738562792e7ee5bfb7e245d69";
    private static final String V244_AGENTS_LF_SHA =
            "bf826373f8ada62f996041c7f459d97b2dbf2be3c6e0893cbe576cd8dfc336f8";
    private static final String V244_AGENTS_CRLF_SHA =
            "f649c14c6438a8a4ee23ba3174c3ba5c2d7a7154b6fcc1550322ba16dac275a8";

    private OnboardingSeeder() {
    }

    public static void seed(Context appContext, File appData, File projects) {
        try {
            if (!appData.exists() && !appData.mkdirs()) {
                Log.w(TAG, "cannot create " + appData);
                return;
            }
            seedStateFile(appContext, "jetski_state.pbtxt", new File(appData, "jetski_state.pbtxt"));
            seedStateFile(appContext, "antigravity_state.pbtxt",
                    new File(appData, "antigravity_state.pbtxt"));

            // Seed default agent rules (AGENTS.md) and built-in skills into ~/.gemini/config/
            File configDir = projects.getParentFile();
            if (configDir != null && (configDir.isDirectory() || configDir.mkdirs())) {
                File skillsDir = new File(configDir, "skills");
                File hashStore = new File(skillsDir, SEED_HASHES_FILE);
                Properties seedHashes = loadSeedHashes(hashStore);
                boolean hashesChanged = seedManagedFile(
                        appContext,
                        "AGENTS.md",
                        new File(configDir, "AGENTS.md"),
                        seedHashes,
                        V244_AGENTS_LF_SHA,
                        V244_AGENTS_CRLF_SHA);
                if (seedSkillTree(appContext, "skills", skillsDir, seedHashes)) {
                    hashesChanged = true;
                }
                if (hashesChanged) {
                    saveSeedHashes(hashStore, seedHashes);
                }
            }

            // The project store resolves its projects here; without the
            // pseudo-project file it logs project_store_get_file_missing.
            if (!projects.exists() && !projects.mkdirs()) {
                Log.w(TAG, "cannot create " + projects);
                return;
            }
            File outside = new File(projects, "outside-of-project.json");
            if (!outside.isFile()) {
                AtomicFileWriter.writeText(outside, "{}\n");
            }
        } catch (Exception e) {
            Log.w(TAG, "onboarding seed failed (continuing): " + e.getMessage());
        }
    }

    private static boolean seedSkillTree(
            Context appContext, String assetRelPath, File target, Properties seedHashes)
            throws Exception {
        String fullAssetPath = RuntimePaths.SEED_ASSET + "/" + assetRelPath;
        String[] children = appContext.getAssets().list(fullAssetPath);
        if (children == null || children.length == 0) {
            if ("skills/android-setup/SKILL.md".equals(assetRelPath)) {
                return seedManagedFile(appContext, assetRelPath, target, seedHashes,
                        V244_ANDROID_SETUP_SHA, V245_ANDROID_SETUP_SHA);
            }
            return seedManagedFile(appContext, assetRelPath, target, seedHashes);
        }
        if (!target.isDirectory() && !target.mkdirs()) {
            Log.w(TAG, "cannot create skill dir: " + target);
            return false;
        }
        boolean changed = false;
        for (String child : children) {
            if (seedSkillTree(appContext, assetRelPath + "/" + child, new File(target, child), seedHashes)) {
                changed = true;
            }
        }
        return changed;
    }

    private static boolean seedManagedFile(
            Context appContext, String assetRelPath, File target, Properties seedHashes,
            String... legacyKnownHashes)
            throws Exception {
        String content;
        try (InputStream in = appContext.getAssets().open(RuntimePaths.SEED_ASSET + "/" + assetRelPath)) {
            content = com.agy.util.IoUtil.readAll(in, "UTF-8");
        }
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        String newHash = Sha256.of(new ByteArrayInputStream(contentBytes));
        String recordedHash = seedHashes.getProperty(assetRelPath);

        if (target.isFile() && target.length() > 0) {
            String existingHash = Sha256.of(target);
            if (newHash.equals(existingHash)) {
                if (!newHash.equals(recordedHash)) {
                    seedHashes.setProperty(assetRelPath, newHash);
                    return true;
                }
                return false;
            }
            boolean isKnownStock = existingHash.equals(recordedHash);
            if (!isKnownStock && legacyKnownHashes != null) {
                for (String legacyHash : legacyKnownHashes) {
                    if (legacyHash != null && legacyHash.equals(existingHash)) {
                        isKnownStock = true;
                        break;
                    }
                }
            }
            if (!isKnownStock) {
                Log.i(TAG, "managed file customized by user; left untouched: " + target);
                return false;
            }
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            Log.w(TAG, "cannot create parent dir: " + parent);
            return false;
        }
        AtomicFileWriter.writeText(target, content);
        seedHashes.setProperty(assetRelPath, newHash);
        Log.i(TAG, "seeded/updated stock file: " + target);
        return true;
    }

    private static Properties loadSeedHashes(File store) {
        Properties props = new Properties();
        if (store.isFile()) {
            try (FileInputStream in = new FileInputStream(store)) {
                props.load(in);
            } catch (Exception e) {
                Log.w(TAG, "cannot read seed hashes: " + e.getMessage());
            }
        }
        return props;
    }

    private static void saveSeedHashes(File store, Properties props) {
        try {
            File parent = store.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return;
            }
            StringWriter sw = new StringWriter();
            props.store(sw, "AGY bundled skill checksums");
            AtomicFileWriter.writeText(store, sw.toString());
        } catch (Exception e) {
            Log.w(TAG, "cannot write seed hashes: " + e.getMessage());
        }
    }

    private static void seedStateFile(Context appContext, String assetName, File target)
            throws Exception {
        if (target.isFile() && target.length() > 0) {
            Log.i(TAG, "state present; left untouched: " + target);
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            Log.w(TAG, "cannot create parent dir: " + parent);
            return;
        }
        try (InputStream in = appContext.getAssets().open(
                RuntimePaths.SEED_ASSET + "/" + assetName)) {
            String content = com.agy.util.IoUtil.readAll(in, "UTF-8");
            AtomicFileWriter.writeText(target, content);
        }
        Log.i(TAG, "seeded onboarding state (was missing): " + target);
    }
}
