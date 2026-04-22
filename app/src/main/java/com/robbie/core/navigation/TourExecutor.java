package com.robbie.core.navigation;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.ActionListener;
import com.robbie.platform.agent.IAgentBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ejecuta un tour (ruta) completo: navega a cada stop, habla el contenido,
 * espera el dwellTime y avanza al siguiente.
 */
public class TourExecutor {

    private static final String TAG = "TourExecutor";
    private static volatile TourExecutor sInstance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private IAgentBridge agentBridge;

    private boolean running = false;
    private boolean stopping = false;
    private int currentStopIndex = 0;
    private String currentRouteId;
    private String currentRouteName;
    private List<Map<String, Object>> stops = new ArrayList<>();
    private int navReqId = 7001;

    private final List<TourListener> listeners = new ArrayList<>();

    public interface TourListener {
        void onTourStarted(String routeId, String routeName);
        void onStopReached(int index, String stopName);
        void onTourCompleted(String routeId);
        void onTourError(String error);
        void onTourStopped();
    }

    private TourExecutor() {}

    public static TourExecutor getInstance() {
        if (sInstance == null) {
            synchronized (TourExecutor.class) {
                if (sInstance == null) {
                    sInstance = new TourExecutor();
                }
            }
        }
        return sInstance;
    }

    public void setAgentBridge(IAgentBridge bridge) {
        this.agentBridge = bridge;
    }

    /**
     * Inicia la ejecucion de un tour.
     * @param routeId   ID de la ruta
     * @param routeName Nombre de la ruta
     * @param routeStops Lista de stops (Map con keys: name, mapPosition, broadcastContent, dwellTime)
     */
    public void startTour(String routeId, String routeName, List<Map<String, Object>> routeStops) {
        if (running) {
            Log.w(TAG, "Tour already running, stop it first");
            return;
        }

        if (routeStops == null || routeStops.isEmpty()) {
            Log.e(TAG, "Cannot start tour with no stops");
            notifyError("No stops defined in tour");
            return;
        }

        this.currentRouteId = routeId;
        this.currentRouteName = routeName;
        this.stops = new ArrayList<>(routeStops);
        this.currentStopIndex = 0;
        this.running = true;
        this.stopping = false;

        Log.i(TAG, "Starting tour '" + routeName + "' with " + stops.size() + " stops");
        notifyStarted(routeId, routeName);

        // Speak introduction
        speak("Iniciando tour: " + routeName + ". Tenemos " + stops.size() + " paradas.");

        // Start navigating to first stop after intro speech
        handler.postDelayed(this::navigateToCurrentStop, 3000);
    }

    /**
     * Detiene el tour actual.
     */
    public void stopTour() {
        if (!running) return;
        stopping = true;
        running = false;

        try {
            RobotApi api = RobotApi.getInstance();
            if (api != null) {
                api.stopNavigation(navReqId++);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error stopping navigation", e);
        }

        handler.removeCallbacksAndMessages(null);
        speak("Tour detenido.");
        Log.i(TAG, "Tour stopped by user");
        notifyStopped();
    }

    private Runnable navTimeoutRunnable;

    private void navigateToCurrentStop() {
        if (!running || stopping) return;
        if (currentStopIndex >= stops.size()) {
            completeTour();
            return;
        }

        Map<String, Object> stop = stops.get(currentStopIndex);
        String stopName = getStr(stop, "name", "Parada " + (currentStopIndex + 1));
        String mapPosition = getStr(stop, "mapPosition", stopName);

        Log.i(TAG, "=== TOUR STOP " + (currentStopIndex + 1) + "/" + stops.size() + " ===");
        Log.i(TAG, "Stop name: " + stopName + ", mapPosition: " + mapPosition);
        speak("Dirigiendonos a " + stopName);

        try {
            RobotApi api = RobotApi.getInstance();
            if (api == null) {
                Log.e(TAG, "RobotApi is null!");
                notifyError("RobotApi not available");
                running = false;
                return;
            }

            // Per docs: error -6 means chassis is busy. Must stopNavigation first, then wait before re-starting.
            // Stop any previous navigation, wait 2s for chassis release, then start.
            try {
                api.stopNavigation(navReqId++);
                Log.d(TAG, "Called stopNavigation to release chassis before next stop");
            } catch (Exception e) {
                Log.w(TAG, "stopNavigation pre-release: " + e.getMessage());
            }

            // Wait for chassis to be fully released, then start navigation
            handler.postDelayed(() -> {
                if (!running || stopping) return;
                startNavigationToStop(api, stop, stopName, mapPosition);
            }, 2000);

        } catch (Exception e) {
            Log.e(TAG, "Exception navigating to stop", e);
            notifyError("Navigation error: " + e.getMessage());
            running = false;
        }
    }

    private void startNavigationToStop(RobotApi api, Map<String, Object> stop, String stopName, String mapPosition) {
        // Safety timeout: if no callback in 120s, skip this stop
        if (navTimeoutRunnable != null) {
            handler.removeCallbacks(navTimeoutRunnable);
        }
        navTimeoutRunnable = () -> {
            if (!running || stopping) return;
            Log.w(TAG, "Navigation TIMEOUT for: " + mapPosition + " — skipping");
            speak("No logre llegar a " + stopName + " a tiempo. Continuando.");
            try {
                api.stopNavigation(navReqId++);
            } catch (Exception ignored) {}
            handler.postDelayed(() -> {
                currentStopIndex++;
                navigateToCurrentStop();
            }, 2000);
        };
        handler.postDelayed(navTimeoutRunnable, 120000);

        final int thisReqId = navReqId++;
        Log.i(TAG, "Calling startNavigation reqId=" + thisReqId + " dest='" + mapPosition + "'");

        api.startNavigation(thisReqId, mapPosition, 0.2, 30000,
            new ActionListener() {
                @Override
                public void onResult(int status, String response) {
                    handler.removeCallbacks(navTimeoutRunnable);
                    Log.i(TAG, "onResult status=" + status + " response=" + response + " for: " + stopName);
                    if (!running || stopping) return;
                    notifyStopReached(currentStopIndex, stopName);
                    handler.post(() -> onArrived(stop));
                }

                @Override
                public void onError(int errorCode, String errorString) {
                    Log.e(TAG, "onError errorCode=" + errorCode + " errorString=" + errorString + " for: " + stopName);
                    // -6 = ACTION_RESPONSE_REQUEST_RES_ERROR: chassis busy from previous nav
                    // -1 = ACTION_RESPONSE_ALREADY_RUN: nav already running
                    // These are transient — retry after a delay
                    if (errorCode == -6 || errorCode == -1) {
                        Log.w(TAG, "Chassis busy (errorCode=" + errorCode + "), retrying in 3s for: " + stopName);
                        handler.postDelayed(() -> {
                            if (!running || stopping) return;
                            try {
                                api.stopNavigation(navReqId++);
                            } catch (Exception ignored) {}
                            handler.postDelayed(() -> {
                                if (!running || stopping) return;
                                startNavigationToStop(api, stop, stopName, mapPosition);
                            }, 2000);
                        }, 3000);
                        return;
                    }
                    // -113 = ERROR_IN_DESTINATION: already at destination, treat as success
                    if (errorCode == -113) {
                        Log.i(TAG, "Already at destination: " + stopName);
                        handler.removeCallbacks(navTimeoutRunnable);
                        if (!running || stopping) return;
                        notifyStopReached(currentStopIndex, stopName);
                        handler.post(() -> onArrived(stop));
                        return;
                    }
                    handler.removeCallbacks(navTimeoutRunnable);
                    if (!running || stopping) return;
                    speak("No pude llegar a " + stopName + ". Continuando con la siguiente parada.");
                    handler.postDelayed(() -> {
                        currentStopIndex++;
                        navigateToCurrentStop();
                    }, 3000);
                }

                @Override
                public void onStatusUpdate(int status, String data, String extraData) {
                    Log.d(TAG, "onStatusUpdate status=" + status + " data=" + data + " extra=" + extraData + " for: " + stopName);
                }
            });
    }

    private void onArrived(Map<String, Object> stop) {
        if (!running || stopping) return;

        String stopName = getStr(stop, "name", "");
        String broadcast = getStr(stop, "broadcastContent", "");
        int dwellTime = getInt(stop, "dwellTime", 10);

        // Speak the broadcast content for this stop
        if (!broadcast.isEmpty()) {
            speak(broadcast);
        } else {
            speak("Hemos llegado a " + stopName);
        }

        // Wait dwellTime then move to next stop
        long waitMs = Math.max(5000, dwellTime * 1000L);
        Log.d(TAG, "Dwelling at " + stopName + " for " + dwellTime + "s");

        handler.postDelayed(() -> {
            currentStopIndex++;
            navigateToCurrentStop();
        }, waitMs);
    }

    private void completeTour() {
        running = false;
        speak("El tour " + currentRouteName + " ha finalizado. Gracias por acompañarme.");
        Log.i(TAG, "Tour '" + currentRouteName + "' completed");
        notifyCompleted(currentRouteId);
    }

    private void speak(String text) {
        if (agentBridge != null) {
            try {
                agentBridge.tts(text, 20000);
            } catch (Exception e) {
                Log.w(TAG, "TTS error: " + e.getMessage());
            }
        }
    }

    // ─── Status ──────────────────────────────────────────────────────────────

    public boolean isRunning() { return running; }
    public String getCurrentRouteId() { return currentRouteId; }
    public int getCurrentStopIndex() { return currentStopIndex; }
    public int getTotalStops() { return stops.size(); }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new java.util.HashMap<>();
        status.put("running", running);
        status.put("routeId", currentRouteId);
        status.put("routeName", currentRouteName);
        status.put("currentStop", currentStopIndex);
        status.put("totalStops", stops.size());
        if (running && currentStopIndex < stops.size()) {
            status.put("currentStopName", getStr(stops.get(currentStopIndex), "name", ""));
        }
        return status;
    }

    // ─── Listeners ───────────────────────────────────────────────────────────

    public void addListener(TourListener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(TourListener l) { listeners.remove(l); }

    private void notifyStarted(String routeId, String name) {
        for (TourListener l : listeners) { try { l.onTourStarted(routeId, name); } catch (Exception ignored) {} }
    }
    private void notifyStopReached(int idx, String name) {
        for (TourListener l : listeners) { try { l.onStopReached(idx, name); } catch (Exception ignored) {} }
    }
    private void notifyCompleted(String routeId) {
        for (TourListener l : listeners) { try { l.onTourCompleted(routeId); } catch (Exception ignored) {} }
    }
    private void notifyError(String error) {
        for (TourListener l : listeners) { try { l.onTourError(error); } catch (Exception ignored) {} }
    }
    private void notifyStopped() {
        for (TourListener l : listeners) { try { l.onTourStopped(); } catch (Exception ignored) {} }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static String getStr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? v.toString() : def;
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) { try { return Integer.parseInt((String) v); } catch (Exception ignored) {} }
        return def;
    }
}
