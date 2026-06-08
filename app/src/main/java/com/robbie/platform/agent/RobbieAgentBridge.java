package com.robbie.platform.agent;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ainirobot.agent.AgentCore;
import com.ainirobot.agent.OnAgentStatusChangedListener;
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
import com.robbie.core.hardware.LedController;

import java.util.Arrays;
import java.util.Collections;


/**
 * Implementacion del IAgentBridge para el Agent SDK de ainirobot.
 *
 * Encapsula toda la interaccion con:
 * - PageAgent (registro de Actions)
 * - OnTranscribeListener (ASR/TTS)
 * - AgentCore (query, tts, mic, etc.)
 *
 * Cuando se reemplace el Agent OS, se crea una nueva clase que implemente
 * IAgentBridge sin modificar EveActivity ni RobotActionHandler.
 */
public class RobbieAgentBridge implements IAgentBridge {

    private static final String TAG = "RobbieAgentBridge";

    private PageAgent pageAgent;
    private ActionDispatchCallback actionCallback;
    private TranscriptionCallback transcriptionCallback;
    private boolean ready = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isListeningLight = false;
    private final Runnable restoreDefaultLight = () -> LedController.getInstance().restoreDefault();
    private volatile String lastAgentStatus = "reset_status";
    private volatile String lastAgentMessage = "";
    private volatile boolean personVisible = false;

    @Override
    public void initialize(Activity activity,
                           ActionDispatchCallback actionCallback,
                           TranscriptionCallback transcriptionCallback) {
        this.actionCallback = actionCallback;
        this.transcriptionCallback = transcriptionCallback;

        try {
            pageAgent = new PageAgent(activity);

            pageAgent.setObjective(
                "Estas en la pantalla del catalogo de productos. " +
                "Ayuda al cliente a encontrar productos, recomendar segun sus necesidades, " +
                "o buscar productos especificos."
            );

            pageAgent.registerAction(Actions.SAY);

            pageAgent.setOnTranscribeListener(new OnTranscribeListener() {
                @Override
                public boolean onASRResult(Transcription transcription) {
                    String text = transcription.getText().trim();
                    Log.d(TAG, "ASR event: final=" + transcription.getFinal() + " text='" + text + "'");

                    if (!transcription.getFinal()) {
                        if (!isListeningLight) LedController.getInstance().onListeningStarted();
                        mainHandler.removeCallbacks(restoreDefaultLight);
                        mainHandler.postDelayed(restoreDefaultLight, 3000);
                        if (transcriptionCallback != null) transcriptionCallback.onASRPartial(text);
                        return false;
                    }

                    mainHandler.removeCallbacks(restoreDefaultLight);
                    LedController.getInstance().restoreDefault();
                    Log.d(TAG, "ASR final: " + text);

                    if (!text.isEmpty()) {
                        com.robbie.platform.voice.VoiceInteractionTracker.getInstance().startInteraction(text, true);
                        if (transcriptionCallback != null) transcriptionCallback.onASRFinal(text);
                    }
                    // Return false: let AgentOS handle voice bar display and LLM planning natively
                    return false;
                }

                @Override
                public boolean onTTSResult(Transcription transcription) {
                    if (transcription.getFinal()) {
                        Log.d(TAG, "TTS final: " + transcription.getText());
                        
                        com.robbie.platform.voice.VoiceInteractionTracker.getInstance()
                                .finishInteraction("Robbie", transcription.getText(), true, -1);

                        mainHandler.removeCallbacks(restoreDefaultLight);
                        mainHandler.postDelayed(restoreDefaultLight, 1000);
                    }
                    if (transcriptionCallback != null)
                        transcriptionCallback.onTTSUpdate(transcription.getText(), transcription.getFinal());
                    return false;
                }
            });

            pageAgent.setOnAgentStatusChangedListener(new OnAgentStatusChangedListener() {
                @Override
                public boolean onStatusChanged(String status, String message) {
                    lastAgentStatus = status != null ? status : "";
                    lastAgentMessage = message != null ? message : "";
                    Log.d(TAG, "Agent status changed: " + lastAgentStatus + " message=" + lastAgentMessage);
                    if (transcriptionCallback != null) {
                        transcriptionCallback.onAgentStatusChanged(lastAgentStatus, lastAgentMessage);
                        // Derive gate state from AgentOS status — delegated to OrionStar
                        boolean active = "listening".equals(lastAgentStatus)
                            || "thinking".equals(lastAgentStatus)
                            || "processing".equals(lastAgentStatus);
                        transcriptionCallback.onListeningGateChanged(active, personVisible);
                    }
                    return false;
                }
            });

            // Registrar todas las acciones del robot
            registerEmotionActions();
            registerProductActions();
            registerNavigationActions();
            registerLedActions();
            registerTourActions();
            registerModeActions();
            registerChargingActions();

            ready = true;
            Log.i(TAG, "RobbieAgentBridge initialized with all actions");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing RobbieAgentBridge: " + e.getMessage(), e);
        }
    }

    @Override
    public void query(String text) {
        AgentCore.INSTANCE.query(text);
    }

    @Override
    public void tts(String text, int timeoutMs) {
        AgentCore.INSTANCE.tts(text, timeoutMs, null);
    }

    @Override
    public void stopTTS() {
        AgentCore.INSTANCE.stopTTS();
    }

    @Override
    public void setMicrophoneMuted(boolean muted) {
        AgentCore.INSTANCE.setMicrophoneMuted(muted);
    }

    @Override
    public void setVisionListeningState(boolean gateOpen, boolean personVisible) {
        // No-op: gate state is now derived from AgentOS status in onStatusChanged
    }

    @Override
    public void onPersonVisibilityChanged(boolean visible) {
        this.personVisible = visible;
        AgentCore.INSTANCE.setMicrophoneMuted(!visible);
        Log.d(TAG, "PersonApi visibility: " + (visible ? "VISIBLE — mic OPEN" : "GONE — mic MUTED"));
        if (transcriptionCallback != null) {
            boolean active = "listening".equals(lastAgentStatus)
                    || "thinking".equals(lastAgentStatus)
                    || "processing".equals(lastAgentStatus);
            transcriptionCallback.onListeningGateChanged(active, visible);
        }
    }

    @Override
    public void uploadInterfaceInfo(String info) {
        AgentCore.INSTANCE.uploadInterfaceInfo(info);
    }

    @Override
    public void clearContext() {
        AgentCore.INSTANCE.clearContext();
    }

    @Override
    public void destroy() {
        ready = false;
        if (pageAgent != null) {
            try {
                pageAgent.destroy();
            } catch (Exception e) {
                Log.w(TAG, "Failed to destroy PageAgent", e);
            }
            pageAgent = null;
        }
        AgentCore.INSTANCE.stopTTS();
        AgentCore.INSTANCE.clearContext();
        mainHandler.removeCallbacks(restoreDefaultLight);
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    /**
     * Configura el Agent OS al iniciar (wake mode, mic, voice bar).
     * Llamar desde onStart() de la Activity.
     */
    public void onActivityStart() {
        // PageAgent(Activity) auto-manages begin()/end() tied to Activity lifecycle.
        // No manual pageAgent.begin() needed — per SDK docs section 2.2.2:
        // "Actions automatically take effect when the page becomes visible to the user"
        // VisionSdk face detection is broken on this unit (output_faces:0 always).
        // Wake-free depends on VisionSdk, so it never activates.
        // Workaround: unconditional listening + PersonApi-based smart detection.
        AgentCore.INSTANCE.enableWakeupMode(false);
        AgentCore.INSTANCE.setEnableWakeFree(false);
        AgentCore.INSTANCE.setMicrophoneMuted(true);
        AgentCore.INSTANCE.setEnableVoiceBar(true);
        Log.d(TAG, "Wake mode: OFF, wake-free=OFF, mic=MUTED (until person detected), voiceBar=ON");
    }

    public void onActivityStop() {
        // PageAgent(Activity) auto-manages begin()/end() tied to Activity lifecycle.
        // No manual pageAgent.end() needed.
        Log.d(TAG, "onActivityStop — PageAgent lifecycle auto-managed");
    }

    /**
     * TTS de bienvenida.
     */
    public void speakWelcome() {
        com.robbie.platform.retail.AsyncTaskHelper.executeDelayed(() -> {
            AgentCore.INSTANCE.tts(
                "Hola, soy Robbie. Puedo ayudarte a encontrar productos o recomendarte lo que necesites.",
                20000, null);
        }, 500);
    }

    // ==================== Registro de Actions ====================

    private ActionResult successResult() {
        return new ActionResult(ActionStatus.SUCCEEDED, null, null, "", "");
    }

    private ActionResult failedResult(String message) {
        return new ActionResult(ActionStatus.FAILED, null, null, message != null ? message : "", "");
    }

    private IAgentBridge.ActionCompletionCallback createNotifyCompletion(Action action) {
        return new IAgentBridge.ActionCompletionCallback() {
            private boolean completed;

            @Override
            public synchronized void onSuccess() {
                if (completed) return;
                completed = true;
                action.notify(successResult(), false);
            }

            @Override
            public synchronized void onFailure(String message) {
                if (completed) return;
                completed = true;
                action.notify(failedResult(message), false);
            }
        };
    }

    private void dispatchAction(String actionName, Action action, Bundle params) {
        if (actionCallback != null) {
            actionCallback.onActionDispatched(actionName, params, createNotifyCompletion(action));
        }
    }

    private void registerEmotionActions() {
        // Action: respuesta alegre
        pageAgent.registerAction(new Action(
            "com.robbie.action.SHOW_HAPPY",
            "Respuesta alegre",
            "Responde con alegria cuando el usuario esta contento o satisfecho",
            Arrays.asList(
                new Parameter("sentence", ParameterType.STRING,
                    "Frase alegre para decir al usuario", true, null)
            ),
            (action, params) -> {
                dispatchAction("com.robbie.action.SHOW_HAPPY", action, params);
                return true;
            }
        ));

        // Action: respuesta empatica
        pageAgent.registerAction(new Action(
            "com.robbie.action.SHOW_SAD",
            "Respuesta empatica",
            "Responde con empatia cuando el usuario esta triste o desanimado",
            Arrays.asList(
                new Parameter("sentence", ParameterType.STRING,
                    "Frase de consuelo para el usuario", true, null)
            ),
            (action, params) -> {
                dispatchAction("com.robbie.action.SHOW_SAD", action, params);
                return true;
            }
        ));

        // Action: respuesta calmada
        pageAgent.registerAction(new Action(
            "com.robbie.action.SHOW_ANGRY",
            "Respuesta calmada",
            "Responde con calma cuando el usuario esta enojado o frustrado",
            Arrays.asList(
                new Parameter("sentence", ParameterType.STRING,
                    "Frase para calmar al usuario", true, null)
            ),
            (action, params) -> {
                dispatchAction("com.robbie.action.SHOW_ANGRY", action, params);
                return true;
            }
        ));
    }

    private void registerProductActions() {
        // Action: Recommend products
        pageAgent.registerAction(new Action(
            "com.robbie.action.RECOMMEND_PRODUCTS",
            "Recomendar productos",
            "Recomienda productos basandose en las necesidades del usuario y restricciones dieteticas",
            Arrays.asList(
                new Parameter("user_need", ParameterType.STRING,
                    "Lo que necesita el usuario", true, null),
                new Parameter("dietary_restriction", ParameterType.STRING,
                    "Restriccion dietetica si hay", false, null)
            ),
            (action, params) -> {
                if (actionCallback != null)
                    actionCallback.onActionDispatched("com.robbie.action.RECOMMEND_PRODUCTS", params, createNotifyCompletion(action));
                return true;
            }
        ));

        // Action: Show product detail
        pageAgent.registerAction(new Action(
            "com.robbie.action.SHOW_PRODUCT_DETAIL",
            "Detalle de producto",
            "Muestra y describe vocalmente los detalles de un producto especifico",
            Arrays.asList(
                new Parameter("product_name", ParameterType.STRING,
                    "Nombre del producto", true, null)
            ),
            (action, params) -> {
                dispatchAction("com.robbie.action.SHOW_PRODUCT_DETAIL", action, params);
                return true;
            }
        ));

        // Action: Buscar productos en el catalogo local
        pageAgent.registerAction(new Action(
            "com.robbie.action.SEARCH_PRODUCTS",
            "Buscar productos en catalogo",
            "OBLIGATORIO: Usa SIEMPRE este action cuando el usuario pregunte por cualquier producto, suplemento, marca, categoria o ingrediente. " +
            "Ejemplos: 'tienes proteina?', 'busca omega 3', 'quiero barras', 'vitaminas para energia', 'creatina', 'snacks'. " +
            "NO respondas solo con texto, SIEMPRE ejecuta este action para buscar en el catalogo real.",
            Arrays.asList(
                new Parameter("query", ParameterType.STRING,
                    "Terminos de busqueda extraidos de lo que pide el usuario (ej: proteina, omega 3, barras, vitamina c, creatina)", true, null),
                new Parameter("recommendation", ParameterType.STRING,
                    "Breve recomendacion personalizada para el usuario", false, null)
            ),
            (action, params) -> {
                if (actionCallback != null)
                    actionCallback.onActionDispatched("com.robbie.action.SEARCH_PRODUCTS", params, createNotifyCompletion(action));
                return true;
            }
        ));
    }

    private void registerNavigationActions() {
        // Action: Navegar a ubicacion
        pageAgent.registerAction(new Action(
            "com.robbie.action.NAVIGATE_TO_LOCATION",
            "Ir a ubicacion",
            "Lleva al robot a un punto o seccion mapeada en la tienda. Usa este action cuando el usuario pida ir a una seccion, area o punto especifico como proteinas, vitaminas, caja, entrada, etc.",
            Arrays.asList(
                new Parameter("destination", ParameterType.STRING,
                    "Nombre del punto de destino al que el robot debe navegar, por ejemplo: proteinas, vitaminas, caja, entrada", true, null)
            ),
            (action, params) -> {
                if (actionCallback != null)
                    actionCallback.onActionDispatched("com.robbie.action.NAVIGATE_TO_LOCATION", params, createNotifyCompletion(action));
                return true;
            }
        ));

        // Action: Detener navegacion
        pageAgent.registerAction(new Action(
            "com.robbie.action.STOP_NAVIGATION",
            "Detener navegacion",
            "Detiene el movimiento del robot. Usa este action cuando el usuario pida parar, detenerse, o cancelar el recorrido.",
            Collections.emptyList(),
            (action, params) -> {
                if (actionCallback != null)
                    actionCallback.onActionDispatched("com.robbie.action.STOP_NAVIGATION", params, createNotifyCompletion(action));
                return true;
            }
        ));
    }

    private void registerLedActions() {
        // Action: Cambiar color de LEDs
        pageAgent.registerAction(new Action(
            "com.robbie.action.SET_LED_COLOR",
            "Cambiar color LEDs",
            "Cambia el color de los LEDs del robot a un color especifico. Usa este action cuando el usuario pida cambiar el color de las luces.",
            Arrays.asList(
                new Parameter("color", ParameterType.STRING,
                    "Color en formato hexadecimal (ej: #FF0000 para rojo, #00FF00 para verde, #0000FF para azul)", true, null)
            ),
            (action, params) -> {
                dispatchAction("com.robbie.action.SET_LED_COLOR", action, params);
                return true;
            }
        ));

        // Action: Iniciar efecto LED
        pageAgent.registerAction(new Action(
            "com.robbie.action.START_LED_EFFECT",
            "Efecto LEDs",
            "Inicia un efecto visual en los LEDs del robot como respiracion, parpadeo, arcoiris, pulso o onda.",
            Arrays.asList(
                new Parameter("effect", ParameterType.ENUM,
                    "Tipo de efecto LED", true, Arrays.asList("BREATHING", "BLINK", "RAINBOW", "PULSE", "WAVE")),
                new Parameter("color", ParameterType.STRING,
                    "Color para el efecto (opcional, usa color por defecto si no se especifica)", false, null)
            ),
            (action, params) -> {
                dispatchAction("com.robbie.action.START_LED_EFFECT", action, params);
                return true;
            }
        ));

        // Action: Restaurar LEDs por defecto
        pageAgent.registerAction(new Action(
            "com.robbie.action.RESTORE_LED_DEFAULT",
            "Restaurar LEDs",
            "Restaura los LEDs del robot al color y estado por defecto (rosa Ikalp).",
            Collections.emptyList(),
            (action, params) -> {
                dispatchAction("com.robbie.action.RESTORE_LED_DEFAULT", action, params);
                return true;
            }
        ));
    }

    private void registerTourActions() {
        // Action: Iniciar tour
        pageAgent.registerAction(new Action(
            "com.robbie.action.START_TOUR",
            "Iniciar tour",
            "Inicia un recorrido guiado por la tienda. Usa este action cuando el usuario pida un tour, recorrido, visita guiada o quiera conocer la tienda. Si el usuario no especifica cual tour, inicia el primer tour publicado disponible.",
            Arrays.asList(
                new Parameter("routeName", ParameterType.STRING,
                    "Nombre del tour a iniciar (opcional, usa el primer tour publicado si no se especifica)", false, null)
            ),
            (action, params) -> {
                dispatchAction("com.robbie.action.START_TOUR", action, params);
                return true;
            }
        ));

        // Action: Detener tour
        pageAgent.registerAction(new Action(
            "com.robbie.action.STOP_TOUR",
            "Detener tour",
            "Detiene el tour o recorrido guiado actual. Usa este action cuando el usuario pida detener, parar o cancelar el tour.",
            Collections.emptyList(),
            (action, params) -> {
                dispatchAction("com.robbie.action.STOP_TOUR", action, params);
                return true;
            }
        ));
    }

    private void registerModeActions() {
        // Action: Cambiar modo
        pageAgent.registerAction(new Action(
            "com.robbie.action.SWITCH_MODE",
            "Cambiar modo",
            "Cambia el modo de operacion del robot (retail, exhibition, idle). Usa este action cuando el usuario pida cambiar de modo.",
            Arrays.asList(
                new Parameter("mode", ParameterType.STRING,
                    "Modo al que cambiar (retail, exhibition, idle)", true, null)
            ),
            (action, params) -> {
                dispatchAction("com.robbie.action.SWITCH_MODE", action, params);
                return true;
            }
        ));

        // Action: Regresar al menu principal
        pageAgent.registerAction(new Action(
            "com.robbie.action.GO_TO_MENU",
            "Ir al menu",
            "Navega a la pagina del menu principal. Usa este action cuando el usuario diga 'regresa a menu', 'volver al menu', 'ir al menu principal' o similar.",
            Arrays.asList(),
            (action, params) -> {
                dispatchAction("com.robbie.action.GO_TO_MENU", action, params);
                return true;
            }
        ));

        // Action: Entrar en modo retail con productos
        pageAgent.registerAction(new Action(
            "com.robbie.action.ENTER_RETAIL_MODE",
            "Entrar en modo retail",
            "Activa el modo retail y muestra todos los productos disponibles. Usa este action cuando el usuario diga 'entra en modo retail', 'modo tienda', 'mostrar productos' o similar.",
            Arrays.asList(),
            (action, params) -> {
                dispatchAction("com.robbie.action.ENTER_RETAIL_MODE", action, params);
                return true;
            }
        ));

        // Action: Entrar en modo promocion/exhibicion
        pageAgent.registerAction(new Action(
            "com.robbie.action.ENTER_EXHIBITION_MODE",
            "Entrar en modo exhibicion",
            "Activa el modo exhibicion/promocion y navega a la pagina de promociones. Usa este action cuando el usuario diga 'entra en modo promocion', 'modo exhibicion', 'mostrar promociones' o similar.",
            Arrays.asList(),
            (action, params) -> {
                dispatchAction("com.robbie.action.ENTER_EXHIBITION_MODE", action, params);
                return true;
            }
        ));
    }

    private void registerChargingActions() {
        // Action: Ir a cargar
        pageAgent.registerAction(new Action(
            "com.robbie.action.GO_CHARGE",
            "Ir a cargar",
            "Envia al robot a su estacion de carga. Usa este action cuando el usuario diga 've a cargar', 'carga tu bateria', 'necesitas cargarte', 'recarga', 'ir a cargar' o similar.",
            Collections.emptyList(),
            (action, params) -> {
                if (actionCallback != null)
                    actionCallback.onActionDispatched("com.robbie.action.GO_CHARGE", params, createNotifyCompletion(action));
                return true;
            }
        ));

        // Action: Dejar de cargar
        pageAgent.registerAction(new Action(
            "com.robbie.action.STOP_CHARGE",
            "Dejar de cargar",
            "Detiene la carga del robot y sale de la estacion de carga. Usa este action cuando el usuario diga 'deja de cargar', 'sal del cargador', 'detener carga', 'ya no cargues' o similar.",
            Collections.emptyList(),
            (action, params) -> {
                if (actionCallback != null)
                    actionCallback.onActionDispatched("com.robbie.action.STOP_CHARGE", params, createNotifyCompletion(action));
                return true;
            }
        ));
    }
}
