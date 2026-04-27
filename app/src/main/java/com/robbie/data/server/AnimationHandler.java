package com.robbie.data.server;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.robbie.data.local.RobbieDatabase;
import com.robbie.data.local.entity.AnimationEntity;

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

public class AnimationHandler extends BaseHandler {

    private static final String TAG = "AnimationHandler";

    public AnimationHandler(RobbieDatabase db, Gson gson) {
        super(db, gson);
    }

    @Override
    public Response handle(Method method, List<String> parts, IHTTPSession session) {
        String id = parts.size() > 2 ? parts.get(2) : null;

        // POST /api/animations/bulk → bulk create
        if (method == Method.POST && "bulk".equals(id)) {
            return bulkCreate(session);
        }

        // DELETE /api/animations/all → delete all
        if (method == Method.DELETE && "all".equals(id)) {
            return deleteAll();
        }

        // GET /api/animations/trigger/:trigger → by trigger
        if (method == Method.GET && "trigger".equals(id) && parts.size() > 3) {
            return getByTrigger(parts.get(3));
        }

        switch (method) {
            case GET:
                return id != null ? getById(id) : getAll();
            case POST:
                return create(session);
            case PUT:
                return id != null ? update(id, session) :
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Animation ID required"));
            case DELETE:
                return id != null ? delete(id) :
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Animation ID required"));
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
        }
    }

    private Response getAll() {
        List<AnimationEntity> animations = db.animationDao().getAllAnimationsSync();
        return jsonResponse(Response.Status.OK, animations);
    }

    private Response getById(String id) {
        AnimationEntity animation = db.animationDao().getAnimationById(id);
        if (animation != null) {
            return jsonResponse(Response.Status.OK, animation);
        }
        return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Animation not found"));
    }

    private Response getByTrigger(String trigger) {
        List<AnimationEntity> animations = db.animationDao().getAnimationsByTrigger(trigger);
        return jsonResponse(Response.Status.OK, animations);
    }

    private Response create(IHTTPSession session) {
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> json = gson.fromJson(body, mapType);

        AnimationEntity animation = buildAnimation(json);
        animation.setCreatedAt(System.currentTimeMillis());
        animation.setUpdatedAt(System.currentTimeMillis());

        db.animationDao().insertAnimation(animation);
        return jsonResponse(Response.Status.CREATED, animation);
    }

    private Response update(String id, IHTTPSession session) {
        AnimationEntity existing = db.animationDao().getAnimationById(id);
        if (existing == null) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Animation not found"));
        }

        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> json = gson.fromJson(body, mapType);

        existing.setName(getStringOrDefault(json, "name", existing.getName()));
        existing.setTrigger(getStringOrDefault(json, "trigger", existing.getTrigger()));
        existing.setHeadMovements(getStringOrDefault(json, "headMovements", existing.getHeadMovements()));
        existing.setLedColor(getStringOrDefault(json, "ledColor", existing.getLedColor()));
        existing.setLedMode(getStringOrDefault(json, "ledMode", existing.getLedMode()));
        existing.setScreenExpression(getStringOrDefault(json, "screenExpression", existing.getScreenExpression()));
        existing.setTtsText(getStringOrDefault(json, "ttsText", existing.getTtsText()));
        existing.setDescription(getStringOrDefault(json, "description", existing.getDescription()));
        existing.setDurationMs(getIntOrDefault(json, "durationMs", existing.getDurationMs()));
        if (json.containsKey("enabled")) {
            existing.setEnabled(getBooleanOrDefault(json, "enabled", existing.isEnabled()));
        }
        existing.setPriority(getIntOrDefault(json, "priority", existing.getPriority()));
        existing.setUpdatedAt(System.currentTimeMillis());

        db.animationDao().updateAnimation(existing);
        return jsonResponse(Response.Status.OK, existing);
    }

    private Response delete(String id) {
        db.animationDao().deleteAnimationById(id);
        return jsonResponse(Response.Status.OK, mapOf("message", "Animation deleted"));
    }

    private Response deleteAll() {
        db.animationDao().deleteAllAnimations();
        return jsonResponse(Response.Status.OK, mapOf("message", "All animations deleted"));
    }

    private Response bulkCreate(IHTTPSession session) {
        String body = getRequestBody(session);
        List<Map<String, Object>> jsonArray;
        try {
            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            jsonArray = gson.fromJson(body, listType);
        } catch (Exception e) {
            return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Invalid JSON: " + e.getMessage()));
        }

        List<AnimationEntity> animations = new ArrayList<>();
        for (Map<String, Object> json : jsonArray) {
            AnimationEntity animation = buildAnimation(json);
            animation.setCreatedAt(System.currentTimeMillis());
            animation.setUpdatedAt(System.currentTimeMillis());
            animations.add(animation);
        }

        db.animationDao().insertAnimations(animations);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Animations created");
        result.put("count", animations.size());
        return jsonResponse(Response.Status.CREATED, result);
    }

    private AnimationEntity buildAnimation(Map<String, Object> json) {
        AnimationEntity a = new AnimationEntity();
        a.setId(getStringOrDefault(json, "id", UUID.randomUUID().toString()));
        a.setName(getStringOrDefault(json, "name", ""));
        a.setTrigger(getStringOrDefault(json, "trigger", "custom"));
        a.setHeadMovements(getStringOrDefault(json, "headMovements", null));
        a.setLedColor(getStringOrDefault(json, "ledColor", null));
        a.setLedMode(getStringOrDefault(json, "ledMode", null));
        a.setScreenExpression(getStringOrDefault(json, "screenExpression", null));
        a.setTtsText(getStringOrDefault(json, "ttsText", null));
        a.setDescription(getStringOrDefault(json, "description", null));
        a.setDurationMs(getIntOrDefault(json, "durationMs", 0));
        a.setEnabled(getBooleanOrDefault(json, "enabled", true));
        a.setPriority(getIntOrDefault(json, "priority", 0));
        return a;
    }
}
