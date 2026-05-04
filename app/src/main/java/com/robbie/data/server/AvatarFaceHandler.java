package com.robbie.data.server;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.robbie.core.animation.ProceduralAnimationManager;
import com.robbie.data.local.RobbieDatabase;
import com.robbie.platform.react.EveActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Exposes OrionStar HeadService procedural animation controls over the local Robbie HTTP API.
 *
 * Endpoints:
 *   GET  /api/avatar-face/status
 *   GET  /api/avatar-face/presets
 *   POST /api/avatar-face/enable
 *   POST /api/avatar-face/disable
 *   POST /api/avatar-face/play
 *   POST /api/avatar-face/stop
 *   POST /api/avatar-face/record/start
 *   POST /api/avatar-face/record/stop
 */
public class AvatarFaceHandler extends BaseHandler {

    private static final String TAG = "AvatarFaceHandler";
    private static final List<String> SUPPORTED_EMOTIONS = Arrays.asList(
        "neutral",
        "sceptic",
        "sad",
        "broken",
        "tired",
        "crazy",
        "wink",
        "surprised",
        "angry",
        "in_love",
        "happy",
        "denying",
        "calm",
        "confused",
        "interested",
        "afraid",
        "disgusted"
    );

    private final ProceduralAnimationManager animationManager;

    public AvatarFaceHandler(Context context, RobbieDatabase db, Gson gson) {
        super(db, gson);
        this.animationManager = ProceduralAnimationManager.getInstance(context);
    }

    @Override
    public Response handle(NanoHTTPD.Method method, List<String> parts, IHTTPSession session) {
        String action = parts.size() > 2 ? parts.get(2) : "status";

        if (method == NanoHTTPD.Method.GET && "status".equals(action)) {
            return getStatus();
        }

        if (method == NanoHTTPD.Method.GET && "presets".equals(action)) {
            return getPresets();
        }

        if (method != NanoHTTPD.Method.POST) {
            return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
        }

        switch (action) {
            case "enable":
                return toggleAvatarMode(true);
            case "disable":
                return toggleAvatarMode(false);
            case "play":
                return playProceduralAnimation(session);
            case "stop":
                return stopProceduralAnimation();
            case "record":
                if (parts.size() < 4) {
                    return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Use /api/avatar-face/record/start or /stop"));
                }
                String recordAction = parts.get(3);
                if ("start".equals(recordAction)) {
                    return toggleRecording(true);
                }
                if ("stop".equals(recordAction)) {
                    return toggleRecording(false);
                }
                return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Unknown record action"));
            default:
                return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Unknown avatar-face action"));
        }
    }

    private Response getStatus() {
        boolean available = animationManager.isAvailable();
        boolean systemFaceVisible = animationManager.isSystemFaceVisible();
        Map<String, Object> result = new HashMap<>();
        result.put("connected", available);
        result.put("supported", true);
        result.put("proceduralAnimations", available);
        result.put("systemFaceVisible", systemFaceVisible);
        result.put("animationEngine", "HeadMovement + SystemFaceReveal");
        result.put("supportedEmotions", new ArrayList<>(SUPPORTED_EMOTIONS));
        result.put("supportedAnimations", Arrays.asList("expression", "systemFace", "headMovement"));
        result.put("actions", Arrays.asList("enable", "disable", "play", "stop"));
        result.put("status", "ready");
        return jsonResponse(Response.Status.OK, result);
    }

    private Response getPresets() {
        Map<String, Object> result = new HashMap<>();
        result.put("connected", animationManager.isAvailable());
        result.put("presets", Arrays.asList(
            preset("neutral", "neutral", "Estado neutro, ojos relajados"),
            preset("sceptic", "sceptic", "Mirada escéptica, ojos entrecerrados"),
            preset("sad", "sad", "Expresión triste, ojos caídos"),
            preset("broken", "broken", "Ojos rotos en X, error o falla"),
            preset("tired", "tired", "Ojos cansados, semicírculos abajo"),
            preset("crazy", "crazy", "Ojos locos, X y espiral"),
            preset("wink", "wink", "Guiño amigable"),
            preset("surprised", "surprised", "Ojos abiertos de sorpresa"),
            preset("angry", "angry", "Expresión de enojo, ojos rojos"),
            preset("in_love", "in_love", "Ojos de corazón, enamorado"),
            preset("happy", "happy", "Sonrisa feliz, ojos arqueados"),
            preset("denying", "denying", "Negando, ojos en > <"),
            preset("calm", "calm", "Estado calmado y sereno"),
            preset("confused", "confused", "Expresión de confusión"),
            preset("interested", "interested", "Atención e interés"),
            preset("afraid", "afraid", "Expresión de miedo"),
            preset("disgusted", "disgusted", "Expresión de disgusto")
        ));
        return jsonResponse(Response.Status.OK, result);
    }

    private Map<String, Object> preset(String id, String emotion, String description) {
        Map<String, Object> preset = new HashMap<>();
        preset.put("id", id);
        preset.put("emotion", emotion);
        preset.put("description", description);
        return preset;
    }

    private Response playProceduralAnimation(IHTTPSession session) {
        String body = getRequestBody(session);
        @SuppressWarnings("unchecked")
        Map<String, Object> json = gson.fromJson(body, Map.class);
        if (json == null) {
            json = new HashMap<>();
        }

        String type = getStringOrDefault(json, "type", "expression");
        String emotionName = getStringOrDefault(json, "emotion", getStringOrDefault(json, "preset", "neutral"));
        long durationMs = getIntOrDefault(json, "durationMs", 5000);
        boolean showFace = getBooleanOrDefault(json, "showFace", true);

        try {
            ProceduralAnimationManager.Emotion emotion = resolveEmotion(emotionName);

            if (showFace) {
                // Emit emotion to React Native FaceOverlay + physical head movement
                EveActivity.emitEmotionFromExternal(emotion.value);
                animationManager.playExpression(emotion);
            } else {
                // Physical head movement only
                animationManager.playExpression(emotion);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("action", "play_expression");
            result.put("emotion", emotion.value);
            result.put("showFace", showFace);
            result.put("durationMs", durationMs);
            return jsonResponse(Response.Status.OK, result);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play procedural animation", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage() != null ? e.getMessage() : "Failed to play animation"));
        }
    }

    private Response stopProceduralAnimation() {
        // Bring our app back to foreground (hide the system face)
        animationManager.hideSystemFace();

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("action", "stop");
        result.put("message", "System face hidden, app returned to foreground");
        return jsonResponse(Response.Status.OK, result);
    }

    private ProceduralAnimationManager.Emotion resolveEmotion(String emotionName) {
        if (emotionName == null) {
            return ProceduralAnimationManager.Emotion.NEUTRAL;
        }

        try {
            return ProceduralAnimationManager.Emotion.valueOf(emotionName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ProceduralAnimationManager.Emotion.NEUTRAL;
        }
    }

    private Response toggleAvatarMode(boolean enable) {
        try {
            if (enable) {
                animationManager.enableAvatarMode();
            } else {
                animationManager.disableAvatarMode();
            }
            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("action", enable ? "enable" : "disable");
            result.put("systemFaceVisible", animationManager.isSystemFaceVisible());
            return jsonResponse(Response.Status.OK, result);
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle avatar mode", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }

    private Response toggleRecording(boolean start) {
        if (!animationManager.isAvailable()) {
            return jsonResponse(Response.Status.SERVICE_UNAVAILABLE, mapOf("error", "HeadService not available"));
        }

        try {
            if (start) {
                animationManager.startRecordingFaceData();
            } else {
                animationManager.stopRecordingFaceData();
            }
            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("action", start ? "record/start" : "record/stop");
            return jsonResponse(Response.Status.OK, result);
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle recording", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error", e.getMessage()));
        }
    }
}
