package com.robbie.data.server;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.robbie.data.local.RobbieDatabase;
import com.robbie.data.local.entity.ConfigEntity;
import com.robbie.data.local.entity.TourStopEntity;

import com.robbie.core.navigation.TourExecutor;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

public class TourHandler extends BaseHandler {

    private static final String TAG = "TourHandler";

    public TourHandler(RobbieDatabase db, Gson gson) {
        super(db, gson);
    }

    @Override
    public Response handle(Method method, List<String> parts, IHTTPSession session) {
        // This handler is not directly routed — use handleStops / handleRoutes instead
        return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Use handleStops or handleRoutes"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOUR STOPS
    // ─────────────────────────────────────────────────────────────────────────

    public Response handleStops(Method method, List<String> parts, IHTTPSession session) {
        String id = parts.size() > 2 ? parts.get(2) : null;

        switch (method) {
            case GET:
                return id != null ? getStopById(id) : getAllStops();
            case POST:
                return createStop(session);
            case PUT:
                return id != null ? updateStop(id, session) :
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Tour stop ID required"));
            case DELETE:
                return id != null ? deleteStop(id) :
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Tour stop ID required"));
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
        }
    }

    private Response getAllStops() {
        List<TourStopEntity> stops = db.tourStopDao().getAllTourStopsSync();
        return jsonResponse(Response.Status.OK, stops);
    }

    private Response getStopById(String id) {
        TourStopEntity stop = db.tourStopDao().getTourStopById(id);
        if (stop != null) {
            return jsonResponse(Response.Status.OK, stop);
        }
        return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Tour stop not found"));
    }

    private Response createStop(IHTTPSession session) {
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> json = gson.fromJson(body, mapType);

        TourStopEntity stop = new TourStopEntity();
        stop.setId(getStringOrDefault(json, "id", UUID.randomUUID().toString()));
        stop.setName(getStringOrDefault(json, "name", ""));
        stop.setDescription(getStringOrDefault(json, "description", ""));
        stop.setLocationId(getStringOrDefault(json, "locationId", ""));
        stop.setOrderIndex(getIntOrDefault(json, "orderIndex", 0));
        stop.setWaitTime(getIntOrDefault(json, "waitTime", 0));
        stop.setSpeech(getStringOrDefault(json, "speech", ""));
        stop.setCreatedAt(System.currentTimeMillis());
        stop.setUpdatedAt(System.currentTimeMillis());

        db.tourStopDao().insertTourStop(stop);
        return jsonResponse(Response.Status.CREATED, stop);
    }

    private Response updateStop(String id, IHTTPSession session) {
        TourStopEntity existing = db.tourStopDao().getTourStopById(id);
        if (existing == null) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Tour stop not found"));
        }

        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> json = gson.fromJson(body, mapType);

        existing.setName(getStringOrDefault(json, "name", existing.getName()));
        existing.setDescription(getStringOrDefault(json, "description", existing.getDescription()));
        existing.setLocationId(getStringOrDefault(json, "locationId", existing.getLocationId()));
        existing.setOrderIndex(getIntOrDefault(json, "orderIndex", existing.getOrderIndex()));
        existing.setWaitTime(getIntOrDefault(json, "waitTime", existing.getWaitTime()));
        existing.setSpeech(getStringOrDefault(json, "speech", existing.getSpeech()));
        existing.setUpdatedAt(System.currentTimeMillis());

        db.tourStopDao().updateTourStop(existing);
        return jsonResponse(Response.Status.OK, existing);
    }

    private Response deleteStop(String id) {
        db.tourStopDao().deleteTourStopById(id);
        return jsonResponse(Response.Status.OK, mapOf("message", "Tour stop deleted"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOUR ROUTES (stored in config as JSON)
    // ─────────────────────────────────────────────────────────────────────────

    public Response handleRoutes(Method method, List<String> parts, IHTTPSession session) {
        // Handle action endpoints: /api/tour-routes/{id}/start|stop|status
        if (parts.size() >= 4) {
            String routeId = parts.get(2);
            String action = parts.get(3);
            switch (action) {
                case "start":
                    return startTourRoute(routeId);
                case "stop":
                    return stopTourRoute();
                case "status":
                    return getTourStatus();
            }
        }

        // Handle /api/tour-routes/status (no routeId)
        if (parts.size() == 3 && "status".equals(parts.get(2))) {
            return getTourStatus();
        }

        // Handle /api/tour-routes/stop
        if (parts.size() == 3 && "stop".equals(parts.get(2)) && method == Method.POST) {
            return stopTourRoute();
        }

        switch (method) {
            case GET:
                if (parts.size() >= 3) {
                    return getRoute(parts.get(2));
                }
                return getAllRoutes();
            case POST:
                return saveRoute(session);
            case PUT:
                if (parts.size() >= 3) {
                    return updateRoute(parts.get(2), session);
                }
                return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Route ID required"));
            case DELETE:
                if (parts.size() >= 3) {
                    return deleteRoute(parts.get(2));
                }
                return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Route ID required"));
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOUR EXECUTION
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Response startTourRoute(String routeId) {
        try {
            // Find the route
            List<Map<String, Object>> routes = loadRoutesList();
            Map<String, Object> route = null;
            for (Map<String, Object> r : routes) {
                if (routeId.equals(r.get("id"))) {
                    route = r;
                    break;
                }
            }
            if (route == null) {
                return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Route not found"));
            }

            String routeName = route.get("name") != null ? route.get("name").toString() : "Tour";
            Object stopsObj = route.get("stops");
            if (!(stopsObj instanceof List)) {
                return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Route has no stops"));
            }
            List<Map<String, Object>> stops = (List<Map<String, Object>>) stopsObj;

            TourExecutor executor = TourExecutor.getInstance();
            if (executor.isRunning()) {
                return jsonResponse(Response.Status.CONFLICT, mapOf("error", "A tour is already running. Stop it first."));
            }

            executor.startTour(routeId, routeName, stops);
            Log.i(TAG, "Tour started: " + routeName + " (" + stops.size() + " stops)");

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Tour started: " + routeName);
            result.put("stops", stops.size());
            return jsonResponse(Response.Status.OK, result);
        } catch (Exception e) {
            Log.e(TAG, "Error starting tour", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    private Response stopTourRoute() {
        TourExecutor executor = TourExecutor.getInstance();
        executor.stopTour();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Tour stopped");
        return jsonResponse(Response.Status.OK, result);
    }

    private Response getTourStatus() {
        TourExecutor executor = TourExecutor.getInstance();
        return jsonResponse(Response.Status.OK, executor.getStatus());
    }

    private Response getAllRoutes() {
        try {
            ConfigEntity entity = db.configDao().getConfig("tour_routes");
            if (entity == null || entity.getValue() == null) {
                return jsonResponse(Response.Status.OK, new ArrayList<>());
            }
            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> routes = gson.fromJson(entity.getValue(), listType);
            return jsonResponse(Response.Status.OK, routes != null ? routes : new ArrayList<>());
        } catch (Exception e) {
            Log.e(TAG, "Error getting tour routes", e);
            return jsonResponse(Response.Status.OK, new ArrayList<>());
        }
    }

    private Response getRoute(String routeId) {
        try {
            ConfigEntity entity = db.configDao().getConfig("tour_routes");
            if (entity == null || entity.getValue() == null) {
                return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Route not found"));
            }
            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> routes = gson.fromJson(entity.getValue(), listType);
            if (routes != null) {
                for (Map<String, Object> route : routes) {
                    if (routeId.equals(route.get("id"))) {
                        return jsonResponse(Response.Status.OK, route);
                    }
                }
            }
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Route not found"));
        } catch (Exception e) {
            Log.e(TAG, "Error getting tour route", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    private Response saveRoute(IHTTPSession session) {
        try {
            String body = getRequestBody(session);
            Map<String, Object> newRoute = gson.fromJson(body, new TypeToken<Map<String, Object>>(){}.getType());
            if (newRoute == null) {
                return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Invalid route data"));
            }
            if (!newRoute.containsKey("id")) {
                newRoute.put("id", UUID.randomUUID().toString());
            }
            newRoute.put("createdAt", System.currentTimeMillis());
            newRoute.put("updatedAt", System.currentTimeMillis());

            List<Map<String, Object>> routes = loadRoutesList();
            routes.add(newRoute);
            saveRoutesList(routes);

            return jsonResponse(Response.Status.OK, newRoute);
        } catch (Exception e) {
            Log.e(TAG, "Error saving tour route", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    private Response updateRoute(String routeId, IHTTPSession session) {
        try {
            String body = getRequestBody(session);
            Map<String, Object> updates = gson.fromJson(body, new TypeToken<Map<String, Object>>(){}.getType());
            if (updates == null) {
                return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Invalid route data"));
            }

            List<Map<String, Object>> routes = loadRoutesList();
            boolean found = false;
            for (int i = 0; i < routes.size(); i++) {
                if (routeId.equals(routes.get(i).get("id"))) {
                    Map<String, Object> existing = routes.get(i);
                    existing.putAll(updates);
                    existing.put("id", routeId);
                    existing.put("updatedAt", System.currentTimeMillis());
                    routes.set(i, existing);
                    found = true;
                    break;
                }
            }

            if (!found) {
                return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Route not found"));
            }

            saveRoutesList(routes);
            return jsonResponse(Response.Status.OK,
                routes.stream().filter(r -> routeId.equals(r.get("id"))).findFirst().orElse(new HashMap<>()));
        } catch (Exception e) {
            Log.e(TAG, "Error updating tour route", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    private Response deleteRoute(String routeId) {
        try {
            List<Map<String, Object>> routes = loadRoutesList();
            routes.removeIf(r -> routeId.equals(r.get("id")));
            saveRoutesList(routes);
            return jsonResponse(Response.Status.OK, mapOf("success", true));
        } catch (Exception e) {
            Log.e(TAG, "Error deleting tour route", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    private List<Map<String, Object>> loadRoutesList() {
        try {
            ConfigEntity entity = db.configDao().getConfig("tour_routes");
            if (entity != null && entity.getValue() != null) {
                Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
                List<Map<String, Object>> routes = gson.fromJson(entity.getValue(), listType);
                return routes != null ? new ArrayList<>(routes) : new ArrayList<>();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading tour routes", e);
        }
        return new ArrayList<>();
    }

    private void saveRoutesList(List<Map<String, Object>> routes) {
        String json = gson.toJson(routes);
        ConfigEntity entity = new ConfigEntity("tour_routes", json, System.currentTimeMillis());
        db.configDao().setConfig(entity);
    }
}
