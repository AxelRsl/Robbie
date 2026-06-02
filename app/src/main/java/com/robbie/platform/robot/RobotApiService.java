package com.robbie.platform.robot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ainirobot.coreservice.client.ApiListener;
import com.ainirobot.coreservice.client.RobotApi;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class RobotApiService {
    private static final String TAG = "RobotApiService";
    private static final long BASE_RECONNECT_DELAY_MS = 1500L;
    private static final long MAX_RECONNECT_DELAY_MS = 10000L;
    private static RobotApiService instance;

    private enum LifecycleState {
        IDLE,
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        DISABLED
    }

    public interface ConnectionListener {
        void onRobotApiConnected(RobotApi robotApi);
        void onRobotApiDisconnected();
        void onRobotApiDisabled();
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<ConnectionListener> listeners = new CopyOnWriteArraySet<>();
    private final Set<String> owners = new CopyOnWriteArraySet<>();
    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            attemptReconnect();
        }
    };

    private Context appContext;
    private RobotApi robotApi;
    private boolean connecting = false;
    private boolean connected = false;
    private boolean manualDisconnect = false;
    private int reconnectAttempt = 0;
    private LifecycleState lifecycleState = LifecycleState.IDLE;
    private final ApiListener apiListener = new ApiListener() {
        @Override
        public void handleApiDisabled() {
            connecting = false;
            connected = false;
            lifecycleState = LifecycleState.DISABLED;
            Log.w(TAG, "RobotApi disabled");
            notifyDisabled();
            scheduleReconnectLocked("api_disabled");
        }

        @Override
        public void handleApiConnected() {
            connecting = false;
            connected = true;
            manualDisconnect = false;
            reconnectAttempt = 0;
            lifecycleState = LifecycleState.CONNECTED;
            robotApi = RobotApi.getInstance();
            mainHandler.removeCallbacks(reconnectRunnable);
            Log.i(TAG, "RobotApi connected");
            notifyConnected();
        }

        @Override
        public void handleApiDisconnected() {
            connecting = false;
            connected = false;
            lifecycleState = LifecycleState.DISCONNECTED;
            Log.w(TAG, "RobotApi disconnected");
            notifyDisconnected();
            scheduleReconnectLocked("api_disconnected");
        }
    };

    public static synchronized RobotApiService getInstance() {
        if (instance == null) {
            instance = new RobotApiService();
        }
        return instance;
    }

    public synchronized void retain(String owner, Context context) {
        if (owner != null && !owner.trim().isEmpty()) {
            owners.add(owner);
        }
        if (context != null) {
            appContext = context.getApplicationContext();
        }
        manualDisconnect = false;
        connect(appContext, null);
    }

    public synchronized void release(String owner) {
        if (owner != null && !owner.trim().isEmpty()) {
            owners.remove(owner);
        }
        if (owners.isEmpty()) {
            disconnectInternal(true);
        }
    }

    public synchronized void connect(Context context, ConnectionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
        if (context != null) {
            appContext = context.getApplicationContext();
        }

        if (robotApi == null) {
            robotApi = RobotApi.getInstance();
        }

        if (robotApi == null) {
            lifecycleState = LifecycleState.DISABLED;
            notifyDisabled();
            scheduleReconnectLocked("api_null");
            return;
        }

        if (connected) {
            lifecycleState = LifecycleState.CONNECTED;
            if (listener != null) {
                notifyConnected(listener);
            }
            return;
        }

        if (connecting || appContext == null) {
            return;
        }

        try {
            connecting = true;
            lifecycleState = LifecycleState.CONNECTING;
            robotApi.connectServer(appContext, apiListener);
        } catch (Exception e) {
            connecting = false;
            connected = false;
            lifecycleState = LifecycleState.DISCONNECTED;
            Log.e(TAG, "Could not connect RobotApi", e);
            notifyDisconnected();
            scheduleReconnectLocked("connect_exception");
        }
    }

    public synchronized void addConnectionListener(ConnectionListener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        if (connected) {
            notifyConnected(listener);
        }
    }

    public synchronized void removeConnectionListener(ConnectionListener listener) {
        if (listener == null) {
            return;
        }
        listeners.remove(listener);
        if (listeners.isEmpty() && owners.isEmpty()) {
            mainHandler.removeCallbacks(reconnectRunnable);
        }
    }

    public synchronized RobotApi getRobotApi() {
        if (robotApi == null) {
            robotApi = RobotApi.getInstance();
        }
        return robotApi;
    }

    public synchronized boolean isConnected() {
        return connected;
    }

    public synchronized boolean hasActiveOwners() {
        return !owners.isEmpty();
    }

    public synchronized void disconnect() {
        owners.clear();
        disconnectInternal(true);
    }

    private synchronized void attemptReconnect() {
        if (appContext != null && !connected && !connecting && shouldReconnectLocked()) {
            connect(appContext, null);
        }
    }

    private synchronized boolean shouldReconnectLocked() {
        return !manualDisconnect && (!owners.isEmpty() || !listeners.isEmpty());
    }

    private synchronized void scheduleReconnectLocked(String reason) {
        if (!shouldReconnectLocked()) {
            return;
        }
        long delayMs = Math.min(MAX_RECONNECT_DELAY_MS, BASE_RECONNECT_DELAY_MS * (1L << Math.min(reconnectAttempt, 3)));
        reconnectAttempt++;
        mainHandler.removeCallbacks(reconnectRunnable);
        Log.i(TAG, "Scheduling RobotApi reconnect in " + delayMs + "ms due to " + reason + " (attempt=" + reconnectAttempt + ")");
        mainHandler.postDelayed(reconnectRunnable, delayMs);
    }

    private synchronized void disconnectInternal(boolean manual) {
        manualDisconnect = manual;
        connecting = false;
        connected = false;
        lifecycleState = LifecycleState.IDLE;
        mainHandler.removeCallbacks(reconnectRunnable);
        try {
            if (robotApi != null) {
                robotApi.disconnectApi();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not disconnect RobotApi", e);
        }
    }

    private void notifyConnected() {
        if (lifecycleState != LifecycleState.CONNECTED) {
            return;
        }
        for (ConnectionListener listener : listeners) {
            notifyConnected(listener);
        }
    }

    private void notifyConnected(ConnectionListener listener) {
        if (listener == null) {
            return;
        }
        RobotApi api = getRobotApi();
        mainHandler.post(() -> listener.onRobotApiConnected(api));
    }

    private void notifyDisconnected() {
        if (lifecycleState != LifecycleState.DISCONNECTED) {
            return;
        }
        for (ConnectionListener listener : listeners) {
            if (listener != null) {
                mainHandler.post(listener::onRobotApiDisconnected);
            }
        }
    }

    private void notifyDisabled() {
        if (lifecycleState != LifecycleState.DISABLED) {
            return;
        }
        for (ConnectionListener listener : listeners) {
            if (listener != null) {
                mainHandler.post(listener::onRobotApiDisabled);
            }
        }
    }

}
