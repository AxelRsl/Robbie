package com.robbie.platform.robot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import com.ainirobot.coreservice.client.ApiListener;
import com.ainirobot.coreservice.client.RobotApi;
import com.robbie.platform.vision.OrionPersonData;
import com.robbie.platform.vision.OrionVisionSdkClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    public interface VisionProbeListener {
        void onVisionProbeConnected();
        void onVisionProbeDisconnected();
        void onVisionProbeData(List<OrionPersonData> persons);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<ConnectionListener> listeners = new CopyOnWriteArraySet<>();
    private final Set<VisionProbeListener> visionProbeListeners = new CopyOnWriteArraySet<>();
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
    private OrionVisionSdkClient visionSdkClient;
    private boolean visionProbeRequested = false;
    private boolean visionDataConnected = false;
    private boolean visionSurfaceConnected = false;
    private List<OrionPersonData> latestVisionPersons = Collections.emptyList();
    private long latestVisionUpdateElapsedMs = 0L;
    private String lastVisionSummary = "";

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
            ensureVisionProbeConnectedLocked("robot_api_connected");
            notifyConnected();
        }

        @Override
        public void handleApiDisconnected() {
            connecting = false;
            connected = false;
            lifecycleState = LifecycleState.DISCONNECTED;
            Log.w(TAG, "RobotApi disconnected");
            disconnectVisionProbeLocked("robot_api_disconnected");
            notifyDisconnected();
            scheduleReconnectLocked("api_disconnected");
        }
    };

    private final OrionVisionSdkClient.Listener visionSdkListener = new OrionVisionSdkClient.Listener() {
        @Override
        public void onVisionDataConnected() {
            synchronized (RobotApiService.this) {
                visionDataConnected = true;
                Log.i(TAG, "VisionSdk IVisionData connected");
                if (visionSdkClient != null) {
                    boolean callbackRegistered = visionSdkClient.registerDataCallback();
                    Log.i(TAG, "VisionSdk registerDataCallback=" + callbackRegistered);
                }
            }
            notifyVisionProbeConnected();
        }

        @Override
        public void onVisionDataDisconnected() {
            synchronized (RobotApiService.this) {
                visionDataConnected = false;
                latestVisionPersons = Collections.emptyList();
                latestVisionUpdateElapsedMs = 0L;
                lastVisionSummary = "";
                Log.w(TAG, "VisionSdk IVisionData disconnected");
            }
            notifyVisionProbeDisconnected();
        }

        @Override
        public void onSurfaceShareConnected() {
            synchronized (RobotApiService.this) {
                visionSurfaceConnected = true;
                Log.i(TAG, "VisionSdk surface share connected");
                if (visionSdkClient != null) {
                    boolean callbackRegistered = visionSdkClient.registerSurfaceCallback();
                    Log.i(TAG, "VisionSdk registerSurfaceCallback=" + callbackRegistered);
                }
            }
        }

        @Override
        public void onSurfaceShareDisconnected() {
            synchronized (RobotApiService.this) {
                visionSurfaceConnected = false;
                Log.w(TAG, "VisionSdk surface share disconnected");
            }
        }

        @Override
        public void onPersonData(List<OrionPersonData> persons) {
            synchronized (RobotApiService.this) {
                latestVisionPersons = persons == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(persons));
                latestVisionUpdateElapsedMs = android.os.SystemClock.elapsedRealtime();
                maybeLogVisionPersonsLocked(latestVisionPersons);
            }
            notifyVisionProbeData(persons == null ? Collections.emptyList() : persons);
        }

        @Override
        public void onExposure(String exposure) {
            Log.d(TAG, "VisionSdk exposure=" + exposure);
        }

        @Override
        public void onVisionState(String state, String message) {
            Log.i(TAG, "VisionSdk state=" + state + " message=" + message);
        }

        @Override
        public void onSurfaceStop(int reason) {
            Log.w(TAG, "VisionSdk surface stop reason=" + reason);
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

    public synchronized void startVisionSdkProbe(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
        visionProbeRequested = true;
        ensureVisionProbeConnectedLocked("startVisionSdkProbe");
    }

    public synchronized void stopVisionSdkProbe() {
        visionProbeRequested = false;
        disconnectVisionProbeLocked("stopVisionSdkProbe");
    }

    public synchronized boolean isVisionProbeConnected() {
        return visionDataConnected;
    }

    public synchronized List<OrionPersonData> getLatestVisionPersons() {
        return latestVisionPersons;
    }

    public synchronized long getLatestVisionUpdateElapsedMs() {
        return latestVisionUpdateElapsedMs;
    }

    public synchronized boolean setVisionTrackFaceEnabled(boolean enabled) {
        if (visionSdkClient == null) {
            return false;
        }
        return visionSdkClient.trackFace(enabled);
    }

    public synchronized boolean setVisionPersonLimit(int limit) {
        if (visionSdkClient == null) {
            return false;
        }
        return visionSdkClient.setPersonLimit(limit);
    }

    public synchronized boolean setVisionDetectOtherFaceOnTrack(boolean enabled) {
        if (visionSdkClient == null) {
            return false;
        }
        return visionSdkClient.setDetectOtherFaceOnTrack(enabled);
    }

    public synchronized boolean bindVisionSurface(Surface surface) {
        if (visionSdkClient == null) {
            return false;
        }
        return visionSdkClient.bindSurface(surface);
    }

    public synchronized boolean setVisionPreviewSurface(Surface surface) {
        if (visionSdkClient == null || surface == null) {
            return false;
        }
        return visionSdkClient.setStreamSurface(surface);
    }

    public synchronized boolean showVisionPreviewSurface(Surface surface, boolean show) {
        if (visionSdkClient == null || surface == null) {
            return false;
        }
        return visionSdkClient.showSurface(surface, show);
    }

    public synchronized boolean clearVisionPreviewSurface() {
        if (visionSdkClient == null) {
            return false;
        }
        return visionSdkClient.unSetStreamSurface();
    }

    public synchronized void addVisionProbeListener(VisionProbeListener listener) {
        if (listener == null) {
            return;
        }
        visionProbeListeners.add(listener);
        if (visionDataConnected) {
            mainHandler.post(listener::onVisionProbeConnected);
        }
        List<OrionPersonData> snapshot = latestVisionPersons;
        if (snapshot != null && !snapshot.isEmpty()) {
            List<OrionPersonData> copy = snapshot;
            mainHandler.post(() -> listener.onVisionProbeData(copy));
        }
    }

    public synchronized void removeVisionProbeListener(VisionProbeListener listener) {
        if (listener == null) {
            return;
        }
        visionProbeListeners.remove(listener);
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
        disconnectVisionProbeLocked("disconnectInternal");
        try {
            if (robotApi != null) {
                robotApi.disconnectApi();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not disconnect RobotApi", e);
        }
    }

    private synchronized void ensureVisionProbeConnectedLocked(String reason) {
        if (!visionProbeRequested || appContext == null) {
            return;
        }
        if (visionSdkClient == null) {
            visionSdkClient = new OrionVisionSdkClient(appContext);
            visionSdkClient.setListener(visionSdkListener);
        }
        boolean visionBind = visionSdkClient.bindVisionData();
        boolean surfaceBind = visionSdkClient.bindSurfaceShare();
        Log.i(TAG, "ensureVisionProbeConnected reason=" + reason + " visionBind=" + visionBind + " surfaceBind=" + surfaceBind);
    }

    private synchronized void disconnectVisionProbeLocked(String reason) {
        if (visionSdkClient == null) {
            return;
        }
        Log.i(TAG, "disconnectVisionProbe reason=" + reason);
        visionSdkClient.disconnect();
        visionSdkClient = null;
        visionDataConnected = false;
        visionSurfaceConnected = false;
        latestVisionPersons = Collections.emptyList();
        latestVisionUpdateElapsedMs = 0L;
        lastVisionSummary = "";
    }

    private void maybeLogVisionPersonsLocked(List<OrionPersonData> persons) {
        String summary = buildVisionSummary(persons);
        if (!summary.equals(lastVisionSummary)) {
            lastVisionSummary = summary;
            Log.i(TAG, summary);
        }
    }

    private String buildVisionSummary(List<OrionPersonData> persons) {
        if (persons == null || persons.isEmpty()) {
            return "VisionSdk persons=0";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("VisionSdk persons=").append(persons.size()).append(' ');
        int max = Math.min(persons.size(), 3);
        for (int i = 0; i < max; i++) {
            OrionPersonData person = persons.get(i);
            builder.append("[#")
                    .append(i)
                    .append(" id=")
                    .append(person.getId())
                    .append(" assoc=")
                    .append(person.getAssociateId())
                    .append(" face=")
                    .append(person.getWithFace())
                    .append(" body=")
                    .append(person.getWithBody())
                    .append(" dist=")
                    .append(person.getDistance())
                    .append(" angle=")
                    .append(person.getAngle())
                    .append(']');
        }
        return builder.toString();
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

    private void notifyVisionProbeConnected() {
        for (VisionProbeListener listener : visionProbeListeners) {
            if (listener != null) {
                mainHandler.post(listener::onVisionProbeConnected);
            }
        }
    }

    private void notifyVisionProbeDisconnected() {
        for (VisionProbeListener listener : visionProbeListeners) {
            if (listener != null) {
                mainHandler.post(listener::onVisionProbeDisconnected);
            }
        }
    }

    private void notifyVisionProbeData(List<OrionPersonData> persons) {
        List<OrionPersonData> snapshot = persons == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(persons));
        for (VisionProbeListener listener : visionProbeListeners) {
            if (listener != null) {
                mainHandler.post(() -> listener.onVisionProbeData(snapshot));
            }
        }
    }
}
