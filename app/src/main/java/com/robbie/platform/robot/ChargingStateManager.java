package com.robbie.platform.robot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.StatusListener;
import com.ainirobot.coreservice.client.listener.ActionListener;
import com.ainirobot.coreservice.client.listener.CommandListener;

import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ChargingStateManager implements RobotApiService.ConnectionListener {
    private static final String TAG = "ChargingStateManager";
    private static final int LOW_BATTERY_THRESHOLD = 20;
    private static final int CHARGE_TIMEOUT_MS = 120000;
    private static final long CHARGE_CONFIRMATION_TIMEOUT_MS = 10000L;
    private static final long BATTERY_EVENT_DEBOUNCE_MS = 750L;
    private static final long MALFORMED_PAYLOAD_LOG_DEBOUNCE_MS = 5000L;
    private static final long TRANSITION_LOCK_TIMEOUT_MS = 45000L;
    private static final int CHARGING_TRUE_CONFIRMATION_EVENTS = 2;
    private static final int CHARGING_FALSE_CONFIRMATION_EVENTS = 2;
    private static final long CHARGING_FALSE_GRACE_MS = 3500L;
    private static ChargingStateManager instance;

    public static class Snapshot {
        public final String status;
        public final String message;
        public final int batteryLevel;
        public final boolean isCharging;
        public final boolean isNavigatingToCharger;
        public final boolean robotApiConnected;
        public final boolean autoTriggered;

        public Snapshot(String status, String message, int batteryLevel, boolean isCharging,
                        boolean isNavigatingToCharger, boolean robotApiConnected, boolean autoTriggered) {
            this.status = status;
            this.message = message;
            this.batteryLevel = batteryLevel;
            this.isCharging = isCharging;
            this.isNavigatingToCharger = isNavigatingToCharger;
            this.robotApiConnected = robotApiConnected;
            this.autoTriggered = autoTriggered;
        }
    }

    public interface Listener {
        void onChargingStateChanged(Snapshot snapshot);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final Set<String> lifecycleOwners = new CopyOnWriteArraySet<>();
    private final RobotApiService robotApiService = RobotApiService.getInstance();

    private Context appContext;
    private StatusListener batteryStatusListener;
    private boolean started = false;
    private boolean batteryListenerRegistered = false;
    private boolean autoChargeArmed = true;
    private int chargeReqId = 8100;
    private long activeOperationToken = 0L;
    private String activeOperation = "idle";
    private boolean transitionLocked = false;
    private long transitionLockStartedAtMs = 0L;
    private long lastBatteryDispatchAtMs = 0L;
    private long lastBatteryPayloadAtMs = 0L;
    private long lastMalformedPayloadLogAtMs = 0L;
    private String lastBatteryPayload = "";
    private long lastChargingTrueAtMs = 0L;
    private long lastChargingFalseAtMs = 0L;
    private int chargingTrueSignalCount = 0;
    private int chargingFalseSignalCount = 0;
    private Boolean pendingStartAutoTriggered = null;
    private boolean pendingStopOnReconnect = false;
    private boolean awaitingChargeConfirmation = false;
    private Runnable chargeConfirmationTimeoutRunnable;

    private String status = "idle";
    private String message = "";
    private int batteryLevel = -1;
    private boolean isCharging = false;
    private boolean isNavigatingToCharger = false;
    private boolean robotApiConnected = false;
    private boolean autoTriggered = false;
    private String lastSignature = "";

    public static synchronized ChargingStateManager getInstance() {
        if (instance == null) {
            instance = new ChargingStateManager();
        }
        return instance;
    }

    public synchronized void start(Context context) {
        start(context, TAG);
    }

    public synchronized void start(Context context, String owner) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
        String normalizedOwner = normalizeOwner(owner);
        lifecycleOwners.add(normalizedOwner);
        robotApiService.retain(buildServiceOwner(normalizedOwner), appContext);
        if (started) {
            if (robotApiService.isConnected()) {
                registerBatteryListener(robotApiService.getRobotApi());
            }
            return;
        }
        started = true;
        robotApiService.addConnectionListener(this);
        robotApiService.connect(appContext, this);
    }

    public synchronized void stop() {
        stop(TAG);
    }

    public synchronized void stop(String owner) {
        String normalizedOwner = normalizeOwner(owner);
        lifecycleOwners.remove(normalizedOwner);
        robotApiService.release(buildServiceOwner(normalizedOwner));
        if (!lifecycleOwners.isEmpty()) {
            Log.d(TAG, "ChargingStateManager stop skipped, active owners=" + lifecycleOwners.size());
            return;
        }
        unregisterBatteryListener();
        robotApiService.removeConnectionListener(this);
        started = false;
        transitionLocked = false;
        activeOperation = "idle";
        pendingStartAutoTriggered = null;
        pendingStopOnReconnect = false;
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        notifyListener(listener, snapshot());
    }

    public void removeListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.remove(listener);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(status, message, batteryLevel, isCharging, isNavigatingToCharger, robotApiConnected, autoTriggered);
    }

    public synchronized void requestStartCharging(boolean autoTriggeredRequest) {
        releaseStaleTransitionLockIfNeeded();
        cancelChargeConfirmationTimeout();
        RobotApi api = robotApiService.getRobotApi();
        if (!robotApiService.isConnected() || api == null) {
            pendingStartAutoTriggered = autoTriggeredRequest;
            pendingStopOnReconnect = false;
            updateDisconnectedSnapshot("api_disconnected", "RobotApi no esta conectado", false, true, autoTriggeredRequest);
            return;
        }
        if (transitionLocked) {
            Log.i(TAG, "Ignoring start charging request while transition lock active for operation=" + activeOperation);
            return;
        }
        if (isCharging || isNavigatingToCharger) {
            Log.d(TAG, "Ignoring start charging request because charging state is already active");
            return;
        }

        final long operationToken = beginTransitionLock("start");
        pendingStartAutoTriggered = null;
        pendingStopOnReconnect = false;
        resetChargingSignalTracking();
        autoTriggered = autoTriggeredRequest;
        isNavigatingToCharger = true;
        updateState("navigating_to_charger",
            autoTriggeredRequest ? "Bateria baja, yendo a cargar..." : "Yendo al cargador...",
            batteryLevel, false, true, true, autoTriggeredRequest);

        try {
            api.startNaviToAutoChargeAction(chargeReqId++, CHARGE_TIMEOUT_MS, 0.3, 30000L, new ActionListener() {
                @Override
                public void onResult(int result, String responseString) throws RemoteException {
                    if (isStaleOperation(operationToken, "start")) {
                        Log.w(TAG, "Ignoring stale start charging result callback");
                        return;
                    }
                    finishTransitionLock(operationToken, "start");
                    if (result == Definition.RESULT_OK) {
                        isNavigatingToCharger = true;
                        isCharging = false;
                        scheduleChargeConfirmationTimeout(operationToken);
                        updateState("charging_connecting", "Acoplando al cargador...", batteryLevel, false, true, robotApiConnected, autoTriggered);
                    } else {
                        cancelChargeConfirmationTimeout();
                        isNavigatingToCharger = false;
                        isCharging = false;
                        updateState("charge_failed", "No se pudo llegar al cargador", batteryLevel, false, false, robotApiConnected, autoTriggered);
                        autoTriggered = false;
                    }
                }

                @Override
                public void onError(int errorCode, String errorString) throws RemoteException {
                    if (isStaleOperation(operationToken, "start")) {
                        Log.w(TAG, "Ignoring stale start charging error callback");
                        return;
                    }
                    finishTransitionLock(operationToken, "start");
                    cancelChargeConfirmationTimeout();
                    isNavigatingToCharger = false;
                    isCharging = false;
                    updateState("charge_failed", "Error: " + errorString, batteryLevel, false, false, robotApiConnected, autoTriggered);
                    autoTriggered = false;
                }

                @Override
                public void onStatusUpdate(int statusCode, String data) throws RemoteException {
                    if (isStaleOperation(operationToken, "start")) {
                        return;
                    }
                    if (statusCode == Definition.STATUS_NAVI_AVOID) {
                        updateState("charge_obstacle", "Hay un obstaculo en el camino", batteryLevel, isCharging, isNavigatingToCharger, robotApiConnected, autoTriggered);
                    } else if (statusCode == Definition.STATUS_NAVI_AVOID_END) {
                        updateState("navigating_to_charger", autoTriggered ? "Bateria baja, yendo a cargar..." : "Yendo al cargador...", batteryLevel, isCharging, isNavigatingToCharger, robotApiConnected, autoTriggered);
                    } else if (statusCode == Definition.STATUS_NAVI_GLOBAL_PATH_FAILED) {
                        finishTransitionLock(operationToken, "start");
                        cancelChargeConfirmationTimeout();
                        isNavigatingToCharger = false;
                        updateState("charge_failed", "Ruta al cargador no encontrada", batteryLevel, false, false, robotApiConnected, autoTriggered);
                    } else if (statusCode == Definition.STATUS_NAVI_OUT_MAP) {
                        finishTransitionLock(operationToken, "start");
                        cancelChargeConfirmationTimeout();
                        isNavigatingToCharger = false;
                        updateState("charge_failed", "Cargador fuera del mapa", batteryLevel, false, false, robotApiConnected, autoTriggered);
                    }
                }
            });
        } catch (Exception e) {
            finishTransitionLock(operationToken, "start");
            cancelChargeConfirmationTimeout();
            isNavigatingToCharger = false;
            isCharging = false;
            Log.e(TAG, "Error starting auto charge", e);
            updateState("charge_failed", "Error al intentar ir a cargar", batteryLevel, false, false, robotApiConnected, autoTriggeredRequest);
            autoTriggered = false;
        }
    }

    public synchronized void requestStopCharging() {
        releaseStaleTransitionLockIfNeeded();
        cancelChargeConfirmationTimeout();
        RobotApi api = robotApiService.getRobotApi();
        boolean wasCharging = isCharging || "charging".equals(status);
        boolean wasNavigating = isNavigatingToCharger || "navigating_to_charger".equals(status) || "charge_obstacle".equals(status);
        final long operationToken = beginTransitionLock("stop");
        pendingStartAutoTriggered = null;
        pendingStopOnReconnect = false;
        if (!robotApiService.isConnected() || api == null) {
            pendingStopOnReconnect = true;
            isCharging = false;
            isNavigatingToCharger = false;
            autoTriggered = false;
            finishTransitionLock(operationToken, "stop");
            updateDisconnectedSnapshot("charge_stopped", "Carga detenida", false, false, false);
            return;
        }

        try {
            api.stopAutoChargeAction(chargeReqId++, true);
        } catch (Exception e) {
            Log.w(TAG, "Error stopping auto charge action", e);
        }

        isCharging = false;
        isNavigatingToCharger = false;
        resetChargingSignalTracking();
        autoTriggered = false;
        updateState("charge_stopped", "Carga detenida", batteryLevel, false, false, robotApiConnected, false);

        if (!wasCharging) {
            finishTransitionLock(operationToken, "stop");
            mainHandler.postDelayed(() -> {
                synchronized (ChargingStateManager.this) {
                    if (!isOperationSuperseded(operationToken)) {
                        updateState("idle", "", batteryLevel, false, false, robotApiConnected, false);
                    }
                }
            }, wasNavigating ? 400L : 0L);
            return;
        }

        try {
            api.disableBattery();
            api.leaveChargingPile(chargeReqId++, 0.3f, 0.5f, new CommandListener() {
                @Override
                public void onResult(int result, String message) {
                    if (isStaleOperation(operationToken, "stop")) {
                        Log.w(TAG, "Ignoring stale stop charging result callback");
                        return;
                    }
                    try {
                        api.enableBattery();
                    } catch (Exception ignored) {
                    }
                    finishTransitionLock(operationToken, "stop");
                    if (result == Definition.RESULT_OK) {
                        updateState("idle", "", batteryLevel, false, false, robotApiConnected, false);
                    } else {
                        updateState("idle", "", batteryLevel, false, false, robotApiConnected, false);
                    }
                }
            });
        } catch (Exception e) {
            finishTransitionLock(operationToken, "stop");
            Log.w(TAG, "Error leaving charging pile", e);
            try {
                api.enableBattery();
            } catch (Exception ignored) {
            }
            updateState("idle", "", batteryLevel, false, false, robotApiConnected, false);
        }
    }

    public synchronized void disableBatteryUi() {
        RobotApi api = robotApiService.getRobotApi();
        if (!robotApiService.isConnected() || api == null) {
            return;
        }
        try {
            api.disableBattery();
        } catch (Exception e) {
            Log.w(TAG, "Error disabling battery UI", e);
        }
    }

    @Override
    public synchronized void onRobotApiConnected(RobotApi robotApi) {
        robotApiConnected = true;
        registerBatteryListener(robotApi);
        safeRefreshBatteryLevel(robotApi);
        updateState(status, message, batteryLevel, isCharging, isNavigatingToCharger, true, autoTriggered);
        if (pendingStopOnReconnect) {
            mainHandler.post(this::requestStopCharging);
        } else if (pendingStartAutoTriggered != null && !isCharging && !isNavigatingToCharger) {
            final boolean nextAutoTriggered = pendingStartAutoTriggered;
            mainHandler.post(() -> requestStartCharging(nextAutoTriggered));
        }
    }

    @Override
    public synchronized void onRobotApiDisconnected() {
        robotApiConnected = false;
        batteryListenerRegistered = false;
        boolean preservePendingStart = false;
        if (transitionLocked && "start".equals(activeOperation)) {
            pendingStartAutoTriggered = autoTriggered;
            preservePendingStart = true;
        } else if (transitionLocked && "stop".equals(activeOperation)) {
            pendingStopOnReconnect = true;
        }
        updateDisconnectedSnapshot("api_disconnected", "RobotApi desconectado", true, preservePendingStart, autoTriggered);
    }

    @Override
    public synchronized void onRobotApiDisabled() {
        robotApiConnected = false;
        batteryListenerRegistered = false;
        boolean preservePendingStart = false;
        if (transitionLocked && "start".equals(activeOperation)) {
            pendingStartAutoTriggered = autoTriggered;
            preservePendingStart = true;
        } else if (transitionLocked && "stop".equals(activeOperation)) {
            pendingStopOnReconnect = true;
        }
        updateDisconnectedSnapshot("api_disabled", "RobotApi deshabilitado", true, preservePendingStart, autoTriggered);
    }

    private void registerBatteryListener(RobotApi robotApi) {
        if (robotApi == null || batteryListenerRegistered) {
            return;
        }
        if (batteryStatusListener == null) {
            batteryStatusListener = new StatusListener() {
                @Override
                public void onStatusUpdate(String type, String data) throws RemoteException {
                    handleBatteryUpdate(data);
                }
            };
        }
        try {
            robotApi.registerStatusListener(Definition.STATUS_BATTERY, batteryStatusListener);
            batteryListenerRegistered = true;
        } catch (Exception e) {
            Log.e(TAG, "Error registering battery listener", e);
        }
    }

    private synchronized void unregisterBatteryListener() {
        RobotApi api = robotApiService.getRobotApi();
        if (batteryStatusListener == null || api == null || !batteryListenerRegistered) {
            batteryListenerRegistered = false;
            return;
        }
        try {
            api.unregisterStatusListener(batteryStatusListener);
        } catch (Exception e) {
            Log.w(TAG, "Error unregistering battery listener", e);
        }
        batteryListenerRegistered = false;
    }

    private synchronized void scheduleChargeConfirmationTimeout(long operationToken) {
        cancelChargeConfirmationTimeout();
        awaitingChargeConfirmation = true;
        chargeConfirmationTimeoutRunnable = () -> {
            synchronized (ChargingStateManager.this) {
                if (!awaitingChargeConfirmation || isCharging || operationToken != activeOperationToken) {
                    return;
                }
                awaitingChargeConfirmation = false;
                isNavigatingToCharger = false;
                autoTriggered = false;
                updateState("charge_failed", "No se confirmo carga real", batteryLevel, false, false, robotApiConnected, false);
            }
        };
        mainHandler.postDelayed(chargeConfirmationTimeoutRunnable, CHARGE_CONFIRMATION_TIMEOUT_MS);
    }

    private synchronized void cancelChargeConfirmationTimeout() {
        awaitingChargeConfirmation = false;
        if (chargeConfirmationTimeoutRunnable != null) {
            mainHandler.removeCallbacks(chargeConfirmationTimeoutRunnable);
            chargeConfirmationTimeoutRunnable = null;
        }
    }

    private synchronized void handleBatteryUpdate(String data) {
        long now = SystemClock.elapsedRealtime();
        if (data == null || data.trim().isEmpty()) {
            logMalformedPayload("Battery payload is empty", now);
            return;
        }
        if (data.equals(lastBatteryPayload) && (now - lastBatteryPayloadAtMs) < BATTERY_EVENT_DEBOUNCE_MS) {
            return;
        }
        lastBatteryPayload = data;
        lastBatteryPayloadAtMs = now;

        try {
            JSONObject json = new JSONObject(data);
            int nextBatteryLevel = parseBatteryLevel(json);
            boolean rawIsCharging = parseChargingFlag(json);
            boolean previouslyCharging = isCharging;
            boolean batteryChanged = nextBatteryLevel != batteryLevel;
            boolean chargingChanged = rawIsCharging != isCharging;

            if (!batteryChanged && !chargingChanged && (now - lastBatteryDispatchAtMs) < BATTERY_EVENT_DEBOUNCE_MS) {
                return;
            }

            updateChargingSignalTracking(rawIsCharging, now);
            boolean nextIsCharging = resolveChargingState(rawIsCharging, now, previouslyCharging);
            batteryLevel = nextBatteryLevel;
            isCharging = nextIsCharging;
            lastBatteryDispatchAtMs = now;

            if (nextBatteryLevel >= LOW_BATTERY_THRESHOLD) {
                autoChargeArmed = true;
            }

            if (nextIsCharging) {
                cancelChargeConfirmationTimeout();
                finishTransitionLock(activeOperationToken, activeOperation);
            }

            if (nextIsCharging) {
                isNavigatingToCharger = false;
                updateState("charging", "Cargando...", batteryLevel, true, false, robotApiConnected, autoTriggered);
            } else if (!isNavigatingToCharger && ("charging".equals(status) || "charging_connecting".equals(status))) {
                updateState("idle", "", batteryLevel, false, false, robotApiConnected, false);
            } else {
                updateState(status, message, batteryLevel, isCharging, isNavigatingToCharger, robotApiConnected, autoTriggered);
            }

            if (nextBatteryLevel >= 0 && nextBatteryLevel < LOW_BATTERY_THRESHOLD && !nextIsCharging && !isNavigatingToCharger && !"charging".equals(status) && autoChargeArmed) {
                autoChargeArmed = false;
                mainHandler.post(() -> requestStartCharging(true));
            }
        } catch (Exception e) {
            logMalformedPayload("Invalid battery payload: " + data, now);
        }
    }

    private int parseBatteryLevel(JSONObject json) {
        if (json.has("level")) return json.optInt("level", -1);
        if (json.has("batteryLevel")) return json.optInt("batteryLevel", -1);
        if (json.has("batteryPercent")) return json.optInt("batteryPercent", -1);
        return -1;
    }

    private boolean parseChargingFlag(JSONObject json) {
        if (json.has("isCharging")) return json.optBoolean("isCharging", false);
        if (json.has("charging")) return json.optBoolean("charging", false);
        if (json.has("chargeStatus")) {
            Object raw = json.opt("chargeStatus");
            if (raw instanceof Number) {
                return ((Number) raw).intValue() > 0;
            }
            if (raw instanceof String) {
                String value = ((String) raw).toLowerCase();
                return "1".equals(value) || "charging".equals(value) || "true".equals(value);
            }
        }
        return false;
    }

    private synchronized void updateChargingSignalTracking(boolean chargingSignal, long now) {
        if (chargingSignal) {
            lastChargingTrueAtMs = now;
            chargingTrueSignalCount = Math.min(chargingTrueSignalCount + 1, CHARGING_TRUE_CONFIRMATION_EVENTS + 1);
            chargingFalseSignalCount = 0;
            return;
        }
        lastChargingFalseAtMs = now;
        chargingFalseSignalCount = Math.min(chargingFalseSignalCount + 1, CHARGING_FALSE_CONFIRMATION_EVENTS + 1);
        chargingTrueSignalCount = 0;
    }

    private synchronized boolean resolveChargingState(boolean chargingSignal, long now, boolean previouslyCharging) {
        if (chargingSignal) {
            if (previouslyCharging) {
                return true;
            }
            return chargingTrueSignalCount >= CHARGING_TRUE_CONFIRMATION_EVENTS;
        }
        if (!previouslyCharging) {
            return false;
        }
        if ("stop".equals(activeOperation) || "charge_stopped".equals(status)) {
            return false;
        }
        boolean withinGraceWindow = lastChargingTrueAtMs > 0L && (now - lastChargingTrueAtMs) < CHARGING_FALSE_GRACE_MS;
        if (withinGraceWindow) {
            return true;
        }
        return chargingFalseSignalCount < CHARGING_FALSE_CONFIRMATION_EVENTS;
    }

    private synchronized void resetChargingSignalTracking() {
        lastChargingTrueAtMs = 0L;
        lastChargingFalseAtMs = 0L;
        chargingTrueSignalCount = 0;
        chargingFalseSignalCount = 0;
    }

    private synchronized void updateState(String nextStatus, String nextMessage, int nextBatteryLevel,
                                          boolean nextIsCharging, boolean nextIsNavigatingToCharger,
                                          boolean nextRobotApiConnected, boolean nextAutoTriggered) {
        releaseStaleTransitionLockIfNeeded();
        status = nextStatus == null ? "idle" : nextStatus;
        message = nextMessage == null ? "" : nextMessage;
        batteryLevel = nextBatteryLevel;
        isCharging = nextIsCharging;
        isNavigatingToCharger = nextIsNavigatingToCharger;
        robotApiConnected = nextRobotApiConnected;
        autoTriggered = nextAutoTriggered;

        Snapshot snapshot = snapshot();
        String signature = snapshot.status + "|" + snapshot.message + "|" + snapshot.batteryLevel + "|"
            + snapshot.isCharging + "|" + snapshot.isNavigatingToCharger + "|" + snapshot.robotApiConnected + "|" + snapshot.autoTriggered;
        if (signature.equals(lastSignature)) {
            return;
        }
        lastSignature = signature;
        for (Listener listener : listeners) {
            notifyListener(listener, snapshot);
        }
    }

    private void notifyListener(Listener listener, Snapshot snapshot) {
        if (listener == null || snapshot == null) {
            return;
        }
        mainHandler.post(() -> {
            try {
                listener.onChargingStateChanged(snapshot);
            } catch (Exception e) {
                Log.e(TAG, "ChargingStateManager listener failure", e);
            }
        });
    }

    private synchronized String normalizeOwner(String owner) {
        if (owner == null || owner.trim().isEmpty()) {
            return TAG;
        }
        return owner.trim();
    }

    private String buildServiceOwner(String owner) {
        return "charging-manager:" + owner;
    }

    private synchronized long beginTransitionLock(String operation) {
        activeOperationToken++;
        activeOperation = operation == null ? "unknown" : operation;
        transitionLocked = true;
        transitionLockStartedAtMs = SystemClock.elapsedRealtime();
        return activeOperationToken;
    }

    private synchronized void finishTransitionLock(long token, String operation) {
        if (token != activeOperationToken) {
            return;
        }
        if (operation != null && !operation.equals(activeOperation)) {
            return;
        }
        transitionLocked = false;
        activeOperation = "idle";
        transitionLockStartedAtMs = 0L;
    }

    private synchronized boolean isStaleOperation(long token, String operation) {
        releaseStaleTransitionLockIfNeeded();
        if (token != activeOperationToken) {
            return true;
        }
        if (!transitionLocked) {
            return true;
        }
        return operation != null && !operation.equals(activeOperation);
    }

    private synchronized boolean isOperationSuperseded(long token) {
        return token != activeOperationToken;
    }

    private synchronized void releaseStaleTransitionLockIfNeeded() {
        if (!transitionLocked) {
            return;
        }
        long elapsed = SystemClock.elapsedRealtime() - transitionLockStartedAtMs;
        if (elapsed > TRANSITION_LOCK_TIMEOUT_MS) {
            Log.w(TAG, "Releasing stale transition lock for operation=" + activeOperation + " after " + elapsed + "ms");
            transitionLocked = false;
            activeOperation = "idle";
            transitionLockStartedAtMs = 0L;
        }
    }

    private synchronized void updateDisconnectedSnapshot(String disconnectedStatus, String disconnectedMessage,
                                                          boolean preserveActiveFlags, boolean preservePendingStart,
                                                          boolean nextAutoTriggered) {
        boolean nextCharging = preserveActiveFlags ? isCharging : false;
        boolean nextNavigating = preserveActiveFlags ? isNavigatingToCharger : false;
        String nextStatus = preserveActiveFlags && (isCharging || isNavigatingToCharger) ? status : disconnectedStatus;
        String nextMessage = preserveActiveFlags && (isCharging || isNavigatingToCharger) ? message : disconnectedMessage;
        if (!preservePendingStart) {
            pendingStartAutoTriggered = null;
        }
        updateState(nextStatus, nextMessage, batteryLevel, nextCharging, nextNavigating, false, nextAutoTriggered);
    }

    private synchronized void safeRefreshBatteryLevel(RobotApi robotApi) {
        if (robotApi == null) {
            return;
        }
        try {
            int latestLevel = robotApi.getBatteryLevel();
            if (latestLevel >= 0) {
                batteryLevel = latestLevel;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not refresh battery level on reconnect", e);
        }
    }

    private void logMalformedPayload(String message, long now) {
        if ((now - lastMalformedPayloadLogAtMs) < MALFORMED_PAYLOAD_LOG_DEBOUNCE_MS) {
            return;
        }
        lastMalformedPayloadLogAtMs = now;
        Log.w(TAG, message);
    }
}
