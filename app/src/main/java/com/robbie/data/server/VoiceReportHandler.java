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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
             BufferedReader reader = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            
            List<Object> result = gson.fromJson(sb.toString(), new TypeToken<List<Object>>(){}.getType());
            return jsonResponse(Response.Status.OK, result != null ? result : new ArrayList<>());
        } catch (Exception e) {
            Log.e(TAG, "Error reading voice reports JSON", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    // Método estático para registrar interacciones desde el Bridge
    public static synchronized void logInteraction(String robotName, String question, String answer, long durationSecs, boolean resolved, String userId) {
        File dir = new File(DIR);
        if (!dir.exists()) dir.mkdirs();

        File file = new File(FILE);
        List<Object> logs = new ArrayList<>();
        Gson gson = new Gson();

        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file);
                 InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
                 BufferedReader reader = new BufferedReader(isr)) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                List<Object> existing = gson.fromJson(sb.toString(), new TypeToken<List<Object>>(){}.getType());
                if (existing != null) logs.addAll(existing);
            } catch (Exception e) {
                Log.e(TAG, "Error reading previous logs", e);
            }
        }

        // Crear nueva entrada
        java.util.Map<String, Object> newLog = new java.util.HashMap<>();
        newLog.put("id", "log_" + System.currentTimeMillis());
        newLog.put("robot", robotName);
        newLog.put("user", userId);
        newLog.put("question", question);
        newLog.put("answer", answer);
        newLog.put("resolved", resolved);
        
        // Formato ISO 8601 completo
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        newLog.put("timestamp", sdf.format(new Date()));
        
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
}
