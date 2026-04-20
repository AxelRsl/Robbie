package com.robbie.data.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.robbie.data.local.RobbieDatabase;
import com.robbie.data.local.entity.MapEntity;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

public class MapHandler extends BaseHandler {

    public MapHandler(RobbieDatabase db, Gson gson) {
        super(db, gson);
    }

    @Override
    public Response handle(Method method, List<String> parts, IHTTPSession session) {
        String id = parts.size() > 2 ? parts.get(2) : null;
        String action = parts.size() > 3 ? parts.get(3) : null;

        if (method == Method.POST && id != null && "activate".equals(action)) {
            return activate(id);
        }

        switch (method) {
            case GET:
                return id != null ? getById(id) : getAll();
            case POST:
                return create(session);
            case PUT:
                return id != null ? update(id, session) :
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Map ID required"));
            case DELETE:
                return id != null ? delete(id) :
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Map ID required"));
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
        }
    }

    private Response getAll() {
        List<MapEntity> maps = db.mapDao().getAllMapsSync();
        return jsonResponse(Response.Status.OK, maps);
    }

    private Response getById(String id) {
        MapEntity map = db.mapDao().getMapById(id);
        if (map != null) {
            return jsonResponse(Response.Status.OK, map);
        }
        return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Map not found"));
    }

    private Response create(IHTTPSession session) {
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> json = gson.fromJson(body, mapType);

        MapEntity map = new MapEntity();
        map.setId(getStringOrDefault(json, "id", UUID.randomUUID().toString()));
        map.setName(getStringOrDefault(json, "name", ""));
        map.setDescription(getStringOrDefault(json, "description", ""));
        map.setMapData(getStringOrDefault(json, "mapData", ""));
        map.setImageUrl(getStringOrDefault(json, "imageUrl", ""));
        map.setIsActive(getBooleanOrDefault(json, "isActive", false));
        map.setCreatedAt(System.currentTimeMillis());
        map.setUpdatedAt(System.currentTimeMillis());

        db.mapDao().insertMap(map);
        return jsonResponse(Response.Status.CREATED, map);
    }

    private Response update(String id, IHTTPSession session) {
        MapEntity existing = db.mapDao().getMapById(id);
        if (existing == null) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Map not found"));
        }

        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> json = gson.fromJson(body, mapType);

        existing.setName(getStringOrDefault(json, "name", existing.getName()));
        existing.setDescription(getStringOrDefault(json, "description", existing.getDescription()));
        existing.setMapData(getStringOrDefault(json, "mapData", existing.getMapData()));
        existing.setImageUrl(getStringOrDefault(json, "imageUrl", existing.getImageUrl()));
        if (json.containsKey("isActive")) {
            existing.setIsActive(getBooleanOrDefault(json, "isActive", existing.getIsActive()));
        }
        existing.setUpdatedAt(System.currentTimeMillis());

        db.mapDao().updateMap(existing);
        return jsonResponse(Response.Status.OK, existing);
    }

    private Response activate(String id) {
        MapEntity map = db.mapDao().getMapById(id);
        if (map == null) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Map not found"));
        }

        db.mapDao().deactivateAllMaps();
        db.mapDao().activateMap(id);

        map.setIsActive(true);
        return jsonResponse(Response.Status.OK, map);
    }

    private Response delete(String id) {
        db.mapDao().deleteMapById(id);
        return jsonResponse(Response.Status.OK, mapOf("message", "Map deleted"));
    }
}
