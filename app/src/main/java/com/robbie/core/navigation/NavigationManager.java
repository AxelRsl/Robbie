package com.robbie.core.navigation;

import android.content.Context;
import android.util.Log;

import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.ActionListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestor del sistema de navegacion del robot.
 *
 * Funcionalidades:
 * - Gestion de puntos de interes (waypoints)
 * - Navegacion automatica entre puntos
 * - Ejecucion de rutas/patrullaje
 * - Navegacion manual (joystick)
 * - Seguridad: distancia minima a obstaculos
 */
public class NavigationManager {

    private static final String TAG = "NavigationManager";
    private static volatile NavigationManager sInstance;

    private final Context context;
    private String activeMapId;
    private boolean autoNavigationEnabled = true;
    private float safetyDistance = 0.5f;
    private boolean isNavigating = false;
    private boolean isPatrolling = false;
    private int patrolIndex = 0;
    private List<String> patrolRoute = new ArrayList<>();
    private final List<NavigationListener> listeners = new ArrayList<>();
    private int navReqId = 6001;

    public enum NavigationStatus {
        IDLE,
        NAVIGATING,
        ARRIVED,
        BLOCKED,
        ERROR,
        PATROLLING
    }

    public interface NavigationListener {
        void onNavigationStatusChanged(NavigationStatus status, String destination);
        void onWaypointReached(String waypointId);
        void onPatrolComplete();
        void onNavigationError(String error);
    }

    private NavigationManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static NavigationManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (NavigationManager.class) {
                if (sInstance == null) {
                    sInstance = new NavigationManager(context);
                }
            }
        }
        return sInstance;
    }

    public static NavigationManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("NavigationManager not initialized.");
        }
        return sInstance;
    }

    /**
     * Navega a un punto de interes por nombre.
     */
    public void navigateTo(String pointName) {
        if (isNavigating) {
            Log.w(TAG, "Already navigating, ignoring new request");
            return;
        }

        try {
            RobotApi api = RobotApi.getInstance();
            if (api == null) {
                notifyError("RobotApi not available");
                return;
            }

            isNavigating = true;
            notifyStatusChanged(NavigationStatus.NAVIGATING, pointName);

            api.startNavigation(navReqId++, pointName, safetyDistance, 60000, new ActionListener() {
                @Override
                public void onResult(int reqId, String result) {
                    isNavigating = false;
                    notifyStatusChanged(NavigationStatus.ARRIVED, pointName);
                    notifyWaypointReached(pointName);
                    Log.i(TAG, "Arrived at: " + pointName);

                    if (isPatrolling) {
                        continuePatrol();
                    }
                }

                @Override
                public void onError(int reqId, String error) {
                    isNavigating = false;
                    notifyStatusChanged(NavigationStatus.ERROR, pointName);
                    notifyError("Navigation failed: " + error);
                    Log.e(TAG, "Navigation error: " + error);
                }

                @Override
                public void onStatusUpdate(int reqId, String status) {
                    Log.d(TAG, "Nav status: " + status);
                    if ("blocked".equalsIgnoreCase(status)) {
                        notifyStatusChanged(NavigationStatus.BLOCKED, pointName);
                    }
                }
            });

        } catch (Exception e) {
            isNavigating = false;
            notifyError("Navigation exception: " + e.getMessage());
            Log.e(TAG, "Error navigating", e);
        }
    }

    /**
     * Inicia una ruta de patrullaje.
     */
    public void startPatrol(List<String> route) {
        if (route == null || route.isEmpty()) {
            Log.w(TAG, "Empty patrol route");
            return;
        }

        patrolRoute = new ArrayList<>(route);
        patrolIndex = 0;
        isPatrolling = true;
        notifyStatusChanged(NavigationStatus.PATROLLING, route.get(0));
        Log.i(TAG, "Starting patrol with " + route.size() + " stops");

        navigateTo(patrolRoute.get(0));
    }

    /**
     * Continua al siguiente punto de la ruta de patrullaje.
     */
    private void continuePatrol() {
        patrolIndex++;
        if (patrolIndex < patrolRoute.size()) {
            String next = patrolRoute.get(patrolIndex);
            Log.d(TAG, "Patrol: moving to stop " + (patrolIndex + 1) + "/" + patrolRoute.size());
            navigateTo(next);
        } else {
            isPatrolling = false;
            notifyStatusChanged(NavigationStatus.IDLE, null);
            notifyPatrolComplete();
            Log.i(TAG, "Patrol complete");
        }
    }

    /**
     * Detiene la navegacion actual.
     */
    public void stopNavigation() {
        try {
            RobotApi api = RobotApi.getInstance();
            if (api != null) {
                api.stopNavigation(navReqId++);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error stopping navigation", e);
        }
        isNavigating = false;
        isPatrolling = false;
        notifyStatusChanged(NavigationStatus.IDLE, null);
    }

    /**
     * Obtiene la lista de puntos del mapa actual del robot.
     */
    public List<String> getMapPoints() {
        List<String> points = new ArrayList<>();
        try {
            RobotApi api = RobotApi.getInstance();
            if (api != null) {
                String mapName = api.getMapName();
                if (mapName != null) {
                    // Los puntos se obtienen del mapa cargado en el robot
                    Log.d(TAG, "Current map: " + mapName);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting map points", e);
        }
        return points;
    }

    public boolean isNavigating() {
        return isNavigating;
    }

    public boolean isPatrolling() {
        return isPatrolling;
    }

    public String getActiveMapId() {
        return activeMapId;
    }

    public void setActiveMapId(String mapId) {
        this.activeMapId = mapId;
    }

    public float getSafetyDistance() {
        return safetyDistance;
    }

    public void setSafetyDistance(float distance) {
        this.safetyDistance = Math.max(0.1f, Math.min(2.0f, distance));
    }

    public boolean isAutoNavigationEnabled() {
        return autoNavigationEnabled;
    }

    public void setAutoNavigationEnabled(boolean enabled) {
        this.autoNavigationEnabled = enabled;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isNavigating", isNavigating);
        status.put("isPatrolling", isPatrolling);
        status.put("autoNavigation", autoNavigationEnabled);
        status.put("safetyDistance", safetyDistance);
        status.put("activeMapId", activeMapId);
        if (isPatrolling) {
            status.put("patrolProgress", patrolIndex + "/" + patrolRoute.size());
        }
        return status;
    }

    public void addListener(NavigationListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(NavigationListener listener) {
        listeners.remove(listener);
    }

    private void notifyStatusChanged(NavigationStatus status, String destination) {
        for (NavigationListener l : listeners) {
            try { l.onNavigationStatusChanged(status, destination); } catch (Exception e) { /* ignore */ }
        }
    }

    private void notifyWaypointReached(String waypointId) {
        for (NavigationListener l : listeners) {
            try { l.onWaypointReached(waypointId); } catch (Exception e) { /* ignore */ }
        }
    }

    private void notifyPatrolComplete() {
        for (NavigationListener l : listeners) {
            try { l.onPatrolComplete(); } catch (Exception e) { /* ignore */ }
        }
    }

    private void notifyError(String error) {
        for (NavigationListener l : listeners) {
            try { l.onNavigationError(error); } catch (Exception e) { /* ignore */ }
        }
    }

    public void destroy() {
        stopNavigation();
        listeners.clear();
        sInstance = null;
    }
}
