package com.robbie.data.server;

import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.robbie.data.local.RobbieDatabase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Handler for the robot's native guide routes (OrionBase module_guide).
 * Reads/writes the JSON configs + media files in /storage/emulated/0/moduledata/module_guide/.
 *
 * Endpoints:
 *   GET    /api/guide-routes                       → List all guide routes
 *   GET    /api/guide-routes/{tourId}               → Get a single guide route
 *   POST   /api/guide-routes                       → Create a new guide route
 *   PUT    /api/guide-routes/{tourId}               → Update a guide route
 *   DELETE /api/guide-routes/{tourId}               → Delete a guide route
 *   GET    /api/guide-routes/{tourId}/media          → List media files in a tour
 *   POST   /api/guide-routes/{tourId}/media          → Upload a media file
 *   GET    /api/guide-routes/{tourId}/media/{filename} → Serve a media file
 */
public class GuideRouteHandler extends BaseHandler {

    private static final String TAG = "GuideRouteHandler";
    private static final String MODULE_GUIDE_PATH = Environment.getExternalStorageDirectory()
            + "/moduledata/module_guide";

    public GuideRouteHandler(RobbieDatabase db, Gson gson) {
        super(db, gson);
    }

    @Override
    public Response handle(Method method, List<String> parts, IHTTPSession session) {
        String tourId = parts.size() > 2 ? parts.get(2) : null;
        String subAction = parts.size() > 3 ? parts.get(3) : null;
        String mediaFile = parts.size() > 4 ? parts.get(4) : null;

        // Handle media sub-endpoints
        if ("media".equals(subAction)) {
            switch (method) {
                case GET:
                    return mediaFile != null ? serveMediaFile(tourId, mediaFile) : listMedia(tourId);
                case POST:
                    return uploadMedia(tourId, mediaFile, session);
                default:
                    return jsonResponse(Response.Status.METHOD_NOT_ALLOWED,
                            mapOf("error", "Method not allowed"));
            }
        }

        // Chunked upload endpoints for large files (videos, etc.)
        if ("upload-chunk".equals(subAction) && method == Method.POST) {
            return uploadChunk(tourId, mediaFile, session);
        }
        if ("upload-complete".equals(subAction) && method == Method.POST) {
            return completeChunkedUpload(tourId, session);
        }

        switch (method) {
            case GET:
                return tourId != null ? getGuideRoute(tourId) : listGuideRoutes();
            case POST:
                return createGuideRoute(session);
            case PUT:
                return tourId != null ? updateGuideRoute(tourId, session)
                        : jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Tour ID required"));
            case DELETE:
                return tourId != null ? deleteGuideRoute(tourId)
                        : jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Tour ID required"));
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED,
                        mapOf("error", "Method not allowed"));
        }
    }

    // ── GET: List all guide routes ──────────────────────────────────────
    private Response listGuideRoutes() {
        File guideDir = new File(MODULE_GUIDE_PATH);
        Response storageError = requireSharedStorageDirectory(guideDir);
        if (storageError != null) {
            return storageError;
        }
        if (!guideDir.exists() || !guideDir.isDirectory()) {
            Map<String, Object> result = new HashMap<>();
            result.put("routes", new ArrayList<>());
            result.put("count", 0);
            result.put("path", MODULE_GUIDE_PATH);
            return jsonResponse(Response.Status.OK, result);
        }

        List<Map<String, Object>> routes = new ArrayList<>();
        File[] tourDirs = guideDir.listFiles(File::isDirectory);
        if (tourDirs == null) {
            return sharedStorageError(guideDir,
                    "Could not enumerate guide route directories");
        }
        for (File tourDir : tourDirs) {
            if ("module_public".equals(tourDir.getName())) continue;
            Map<String, Object> routeInfo = readTourConfig(tourDir);
            if (routeInfo != null) {
                routes.add(routeInfo);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("routes", routes);
        result.put("count", routes.size());
        result.put("path", MODULE_GUIDE_PATH);
        return jsonResponse(Response.Status.OK, result);
    }

    // ── GET: Single guide route ────────────────────────────────────────
    private Response getGuideRoute(String tourId) {
        File guideDir = new File(MODULE_GUIDE_PATH);
        Response storageError = requireSharedStorageDirectory(guideDir);
        if (storageError != null) {
            return storageError;
        }
        File tourDir = new File(MODULE_GUIDE_PATH, tourId);
        if (!tourDir.exists() || !tourDir.isDirectory()) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Tour not found: " + tourId));
        }

        Map<String, Object> routeInfo = readTourConfig(tourDir);
        if (routeInfo == null) {
            return jsonResponse(Response.Status.NOT_FOUND,
                    mapOf("error", "No JSON config found in tour: " + tourId));
        }
        return jsonResponse(Response.Status.OK, routeInfo);
    }

    // ── POST: Create new guide route ───────────────────────────────────
    private Response createGuideRoute(IHTTPSession session) {
        String body = getRequestBody(session);
        if (body.isEmpty() || body.equals("{}")) {
            return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Empty request body"));
        }

        try {
            // Parse incoming JSON
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(body, Map.class);
            if (data == null) {
                return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Invalid JSON"));
            }

            // Generate tour ID if not provided
            String tourId = data.containsKey("tour_id") ? data.get("tour_id").toString()
                    : String.valueOf(System.currentTimeMillis());

            data.put("tour_id", tourId);
            data.put("timestamp", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    java.util.Locale.US).format(new java.util.Date()));

            // Create tour directory
            File tourDir = new File(MODULE_GUIDE_PATH, tourId);
            if (!tourDir.exists()) {
                tourDir.mkdirs();
            }

            // Copy public assets (icons, animations)
            copyPublicAssets(tourDir);

            // Write JSON config
            String configHash = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
            File jsonFile = new File(tourDir, configHash + ".json");
            writeFileContent(jsonFile, gson.toJson(data));

            Log.i(TAG, "Created guide route: " + tourId + " -> " + jsonFile.getAbsolutePath());

            Map<String, Object> result = new HashMap<>();
            result.put("tour_id", tourId);
            result.put("message", "Guide route created");
            result.put("data", data);
            return jsonResponse(Response.Status.OK, result);
        } catch (Exception e) {
            Log.e(TAG, "Error creating guide route", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    // ── PUT: Update guide route ────────────────────────────────────────
    private Response updateGuideRoute(String tourId, IHTTPSession session) {
        File tourDir = new File(MODULE_GUIDE_PATH, tourId);
        if (!tourDir.exists() || !tourDir.isDirectory()) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Tour not found: " + tourId));
        }

        String body = getRequestBody(session);
        if (body.isEmpty() || body.equals("{}")) {
            return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Empty request body"));
        }

        try {
            JsonParser.parseString(body);
        } catch (Exception e) {
            return jsonResponse(Response.Status.BAD_REQUEST,
                    mapOf("error", "Invalid JSON: " + e.getMessage()));
        }

        // Find the existing JSON file
        File[] jsonFiles = tourDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            return jsonResponse(Response.Status.NOT_FOUND,
                    mapOf("error", "No JSON config file found in tour: " + tourId));
        }

        try {
            writeFileContent(jsonFiles[0], body);
            Log.i(TAG, "Updated guide route: " + tourId);

            Map<String, Object> result = new HashMap<>();
            result.put("tour_id", tourId);
            result.put("message", "Guide route updated");
            try {
                JsonElement element = JsonParser.parseString(body);
                result.put("data", gson.fromJson(element, Object.class));
            } catch (Exception e) {
                result.put("data", body);
            }
            return jsonResponse(Response.Status.OK, result);
        } catch (Exception e) {
            Log.e(TAG, "Error updating guide route", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    // ── DELETE: Delete guide route ──────────────────────────────────────
    private Response deleteGuideRoute(String tourId) {
        File guideDir = new File(MODULE_GUIDE_PATH);
        Response storageError = requireSharedStorageDirectory(guideDir);
        if (storageError != null) {
            return storageError;
        }
        File tourDir = new File(MODULE_GUIDE_PATH, tourId);
        if (!tourDir.exists() || !tourDir.isDirectory()) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Tour not found: " + tourId));
        }

        try {
            deleteRecursive(tourDir);
            Log.i(TAG, "Deleted guide route: " + tourId);
            return jsonResponse(Response.Status.OK, mapOf("message", "Guide route deleted: " + tourId));
        } catch (Exception e) {
            Log.e(TAG, "Error deleting guide route", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    // ── Media: List media files ────────────────────────────────────────
    private Response listMedia(String tourId) {
        File guideDir = new File(MODULE_GUIDE_PATH);
        Response storageError = requireSharedStorageDirectory(guideDir);
        if (storageError != null) {
            return storageError;
        }
        File tourDir = new File(MODULE_GUIDE_PATH, tourId);
        if (!tourDir.exists() || !tourDir.isDirectory()) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Tour not found: " + tourId));
        }

        List<Map<String, Object>> media = new ArrayList<>();
        File[] files = tourDir.listFiles();
        if (files == null) {
            return sharedStorageError(tourDir,
                    "Could not enumerate media files for tour: " + tourId);
        }
        for (File file : files) {
            if (file.isDirectory()) continue;
            String name = file.getName();
            if (name.endsWith(".json")) continue;

            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("name", name);
            fileInfo.put("size", file.length());
            fileInfo.put("lastModified", file.lastModified());
            fileInfo.put("url", "/api/guide-routes/" + tourId + "/media/" + name);

            String lower = name.toLowerCase();
            if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".avi")) {
                fileInfo.put("type", "video");
            } else if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg")) {
                fileInfo.put("type", "audio");
            } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".gif") || lower.endsWith(".webp")) {
                fileInfo.put("type", "image");
            } else if (lower.endsWith(".animator")) {
                fileInfo.put("type", "animation");
            } else {
                fileInfo.put("type", "other");
            }
            media.add(fileInfo);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("tour_id", tourId);
        result.put("media", media);
        result.put("count", media.size());
        return jsonResponse(Response.Status.OK, result);
    }

    // ── Media: Upload file (small files only, <2MB) ────────────────────
    // For large files, use /upload-chunk + /upload-complete instead.
    private Response uploadMedia(String tourId, String filename, IHTTPSession session) {
        File tourDir = new File(MODULE_GUIDE_PATH, tourId);
        if (!tourDir.exists()) {
            tourDir.mkdirs();
        }

        try {
            String contentLengthStr = session.getHeaders().get("content-length");
            long contentLength = contentLengthStr != null ? Long.parseLong(contentLengthStr) : 0;
            if (contentLength == 0) {
                return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Empty upload"));
            }

            if (filename == null || filename.isEmpty()) {
                filename = "upload_" + System.currentTimeMillis();
            }
            filename = sanitizeFilename(filename);

            // For small files, read body from NanoHTTPD's parsed data
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String bodyData = files.get("postData");
            if (bodyData != null) {
                File destFile = new File(tourDir, filename);
                writeFileContent(destFile, bodyData);
                Log.i(TAG, "Uploaded small media: " + destFile.getAbsolutePath());

                Map<String, Object> result = new HashMap<>();
                result.put("tour_id", tourId);
                result.put("filename", filename);
                result.put("size", destFile.length());
                result.put("url", "/api/guide-routes/" + tourId + "/media/" + filename);
                return jsonResponse(Response.Status.OK, result);
            }
            return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "No data received"));
        } catch (Exception e) {
            Log.e(TAG, "Error uploading media", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR,
                    mapOf("error", "Upload failed: " + e.getMessage()));
        }
    }

    // ── Chunked Upload: Receive a single chunk ─────────────────────────
    // POST /api/guide-routes/{tourId}/upload-chunk/{chunkIndex}
    // Body: raw binary chunk (~512KB)
    // Query params: filename, chunkIndex, totalChunks
    private Response uploadChunk(String tourId, String chunkIndexStr, IHTTPSession session) {
        File tourDir = new File(MODULE_GUIDE_PATH, tourId);
        File chunksDir = new File(tourDir, ".chunks");
        if (!chunksDir.exists()) {
            chunksDir.mkdirs();
        }

        try {
            // Get chunk index from URL path
            int chunkIndex = 0;
            if (chunkIndexStr != null) {
                try { chunkIndex = Integer.parseInt(chunkIndexStr); } catch (Exception ignored) {}
            }

            // NanoHTTPD has already read the body — get it from parsed data
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);

            // The raw body is stored in a temp file by NanoHTTPD
            String tempPath = files.get("postData");
            if (tempPath == null) {
                // Check for 'content' key too
                for (Map.Entry<String, String> entry : files.entrySet()) {
                    if (entry.getValue() != null && new File(entry.getValue()).exists()) {
                        tempPath = entry.getValue();
                        break;
                    }
                }
            }

            // Write chunk data to numbered chunk file
            File chunkFile = new File(chunksDir, "chunk_" + String.format("%05d", chunkIndex));

            if (tempPath != null && new File(tempPath).exists()) {
                // NanoHTTPD stored it as a temp file — copy it
                copyFile(new File(tempPath), chunkFile);
            } else {
                // Body might be inline string data (for very small chunks)
                String body = getRequestBody(session);
                if (body != null && !body.isEmpty()) {
                    try (FileOutputStream fos = new FileOutputStream(chunkFile)) {
                        fos.write(body.getBytes("ISO-8859-1")); // preserve binary
                        fos.flush();
                    }
                } else {
                    return jsonResponse(Response.Status.BAD_REQUEST,
                            mapOf("error", "No chunk data received for chunk " + chunkIndex));
                }
            }

            Log.i(TAG, "Received chunk " + chunkIndex + " (" + chunkFile.length() + " bytes) for tour " + tourId);

            Map<String, Object> result = new HashMap<>();
            result.put("chunkIndex", chunkIndex);
            result.put("size", chunkFile.length());
            result.put("status", "ok");
            return jsonResponse(Response.Status.OK, result);
        } catch (Exception e) {
            Log.e(TAG, "Error receiving chunk", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR,
                    mapOf("error", "Chunk upload failed: " + e.getMessage()));
        }
    }

    // ── Chunked Upload: Merge all chunks into final file ───────────────
    // POST /api/guide-routes/{tourId}/upload-complete
    // Body JSON: { "filename": "video.mp4", "totalChunks": 10, "totalSize": 12345678 }
    private Response completeChunkedUpload(String tourId, IHTTPSession session) {
        File tourDir = new File(MODULE_GUIDE_PATH, tourId);
        File chunksDir = new File(tourDir, ".chunks");

        String body = getRequestBody(session);
        if (body.isEmpty()) {
            return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Missing body"));
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(body, Map.class);
            String filename = (String) data.get("filename");
            int totalChunks = data.get("totalChunks") != null
                    ? ((Number) data.get("totalChunks")).intValue() : 0;

            if (filename == null || filename.isEmpty()) {
                return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Missing filename"));
            }
            filename = sanitizeFilename(filename);

            // Merge all chunks into final file
            File destFile = new File(tourDir, filename);
            long totalSize = 0;
            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                for (int i = 0; i < totalChunks; i++) {
                    File chunkFile = new File(chunksDir, "chunk_" + String.format("%05d", i));
                    if (!chunkFile.exists()) {
                        return jsonResponse(Response.Status.BAD_REQUEST,
                                mapOf("error", "Missing chunk " + i + " of " + totalChunks));
                    }
                    try (FileInputStream fis = new FileInputStream(chunkFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = fis.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                            totalSize += read;
                        }
                    }
                }
                fos.flush();
            }

            // Clean up chunk files
            deleteRecursive(chunksDir);

            Log.i(TAG, "Merged " + totalChunks + " chunks into: " + destFile.getAbsolutePath()
                    + " (" + totalSize + " bytes)");

            Map<String, Object> result = new HashMap<>();
            result.put("tour_id", tourId);
            result.put("filename", filename);
            result.put("size", destFile.length());
            result.put("url", "/api/guide-routes/" + tourId + "/media/" + filename);
            result.put("path", destFile.getAbsolutePath());
            result.put("chunks", totalChunks);
            return jsonResponse(Response.Status.OK, result);
        } catch (Exception e) {
            Log.e(TAG, "Error merging chunks", e);
            // Clean up on failure
            if (chunksDir.exists()) deleteRecursive(chunksDir);
            return jsonResponse(Response.Status.INTERNAL_ERROR,
                    mapOf("error", "Merge failed: " + e.getMessage()));
        }
    }

    /** Sanitize a filename: URL-decode, strip special chars, truncate */
    private String sanitizeFilename(String filename) {
        try {
            filename = java.net.URLDecoder.decode(filename, "UTF-8");
        } catch (Exception ignored) {}
        filename = filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        if (filename.length() > 100) {
            int dotIdx = filename.lastIndexOf('.');
            String ext = dotIdx > 0 ? filename.substring(dotIdx) : "";
            filename = filename.substring(0, 80) + "_" + System.currentTimeMillis() + ext;
        }
        return filename;
    }

    // ── Media: Serve file ──────────────────────────────────────────────
    private Response serveMediaFile(String tourId, String filename) {
        File file = new File(MODULE_GUIDE_PATH + "/" + tourId, filename);
        if (!file.exists() || !file.isFile()) {
            return jsonResponse(Response.Status.NOT_FOUND,
                    mapOf("error", "File not found: " + filename));
        }

        try {
            String mime = guessMimeType(filename);
            FileInputStream fis = new FileInputStream(file);
            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, mime, fis, file.length());
        } catch (Exception e) {
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private Map<String, Object> readTourConfig(File tourDir) {
        File[] jsonFiles = tourDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) return null;

        File jsonFile = jsonFiles[0];
        String content = readFileContent(jsonFile);
        if (content.isEmpty()) return null;

        Map<String, Object> result = new HashMap<>();
        result.put("tour_id", tourDir.getName());
        result.put("configFile", jsonFile.getName());
        result.put("path", tourDir.getAbsolutePath());

        File[] allFiles = tourDir.listFiles();
        if (allFiles == null) return null;
        int mediaCount = 0;
        for (File f : allFiles) {
            if (!f.isDirectory() && !f.getName().endsWith(".json")) mediaCount++;
        }
        result.put("mediaCount", mediaCount);

        try {
            JsonElement element = JsonParser.parseString(content);
            result.put("data", gson.fromJson(element, Object.class));
        } catch (Exception e) {
            result.put("data", content);
            result.put("parseError", e.getMessage());
        }
        return result;
    }

    private void copyPublicAssets(File tourDir) {
        File publicDir = new File(MODULE_GUIDE_PATH, "module_public");
        if (!publicDir.exists() || !publicDir.isDirectory()) return;

        File[] publicFiles = publicDir.listFiles();
        if (publicFiles == null) return;

        for (File src : publicFiles) {
            if (src.isFile()) {
                File dest = new File(tourDir, src.getName());
                if (!dest.exists()) {
                    try {
                        copyFile(src, dest);
                    } catch (Exception e) {
                        Log.w(TAG, "Could not copy public asset: " + src.getName(), e);
                    }
                }
            }
        }
    }

    private void copyFile(File src, File dest) throws Exception {
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    private String readFileContent(File file) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading file: " + file.getAbsolutePath(), e);
        }
        return sb.toString();
    }

    private void writeFileContent(File file, String content) throws Exception {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), "UTF-8")) {
            writer.write(content);
            writer.flush();
        }
    }

    private String guessMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
