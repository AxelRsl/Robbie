package com.robbie.data.server;

import android.util.Log;

import com.google.gson.Gson;
import com.robbie.data.local.RobbieDatabase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public abstract class BaseHandler {

    protected final RobbieDatabase db;
    protected final Gson gson;

    public BaseHandler(RobbieDatabase db, Gson gson) {
        this.db = db;
        this.gson = gson;
    }

    public abstract Response handle(NanoHTTPD.Method method, List<String> parts, IHTTPSession session);

    // ─── Response helpers ────────────────────────────────────────────────────

    protected Response jsonResponse(Response.Status status, Object data) {
        String json = gson.toJson(data);
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", json);
    }

    protected Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    // ─── Request body ────────────────────────────────────────────────────────

    protected String getRequestBody(IHTTPSession session) {
        String contentLengthStr = session.getHeaders().get("content-length");
        int contentLength = contentLengthStr != null ? Integer.parseInt(contentLengthStr) : 0;
        if (contentLength == 0) return "{}";

        try {
            byte[] buffer = new byte[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = session.getInputStream().read(buffer, totalRead, contentLength - totalRead);
                if (read == -1) break;
                totalRead += read;
            }
            return new String(buffer, 0, totalRead, "UTF-8");
        } catch (IOException e) {
            Log.e("BaseHandler", "Error reading request body", e);
            return "{}";
        }
    }

    // ─── JSON parsing helpers ────────────────────────────────────────────────

    protected String getStringOrDefault(Map<String, Object> json, String key, String defaultValue) {
        Object value = json.get(key);
        if (value == null) return defaultValue;
        String str = value.toString();
        return str.isEmpty() ? defaultValue : str;
    }

    protected double getDoubleOrDefault(Map<String, Object> json, String key, double defaultValue) {
        Object value = json.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    protected int getIntOrDefault(Map<String, Object> json, String key, int defaultValue) {
        Object value = json.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    protected boolean getBooleanOrDefault(Map<String, Object> json, String key, boolean defaultValue) {
        Object value = json.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    protected List<String> getStringListOrDefault(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value == null) return new ArrayList<>();
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item != null) result.add(item.toString());
            }
            return result;
        }
        return new ArrayList<>();
    }
}
