package com.robbie.data.server;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.robbie.data.local.RobbieDatabase;
import com.robbie.data.local.entity.ConfigEntity;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

public class ConfigHandler extends BaseHandler {

    private static final String TAG = "ConfigHandler";

    public interface OnPersonaChangedListener {
        void onPersonaChanged(Map<String, Object> persona);
    }

    private OnPersonaChangedListener personaChangedListener;

    public ConfigHandler(RobbieDatabase db, Gson gson) {
        super(db, gson);
    }

    public void setOnPersonaChangedListener(OnPersonaChangedListener listener) {
        this.personaChangedListener = listener;
    }

    @Override
    public Response handle(Method method, List<String> parts, IHTTPSession session) {
        String key = parts.size() > 2 ? parts.get(2) : null;

        switch (method) {
            case GET:
                return key != null ? getByKey(key) : getAll();
            case PUT:
                return key != null ? set(key, session) :
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Config key required"));
            case DELETE:
                return key != null ? delete(key) :
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Config key required"));
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONFIG
    // ─────────────────────────────────────────────────────────────────────────

    private Response getAll() {
        List<ConfigEntity> configs = db.configDao().getAllConfigSync();
        Map<String, String> configMap = new HashMap<>();
        for (ConfigEntity config : configs) {
            configMap.put(config.getKey(), config.getValue());
        }
        return jsonResponse(Response.Status.OK, configMap);
    }

    private Response getByKey(String key) {
        ConfigEntity config = db.configDao().getConfig(key);
        if (config != null) {
            Map<String, String> result = new HashMap<>();
            result.put("key", config.getKey());
            result.put("value", config.getValue());
            return jsonResponse(Response.Status.OK, result);
        }
        return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Config not found"));
    }

    private Response set(String key, IHTTPSession session) {
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(body, mapType);
        String value = data.get("value") != null ? data.get("value").toString() : "";

        ConfigEntity config = new ConfigEntity(key, value, System.currentTimeMillis());
        db.configDao().setConfig(config);

        Map<String, String> result = new HashMap<>();
        result.put("key", key);
        result.put("value", value);
        return jsonResponse(Response.Status.OK, result);
    }

    private Response delete(String key) {
        db.configDao().deleteConfig(key);
        return jsonResponse(Response.Status.OK, mapOf("message", "Config deleted"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PERSONA
    // ─────────────────────────────────────────────────────────────────────────

    public Response handlePersona(Method method, IHTTPSession session) {
        switch (method) {
            case GET:
                return getPersona();
            case PUT:
            case POST:
                return savePersona(session);
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
        }
    }

    private Response getPersona() {
        ConfigEntity config = db.configDao().getConfig("persona");
        if (config != null && config.getValue() != null && !config.getValue().isEmpty()) {
            try {
                Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> persona = gson.fromJson(config.getValue(), mapType);
                return jsonResponse(Response.Status.OK, persona);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing persona config", e);
            }
        }

        // Return default persona
        Map<String, Object> defaultPersona = new HashMap<>();
        defaultPersona.put("robotName", "Robbie");
        defaultPersona.put("robotIdentity", "Un asistente amigable que ayuda a los clientes");
        defaultPersona.put("enterpriseIntro", "");
        defaultPersona.put("additionalInfo", "");
        defaultPersona.put("greeting", "\u00a1Hola! Soy Robbie, \u00bfen qu\u00e9 puedo ayudarte?");
        defaultPersona.put("farewell", "\u00a1Gracias por visitarnos! Que tengas un excelente d\u00eda.");
        defaultPersona.put("idleMessage", "\u00bfNecesitas ayuda? Ac\u00e9rcate y preg\u00fantame lo que quieras.");
        defaultPersona.put("personality", "friendly");
        defaultPersona.put("language", "es-MX");
        defaultPersona.put("voiceId", "es-mx-x-efg-local");
        defaultPersona.put("speakSpeed", 1.0);
        defaultPersona.put("conversationStyles", new String[]{"natural", "friendly"});
        defaultPersona.put("autoChatEnabled", true);
        defaultPersona.put("autoChatInterval", 30);
        return jsonResponse(Response.Status.OK, defaultPersona);
    }

    private Response savePersona(IHTTPSession session) {
        String body = getRequestBody(session);

        // Validate JSON
        try {
            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            gson.fromJson(body, mapType);
        } catch (Exception e) {
            return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Invalid JSON: " + e.getMessage()));
        }

        ConfigEntity config = new ConfigEntity("persona", body, System.currentTimeMillis());
        db.configDao().setConfig(config);

        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> persona = gson.fromJson(body, mapType);

        // Notificar al AgentOS del cambio de persona
        if (personaChangedListener != null) {
            try {
                personaChangedListener.onPersonaChanged(persona);
            } catch (Exception e) {
                Log.w(TAG, "Error notifying persona change", e);
            }
        }

        return jsonResponse(Response.Status.OK, persona);
    }
}
