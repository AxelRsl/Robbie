package com.robbie.data.server;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.robbie.data.local.RobbieDatabase;
import com.robbie.data.local.entity.SceneFunctionEntity;
import com.robbie.data.local.entity.SceneProjectEntity;

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

/**
 * REST API handler para la configuracion de proyectos de escena y sus funciones.
 *
 * Endpoints:
 *   GET    /api/scene-projects                          - Lista todos los proyectos
 *   GET    /api/scene-projects/active                   - Obtiene el proyecto activo
 *   GET    /api/scene-projects/{id}                     - Obtiene un proyecto por ID
 *   POST   /api/scene-projects                          - Crea un nuevo proyecto
 *   PUT    /api/scene-projects/{id}                     - Actualiza un proyecto
 *   PUT    /api/scene-projects/{id}/activate             - Activa un proyecto
 *   DELETE /api/scene-projects/{id}                     - Elimina un proyecto
 *
 *   GET    /api/scene-projects/{id}/functions            - Lista funciones de un proyecto
 *   POST   /api/scene-projects/{id}/functions            - Crea una funcion
 *   PUT    /api/scene-projects/{id}/functions/{funcId}   - Actualiza una funcion
 *   DELETE /api/scene-projects/{id}/functions/{funcId}   - Elimina una funcion
 *   PUT    /api/scene-projects/{id}/functions/reorder     - Reordena funciones
 */
public class SceneProjectHandler extends BaseHandler {

    private static final String TAG = "SceneProjectHandler";

    public SceneProjectHandler(RobbieDatabase db, Gson gson) {
        super(db, gson);
    }

    @Override
    public Response handle(Method method, List<String> parts, IHTTPSession session) {
        // parts: [api, scene-projects, ...]
        int size = parts.size();

        if (size == 2) {
            // /api/scene-projects
            switch (method) {
                case GET:
                    return getAllProjects();
                case POST:
                    return createProject(session);
                default:
                    return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
            }
        }

        if (size == 3) {
            String idOrAction = parts.get(2);

            if ("active".equals(idOrAction)) {
                // GET /api/scene-projects/active
                if (method == Method.GET) {
                    return getActiveProject();
                }
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
            }

            // /api/scene-projects/{id}
            switch (method) {
                case GET:
                    return getProjectById(idOrAction);
                case PUT:
                    return updateProject(idOrAction, session);
                case DELETE:
                    return deleteProject(idOrAction);
                default:
                    return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
            }
        }

        if (size >= 4) {
            String projectId = parts.get(2);
            String sub = parts.get(3);

            if ("activate".equals(sub)) {
                // PUT /api/scene-projects/{id}/activate
                if (method == Method.PUT) {
                    return activateProject(projectId);
                }
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
            }

            if ("functions".equals(sub)) {
                return handleFunctions(method, parts, projectId, session);
            }
        }

        return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Not found"));
    }

    // ─── PROJECTS ──────────────────────────────────────────────────────────────

    private Response getAllProjects() {
        List<SceneProjectEntity> projects = db.sceneProjectDao().getAllProjects();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SceneProjectEntity p : projects) {
            result.add(projectToMap(p));
        }
        return jsonResponse(Response.Status.OK, result);
    }

    private Response getActiveProject() {
        SceneProjectEntity project = db.sceneProjectDao().getActiveProject();
        if (project == null) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "No active project"));
        }
        Map<String, Object> result = projectToMap(project);
        List<SceneFunctionEntity> functions = db.sceneFunctionDao().getFunctionsByProject(project.getId());
        result.put("functions", functionsToList(functions));
        return jsonResponse(Response.Status.OK, result);
    }

    private Response getProjectById(String id) {
        SceneProjectEntity project = db.sceneProjectDao().getProjectById(id);
        if (project == null) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Project not found"));
        }
        Map<String, Object> result = projectToMap(project);
        List<SceneFunctionEntity> functions = db.sceneFunctionDao().getFunctionsByProject(id);
        result.put("functions", functionsToList(functions));
        return jsonResponse(Response.Status.OK, result);
    }

    private Response createProject(IHTTPSession session) {
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(body, mapType);

        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        SceneProjectEntity project = new SceneProjectEntity();
        project.setId(id);
        project.setName(getStringOrDefault(data, "name", "Nuevo Proyecto"));
        project.setTitleType(getStringOrDefault(data, "titleType", "TEXT"));
        project.setTitleText(getStringOrDefault(data, "titleText", ""));
        project.setTitleImageUrl(getStringOrDefault(data, "titleImageUrl", ""));
        project.setTemplateType(getStringOrDefault(data, "templateType", "FUNCTIONAL_GRID"));
        project.setBackgroundImageUrl(getStringOrDefault(data, "backgroundImageUrl", ""));
        project.setActive(getBooleanOrDefault(data, "isActive", false));
        project.setCreatedAt(now);
        project.setUpdatedAt(now);

        if (project.isActive()) {
            db.sceneProjectDao().deactivateAll();
        }

        db.sceneProjectDao().insertProject(project);
        Log.i(TAG, "Project created: " + id + " (" + project.getName() + ")");

        // Si se incluyen funciones, crearlas tambien
        if (data.containsKey("functions")) {
            createFunctionsFromData(id, data);
        }

        return jsonResponse(Response.Status.CREATED, projectToMap(project));
    }

    private Response updateProject(String id, IHTTPSession session) {
        SceneProjectEntity existing = db.sceneProjectDao().getProjectById(id);
        if (existing == null) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Project not found"));
        }

        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(body, mapType);

        existing.setName(getStringOrDefault(data, "name", existing.getName()));
        existing.setTitleType(getStringOrDefault(data, "titleType", existing.getTitleType()));
        existing.setTitleText(getStringOrDefault(data, "titleText", existing.getTitleText()));
        existing.setTitleImageUrl(getStringOrDefault(data, "titleImageUrl", existing.getTitleImageUrl()));
        existing.setTemplateType(getStringOrDefault(data, "templateType", existing.getTemplateType()));
        existing.setBackgroundImageUrl(getStringOrDefault(data, "backgroundImageUrl", existing.getBackgroundImageUrl()));
        existing.setUpdatedAt(System.currentTimeMillis());

        if (data.containsKey("isActive") && getBooleanOrDefault(data, "isActive", false)) {
            db.sceneProjectDao().deactivateAll();
            existing.setActive(true);
        }

        db.sceneProjectDao().updateProject(existing);
        Log.i(TAG, "Project updated: " + id);

        return jsonResponse(Response.Status.OK, projectToMap(existing));
    }

    private Response activateProject(String projectId) {
        SceneProjectEntity project = db.sceneProjectDao().getProjectById(projectId);
        if (project == null) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Project not found"));
        }

        db.sceneProjectDao().deactivateAll();
        db.sceneProjectDao().activateProject(projectId);
        Log.i(TAG, "Project activated: " + projectId);

        project.setActive(true);
        Map<String, Object> result = projectToMap(project);
        List<SceneFunctionEntity> functions = db.sceneFunctionDao().getFunctionsByProject(projectId);
        result.put("functions", functionsToList(functions));
        return jsonResponse(Response.Status.OK, result);
    }

    private Response deleteProject(String id) {
        db.sceneProjectDao().deleteProjectById(id);
        Log.i(TAG, "Project deleted: " + id);
        return jsonResponse(Response.Status.OK, mapOf("message", "Project deleted"));
    }

    // ─── FUNCTIONS ─────────────────────────────────────────────────────────────

    private Response handleFunctions(Method method, List<String> parts, String projectId, IHTTPSession session) {
        int size = parts.size();

        if (size == 4) {
            // /api/scene-projects/{id}/functions
            switch (method) {
                case GET:
                    return getFunctions(projectId);
                case POST:
                    return createFunction(projectId, session);
                default:
                    return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
            }
        }

        if (size == 5) {
            String funcIdOrAction = parts.get(4);

            if ("reorder".equals(funcIdOrAction)) {
                // PUT /api/scene-projects/{id}/functions/reorder
                if (method == Method.PUT) {
                    return reorderFunctions(projectId, session);
                }
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
            }

            // /api/scene-projects/{id}/functions/{funcId}
            switch (method) {
                case PUT:
                    return updateFunction(funcIdOrAction, session);
                case DELETE:
                    return deleteFunction(funcIdOrAction);
                default:
                    return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
            }
        }

        return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Not found"));
    }

    private Response getFunctions(String projectId) {
        List<SceneFunctionEntity> functions = db.sceneFunctionDao().getFunctionsByProject(projectId);
        return jsonResponse(Response.Status.OK, functionsToList(functions));
    }

    private Response createFunction(String projectId, IHTTPSession session) {
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(body, mapType);

        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        int count = db.sceneFunctionDao().getFunctionCount(projectId);

        SceneFunctionEntity func = new SceneFunctionEntity();
        func.setId(id);
        func.setProjectId(projectId);
        func.setName(getStringOrDefault(data, "name", "Nueva Funcion"));
        func.setIcon(getStringOrDefault(data, "icon", "settings"));
        func.setActivationCommand(getStringOrDefault(data, "activationCommand", ""));
        func.setOrderIndex(getIntOrDefault(data, "orderIndex", count));
        func.setColor(getStringOrDefault(data, "color", "#E4027C"));
        func.setDescription(getStringOrDefault(data, "description", ""));
        func.setCreatedAt(now);
        func.setUpdatedAt(now);

        db.sceneFunctionDao().insertFunction(func);
        Log.i(TAG, "Function created: " + id + " for project " + projectId);

        return jsonResponse(Response.Status.CREATED, functionToMap(func));
    }

    private Response updateFunction(String funcId, IHTTPSession session) {
        SceneFunctionEntity existing = db.sceneFunctionDao().getFunctionById(funcId);
        if (existing == null) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Function not found"));
        }

        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(body, mapType);

        existing.setName(getStringOrDefault(data, "name", existing.getName()));
        existing.setIcon(getStringOrDefault(data, "icon", existing.getIcon()));
        existing.setActivationCommand(getStringOrDefault(data, "activationCommand", existing.getActivationCommand()));
        existing.setOrderIndex(getIntOrDefault(data, "orderIndex", existing.getOrderIndex()));
        existing.setColor(getStringOrDefault(data, "color", existing.getColor()));
        existing.setDescription(getStringOrDefault(data, "description", existing.getDescription()));
        existing.setUpdatedAt(System.currentTimeMillis());

        db.sceneFunctionDao().updateFunction(existing);
        Log.i(TAG, "Function updated: " + funcId);

        return jsonResponse(Response.Status.OK, functionToMap(existing));
    }

    private Response deleteFunction(String funcId) {
        db.sceneFunctionDao().deleteFunctionById(funcId);
        Log.i(TAG, "Function deleted: " + funcId);
        return jsonResponse(Response.Status.OK, mapOf("message", "Function deleted"));
    }

    @SuppressWarnings("unchecked")
    private Response reorderFunctions(String projectId, IHTTPSession session) {
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(body, mapType);

        List<String> order = getStringListOrDefault(data, "order");
        for (int i = 0; i < order.size(); i++) {
            SceneFunctionEntity func = db.sceneFunctionDao().getFunctionById(order.get(i));
            if (func != null) {
                func.setOrderIndex(i);
                func.setUpdatedAt(System.currentTimeMillis());
                db.sceneFunctionDao().updateFunction(func);
            }
        }

        List<SceneFunctionEntity> updated = db.sceneFunctionDao().getFunctionsByProject(projectId);
        return jsonResponse(Response.Status.OK, functionsToList(updated));
    }

    // ─── HELPERS ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void createFunctionsFromData(String projectId, Map<String, Object> data) {
        Object functionsObj = data.get("functions");
        if (!(functionsObj instanceof List)) return;

        List<?> functionsList = (List<?>) functionsObj;
        long now = System.currentTimeMillis();
        int idx = 0;

        for (Object item : functionsList) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> funcData = (Map<String, Object>) item;

            SceneFunctionEntity func = new SceneFunctionEntity();
            func.setId(UUID.randomUUID().toString());
            func.setProjectId(projectId);
            func.setName(getStringOrDefault(funcData, "name", "Funcion " + (idx + 1)));
            func.setIcon(getStringOrDefault(funcData, "icon", "settings"));
            func.setActivationCommand(getStringOrDefault(funcData, "activationCommand", ""));
            func.setOrderIndex(getIntOrDefault(funcData, "orderIndex", idx));
            func.setColor(getStringOrDefault(funcData, "color", "#E4027C"));
            func.setDescription(getStringOrDefault(funcData, "description", ""));
            func.setCreatedAt(now);
            func.setUpdatedAt(now);

            db.sceneFunctionDao().insertFunction(func);
            idx++;
        }

        Log.i(TAG, "Created " + idx + " functions for project " + projectId);
    }

    private Map<String, Object> projectToMap(SceneProjectEntity p) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("name", p.getName());
        map.put("titleType", p.getTitleType());
        map.put("titleText", p.getTitleText());
        map.put("titleImageUrl", p.getTitleImageUrl());
        map.put("templateType", p.getTemplateType());
        map.put("backgroundImageUrl", p.getBackgroundImageUrl());
        map.put("isActive", p.isActive());
        map.put("createdAt", p.getCreatedAt());
        map.put("updatedAt", p.getUpdatedAt());
        return map;
    }

    private Map<String, Object> functionToMap(SceneFunctionEntity f) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", f.getId());
        map.put("projectId", f.getProjectId());
        map.put("name", f.getName());
        map.put("icon", f.getIcon());
        map.put("activationCommand", f.getActivationCommand());
        map.put("orderIndex", f.getOrderIndex());
        map.put("color", f.getColor());
        map.put("description", f.getDescription());
        map.put("createdAt", f.getCreatedAt());
        map.put("updatedAt", f.getUpdatedAt());
        return map;
    }

    private List<Map<String, Object>> functionsToList(List<SceneFunctionEntity> functions) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SceneFunctionEntity f : functions) {
            list.add(functionToMap(f));
        }
        return list;
    }
}
