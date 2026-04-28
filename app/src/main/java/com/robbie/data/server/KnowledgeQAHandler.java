package com.robbie.data.server;

import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
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
 * Handler for Knowledge Q&A database.
 * Stores the Q&A pairs in the robot's local DB directory.
 * Endpoint: /api/knowledge
 */
public class KnowledgeQAHandler extends BaseHandler {

    private static final String TAG = "KnowledgeQAHandler";
    // We use the character profile directory as requested
    private static final String KNOWLEDGE_DIR = Environment.getExternalStorageDirectory()
            + "/moduledata/module_robot_character_profile/1774460314669";
    private static final String KNOWLEDGE_FILE = KNOWLEDGE_DIR + "/leveldb_qa.json";

    public KnowledgeQAHandler(RobbieDatabase db, Gson gson) {
        super(db, gson);
    }

    @Override
    public Response handle(Method method, List<String> parts, IHTTPSession session) {
        switch (method) {
            case GET:
                return getKnowledge();
            case POST:
            case PUT:
                return saveKnowledge(session);
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
        }
    }

    private Response getKnowledge() {
        File file = new File(KNOWLEDGE_FILE);
        if (!file.exists()) {
            return jsonResponse(Response.Status.OK, new ArrayList<>());
        }

        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
             BufferedReader reader = new BufferedReader(isr)) {
            
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            
            List<?> result = gson.fromJson(sb.toString(), List.class);
            return jsonResponse(Response.Status.OK, result != null ? result : new ArrayList<>());
        } catch (Exception e) {
            Log.e(TAG, "Error reading knowledge file", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    private Response saveKnowledge(IHTTPSession session) {
        String body = getRequestBody(session);
        if (body == null || body.isEmpty()) {
            return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Empty body"));
        }

        File dir = new File(KNOWLEDGE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File file = new File(KNOWLEDGE_FILE);
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8")) {
            osw.write(body);
            osw.flush();
            return jsonResponse(Response.Status.OK, mapOf("success", true));
        } catch (Exception e) {
            Log.e(TAG, "Error saving knowledge file", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }
}
