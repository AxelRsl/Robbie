package com.robbie.data.server;

import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.robbie.data.local.RobbieDatabase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

public class KnowledgeDocumentHandler extends BaseHandler {

    private static final String TAG = "KnowledgeDocumentHandler";
    private static final String DIR = Environment.getExternalStorageDirectory()
            + "/moduledata/module_robot_character_profile/1774460314669";
    private static final String DOCS_DIR = DIR + "/docs";
    private static final String FILE = DIR + "/documents.json";

    public KnowledgeDocumentHandler(RobbieDatabase db, Gson gson) {
        super(db, gson);
    }

    @Override
    public Response handle(Method method, List<String> parts, IHTTPSession session) {
        String subAction = parts.size() > 2 ? parts.get(2) : null;
        
        if ("upload".equals(subAction) && method == Method.POST) {
            return handleUpload(session);
        }

        switch (method) {
            case GET:
                return getDocuments();
            case POST:
            case PUT:
                return saveDocuments(session);
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
        }
    }

    private Response getDocuments() {
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
            List<?> result = gson.fromJson(sb.toString(), List.class);
            return jsonResponse(Response.Status.OK, result != null ? result : new ArrayList<>());
        } catch (Exception e) {
            Log.e(TAG, "Error reading docs JSON", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    private Response saveDocuments(IHTTPSession session) {
        String body = getRequestBody(session);
        if (body == null || body.isEmpty()) return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Empty body"));
        File dir = new File(DIR);
        if (!dir.exists()) dir.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(new File(FILE));
             OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8")) {
            osw.write(body);
            osw.flush();
            return jsonResponse(Response.Status.OK, mapOf("success", true));
        } catch (Exception e) {
            Log.e(TAG, "Error saving docs JSON", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    private Response handleUpload(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);

            Map<String, List<String>> decodedParamNames = session.getParameters();
            String originalFileName = "uploaded_doc";
            if (decodedParamNames.containsKey("fileName")) {
                originalFileName = decodedParamNames.get("fileName").get(0);
            }

            String tmpFilePath = files.get("file");
            if (tmpFilePath == null) {
                return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Missing file"));
            }

            File docsDir = new File(DOCS_DIR);
            if (!docsDir.exists()) docsDir.mkdirs();

            File targetFile = new File(docsDir, originalFileName);
            File tmpFile = new File(tmpFilePath);
            
            try (FileInputStream in = new FileInputStream(tmpFile);
                 FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            // Si es un archivo de texto, subimos su contenido a AgentCore para el RAG local
            if (originalFileName.toLowerCase().endsWith(".txt")) {
                try {
                    StringBuilder text = new StringBuilder();
                    try (FileInputStream fis = new FileInputStream(targetFile);
                         InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
                         BufferedReader reader = new BufferedReader(isr)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            text.append(line).append("\n");
                        }
                    }
                    if (text.length() > 0) {
                        com.ainirobot.agent.AgentCore.INSTANCE.uploadInterfaceInfo(
                                "Base de conocimiento adicional para respuestas de la empresa:\n" + text.toString());
                        Log.i(TAG, "Document uploaded and injected into AgentCore context.");
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Failed to ingest text into AgentCore", ex);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("path", targetFile.getAbsolutePath());
            return jsonResponse(Response.Status.OK, result);
        } catch (Exception e) {
            Log.e(TAG, "Error uploading document file", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }
}
