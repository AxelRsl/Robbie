package com.robbie.platform.react;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

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
import com.ainirobot.coreservice.client.ApiListener;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.ActionListener;
import com.ainirobot.coreservice.client.listener.CommandListener;
import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.person.PersonApi;
import com.ainirobot.coreservice.client.person.PersonListener;
import com.ainirobot.coreservice.client.listener.Person;
import org.json.JSONArray;
import com.robbie.moduleapp.lidd.RobotApp;
import com.robbie.platform.retail.AsyncTaskHelper;
import com.robbie.platform.retail.Product;
import com.robbie.platform.retail.RecommendationEngine;
import com.robbie.platform.retail.RobbieConfig;
import com.facebook.react.ReactActivity;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * EveActivity - Activity principal del motor React Native.
 *
 * Ejecutada en proceso :sandbox, se encarga de:
 * 1. Cargar el entorno React Native en el motor Hermes
 * 2. Ejecutar el bundle JavaScript (platform.android.bundle)
 * 3. Mostrar la interfaz React Native (MenuScreen como pantalla inicial)
 * 4. Inicializar PageAgent con Actions de emociones (Agent SDK)
 * 5. Escuchar transcripciones ASR/TTS via OnTranscribeListener
 *
 * Firmas reales del SDK (verificadas via javap sobre agent-sdk-0.2.2.aar + agent-base-0.1.8.aar):
 * - Action(name, desc, sid, List<Parameter>, ActionExecutor) -- 5 params sin displayName
 * - Action(name, displayName, desc, sid, List<Parameter>, ActionExecutor) -- 6 params
 * - Parameter(name, ParameterType, desc, required, List<String> enumValues)
 * - ActionResult(ActionStatus, Bundle result, Bundle extra, String sid, String appId)
 * - action.notify(ActionResult, isTriggerFollowUp)
 * - OnTranscribeListener en com.ainirobot.agent (no com.ainirobot.agent.base)
 */
public class EveActivity extends ReactActivity {

    private static final String TAG = "EveActivity";

    private PageAgent pageAgent;
    private RecommendationEngine recommendationEngine;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private RobotApi robotApi;
    private int faceTrackReqId = 1001;
    private int lightReqId = 2001;
    private boolean isListeningLight = false;
    private final Runnable restoreDefaultLight = () -> setLightSolidColor(0xFF0000);

    private boolean robotApiConnected = false;
    private boolean faceTrackActive = false;
    private boolean isFollowing = false;
    private int currentFollowId = -1;
    private PersonListener personListener;
    private int navReqId = 3001;
    private boolean isNavigating = false;
    private final List<String> mapPlaces = new ArrayList<>();

    @Override
    protected String getMainComponentName() {
        return "xiabao-retail-app";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        recommendationEngine = new RecommendationEngine();
        initializePageAgent();
    }

    @Override
    protected void onStart() {
        super.onStart();
        
        uploadCatalogInfoToAgent();
        
        // Desactivar wake word - el robot escucha sin necesidad de decir "Robbie"
        AgentCore.INSTANCE.enableWakeupMode(false);
        AgentCore.INSTANCE.setEnableWakeFree(true);
        
        // Asegurar que el micrófono NO esté muteado
        AgentCore.INSTANCE.setMicrophoneMuted(false);
        
        // Habilitar la barra de voz del sistema (muestra ASR/TTS)
        AgentCore.INSTANCE.setEnableVoiceBar(true);
        
        Log.d(TAG, "Wake mode: OFF, wake-free=ON, mic=ON, voiceBar=ON");
        
        setLightSolidColor(0xFF0000);
        
        startFaceTracking();
        
        AsyncTaskHelper.executeDelayed(() -> {
            AgentCore.INSTANCE.tts(
                "Hola, soy Robbie. Puedo ayudarte a encontrar productos o recomendarte lo que necesites.",
                20000, null);
        }, 500);
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopFaceTracking();
    }
    
    @Override
    protected void onDestroy() {
        stopFaceTracking();
        AgentCore.INSTANCE.stopTTS();
        AgentCore.INSTANCE.clearContext();
        mainHandler.removeCallbacks(restoreDefaultLight);
        if (robotApi != null) robotApi.disconnectApi();
        super.onDestroy();
    }

    /**
     * Crea ActionResult exitoso con valores por defecto.
     * Constructor real: ActionResult(ActionStatus, Bundle result, Bundle extra, String sid, String appId)
     */
    private ActionResult successResult() {
        return new ActionResult(ActionStatus.SUCCEEDED, null, null, null, null);
    }

    private void initializePageAgent() {
        try {
            pageAgent = new PageAgent(this);
            
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
                        if (!isListeningLight) setLightBlueBreathing();
                        mainHandler.removeCallbacks(restoreDefaultLight);
                        mainHandler.postDelayed(restoreDefaultLight, 3000);
                        emitTranscriptionEvent(text, true, false);
                        return false;
                    }
                    
                    mainHandler.removeCallbacks(restoreDefaultLight);
                    setLightSolidColor(0xFF0000);
                    
                    Log.d(TAG, "ASR final: " + text);
                    
                    // Enviar cualquier texto directamente al query (sin wake word)
                    if (!text.isEmpty()) {
                        Log.d(TAG, "Sending to query: " + text);
                        AgentCore.INSTANCE.query(text);
                        emitTranscriptionEvent(text, true, true);
                    }
                    
                    return true;
                }
                
                @Override
                public boolean onTTSResult(Transcription transcription) {
                    if (transcription.getFinal()) {
                        Log.d(TAG, "TTS final: " + transcription.getText());
                        mainHandler.removeCallbacks(restoreDefaultLight);
                        mainHandler.postDelayed(restoreDefaultLight, 1000);
                        AgentCore.INSTANCE.setEnableWakeFree(true);
                    }
                    emitTranscriptionEvent(transcription.getText(), false, transcription.getFinal());
                    return false;
                }
            })
                // Action: respuesta alegre
                .registerAction(new Action(
                    "com.ainirobot.lidd.action.SHOW_HAPPY",
                    "Respuesta alegre",
                    "Responde con alegria cuando el usuario esta contento o satisfecho",
                    Arrays.asList(
                        new Parameter("sentence", ParameterType.STRING,
                            "Frase alegre para decir al usuario", true, null)
                    ),
                    new ActionExecutor() {
                        @Override
                        public boolean onExecute(Action action, Bundle params) {
                            String sentence = params != null ? params.getString("sentence", "") : "";
                            emitEmotionEvent("happy", sentence);
                            if (!sentence.isEmpty()) {
                                AgentCore.INSTANCE.tts(sentence, 180000, null);
                            }
                            action.notify(successResult(), false);
                            return true;
                        }
                    }
                ))
                // Action: respuesta empatica
                .registerAction(new Action(
                    "com.ainirobot.lidd.action.SHOW_SAD",
                    "Respuesta empatica",
                    "Responde con empatia cuando el usuario esta triste o desanimado",
                    Arrays.asList(
                        new Parameter("sentence", ParameterType.STRING,
                            "Frase de consuelo para el usuario", true, null)
                    ),
                    new ActionExecutor() {
                        @Override
                        public boolean onExecute(Action action, Bundle params) {
                            String sentence = params != null ? params.getString("sentence", "") : "";
                            emitEmotionEvent("sad", sentence);
                            if (!sentence.isEmpty()) {
                                AgentCore.INSTANCE.tts(sentence, 180000, null);
                            }
                            action.notify(successResult(), false);
                            return true;
                        }
                    }
                ))
                // Action: respuesta calmada
                .registerAction(new Action(
                    "com.ainirobot.lidd.action.SHOW_ANGRY",
                    "Respuesta calmada",
                    "Responde con calma cuando el usuario esta enojado o frustrado",
                    Arrays.asList(
                        new Parameter("sentence", ParameterType.STRING,
                            "Frase para calmar al usuario", true, null)
                    ),
                    new ActionExecutor() {
                        @Override
                        public boolean onExecute(Action action, Bundle params) {
                            String sentence = params != null ? params.getString("sentence", "") : "";
                            emitEmotionEvent("angry", sentence);
                            if (!sentence.isEmpty()) {
                                AgentCore.INSTANCE.tts(sentence, 180000, null);
                            }
                            action.notify(successResult(), false);
                            return true;
                        }
                    }
                ))
                // Action: Recommend products
                .registerAction(new Action(
                    "com.ainirobot.retail.RECOMMEND_PRODUCTS",
                    "Recomendar productos",
                    "Recomienda productos basandose en las necesidades del usuario y restricciones dieteticas",
                    Arrays.asList(
                        new Parameter("user_need", ParameterType.STRING,
                            "Lo que necesita el usuario", true, null),
                        new Parameter("dietary_restriction", ParameterType.STRING,
                            "Restriccion dietetica si hay", false, null)
                    ),
                    new ActionExecutor() {
                        @Override
                        public boolean onExecute(Action action, Bundle params) {
                            if (params == null) return false;
                            String need = params.getString("user_need", "");
                            String restriction = params.getString("dietary_restriction", "");
                            if (need.isEmpty()) return false;

                            Log.d(TAG, "Action RECOMMEND: need=" + need + " restriction=" + restriction);
                            mainHandler.post(() -> triggerRecommendation(need, restriction));

                            AsyncTaskHelper.executeDelayed(() -> action.notify(successResult(), false), 3000);
                            return true;
                        }
                    }
                ))
                // Action: Search products
                .registerAction(new Action(
                    "com.ainirobot.retail.SEARCH_PRODUCTS",
                    "Buscar productos",
                    "Busca productos en el catalogo por nombre, categoria o marca",
                    Arrays.asList(
                        new Parameter("query", ParameterType.STRING,
                            "Texto de busqueda", true, null)
                    ),
                    new ActionExecutor() {
                        @Override
                        public boolean onExecute(Action action, Bundle params) {
                            if (params == null) return false;
                            String query = params.getString("query", "");
                            if (query.isEmpty()) return false;

                            Log.d(TAG, "Action SEARCH: query=" + query);
                            mainHandler.post(() -> searchProducts(query));

                            action.notify(successResult(), false);
                            return true;
                        }
                    }
                ))
                // Action: Show product detail
                .registerAction(new Action(
                    "com.ainirobot.retail.SHOW_PRODUCT_DETAIL",
                    "Detalle de producto",
                    "Muestra y describe vocalmente los detalles de un producto especifico",
                    Arrays.asList(
                        new Parameter("product_name", ParameterType.STRING,
                            "Nombre del producto", true, null)
                    ),
                    new ActionExecutor() {
                        @Override
                        public boolean onExecute(Action action, Bundle params) {
                            if (params == null) return false;
                            String name = params.getString("product_name", "").toLowerCase();
                            if (name.isEmpty()) return false;

                            Log.d(TAG, "Action DETAIL: name=" + name);
                            mainHandler.post(() -> {
                                List<Product> products = getProducts();
                                for (Product p : products) {
                                    if (p.getName().toLowerCase().contains(name)) {
                                        speakProductInfo(p);
                                        break;
                                    }
                                }
                            });

                            action.notify(successResult(), false);
                            return true;
                        }
                    }
                ))
                // Action: buscar productos
                .registerAction(new Action(
                    "com.robbie.action.search_products",
                    "Buscar Productos",
                    "Busca productos en el catalogo y navega a la pantalla de resultados",
                    Arrays.asList(
                        new Parameter("query", ParameterType.STRING,
                            "Termino de busqueda de productos (ej: proteina, vitaminas, creatina)", true, null)
                    ),
                    new ActionExecutor() {
                        @Override
                        public boolean onExecute(Action action, Bundle params) {
                            String query = params != null ? params.getString("query", "") : "";
                            Log.d(TAG, "Action SEARCH: query=" + query);
                            mainHandler.post(() -> searchProducts(query));
                            action.notify(successResult(), false);
                            return true;
                        }
                    }
                ))
                // Action: Navegar a un punto del mapa
                .registerAction(new Action(
                    "com.robbie.action.NAVIGATE_TO_LOCATION",
                    "Ir a ubicacion",
                    "Lleva al robot a un punto o seccion mapeada en la tienda. Usa este action cuando el usuario pida ir a una seccion, area o punto especifico como proteinas, vitaminas, caja, entrada, etc.",
                    Arrays.asList(
                        new Parameter("destination", ParameterType.STRING,
                            "Nombre del punto de destino al que el robot debe navegar, por ejemplo: proteinas, vitaminas, caja, entrada", true, null)
                    ),
                    new ActionExecutor() {
                        @Override
                        public boolean onExecute(Action action, Bundle params) {
                            String destination = params != null ? params.getString("destination", "") : "";
                            Log.d(TAG, "Action NAVIGATE: destination=" + destination);
                            if (destination.isEmpty()) {
                                AgentCore.INSTANCE.tts("No entendi a donde quieres ir. Dime el nombre de la seccion.", 10000, null);
                                action.notify(successResult(), false);
                                return true;
                            }
                            mainHandler.post(() -> navigateToLocation(destination, action));
                            return true;
                        }
                    }
                ))
                // Action: Detener navegacion
                .registerAction(new Action(
                    "com.robbie.action.STOP_NAVIGATION",
                    "Detener navegacion",
                    "Detiene el movimiento del robot. Usa este action cuando el usuario pida parar, detenerse, o cancelar el recorrido.",
                    null,
                    new ActionExecutor() {
                        @Override
                        public boolean onExecute(Action action, Bundle params) {
                            Log.d(TAG, "Action STOP_NAVIGATION");
                            mainHandler.post(() -> stopNavigation());
                            AgentCore.INSTANCE.tts("De acuerdo, me detengo.", 10000, null);
                            action.notify(successResult(), false);
                            return true;
                        }
                    }
                ));

            Log.i(TAG, "PageAgent inicializado con Actions de emociones y productos");
        } catch (Exception e) {
            Log.e(TAG, "Error inicializando PageAgent: " + e.getMessage(), e);
        }
    }

    /**
     * Emite evento de transcripcion (ASR/TTS) a React Native.
     */
    private void emitTranscriptionEvent(String text, boolean isUserSpeaking, boolean isFinal) {
        try {
            ReactContext reactContext = getReactInstanceManager()
                .getCurrentReactContext();
            if (reactContext == null || !reactContext.hasActiveReactInstance()) return;

            WritableMap params = Arguments.createMap();
            params.putString("text", text);
            params.putBoolean("isUserSpeaking", isUserSpeaking);
            params.putBoolean("isFinal", isFinal);
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onTranscription", params);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo emitir evento de transcripcion: " + e.getMessage());
        }
    }

    /**
     * Emite evento de emocion detectada a React Native.
     */
    private void emitEmotionEvent(String emotion, String sentence) {
        try {
            ReactContext reactContext = getReactInstanceManager()
                .getCurrentReactContext();
            if (reactContext == null || !reactContext.hasActiveReactInstance()) return;

            WritableMap params = Arguments.createMap();
            params.putString("emotion", emotion);
            params.putString("sentence", sentence);
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onEmotionAction", params);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo emitir evento de emocion: " + e.getMessage());
        }
    }

    private void startFaceTracking() {
        try {
            robotApi = RobotApi.getInstance();
            robotApi.connectServer(getApplicationContext(), new ApiListener() {
                @Override
                public void handleApiDisabled() {
                    Log.w(TAG, "RobotApi disabled");
                }

                @Override
                public void handleApiConnected() {
                    robotApiConnected = true;
                    faceTrackActive = true;
                    Log.d(TAG, "RobotApi connected — starting person detection");
                    registerPersonDetection();
                    tryFollowVisiblePerson();
                    loadPlaceList();
                }

                @Override
                public void handleApiDisconnected() {
                    robotApiConnected = false;
                    isFollowing = false;
                    Log.w(TAG, "RobotApi disconnected");
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not init RobotApi", e);
        }
    }

    private void registerPersonDetection() {
        personListener = new PersonListener() {
            @Override
            public void personChanged() {
                super.personChanged();
                if (faceTrackActive && !isFollowing) {
                    tryFollowVisiblePerson();
                }
            }
        };
        PersonApi.getInstance().registerPersonListener(personListener);
        Log.d(TAG, "PersonListener registered");
    }

    private void tryFollowVisiblePerson() {
        if (!robotApiConnected || robotApi == null || !faceTrackActive || isFollowing) return;
        try {
            List<Person> faces = PersonApi.getInstance().getCompleteFaceList();
            if (faces == null || faces.isEmpty()) {
                faces = PersonApi.getInstance().getAllFaceList();
            }
            if (faces != null && !faces.isEmpty()) {
                Person person = faces.get(0);
                int personId = person.getId();
                if (personId >= 0) {
                    Log.d(TAG, "Person found (id=" + personId + "), starting focus follow");
                    doStartFocusFollow(personId);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "tryFollowVisiblePerson error", e);
        }
    }

    private void doStartFocusFollow(int personId) {
        if (!robotApiConnected || robotApi == null || isFollowing) return;
        isFollowing = true;
        currentFollowId = personId;
        try {
            int result = robotApi.startFocusFollow(
                faceTrackReqId, personId, 1000L, 2.0f, true,
                new ActionListener() {
                    @Override
                    public void onResult(int reqId, String result) {
                        Log.d(TAG, "FocusFollow ended: " + result);
                        isFollowing = false;
                        currentFollowId = -1;
                        if (faceTrackActive) {
                            mainHandler.postDelayed(() -> tryFollowVisiblePerson(), 1000);
                        }
                    }

                    @Override
                    public void onError(int reqId, String error) {
                        Log.w(TAG, "FocusFollow error: " + error);
                        isFollowing = false;
                        currentFollowId = -1;
                    }

                    @Override
                    public void onStatusUpdate(int reqId, String status) {
                        Log.d(TAG, "FocusFollow: " + status);
                    }
                });
            Log.d(TAG, "startFocusFollow(id=" + personId + ") result=" + result);
            if (result != 0) {
                isFollowing = false;
                currentFollowId = -1;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not start focus follow", e);
            isFollowing = false;
            currentFollowId = -1;
        }
    }

    private void stopFaceTracking() {
        faceTrackActive = false;
        isFollowing = false;
        currentFollowId = -1;
        try {
            if (personListener != null) {
                PersonApi.getInstance().unregisterPersonListener(personListener);
                personListener = null;
            }
            if (robotApi != null && robotApiConnected) {
                robotApi.stopFocusFollow(faceTrackReqId);
                Log.d(TAG, "Focus follow stopped");
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not stop face tracking", e);
        }
    }

    private void uploadCatalogInfoToAgent() {
        StringBuilder info = new StringBuilder();
        
        // Incluir puntos del mapa para navegacion
        if (!mapPlaces.isEmpty()) {
            info.append("UBICACIONES DISPONIBLES EN LA TIENDA (el robot puede navegar a estos puntos):\n");
            for (String place : mapPlaces) {
                info.append("- ").append(place).append("\n");
            }
            info.append("\nCuando el usuario pida ir a una seccion, usa la accion NAVIGATE_TO_LOCATION con el nombre exacto del punto.\n\n");
        }
        
        // Incluir productos
        List<Product> products = getProducts();
        if (!products.isEmpty()) {
            info.append("Catalogo - ").append(products.size()).append(" productos:\n");
            int max = Math.min(products.size(), 20);
            for (int i = 0; i < max; i++) {
                Product p = products.get(i);
                info.append("- ").append(p.getName())
                    .append(" ($").append(String.format("%.0f", p.getPrice())).append(")")
                    .append(" [").append(p.getCategory()).append("]\n");
            }
        }
        
        if (info.length() > 0) {
            AgentCore.INSTANCE.uploadInterfaceInfo(info.toString());
            Log.d(TAG, "Interface info uploaded: " + mapPlaces.size() + " places, " + products.size() + " products");
        }
    }

    private void triggerRecommendation(String userNeed, String restriction) {
        if (recommendationEngine == null) {
            Log.w(TAG, "RecommendationEngine not initialized");
            return;
        }
        
        List<Product> products = getProducts();
        if (products.isEmpty()) {
            Log.w(TAG, "No products available for recommendation");
            AgentCore.INSTANCE.tts("Aún no tengo productos cargados, intenta de nuevo en un momento", 10000, null);
            return;
        }
        
        Log.d(TAG, "AI recommendation: need=" + userNeed + " restriction=" + restriction);
        recommendationEngine.recommend(products, userNeed, restriction,
                new RecommendationEngine.RecommendationCallback() {
                    @Override
                    public void onResult(String explanation, List<String> ids) {
                        mainHandler.post(() -> showRecommendation(explanation, ids));
                    }
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Recommendation error: " + error);
                    }
                });
    }

    private void showRecommendation(String explanation, List<String> productIds) {
        List<Product> products = getProducts();
        for (Product p : products) {
            if (productIds.contains(p.getId())) {
                p.setAiRecommended(true);
            }
        }
        
        emitProductRecommendationEvent(explanation, productIds);

        if (!explanation.isEmpty()) {
            String speech = explanation.length() > 200 ? explanation.substring(0, 200) : explanation;
            AgentCore.INSTANCE.tts(speech, 30000, null);
        }
    }

    private void searchProducts(String query) {
        if (query == null || query.isEmpty()) return;
        
        List<Product> products = getProducts();
        if (products.isEmpty()) {
            Log.w(TAG, "No products available for search");
            AgentCore.INSTANCE.tts("Aún no tengo productos cargados, intenta de nuevo en un momento", 10000, null);
            return;
        }
        
        String q = query.toLowerCase();
        List<String> foundIds = new ArrayList<>();
        for (Product p : products) {
            if (p.getName().toLowerCase().contains(q) ||
                p.getCategory().toLowerCase().contains(q) ||
                p.getBrand().toLowerCase().contains(q)) {
                foundIds.add(p.getId());
            }
        }
        
        emitProductSearchEvent(query, foundIds);
        
        if (!foundIds.isEmpty()) {
            AgentCore.INSTANCE.tts("Encontre " + foundIds.size() + " productos que coinciden con " + query, 10000, null);
        } else {
            AgentCore.INSTANCE.tts("No encontre productos con " + query, 10000, null);
        }
    }

    private void speakProductInfo(Product product) {
        String text = product.getName() + ". ";
        if (product.getDiscount() > 0) {
            text += "Tiene " + product.getDiscount() + " por ciento de descuento. ";
            text += "Precio: " + String.format("%.0f", product.getDiscountedPrice()) + " pesos. ";
        } else {
            text += "Precio: " + String.format("%.0f", product.getPrice()) + " pesos. ";
        }
        if (!product.getDescription().isEmpty()) text += product.getDescription();

        AgentCore.INSTANCE.tts(text, 30000, null);
        emitProductDetailEvent(product.getId());
    }

    private void setLightSolidColor(int color) {
        isListeningLight = false;
        setLightColor(color);
    }

    private void setLightBlueBreathing() {
        isListeningLight = true;
        setLightColor(0x0066FF);
    }

    private void setLightColor(int color) {
        try {
            RobotApi api = RobotApi.getInstance();
            if (api == null) return;

            JSONObject params = new JSONObject();
            params.put("type", 1);
            params.put("target", 0);
            params.put("color_rgb_value", color);
            api.setLight(lightReqId++, params.toString(), null);
            
            api.setBottomLedEffect(lightReqId++, color, null);
            api.setClavicleLedEffect(lightReqId++, color, null);
        } catch (Exception e) {
            Log.e(TAG, "setLightColor failed", e);
        }
    }

    private void emitProductRecommendationEvent(String explanation, List<String> productIds) {
        try {
            ReactContext reactContext = getReactInstanceManager().getCurrentReactContext();
            if (reactContext == null || !reactContext.hasActiveReactInstance()) return;

            WritableMap params = Arguments.createMap();
            params.putString("explanation", explanation);
            params.putString("productIds", String.join(",", productIds));
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onProductRecommendation", params);
        } catch (Exception e) {
            Log.w(TAG, "Could not emit product recommendation event", e);
        }
    }

    private void emitProductSearchEvent(String query, List<String> productIds) {
        try {
            ReactContext reactContext = getReactInstanceManager().getCurrentReactContext();
            if (reactContext == null || !reactContext.hasActiveReactInstance()) return;

            WritableMap params = Arguments.createMap();
            params.putString("query", query);
            params.putString("productIds", String.join(",", productIds));
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onProductSearch", params);
        } catch (Exception e) {
            Log.w(TAG, "Could not emit product search event", e);
        }
    }

    private void emitProductDetailEvent(String productId) {
        try {
            ReactContext reactContext = getReactInstanceManager().getCurrentReactContext();
            if (reactContext == null || !reactContext.hasActiveReactInstance()) return;

            WritableMap params = Arguments.createMap();
            params.putString("productId", productId);
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onProductDetail", params);
        } catch (Exception e) {
            Log.w(TAG, "Could not emit product detail event", e);
        }
    }

    private List<Product> getProducts() {
        try {
            RobotApp app = (RobotApp) getApplication();
            RobbieConfig config = app.getRobbieConfig();
            if (config != null && config.getProducts() != null) {
                return config.getProducts();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting products from config", e);
        }
        return new ArrayList<>();
    }

    // ==================== Navigation ====================

    private void loadPlaceList() {
        if (robotApi == null) return;
        try {
            robotApi.getPlaceList(navReqId++, new CommandListener() {
                @Override
                public void onResult(int result, String message) {
                    try {
                        mapPlaces.clear();
                        JSONArray jsonArray = new JSONArray(message);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject json = jsonArray.getJSONObject(i);
                            String name = json.getString("name");
                            int status = json.optInt("status", 0);
                            if (status == 0) { // solo puntos accesibles
                                mapPlaces.add(name);
                            }
                        }
                        Log.i(TAG, "Map places loaded: " + mapPlaces.size() + " -> " + mapPlaces);
                        // Actualizar la info del agente para incluir los puntos
                        mainHandler.post(() -> uploadCatalogInfoToAgent());
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing place list", e);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error loading place list", e);
        }
    }

    private void navigateToLocation(String destination, Action action) {
        if (!robotApiConnected || robotApi == null) {
            AgentCore.INSTANCE.tts("No puedo navegar en este momento, el sistema de movimiento no esta conectado.", 10000, null);
            action.notify(successResult(), false);
            return;
        }

        if (isNavigating) {
            stopNavigation();
        }

        // Buscar el punto mas parecido en la lista
        String matchedPlace = findBestMatchingPlace(destination);

        if (matchedPlace == null) {
            String available = mapPlaces.isEmpty() ? "No hay puntos configurados." 
                : "Los puntos disponibles son: " + String.join(", ", mapPlaces);
            AgentCore.INSTANCE.tts("No encontre la seccion " + destination + ". " + available, 15000, null);
            action.notify(successResult(), false);
            return;
        }

        isNavigating = true;
        // Detener face tracking durante la navegacion
        stopFaceTracking();

        AgentCore.INSTANCE.tts("Vamos, te llevo a " + matchedPlace, 10000, null);
        Log.i(TAG, "Starting navigation to: " + matchedPlace);

        try {
            robotApi.startNavigation(navReqId++, matchedPlace, 0.2, 30000,
                new ActionListener() {
                    @Override
                    public void onResult(int status, String response) {
                        isNavigating = false;
                        if (status == Definition.RESULT_OK && "true".equals(response)) {
                            Log.i(TAG, "Navigation SUCCESS to " + matchedPlace);
                            AgentCore.INSTANCE.tts("Llegamos a " + matchedPlace + ". Aqui esta la seccion que buscabas.", 15000, null);
                        } else {
                            Log.w(TAG, "Navigation ended: status=" + status + " response=" + response);
                            AgentCore.INSTANCE.tts("No pude llegar a " + matchedPlace + ", algo salio mal.", 10000, null);
                        }
                        action.notify(successResult(), false);
                        // Reanudar face tracking
                        mainHandler.postDelayed(() -> startFaceTracking(), 2000);
                    }

                    @Override
                    public void onError(int errorCode, String errorString) {
                        isNavigating = false;
                        Log.e(TAG, "Navigation ERROR: code=" + errorCode + " msg=" + errorString);
                        String errorMsg;
                        switch (errorCode) {
                            case Definition.ERROR_DESTINATION_NOT_EXIST:
                                errorMsg = "El punto " + matchedPlace + " no existe en el mapa.";
                                break;
                            case Definition.ERROR_NOT_ESTIMATE:
                                errorMsg = "No estoy ubicado en el mapa, necesito que me ubiquen primero.";
                                break;
                            case Definition.ERROR_IN_DESTINATION:
                                errorMsg = "Ya estamos en " + matchedPlace + ".";
                                break;
                            default:
                                errorMsg = "No pude navegar a " + matchedPlace + ".";
                                break;
                        }
                        AgentCore.INSTANCE.tts(errorMsg, 10000, null);
                        action.notify(successResult(), false);
                        mainHandler.postDelayed(() -> startFaceTracking(), 2000);
                    }

                    @Override
                    public void onStatusUpdate(int status, String data, String extraData) {
                        Log.d(TAG, "Navigation status: " + status + " data=" + data);
                    }
                });
        } catch (Exception e) {
            isNavigating = false;
            Log.e(TAG, "Error starting navigation", e);
            AgentCore.INSTANCE.tts("Error al intentar navegar.", 10000, null);
            action.notify(successResult(), false);
            mainHandler.postDelayed(() -> startFaceTracking(), 2000);
        }
    }

    private void stopNavigation() {
        if (robotApi != null && isNavigating) {
            try {
                robotApi.stopNavigation(navReqId++);
                isNavigating = false;
                Log.d(TAG, "Navigation stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping navigation", e);
            }
        }
    }

    private String findBestMatchingPlace(String destination) {
        if (mapPlaces.isEmpty()) return null;
        String destLower = destination.toLowerCase().trim();

        // 1. Coincidencia exacta
        for (String place : mapPlaces) {
            if (place.equalsIgnoreCase(destLower)) {
                return place;
            }
        }

        // 2. El punto contiene el destino o viceversa
        for (String place : mapPlaces) {
            String placeLower = place.toLowerCase();
            if (placeLower.contains(destLower) || destLower.contains(placeLower)) {
                return place;
            }
        }

        // 3. Alguna palabra coincide
        String[] destWords = destLower.split("\\s+");
        for (String place : mapPlaces) {
            String placeLower = place.toLowerCase();
            for (String word : destWords) {
                if (word.length() >= 3 && placeLower.contains(word)) {
                    return place;
                }
            }
        }

        return null;
    }

    public static void launch(Activity fromActivity) {
        Intent intent = new Intent(fromActivity, EveActivity.class);
        fromActivity.startActivity(intent);
    }
}
