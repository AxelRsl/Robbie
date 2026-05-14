package com.robbie.data.server;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

import com.robbie.data.local.RobbieDatabase;

public class VoiceReportHandler extends BaseHandler {
    private static final String TAG = "VoiceReportHandler";
    private static final String DIR = "/storage/emulated/0/moduledata/module_robot_character_profile/1774460314669";
    private static final String FILE = DIR + "/voice_reports.json";

    public VoiceReportHandler(RobbieDatabase db, Gson gson) {
        super(db, gson);
    }

    @Override
    public Response handle(Method method, List<String> parts, IHTTPSession session) {
        if (method == Method.GET) {
            return getReports();
        }
        return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
    }

    private Response getReports() {
        File file = new File(FILE);
        if (!file.exists()) {
            return jsonResponse(Response.Status.OK, new ArrayList<>());
        }
        try {
            List<Map<String, Object>> result = normalizeLogs(readLogs(file, gson));
            return jsonResponse(Response.Status.OK, result);
        } catch (Exception e) {
            Log.e(TAG, "Error reading voice reports JSON", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    // Método estático para registrar interacciones desde el Bridge
    public static synchronized void logInteraction(String robotName, String question, String answer, long durationSecs, boolean resolved, String userId, long startedAtMs, long endedAtMs) {
        File dir = new File(DIR);
        if (!dir.exists()) dir.mkdirs();

        File file = new File(FILE);
        List<Map<String, Object>> logs = new ArrayList<>();
        Gson gson = new Gson();

        try {
            logs.addAll(normalizeLogs(readLogs(file, gson)));
        } catch (Exception e) {
            Log.e(TAG, "Error reading previous logs", e);
        }

        // Crear nueva entrada
        Map<String, Object> newLog = new HashMap<>();
        long safeEndedAtMs = endedAtMs > 0 ? endedAtMs : System.currentTimeMillis();
        long safeStartedAtMs = startedAtMs > 0 ? startedAtMs : Math.max(0L, safeEndedAtMs - (Math.max(durationSecs, 1L) * 1000L));
        newLog.put("id", "log_" + safeEndedAtMs);
        newLog.put("robot", robotName);
        newLog.put("user", userId);
        newLog.put("question", question);
        newLog.put("answer", answer);
        newLog.put("resolved", resolved);
        newLog.put("timestamp", formatIsoTimestamp(safeEndedAtMs));
        newLog.put("timestampMs", safeEndedAtMs);
        newLog.put("startedAtMs", safeStartedAtMs);
        newLog.put("endedAtMs", safeEndedAtMs);
        
        // Duración real en segundos
        newLog.put("duration", durationSecs); 

        logs.add(0, newLog); // Insertar al principio
        
        // Mantener maximo 1000 logs para evitar saturar memoria
        if (logs.size() > 1000) {
            logs = logs.subList(0, 1000);
        }

        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8")) {
            osw.write(gson.toJson(logs));
            osw.flush();
            Log.d(TAG, "Interaccion registrada con exito");
        } catch (Exception e) {
            Log.e(TAG, "Error saving voice reports JSON", e);
        }
    }

    private static List<Map<String, Object>> readLogs(File file, Gson gson) throws Exception {
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
             BufferedReader reader = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            List<Map<String, Object>> result = gson.fromJson(sb.toString(), new TypeToken<List<Map<String, Object>>>(){}.getType());
            return result != null ? result : new ArrayList<>();
        }
    }

    private static List<Map<String, Object>> normalizeLogs(List<Map<String, Object>> rawLogs) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> log : rawLogs) {
            if (log != null) {
                normalized.add(normalizeLog(log));
            }
        }
        return normalized;
    }

    private static Map<String, Object> normalizeLog(Map<String, Object> log) {
        Map<String, Object> normalized = new HashMap<>(log);

        long timestampMs = asLong(normalized.get("timestampMs"));
        long endedAtMs = asLong(normalized.get("endedAtMs"));
        long startedAtMs = asLong(normalized.get("startedAtMs"));
        long durationSecs = asLong(normalized.get("duration"));
        String timestamp = asString(normalized.get("timestamp"));
        String id = asString(normalized.get("id"));

        if (timestampMs <= 0) {
            if (endedAtMs > 0) {
                timestampMs = endedAtMs;
            } else {
                timestampMs = parseTimestampMillis(timestamp);
            }
        }

        if (timestampMs <= 0 && id.startsWith("log_")) {
            try {
                timestampMs = Long.parseLong(id.substring(4));
            } catch (NumberFormatException ignored) {
            }
        }

        if (endedAtMs <= 0) {
            endedAtMs = timestampMs;
        }

        if (startedAtMs <= 0) {
            if (endedAtMs > 0 && durationSecs > 0) {
                startedAtMs = Math.max(0L, endedAtMs - (durationSecs * 1000L));
            } else {
                startedAtMs = endedAtMs;
            }
        }

        if ((timestamp == null || timestamp.isEmpty()) && timestampMs > 0) {
            timestamp = formatIsoTimestamp(timestampMs);
        }

        if ((id == null || id.isEmpty()) && timestampMs > 0) {
            id = "log_" + timestampMs;
        }

        normalized.put("id", id);
        normalized.put("timestamp", timestamp);
        normalized.put("timestampMs", timestampMs);
        normalized.put("startedAtMs", startedAtMs);
        normalized.put("endedAtMs", endedAtMs);

        return normalized;
    }

    private static long parseTimestampMillis(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) {
            return 0L;
        }
        try {
            SimpleDateFormat isoUtc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            isoUtc.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = isoUtc.parse(timestamp);
            return parsed != null ? parsed.getTime() : 0L;
        } catch (ParseException ignored) {
        }
        try {
            return Long.parseLong(timestamp);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String formatIsoTimestamp(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(millis));
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
