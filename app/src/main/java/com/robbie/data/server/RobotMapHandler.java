package com.robbie.data.server;

import android.util.Log;

import com.google.gson.Gson;
import com.robbie.data.local.RobbieDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

public class RobotMapHandler extends BaseHandler {

    private static final String TAG = "RobotMapHandler";
    private static final String MAP_DIR_PATH = "/sdcard/robot/map";

    public RobotMapHandler(RobbieDatabase db, Gson gson) {
        super(db, gson);
    }

    @Override
    public Response handle(Method method, List<String> parts, IHTTPSession session) {
        if (method != Method.GET) {
            return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Only GET allowed"));
        }

        // Handle /api/robot-maps/{id}/image
        if (parts.size() >= 4 && "image".equals(parts.get(3))) {
            return getMapImage(parts.get(2));
        }

        // Handle /api/robot-maps/{id}/places
        if (parts.size() >= 4 && "places".equals(parts.get(3))) {
            return getMapPlaces(parts.get(2));
        }

        return listMaps();
    }

    private Response listMaps() {
        List<Map<String, Object>> robotMaps = new ArrayList<>();
        File mapDir = new File(MAP_DIR_PATH);

        if (mapDir.exists() && mapDir.isDirectory()) {
            File[] mapFolders = mapDir.listFiles();
            if (mapFolders != null) {
                for (File folder : mapFolders) {
                    if (folder.isDirectory()) {
                        Map<String, Object> mapInfo = new HashMap<>();
                        mapInfo.put("id", folder.getName());
                        mapInfo.put("name", folder.getName());
                        mapInfo.put("path", folder.getAbsolutePath());

                        // Check for map image (OrionStar saves as .jpeg next to folder)
                        File mapImage = findMapImage(mapDir, folder.getName());
                        mapInfo.put("hasImage", mapImage != null);
                        mapInfo.put("imageUrl", mapImage != null ? "/api/robot-maps/" + folder.getName() + "/image" : null);

                        // Check for map zip file
                        File mapZip = new File(mapDir, folder.getName() + ".zip");
                        mapInfo.put("hasZip", mapZip.exists());

                        mapInfo.put("lastModified", folder.lastModified());

                        robotMaps.add(mapInfo);
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("maps", robotMaps);
        result.put("count", robotMaps.size());
        return jsonResponse(Response.Status.OK, result);
    }

    private Response getMapImage(String mapId) {
        File mapDir = new File(MAP_DIR_PATH);
        File imageFile = findMapImage(mapDir, mapId);

        if (imageFile == null) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Map image not found"));
        }

        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(imageFile);
            String mimeType = imageFile.getName().endsWith(".png") ? "image/png" : "image/jpeg";
            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, mimeType, fis, imageFile.length());
        } catch (Exception e) {
            Log.e(TAG, "Error reading map image", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", "Failed to read image"));
        }
    }

    private Response getMapPlaces(String mapId) {
        File placeFile = new File(MAP_DIR_PATH + "/" + mapId + "/place.json");

        if (!placeFile.exists()) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "No places found for map " + mapId));
        }

        try {
            String content = new String(java.nio.file.Files.readAllBytes(placeFile.toPath()), "UTF-8");
            org.json.JSONArray rawPlaces = new org.json.JSONArray(content);
            List<Map<String, Object>> places = new ArrayList<>();

            for (int i = 0; i < rawPlaces.length(); i++) {
                org.json.JSONObject place = rawPlaces.getJSONObject(i);
                Map<String, Object> placeInfo = new HashMap<>();
                placeInfo.put("id", place.optString("id", ""));
                placeInfo.put("x", place.optDouble("x", 0));
                placeInfo.put("y", place.optDouble("y", 0));
                placeInfo.put("theta", place.optDouble("theta", 0));
                placeInfo.put("status", place.optInt("status", 0));
                placeInfo.put("type", place.optInt("type", 0));
                placeInfo.put("typeId", place.optInt("typeId", 0));
                placeInfo.put("time", place.optLong("time", 0));

                // Extract name - prefer es_ES, then en_US, then zh_CN, then first available
                placeInfo.put("name", extractPlaceName(place));

                places.add(placeInfo);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("places", places);
            result.put("count", places.size());
            result.put("mapId", mapId);
            return jsonResponse(Response.Status.OK, result);
        } catch (Exception e) {
            Log.e(TAG, "Error reading place.json for map " + mapId, e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", "Failed to read places: " + e.getMessage()));
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private File findMapImage(File mapDir, String mapId) {
        File imageFile = new File(mapDir, mapId + ".jpeg");
        if (imageFile.exists()) return imageFile;

        imageFile = new File(mapDir, mapId + ".jpg");
        if (imageFile.exists()) return imageFile;

        imageFile = new File(new File(mapDir, mapId), "map.png");
        if (imageFile.exists()) return imageFile;

        return null;
    }

    private String extractPlaceName(org.json.JSONObject place) {
        org.json.JSONObject nameObj = place.optJSONObject("name");
        if (nameObj == null) return "";

        if (nameObj.has("es_ES")) return nameObj.optString("es_ES");
        if (nameObj.has("en_US")) return nameObj.optString("en_US");
        if (nameObj.has("zh_CN")) return nameObj.optString("zh_CN");

        // Use first available
        java.util.Iterator<String> keys = nameObj.keys();
        if (keys.hasNext()) return nameObj.optString(keys.next());

        return "";
    }
}
