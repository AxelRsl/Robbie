package com.robbie.platform.agent;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.ainirobot.coreservice.client.RobotApi;
import com.robbie.core.navigation.TourExecutor;
import com.robbie.data.local.RobbieDatabase;
import com.robbie.data.local.entity.ConfigEntity;
import com.robbie.platform.retail.AsyncTaskHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Maneja la logica de tours guiados del robot.
 *
 * Extraido de RobotActionHandler para separar responsabilidades.
 * Encapsula:
 * - Carga de rutas desde Room DB (offloaded a background thread)
 * - Inicio/parada de TourExecutor
 * - Notificaciones de estado via TourGuideListener
 */
public class TourGuideHandler {

    private static final String TAG = "TourGuide";

    // ── Injected dependencies ──
    private final Handler mainHandler;
    private final Context context;
    private final RobbieDatabase db;
    private RobotApi robotApi;

    // ── State ──
    private volatile boolean tourActive = false;
    private volatile String currentTourId = null;

    // ── Listener ──
    private TourGuideListener listener;

    public TourGuideHandler(Handler mainHandler, Context context, RobbieDatabase db) {
        this.mainHandler = mainHandler;
        this.context = context.getApplicationContext();
        this.db = db;
    }

    // ══════════════════════════════════════════
    // SETTERS
    // ══════════════════════════════════════════

    public void setRobotApi(RobotApi api) {
        this.robotApi = api;
    }

    public void setTourGuideListener(TourGuideListener l) {
        this.listener = l;
    }

    // ══════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════

    public void startTour(String routeName) {
        Log.d(TAG, "TOUR_START_REQUEST: routeName=" + routeName);

        // DB query and JSON parsing on background thread to avoid blocking UI
        AsyncTaskHelper.execute(() -> {
            try {
                TourExecutor executor = TourExecutor.getInstance();
                if (executor.isRunning()) {
                    notifyError("Ya hay un tour en curso. Si quieres, puedo detenerlo primero.");
                    return;
                }

                // Load published routes from DB (background thread)
                ConfigEntity entity = db.configDao().getConfig("tour_routes");
                if (entity == null || entity.getValue() == null) {
                    notifyError("No hay tours configurados. Puedes crear uno desde el panel de administracion.");
                    return;
                }

                JSONArray routesArray = new JSONArray(entity.getValue());
                Map<String, Object> selectedRoute = null;

                for (int i = 0; i < routesArray.length(); i++) {
                    JSONObject routeJson = routesArray.getJSONObject(i);
                    boolean published = routeJson.optBoolean("published", false);
                    if (!published) continue;

                    String name = routeJson.optString("name", "");
                    if (routeName != null && !routeName.isEmpty()
                            && name.toLowerCase().contains(routeName.toLowerCase())) {
                        selectedRoute = jsonObjectToMap(routeJson);
                        break;
                    }
                    if (selectedRoute == null) {
                        selectedRoute = jsonObjectToMap(routeJson);
                    }
                }

                if (selectedRoute == null) {
                    notifyError("No encontre ningun tour publicado. Crea y publica uno desde el panel.");
                    return;
                }

                String tourName = (String) selectedRoute.get("name");
                Object stopsObj = selectedRoute.get("stops");
                if (!(stopsObj instanceof List)) {
                    notifyError("El tour " + tourName + " no tiene paradas configuradas.");
                    return;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> stops = (List<Map<String, Object>>) stopsObj;

                String routeId = (String) selectedRoute.get("id");
                Log.d(TAG, "TOUR_ROUTES_LOADED: count=" + stops.size());

                final String fRouteId = routeId;
                final String fTourName = tourName;
                final List<Map<String, Object>> fStops = stops;
                // RobotApi calls must run on main thread
                mainHandler.post(() -> {
                    executor.startTour(fRouteId, fTourName, fStops);
                    tourActive = true;
                    currentTourId = fRouteId;
                    Log.d(TAG, "TOUR_START: id=" + fRouteId);
                    if (listener != null) listener.onTourStarted(fRouteId);
                });
            } catch (Exception e) {
                Log.e(TAG, "TOUR_ERROR: " + e.getMessage(), e);
                notifyError("Lo siento, hubo un error al iniciar el tour.");
            }
        });
    }

    public void stopTour() {
        Log.d(TAG, "TOUR_STOP: id=" + currentTourId);
        mainHandler.post(() -> {
            TourExecutor executor = TourExecutor.getInstance();
            if (executor.isRunning()) {
                executor.stopTour();
                String stoppedId = currentTourId;
                tourActive = false;
                currentTourId = null;
                if (listener != null) listener.onTourStopped(stoppedId);
            } else {
                notifyError("No hay ningun tour en curso.");
            }
        });
    }

    public boolean isTourActive() {
        return tourActive;
    }

    public void destroy() {
        if (tourActive) {
            TourExecutor.getInstance().stopTour();
        }
        tourActive = false;
        currentTourId = null;
        listener = null;
    }

    // ══════════════════════════════════════════
    // INTERNAL
    // ══════════════════════════════════════════

    private void notifyError(String reason) {
        Log.d(TAG, "TOUR_ERROR: " + reason);
        mainHandler.post(() -> {
            if (listener != null) listener.onTourError(reason);
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonObjectToMap(JSONObject json) {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object val = json.opt(key);
            if (val instanceof JSONArray) {
                List<Object> list = new ArrayList<>();
                JSONArray arr = (JSONArray) val;
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (item instanceof JSONObject) {
                        list.add(jsonObjectToMap((JSONObject) item));
                    } else {
                        list.add(item);
                    }
                }
                map.put(key, list);
            } else if (val instanceof JSONObject) {
                map.put(key, jsonObjectToMap((JSONObject) val));
            } else {
                map.put(key, val);
            }
        }
        return map;
    }

    // ══════════════════════════════════════════
    // INTERFACE
    // ══════════════════════════════════════════

    public interface TourGuideListener {
        void onTourStarted(String tourId);
        void onTourStopped(String tourId);
        void onTourError(String reason);
    }
}
