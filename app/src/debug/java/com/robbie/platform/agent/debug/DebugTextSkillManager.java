package com.robbie.platform.agent.debug;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.ainirobot.coreservice.client.ApiListener;
import com.ainirobot.coreservice.client.speech.SkillApi;

import java.util.ArrayDeque;

public class DebugTextSkillManager {
    private static final String TAG = "DebugTextSkillManager";
    private static final long BASE_RECONNECT_DELAY_MS = 1500L;
    private static final long MAX_RECONNECT_DELAY_MS = 10000L;
    private static final long IDLE_DISCONNECT_DELAY_MS = 30000L;

    private static DebugTextSkillManager instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> pendingTexts = new ArrayDeque<>();
    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (DebugTextSkillManager.this) {
                if (!shouldReconnectLocked()) {
                    return;
                }
            }
            ensureConnected();
        }
    };
    private final Runnable idleDisconnectRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (DebugTextSkillManager.this) {
                if (!pendingTexts.isEmpty() || connecting || !connected) {
                    return;
                }
                manualDisconnect = true;
                connectRequested = false;
                connected = false;
                connecting = false;
                reconnectAttempt = 0;
                mainHandler.removeCallbacks(reconnectRunnable);
                if (skillApi == null) {
                    manualDisconnect = false;
                    return;
                }
                try {
                    Log.i(TAG, "Disconnecting idle SkillApi binding");
                    skillApi.disconnectApi();
                } catch (Exception e) {
                    manualDisconnect = false;
                    Log.w(TAG, "Error disconnecting SkillApi", e);
                }
            }
        }
    };
    private final ApiListener apiListener = new ApiListener() {
        @Override
        public void handleApiDisabled() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    onApiDisabled();
                }
            });
        }

        @Override
        public void handleApiConnected() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    onApiConnected();
                }
            });
        }

        @Override
        public void handleApiDisconnected() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    onApiDisconnected();
                }
            });
        }
    };

    private Context appContext;
    private SkillApi skillApi;
    private boolean connectRequested = false;
    private boolean connecting = false;
    private boolean connected = false;
    private boolean manualDisconnect = false;
    private int reconnectAttempt = 0;

    public static synchronized DebugTextSkillManager getInstance() {
        if (instance == null) {
            instance = new DebugTextSkillManager();
        }
        return instance;
    }

    public void submitText(Context context, String text) {
        if (context == null) {
            Log.w(TAG, "Ignoring debug text because context is null");
            return;
        }
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(text.trim())) {
            Log.w(TAG, "Ignoring debug text because it is empty");
            return;
        }
        synchronized (this) {
            appContext = context.getApplicationContext();
            pendingTexts.offer(text.trim());
            connectRequested = true;
            manualDisconnect = false;
            mainHandler.removeCallbacks(idleDisconnectRunnable);
            Log.i(TAG, "Queued debug text. pending=" + pendingTexts.size());
        }
        ensureConnected();
    }

    private void ensureConnected() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (DebugTextSkillManager.this) {
                    if (connected) {
                        flushPendingLocked();
                        return;
                    }
                    if (connecting) {
                        Log.d(TAG, "SkillApi connection already in progress");
                        return;
                    }
                    if (appContext == null) {
                        Log.w(TAG, "Cannot connect SkillApi because appContext is null");
                        return;
                    }
                    if (skillApi == null) {
                        skillApi = new SkillApi();
                    }
                    try {
                        connecting = true;
                        manualDisconnect = false;
                        mainHandler.removeCallbacks(reconnectRunnable);
                        Log.i(TAG, "Starting SkillApi connectApi");
                        skillApi.connectApi(appContext, apiListener);
                    } catch (Exception e) {
                        connecting = false;
                        connected = false;
                        Log.e(TAG, "Error starting SkillApi connectApi", e);
                        scheduleReconnectLocked("connect_exception");
                    }
                }
            }
        });
    }

    private void onApiConnected() {
        synchronized (this) {
            connecting = false;
            connected = true;
            manualDisconnect = false;
            reconnectAttempt = 0;
            mainHandler.removeCallbacks(reconnectRunnable);
            Log.i(TAG, "SkillApi binder connected");
            flushPendingLocked();
        }
    }

    private void onApiDisconnected() {
        synchronized (this) {
            connecting = false;
            connected = false;
            mainHandler.removeCallbacks(idleDisconnectRunnable);
            if (manualDisconnect) {
                manualDisconnect = false;
                Log.i(TAG, "SkillApi binder disconnected after idle release");
                return;
            }
            Log.w(TAG, "SkillApi binder disconnected");
            scheduleReconnectLocked("api_disconnected");
        }
    }

    private void onApiDisabled() {
        synchronized (this) {
            connecting = false;
            connected = false;
            mainHandler.removeCallbacks(idleDisconnectRunnable);
            if (manualDisconnect) {
                manualDisconnect = false;
                Log.i(TAG, "SkillApi disabled after idle release");
                return;
            }
            Log.w(TAG, "SkillApi disabled");
            scheduleReconnectLocked("api_disabled");
        }
    }

    private void flushPendingLocked() {
        if (!connected || skillApi == null) {
            return;
        }
        while (!pendingTexts.isEmpty()) {
            String text = pendingTexts.poll();
            if (TextUtils.isEmpty(text)) {
                continue;
            }
            try {
                Log.i(TAG, "Sending debug query through SkillApi: " + text);
                skillApi.queryByTextWithThinking(text, true);
            } catch (Exception e) {
                pendingTexts.addFirst(text);
                connected = false;
                connecting = false;
                Log.e(TAG, "Error sending debug query through SkillApi", e);
                scheduleReconnectLocked("query_exception");
                return;
            }
        }
        scheduleIdleDisconnectLocked();
    }

    private void scheduleIdleDisconnectLocked() {
        mainHandler.removeCallbacks(idleDisconnectRunnable);
        if (!connected || !pendingTexts.isEmpty()) {
            return;
        }
        mainHandler.postDelayed(idleDisconnectRunnable, IDLE_DISCONNECT_DELAY_MS);
    }

    private void scheduleReconnectLocked(String reason) {
        if (!shouldReconnectLocked()) {
            return;
        }
        long delayMs = Math.min(MAX_RECONNECT_DELAY_MS, BASE_RECONNECT_DELAY_MS * (1L << Math.min(reconnectAttempt, 3)));
        reconnectAttempt++;
        mainHandler.removeCallbacks(reconnectRunnable);
        Log.i(TAG, "Scheduling SkillApi reconnect in " + delayMs + "ms due to " + reason + " (attempt=" + reconnectAttempt + ")");
        mainHandler.postDelayed(reconnectRunnable, delayMs);
    }

    private boolean shouldReconnectLocked() {
        return connectRequested || !pendingTexts.isEmpty();
    }
}
