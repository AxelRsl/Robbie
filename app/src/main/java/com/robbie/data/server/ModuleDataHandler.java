package com.robbie.data.server;

import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.robbie.data.local.RobbieDatabase;
import com.robbie.RobotApp;

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

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Handler para leer y escribir datos de los modulos del robot desde el filesystem.
 * Lee/escribe JSONs de /storage/emulated/0/moduledata/ (gestionados por OrionBase).
 *
 * Endpoints:
 *   GET  /api/moduledata                          -> Lista todos los modulos
 *   GET  /api/moduledata/{module}                  -> Obtiene configs de un modulo
 *   GET  /api/moduledata/{module}/{configId}        -> Obtiene una config especifica
 *   PUT  /api/moduledata/{module}/{configId}        -> Actualiza una config (escribe al JSON)
 */
public class ModuleDataHandler extends BaseHandler {

    private static final String TAG = "ModuleDataHandler";
    private static final String MODULEDATA_PATH = Environment.getExternalStorageDirectory()
            + "/moduledata";

    public ModuleDataHandler(RobbieDatabase db, Gson gson) {
        super(db, gson);
    }

    @Override
    public Response handle(Method method, List<String> parts, IHTTPSession session) {
        String moduleName = parts.size() > 2 ? parts.get(2) : null;
        String configId = parts.size() > 3 ? parts.get(3) : null;

        switch (method) {
            case GET:
                if (moduleName == null) return listModules();
                if (configId == null) return getModuleConfigs(moduleName);
                return getSingleConfig(moduleName, configId);
            case PUT:
                if (moduleName == null || configId == null) {
                    return jsonResponse(Response.Status.BAD_REQUEST,
                            mapOf("error", "PUT requires /api/moduledata/{module}/{configId}"));
                }
                return updateConfig(moduleName, configId, session);
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED,
                        mapOf("error", "Only GET and PUT are supported"));
        }
    }

    // ── GET: List all modules ──────────────────────────────────────
    private Response listModules() {
        File moduledataDir = new File(MODULEDATA_PATH);
        if (!moduledataDir.exists() || !moduledataDir.isDirectory()) {
            return jsonResponse(Response.Status.NOT_FOUND,
                    mapOf("error", "moduledata directory not found"));
        }

        List<Map<String, Object>> modules = new ArrayList<>();
        File[] moduleDirs = moduledataDir.listFiles(File::isDirectory);
        if (moduleDirs == null) {
            return jsonResponse(Response.Status.OK, modules);
        }

        for (File moduleDir : moduleDirs) {
            Map<String, Object> moduleInfo = new HashMap<>();
            moduleInfo.put("name", moduleDir.getName());
            List<Map<String, Object>> configs = readModuleConfigs(moduleDir);
            moduleInfo.put("configCount", configs.size());
            List<String> configIds = new ArrayList<>();
            for (Map<String, Object> config : configs) {
                configIds.add((String) config.get("configId"));
            }
            moduleInfo.put("configIds", configIds);
            modules.add(moduleInfo);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("modules", modules);
        result.put("path", MODULEDATA_PATH);
        result.put("count", modules.size());
        return jsonResponse(Response.Status.OK, result);
    }

    // ── GET: All configs for a module ──────────────────────────────
    private Response getModuleConfigs(String moduleName) {
        File moduleDir = new File(MODULEDATA_PATH, moduleName);
        if (!moduleDir.exists() || !moduleDir.isDirectory()) {
            return jsonResponse(Response.Status.NOT_FOUND,
                    mapOf("error", "Module not found: " + moduleName));
        }

        List<Map<String, Object>> configs = readModuleConfigs(moduleDir);
        Map<String, Object> result = new HashMap<>();
        result.put("module", moduleName);
        result.put("configs", configs);
        result.put("count", configs.size());
        return jsonResponse(Response.Status.OK, result);
    }

    // ── GET: Single config by configId ─────────────────────────────
    private Response getSingleConfig(String moduleName, String configId) {
        File configDir = new File(MODULEDATA_PATH + "/" + moduleName, configId);
        if (!configDir.exists() || !configDir.isDirectory()) {
            return jsonResponse(Response.Status.NOT_FOUND,
                    mapOf("error", "Config not found: " + configId));
        }

        File[] jsonFiles = configDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            return jsonResponse(Response.Status.NOT_FOUND,
                    mapOf("error", "No JSON files in config: " + configId));
        }

        File jsonFile = jsonFiles[0];
        String content = readFileContent(jsonFile);
        Map<String, Object> config = new HashMap<>();
        config.put("configId", configId);
        config.put("fileName", jsonFile.getName());
        try {
            JsonElement element = JsonParser.parseString(content);
            config.put("data", gson.fromJson(element, Object.class));
        } catch (Exception e) {
            config.put("data", content);
            config.put("parseError", e.getMessage());
        }
        return jsonResponse(Response.Status.OK, config);
    }

    // ── PUT: Update a config ───────────────────────────────────────
    private Response updateConfig(String moduleName, String configId, IHTTPSession session) {
        File configDir = new File(MODULEDATA_PATH + "/" + moduleName, configId);
        if (!configDir.exists() || !configDir.isDirectory()) {
            return jsonResponse(Response.Status.NOT_FOUND,
                    mapOf("error", "Config not found: " + configId));
        }

        File[] jsonFiles = configDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            return jsonResponse(Response.Status.NOT_FOUND,
                    mapOf("error", "No JSON files in config: " + configId));
        }

        String body = getRequestBody(session);
        if (body.isEmpty() || body.equals("{}")) {
            return jsonResponse(Response.Status.BAD_REQUEST,
                    mapOf("error", "Empty request body"));
        }

        // Validate JSON
        try {
            JsonParser.parseString(body);
        } catch (Exception e) {
            return jsonResponse(Response.Status.BAD_REQUEST,
                    mapOf("error", "Invalid JSON: " + e.getMessage()));
        }

        // Write to the first JSON file in this config dir
        File jsonFile = jsonFiles[0];
        try {
            writeFileContent(jsonFile, body);
            Log.i(TAG, "[PUT] Updated " + jsonFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error writing config file", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR,
                    mapOf("error", "Failed to write: " + e.getMessage()));
        }

        // If this is a character_profile update, also sync to AgentOS
        if (moduleName.contains("character_profile") && !moduleName.contains("_d")) {
            syncPersonaToAgentOS(body);
        }

        // Return the updated config
        Map<String, Object> result = new HashMap<>();
        result.put("configId", configId);
        result.put("fileName", jsonFile.getName());
        result.put("message", "Config updated successfully");
        try {
            JsonElement element = JsonParser.parseString(body);
            result.put("data", gson.fromJson(element, Object.class));
        } catch (Exception e) {
            result.put("data", body);
        }
        return jsonResponse(Response.Status.OK, result);
    }

    /**
     * Sincroniza los datos de personalidad al AgentOS cuando se modifica
     * module_robot_character_profile desde el panel.
     */
    @SuppressWarnings("unchecked")
    private void syncPersonaToAgentOS(String jsonBody) {
        try {
            Map<String, Object> data = gson.fromJson(jsonBody, Map.class);
            if (data == null) return;

            String nickname = data.get("nickname") != null ? data.get("nickname").toString() : "";
            String role = data.get("role") != null ? data.get("role").toString() : "";
            String companyProfile = data.get("company_profile") != null ? data.get("company_profile").toString() : "";
            String extraInfo = data.get("extra_info") != null ? data.get("extra_info").toString() : "";

            // Build persona string for AgentOS setPersona()
            StringBuilder persona = new StringBuilder();
            if (!nickname.isEmpty()) persona.append("Mi nombre es ").append(nickname).append(". ");
            if (!role.isEmpty()) persona.append(role).append(" ");
            if (!companyProfile.isEmpty()) persona.append(companyProfile).append(" ");
            if (!extraInfo.isEmpty()) persona.append(extraInfo);

            String languageStyle = data.get("language_style") != null ? data.get("language_style").toString() : "";

            // Build objective from role
            String objective = !role.isEmpty() ? role : "Asistir a visitantes y responder preguntas.";

            RobotApp app = RobotApp.getInstance();
            if (app != null) {
                // updateAgentPersona(persona, objective, robotName)
                app.updateAgentPersona(persona.toString(), objective, nickname);
                // Also set style via AgentOS if available
                if (!languageStyle.isEmpty()) {
                    app.updateAgentStyle(languageStyle);
                }
                Log.i(TAG, "[SYNC] Persona synced to AgentOS: " + nickname + " style=" + languageStyle);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error syncing persona to AgentOS", e);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    private List<Map<String, Object>> readModuleConfigs(File moduleDir) {
        List<Map<String, Object>> configs = new ArrayList<>();
        File[] subDirs = moduleDir.listFiles(File::isDirectory);
        if (subDirs == null) return configs;

        for (File subDir : subDirs) {
            if ("module_public".equals(subDir.getName())) continue;
            File[] jsonFiles = subDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (jsonFiles == null || jsonFiles.length == 0) continue;

            for (File jsonFile : jsonFiles) {
                try {
                    String content = readFileContent(jsonFile);
                    if (content.isEmpty() || content.equals("{}")) continue;
                    Map<String, Object> config = new HashMap<>();
                    config.put("configId", subDir.getName());
                    config.put("fileName", jsonFile.getName());
                    try {
                        JsonElement element = JsonParser.parseString(content);
                        config.put("data", gson.fromJson(element, Object.class));
                    } catch (Exception e) {
                        config.put("data", content);
                        config.put("parseError", e.getMessage());
                    }
                    configs.add(config);
                } catch (Exception e) {
                    Log.w(TAG, "Error reading " + jsonFile.getAbsolutePath(), e);
                }
            }
        }
        return configs;
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
}
