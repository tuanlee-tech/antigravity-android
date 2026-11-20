package com.agy.core;

import android.content.Context;
import android.util.Log;

import com.agy.util.IoUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;

/**
 * Watches the local language_server trajectory states from CoreServerService
 * so Android notifications fire reliably even when the Activity/WebView is
 * paused in the background or while the user is in another app.
 */
public final class AgentStatusWatcher {

    private static final String TAG = "AgentStatusWatcher";
    private static final String RPC_PATH =
            "/exa.language_server_pb.LanguageServerService/GetAllCascadeTrajectories";

    private static final long POLL_NO_CORE_MS = 2_000L;
    private static final long POLL_FOREGROUND_MS = 2_500L;
    private static final long POLL_BG_ACTIVE_MS = 1_500L;
    private static final long POLL_BG_IDLE_MS = 8_000L;

    private final Context appContext;
    private final Set<String> activeCascades = new HashSet<>();
    private final Set<String> notifiedWaitingSteps = new HashSet<>();

    private volatile boolean running = false;
    private Thread worker;
    private int lastGeneration = -1;
    private boolean hasBaseline = false;

    public AgentStatusWatcher(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::watchLoop, "AgentStatusWatcher");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    private void watchLoop() {
        while (running) {
            long sleepMs = POLL_BG_IDLE_MS;
            try {
                if (CoreRuntimeState.serverUrl == null || CoreRuntimeState.csrfToken == null) {
                    sleepMs = POLL_NO_CORE_MS;
                } else {
                    int gen = CoreRuntimeState.readyGeneration;
                    if (gen != lastGeneration) {
                        lastGeneration = gen;
                        hasBaseline = false;
                        activeCascades.clear();
                        notifiedWaitingSteps.clear();
                    }
                    pollOnce();
                    if (CoreRuntimeState.appInForeground) {
                        sleepMs = POLL_FOREGROUND_MS;
                    } else if (!activeCascades.isEmpty()) {
                        sleepMs = POLL_BG_ACTIVE_MS;
                    } else {
                        sleepMs = POLL_BG_IDLE_MS;
                    }
                }
            } catch (Throwable t) {
                Log.d(TAG, "watch loop tick skipped: " + t.getMessage());
                sleepMs = POLL_BG_IDLE_MS;
            }

            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ie) {
                if (!running) {
                    break;
                }
            }
        }
    }

    private void pollOnce() {
        String csrf = CoreRuntimeState.csrfToken;
        int port = CoreRuntimeState.httpsPort;
        if (csrf == null || port <= 0) {
            return;
        }

        HttpsURLConnection conn = null;
        try {
            URL url = new URL("https://127.0.0.1:" + port + RPC_PATH);
            conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2_000);
            conn.setReadTimeout(3_000);
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Connect-Protocol-Version", "1");
            conn.setRequestProperty("x-codeium-csrf-token", csrf);

            try (OutputStream out = conn.getOutputStream()) {
                out.write("{}".getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() != 200) {
                return;
            }
            byte[] raw;
            try (InputStream in = conn.getInputStream()) {
                raw = IoUtil.readAllBytes(in);
            }
            JSONObject root = new JSONObject(new String(raw, StandardCharsets.UTF_8));
            processSummaries(root.optJSONObject("trajectorySummaries"));
            hasBaseline = true;
        } catch (Exception e) {
            Log.d(TAG, "poll failed: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void processSummaries(JSONObject summaries) {
        if (summaries == null) {
            activeCascades.clear();
            notifiedWaitingSteps.clear();
            return;
        }

        Set<String> seenIds = new HashSet<>();
        Set<String> currentWaitingKeys = new HashSet<>();

        Iterator<String> keys = summaries.keys();
        while (keys.hasNext()) {
            String cascadeId = keys.next();
            JSONObject entry = summaries.optJSONObject(cascadeId);
            if (entry == null) {
                continue;
            }
            JSONObject meta = entry.optJSONObject("trajectoryMetadata");
            if (meta != null) {
                String parentId = meta.optString("parentConversationId", "");
                if (!parentId.isEmpty()) {
                    continue;
                }
                JSONObject sourceMeta = meta.optJSONObject("sourceMetadata");
                if (sourceMeta != null && sourceMeta.optBoolean("hideFromConversationList", false)) {
                    continue;
                }
            }

            seenIds.add(cascadeId);
            String summary = entry.optString("summary", "");
            String status = entry.optString("status", "");
            boolean isRunning = "CASCADE_RUN_STATUS_RUNNING".equals(status)
                    || "CASCADE_RUN_STATUS_BUSY".equals(status)
                    || "CASCADE_RUN_STATUS_CANCELING".equals(status)
                    || (entry.has("fullyIdle") && !entry.optBoolean("fullyIdle", true));

            if (isRunning) {
                activeCascades.add(cascadeId);
                JSONArray waitingSteps = entry.optJSONArray("waitingSteps");
                if (waitingSteps != null && waitingSteps.length() > 0) {
                    for (int i = 0; i < waitingSteps.length(); i++) {
                        JSONObject ws = waitingSteps.optJSONObject(i);
                        if (ws == null) {
                            continue;
                        }
                        int stepIndex = ws.optInt("stepIndex", i);
                        String stepKey = cascadeId + ":" + stepIndex;
                        currentWaitingKeys.add(stepKey);
                        if (notifiedWaitingSteps.add(stepKey)
                                && hasBaseline && !CoreRuntimeState.appInForeground) {
                            String[] msg = describeWaitingStep(ws.optJSONObject("step"), summary);
                            AgentNotifier.notifyActionRequired(
                                    appContext, cascadeId, stepKey, msg[0], msg[1], false);
                        }
                    }
                }
            } else {
                boolean wasActive = activeCascades.remove(cascadeId);
                if (wasActive && hasBaseline && !CoreRuntimeState.appInForeground) {
                    String terminationReason = entry.optString("latestExecutorTerminationReason", "");
                    if (!"EXECUTOR_TERMINATION_REASON_USER_CANCELED".equals(terminationReason)) {
                        AgentNotifier.notifyFinished(appContext, cascadeId, summary, false);
                    }
                }
            }
        }

        // Prune deleted trajectories and resolved waiting steps so repeat prompts notify again.
        activeCascades.retainAll(seenIds);
        notifiedWaitingSteps.retainAll(currentWaitingKeys);
    }

    private static String[] describeWaitingStep(JSONObject step, String fallbackSummary) {
        String title = "Agent đang chờ bạn";
        String body = (fallbackSummary != null && !fallbackSummary.trim().isEmpty())
                ? fallbackSummary.trim()
                : "Bấm để xem và phản hồi.";
        if (step == null) {
            return new String[]{title, body};
        }

        JSONObject ask = step.optJSONObject("askQuestion");
        if (ask != null) {
            title = "Agent có câu hỏi cho bạn";
            JSONArray questions = ask.optJSONArray("questions");
            if (questions != null && questions.length() > 0) {
                JSONObject q0 = questions.optJSONObject(0);
                if (q0 != null) {
                    String qText = q0.optString("question", "").trim();
                    if (!qText.isEmpty()) {
                        body = qText;
                    }
                }
            }
            return new String[]{title, body};
        }

        JSONObject runCmd = step.optJSONObject("runCommand");
        if (runCmd != null) {
            title = "Agent xin phép chạy lệnh";
            String cmd = runCmd.optString("commandLine", "").trim();
            if (!cmd.isEmpty()) {
                body = cmd;
            }
            return new String[]{title, body};
        }

        JSONObject openUrl = step.optJSONObject("openBrowserUrl");
        if (openUrl != null) {
            title = "Agent xin phép mở URL";
            String u = openUrl.optString("url", "").trim();
            if (!u.isEmpty()) {
                body = u;
            }
            return new String[]{title, body};
        }

        JSONObject meta = step.optJSONObject("metadata");
        if (meta != null) {
            String toolSummary = meta.optString("toolSummary", "").trim();
            if (!toolSummary.isEmpty()) {
                body = toolSummary;
            }
        }
        return new String[]{title, body};
    }
}
