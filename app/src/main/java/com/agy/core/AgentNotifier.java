package com.agy.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.agy.ui.MainActivity;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Posts high-priority native Android notifications when an AI agent finishes
 * a task or stops to request user input while the app is backgrounded.
 */
public final class AgentNotifier {

    private static final String TAG = "AgentNotifier";
    public static final String CHANNEL_AGENT_ALERTS = "agy_agent_alerts";
    private static final String NOTIF_TAG_PREFIX = "agy_agent_";
    private static final long DEDUP_WINDOW_MS = 15_000L;
    private static final int ID_BASE = 20_000;
    private static final int ID_RANGE = 800_000;

    private static final Pattern WEB_FINISHED_TAG =
            Pattern.compile("^agent-finished-(.+)-(\\d+)$");
    private static final Pattern WEB_ACTION_TAG =
            Pattern.compile("^(\\d+)([0-9a-fA-F]{8}-[0-9a-fA-F-]{27})$");

    private static final ConcurrentHashMap<String, Long> RECENT_EVENTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> ACTIVE_ALERTS = new ConcurrentHashMap<>();

    private AgentNotifier() {
    }

    public static void ensureChannel(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager)
                context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_AGENT_ALERTS,
                "Agent Updates",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Alerts when Antigravity finishes a task or needs your input");
        channel.enableVibration(true);
        channel.enableLights(true);
        manager.createNotificationChannel(channel);
    }

    public static void notifyFinished(Context context, String cascadeId,
                                      String conversationSummary, boolean silent) {
        if (context == null || CoreRuntimeState.appInForeground) {
            return;
        }
        String safeId = (cascadeId == null || cascadeId.isEmpty()) ? "default" : cascadeId;
        String dedupKey = "done:" + safeId;
        if (!claimEvent(dedupKey)) {
            return;
        }
        String summary = conversationSummary == null ? "" : conversationSummary.trim();
        String title = "Agent đã hoàn thành";
        String body = summary.isEmpty()
                ? "Tác vụ đã hoàn tất. Bấm để vào xem kết quả hoặc nhắn tiếp."
                : summary + " — Bấm để review hoặc nhắn tiếp.";
        postAlert(context, dedupKey, title, body, silent);
    }

    public static void notifyActionRequired(Context context, String cascadeId, String stepKey,
                                            String title, String body, boolean silent) {
        if (context == null || CoreRuntimeState.appInForeground) {
            return;
        }
        String keyPart = (stepKey != null && !stepKey.isEmpty())
                ? stepKey
                : ((cascadeId == null || cascadeId.isEmpty()) ? "default" : cascadeId);
        String dedupKey = "wait:" + keyPart;
        if (!claimEvent(dedupKey)) {
            return;
        }
        String safeTitle = (title == null || title.trim().isEmpty())
                ? "Agent đang chờ bạn"
                : title.trim();
        String safeBody = (body == null || body.trim().isEmpty())
                ? "Bấm để xem và phản hồi."
                : body.trim();
        postAlert(context, dedupKey, safeTitle, safeBody, silent);
    }

    public static void notifyFromWeb(Context context, String title, String body,
                                     String tag, boolean silent) {
        if (context == null || CoreRuntimeState.appInForeground) {
            return;
        }
        String dedupKey = normalizeWebTag(tag, title, body);
        if (!claimEvent(dedupKey)) {
            return;
        }
        String safeTitle = (title == null || title.trim().isEmpty())
                ? "Antigravity"
                : title.trim();
        String safeBody = (body == null || body.trim().isEmpty())
                ? "Bấm để mở ứng dụng."
                : body.trim();
        postAlert(context, dedupKey, safeTitle, safeBody, silent);
    }

    public static void clearAgentAlerts(Context context) {
        if (context == null) {
            return;
        }
        NotificationManager manager = (NotificationManager)
                context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            ACTIVE_ALERTS.clear();
            return;
        }
        for (Map.Entry<String, Integer> entry : ACTIVE_ALERTS.entrySet()) {
            try {
                manager.cancel(entry.getKey(), entry.getValue());
            } catch (Exception ignored) {
            }
        }
        ACTIVE_ALERTS.clear();
    }

    private static String normalizeWebTag(String tag, String title, String body) {
        if (tag != null && !tag.trim().isEmpty()) {
            String trimmed = tag.trim();
            Matcher doneMatcher = WEB_FINISHED_TAG.matcher(trimmed);
            if (doneMatcher.matches()) {
                return "done:" + doneMatcher.group(1);
            }
            Matcher waitMatcher = WEB_ACTION_TAG.matcher(trimmed);
            if (waitMatcher.matches()) {
                return "wait:" + waitMatcher.group(2) + ":" + waitMatcher.group(1);
            }
            return "web:" + trimmed;
        }
        return "web:" + (title == null ? "" : title) + ":" + (body == null ? "" : body);
    }

    private static boolean claimEvent(String dedupKey) {
        long now = System.currentTimeMillis();
        pruneExpired(now);
        Long previous = RECENT_EVENTS.putIfAbsent(dedupKey, now);
        if (previous != null) {
            if (now - previous < DEDUP_WINDOW_MS) {
                return false;
            }
            RECENT_EVENTS.put(dedupKey, now);
        }
        return true;
    }

    private static void pruneExpired(long now) {
        if (RECENT_EVENTS.size() < 32) {
            return;
        }
        Iterator<Map.Entry<String, Long>> it = RECENT_EVENTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (now - e.getValue() > DEDUP_WINDOW_MS * 4L) {
                it.remove();
            }
        }
    }

    private static void postAlert(Context context, String dedupKey,
                                  String title, String body, boolean silent) {
        Context appContext = context.getApplicationContext();
        ensureChannel(appContext);
        NotificationManager manager = (NotificationManager)
                appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        int id = ID_BASE + Math.abs(dedupKey.hashCode() % ID_RANGE);
        String tag = NOTIF_TAG_PREFIX + Integer.toHexString(dedupKey.hashCode());

        Intent launchIntent = new Intent(appContext, MainActivity.class);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(appContext, id, launchIntent, piFlags);

        int icon = appContext.getResources().getIdentifier(
                "ic_notification", "drawable", appContext.getPackageName());
        if (icon == 0) {
            icon = android.R.drawable.ic_dialog_info;
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(appContext, CHANNEL_AGENT_ALERTS)
                : new Notification.Builder(appContext);

        builder.setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setSmallIcon(icon)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(silent ? 0 : Notification.DEFAULT_ALL);

        if (silent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26+ channels control sound, so keep silent requests unobtrusive when supported.
            builder.setOnlyAlertOnce(true);
        }

        try {
            manager.notify(tag, id, builder.build());
            ACTIVE_ALERTS.put(tag, id);
        } catch (SecurityException se) {
            Log.w(TAG, "notification blocked by permission: " + se.getMessage());
        } catch (Exception e) {
            Log.w(TAG, "failed to post notification: " + e.getMessage());
        }
    }
}
