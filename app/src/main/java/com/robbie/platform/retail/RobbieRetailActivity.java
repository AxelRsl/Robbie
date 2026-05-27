package com.robbie.platform.retail;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ainirobot.agent.AgentCore;
import com.ainirobot.agent.OnTranscribeListener;
import com.ainirobot.agent.PageAgent;
import com.ainirobot.agent.action.Action;
import com.ainirobot.agent.action.ActionExecutor;
import com.ainirobot.agent.action.Actions;
import com.ainirobot.agent.base.ActionResult;
import com.ainirobot.agent.base.ActionStatus;
import com.ainirobot.agent.base.Parameter;
import com.ainirobot.agent.base.ParameterType;
import com.ainirobot.agent.base.Transcription;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.robbie.platform.react.EveActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.robbie.data.local.RobbieDatabase;
import com.robbie.data.local.entity.ProductEntity;

/**
 * RobbieRetailActivity - Activity base para retail con AgentOS SDK.
 * 
 * Esta clase extiende EveActivity (React Native) y agrega:
 * - PageAgent con Actions personalizadas para retail
 * - Navegacion automatica entre pantallas via eventos a React Native
 * - Integracion con RecommendationEngine para sugerencias AI
 * 
 * Actions disponibles:
 * - RECOMMEND_PRODUCTS: Recomienda productos segun necesidades del usuario
 * - SEARCH_PRODUCTS: Busca productos por nombre, categoria o marca
 * - SHOW_PRODUCT_DETAIL: Muestra detalles de un producto especifico
 * - NAVIGATE_TO_SCREEN: Navega a diferentes pantallas de la app
 * 
 * Uso desde RobotApp:
 * - Esta clase se usa como base para EveActivity
 * - PageAgent se inicializa automaticamente en onCreate
 * - Las Actions envian eventos a React Native para navegacion
 */
public class RobbieRetailActivity extends EveActivity {

    private static final String TAG = "RobbieRetailActivity";
    
    private PageAgent pageAgent;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private RobbieConfig robbieConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Cargar configuracion
        robbieConfig = getRobbieConfig();
        
        // Setup PageAgent con Actions de retail
        setupPageAgent();
        
        Log.i(TAG, "RobbieRetailActivity inicializada");
    }

    @Override
    protected void onStart() {
        super.onStart();
        // PageAgent(activity) auto-manages lifecycle; no manual begin() needed
        AgentCore.INSTANCE.enableWakeupMode(false);
        AgentCore.INSTANCE.setEnableWakeFree(true);
        Log.d(TAG, "Wake-free activado - mic se abre al detectar cara");
    }

    @Override
    protected void onStop() {
        // PageAgent(activity) auto-manages lifecycle; no manual end() needed
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        // PageAgent(activity) auto-manages lifecycle; no manual destroy() needed
        pageAgent = null;
        AgentCore.INSTANCE.stopTTS();
        AgentCore.INSTANCE.clearContext();
        super.onDestroy();
    }

    /**
     * Configura PageAgent con Actions personalizadas para retail.
     * Las Actions se obtienen dinamicamente desde RobbieConfig.
     */
    private void setupPageAgent() {
        pageAgent = new PageAgent(this);

        // Objetivo del PageAgent desde configuracion
        String objective = robbieConfig != null ? robbieConfig.getPageObjective() : 
            "Estas en la aplicacion de retail GNC. Puedes ayudar al cliente a encontrar productos, recomendar suplementos y navegar entre pantallas.";
        pageAgent.setObjective(objective);

        // Action built-in: SAY (hablar)
        pageAgent.registerAction(Actions.SAY);

        // Registrar Actions dinamicas desde configuracion
        if (robbieConfig != null && robbieConfig.getActions() != null) {
            for (RobbieConfig.ActionConfig actionConfig : robbieConfig.getActions()) {
                registerDynamicAction(actionConfig);
            }
            Log.d(TAG, "PageAgent configurado con " + robbieConfig.getActions().size() + " Actions personalizadas");
        } else {
            Log.w(TAG, "No hay Actions configuradas");
        }

        // Listener de transcripcion (ASR/TTS)
        setupTranscriptionListener();
    }

    /**
     * Registra una Action dinamica basada en la configuracion.
     * Los parametros y comportamiento se obtienen de RobbieConfig.ActionConfig.
     */
    private void registerDynamicAction(final RobbieConfig.ActionConfig config) {
        // Convertir parametros de configuracion a Parameters del SDK
        List<Parameter> parameters = new ArrayList<>();
        for (RobbieConfig.ParameterConfig paramConfig : config.parameters) {
            ParameterType type = parseParameterType(paramConfig.type);
            List<String> enumValues = null;
            if (type == ParameterType.ENUM && config.enumValues != null) {
                enumValues = config.enumValues;
            }
            parameters.add(new Parameter(
                paramConfig.name,
                type,
                paramConfig.description,
                paramConfig.required,
                enumValues
            ));
        }

        // Crear Action con executor generico
        Action action = new Action(
            config.actionId,
            config.displayName,
            config.description,
            parameters,
            new ActionExecutor() {
                @Override
                public boolean onExecute(Action action, Bundle params) {
                    if (params == null) return false;

                    // Extraer parametros dinamicamente
                    String param1 = "";
                    String param2 = "";
                    
                    if (config.parameters.size() > 0) {
                        param1 = params.getString(config.parameters.get(0).name, "");
                    }
                    if (config.parameters.size() > 1) {
                        param2 = params.getString(config.parameters.get(1).name, "");
                    }

                    Log.d(TAG, "Action " + config.actionId + ": param1=" + param1 + " param2=" + param2);
                    
                    // Enviar evento a React Native
                    sendEventToReactNative(config.eventName, param1, param2);

                    // Notificar exito
                    action.notify(new ActionResult(ActionStatus.SUCCEEDED, null, null, null, null), false);
                    return true;
                }
            }
        );

        pageAgent.registerAction(action);
        Log.d(TAG, "Action registrada: " + config.actionId);
    }

    /**
     * Convierte string de tipo a ParameterType del SDK.
     */
    private ParameterType parseParameterType(String type) {
        switch (type.toUpperCase()) {
            case "STRING": return ParameterType.STRING;
            case "INT": return ParameterType.INT;
            case "FLOAT": return ParameterType.FLOAT;
            case "BOOLEAN": return ParameterType.BOOLEAN;
            case "ENUM": return ParameterType.ENUM;
            case "NUMBER_ARRAY": return ParameterType.NUMBER_ARRAY;
            case "STRING_ARRAY": return ParameterType.STRING_ARRAY;
            default: return ParameterType.STRING;
        }
    }

    /**
     * Configura listener de transcripcion para ASR y TTS.
     * Maneja el wake word "Robbie" y variantes.
     */
    private void setupTranscriptionListener() {
        Log.i(TAG, "Configurando listener de transcripcion");
        pageAgent.setOnTranscribeListener(new OnTranscribeListener() {
            @Override
            public boolean onASRResult(Transcription transcription) {
                String text = transcription.getText().trim();
                Log.d(TAG, "[ASR] Recibido - texto: '" + text + "', final: " + transcription.getFinal());

                // Mostrar ASR parcial en la UI del sistema
                if (!transcription.getFinal()) {
                    Log.d(TAG, "[ASR] Transcripcion parcial, mostrando en UI");
                    return false;
                }

                Log.i(TAG, "[ASR] Transcripcion final: " + text);

                // Guardamos el texto bruto final, independientemente del wake word
                com.robbie.platform.voice.VoiceInteractionTracker.getInstance().startInteraction(text);

                // Detectar wake word "Robbie" y variantes
                String lower = text.toLowerCase();
                Log.d(TAG, "[ASR] Buscando wake word en: '" + lower + "'");
                if (lower.contains("robbie") || lower.contains("robi")
                        || lower.contains("rubi") || lower.contains("robin")
                        || lower.contains("robe") || lower.contains("robby")) {
                    
                    Log.i(TAG, "[ASR] Wake word detectado!");
                    // Remover wake word y procesar comando
                    String command = text.replaceAll("(?i)robbie|robi|rubi|robin|robe|robby", "").trim();
                    if (command.isEmpty()) {
                        Log.d(TAG, "[ASR] Solo wake word, pidiendo comando");
                        AgentCore.INSTANCE.tts("Si? En que te puedo ayudar?", 10000, null);
                    } else {
                        Log.i(TAG, "[ASR] Wake word + comando detectado - query: '" + command + "'");
                        // Reiniciar el tracker con el query filtrado
                        com.robbie.platform.voice.VoiceInteractionTracker.getInstance().startInteraction(command);
                        AgentCore.INSTANCE.query(command);
                    }
                    return true;
                } else {
                    Log.w(TAG, "[ASR] No wake word detectado, ignorando: '" + text + "'");
                    // En modo wake-free, AgentOS podría responder de todas formas aunque devolvamos true.
                    // Así que mantenemos lastUserQuestion en caso de que AgentOS emita un TTS.
                    return true; // Suprimir - no hay wake word
                }
            }

            @Override
            public boolean onTTSResult(Transcription transcription) {
                if (transcription.getFinal()) {
                    Log.d(TAG, "Retail ASR/TTS final: " + transcription.getText());
                    
                    com.robbie.platform.voice.VoiceInteractionTracker.getInstance().finishInteraction("Robbie Retail", transcription.getText());
                    
                    AgentCore.INSTANCE.setEnableWakeFree(true);
                }
                return false;
            }
        });
    }

    /**
     * Envia evento a React Native para navegacion y acciones.
     * React Native debe escuchar el evento "onRetailAction".
     */
    private void sendEventToReactNative(String action, String param1, String param2) {
        try {
            WritableMap eventData = Arguments.createMap();
            eventData.putString("action", action);
            eventData.putString("param1", param1 != null ? param1 : "");
            eventData.putString("param2", param2 != null ? param2 : "");
            
            if (getReactInstanceManager().getCurrentReactContext() != null) {
                getReactInstanceManager()
                    .getCurrentReactContext()
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit("onRetailAction", eventData);
                
                Log.i(TAG, "Evento enviado a React Native: " + action);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enviando evento a React Native", e);
        }
    }

    /**
     * Sube informacion del catalogo de productos al Agent para contexto.
     * Esto ayuda al LLM a entender que productos estan disponibles.
     * Ahora carga productos desde la base de datos local.
     */
    private void uploadCatalogInfoToAgent() {
        AgentCore.INSTANCE.uploadInterfaceInfo("");
        Log.d(TAG, "Retail interface info reset to empty state");
    }

    /**
     * Obtiene la configuracion de Robbie desde RobotApp.
     */
    private RobbieConfig getRobbieConfig() {
        try {
            com.robbie.RobotApp app = 
                (com.robbie.RobotApp) getApplication();
            return app.getRobbieConfig();
        } catch (Exception e) {
            Log.w(TAG, "No se pudo obtener RobbieConfig", e);
            return new RobbieConfig();
        }
    }
}
