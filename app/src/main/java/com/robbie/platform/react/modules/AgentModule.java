package com.robbie.platform.react.modules;

import android.util.Log;

import com.ainirobot.agent.AgentCore;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * AgentModule - Puente React Native para las APIs utilitarias de AgentCore.
 *
 * Segun la documentacion oficial del Agent SDK (v0.2.2):
 * - El mic se abre automaticamente cuando la app esta en foreground.
 * - ASR/TTS/LLM son gestionados internamente por AgentOS.
 * - AppAgent se inicializa en RobotApp.onCreate() (persona + objetivo).
 * - PageAgent se inicializa en EveActivity.onCreate() (Actions de emociones).
 *
 * Este modulo solo expone a React Native las APIs utilitarias de AgentCore:
 * - query(text): envia texto al LLM para que planifique Actions
 * - tts(text): reproduce TTS asincrono
 * - stopTTS(): detiene TTS en curso
 * - setMicMuted(boolean): controla el microfono
 * - uploadInterfaceInfo(info): sube info de pantalla al LLM
 * - clearContext(): limpia historial de conversacion del LLM
 * - getAvailablePlugins(): lista plugins OPK en assets
 */
public class AgentModule extends ReactContextBaseJavaModule {

    private static final String TAG = "AgentModule";

    public AgentModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "AgentModule";
    }

    /**
     * Envia texto al LLM como si el usuario lo hubiera dicho por voz.
     * AgentOS planificara y ejecutara el Action correspondiente.
     * Doc: AgentCore.query(text)
     */
    @ReactMethod
    public void query(String text, Promise promise) {
        try {
            Log.i(TAG, "query: " + text);
            AgentCore.INSTANCE.query(text);
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error en query", e);
            promise.reject("QUERY_ERROR", e.getMessage());
        }
    }

    /**
     * Reproduce TTS asincrono via AgentCore.
     * Doc: AgentCore.tts(text, timeout, callback)
     */
    @ReactMethod
    public void tts(String text, Promise promise) {
        try {
            Log.i(TAG, "tts: " + text);
            AgentCore.INSTANCE.tts(text, 180000, null);
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error en tts", e);
            promise.reject("TTS_ERROR", e.getMessage());
        }
    }

    /**
     * Detiene la reproduccion TTS en curso.
     * Doc: AgentCore.stopTTS()
     */
    @ReactMethod
    public void stopTTS(Promise promise) {
        try {
            AgentCore.INSTANCE.stopTTS();
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error en stopTTS", e);
            promise.reject("STOP_TTS_ERROR", e.getMessage());
        }
    }

    /**
     * Controla el estado del microfono.
     * Doc: AgentCore.isMicrophoneMuted = muted
     */
    @ReactMethod
    public void setMicMuted(boolean muted, Promise promise) {
        try {
            AgentCore.INSTANCE.setMicrophoneMuted(muted);
            Log.i(TAG, "Mic muted: " + muted);
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error en setMicMuted", e);
            promise.reject("MIC_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void setRecognizable(boolean enabled, Promise promise) {
        try {
            AgentCore.INSTANCE.setMicrophoneMuted(!enabled);
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error en setRecognizable", e);
            promise.reject("SPEECH_RECOGNIZABLE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void setRecognizeMode(boolean enabled, Promise promise) {
        try {
            AgentCore.INSTANCE.setEnableWakeFree(enabled);
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error en setRecognizeMode", e);
            promise.reject("SPEECH_RECOGNIZE_MODE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void setRecognizeModeForce(boolean enabled, Promise promise) {
        try {
            AgentCore.INSTANCE.enableWakeupMode(enabled);
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error en setRecognizeModeForce", e);
            promise.reject("SPEECH_RECOGNIZE_MODE_FORCE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void setRecognizeModeNew(boolean enabled, boolean closeStreamData, Promise promise) {
        try {
            AgentCore.INSTANCE.setEnableWakeFree(enabled);
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error en setRecognizeModeNew", e);
            promise.reject("SPEECH_RECOGNIZE_MODE_NEW_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void setASREnabled(boolean enabled, Promise promise) {
        try {
            AgentCore.INSTANCE.setMicrophoneMuted(!enabled);
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error en setASREnabled", e);
            promise.reject("SPEECH_ASR_ENABLED_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void setASRParams(String key, String value, Promise promise) {
        try {
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error en setASRParams", e);
            promise.reject("SPEECH_ASR_PARAMS_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void setAsrExtendProperty(String property, Promise promise) {
        try {
            promise.resolve(false);
        } catch (Exception e) {
            Log.e(TAG, "Error en setAsrExtendProperty", e);
            promise.reject("SPEECH_ASR_EXTEND_PROPERTY_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void setAngleCenterRange(double center, double range, Promise promise) {
        try {
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error en setAngleCenterRange", e);
            promise.reject("SPEECH_ANGLE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void queryByText(String text, Promise promise) {
        try {
            AgentCore.INSTANCE.query(text);
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error en queryByText", e);
            promise.reject("QUERY_BY_TEXT_ERROR", e.getMessage());
        }
    }

    /**
     * Sube informacion de la pantalla actual para que el LLM la entienda.
     * Doc: AgentCore.uploadInterfaceInfo(info)
     */
    @ReactMethod
    public void uploadInterfaceInfo(String info, Promise promise) {
        try {
            AgentCore.INSTANCE.uploadInterfaceInfo(info);
            Log.i(TAG, "uploadInterfaceInfo: " + info.substring(0, Math.min(info.length(), 80)));
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error en uploadInterfaceInfo", e);
            promise.reject("UPLOAD_ERROR", e.getMessage());
        }
    }

    /**
     * Limpia el historial de conversacion del LLM.
     * Doc: AgentCore.clearContext()
     */
    @ReactMethod
    public void clearContext(Promise promise) {
        try {
            AgentCore.INSTANCE.clearContext();
            Log.i(TAG, "Contexto LLM limpiado");
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error en clearContext", e);
            promise.reject("CLEAR_ERROR", e.getMessage());
        }
    }

    /**
     * Lista los plugins OPK de AgentOS disponibles en assets.
     */
    @ReactMethod
    public void getAvailablePlugins(Promise promise) {
        try {
            ReactApplicationContext ctx = getReactApplicationContext();
            String[] plugins = ctx.getAssets().list("opk/agentos/plugin");
            JSONArray arr = new JSONArray();
            if (plugins != null) {
                for (String plugin : plugins) {
                    JSONObject obj = new JSONObject();
                    obj.put("filename", plugin);
                    String name = plugin.split("-")[0].replace("OverSea_", "");
                    obj.put("name", name);
                    String[] parts = plugin.split("-");
                    if (parts.length > 1) {
                        obj.put("version", parts[1]);
                    }
                    arr.put(obj);
                }
            }
            promise.resolve(arr.toString());
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }
}
