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
import com.ainirobot.coreservice.client.person.PersonApi;
import com.ainirobot.coreservice.client.person.PersonListener;
import com.ainirobot.coreservice.client.listener.Person;
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
    private final List<Product> allProducts = new ArrayList<>();
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

    @Override
    protected String getMainComponentName() {
        return "xiabao-retail-app";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        RobotApp app = (RobotApp) getApplication();
        RobbieConfig config = app.getRobbieConfig();
        
        recommendationEngine = new RecommendationEngine();
        
        if (config != null) {
            allProducts.addAll(config.getProducts());
            Log.d(TAG, "Loaded " + allProducts.size() + " products from config");
        }
        
        initializePageAgent();
    }

    @Override
    protected void onStart() {
        super.onStart();
        
        // Refrescar productos desde config (puede haber cargado desde la API
        // despues de onCreate, cuando allProducts estaba vacio)
        RobotApp app = (RobotApp) getApplication();
        RobbieConfig config = app.getRobbieConfig();
        if (config != null && config.getProducts() != null && !config.getProducts().isEmpty()
                && allProducts.isEmpty()) {
            allProducts.addAll(config.getProducts());
            Log.i(TAG, "Products refreshed in onStart: " + allProducts.size());
        }
        
        uploadCatalogInfoToAgent();
        
        AgentCore.INSTANCE.enableWakeupMode(true);
        AgentCore.INSTANCE.setEnableWakeFree(true);
        Log.d(TAG, "Wake mode: hardware=ON, wake-free=ON");
        
        setLightSolidColor(0xFF0000);
        
        startFaceTracking();
        
        AsyncTaskHelper.executeDelayed(() -> {
            AgentCore.INSTANCE.tts(
                "Hola, soy Robbie. Di 'Robbie' para hablar conmigo. Puedo ayudarte a encontrar productos o recomendarte lo que necesites.",
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
                    
                    String lower = text.toLowerCase();
                    boolean hasWakeWord = lower.contains("robbie") || lower.contains("robi") ||
                        lower.contains("rubi") || lower.contains("robin") ||
                        lower.contains("robe") || lower.contains("robby");
                    
                    // Si no tiene wake word pero el texto no esta vacio, enviar directamente
                    // al query (el hardware wake word ya filtro la activacion del mic)
                    if (!hasWakeWord && !text.isEmpty()) {
                        Log.d(TAG, "No wake word, sending directly to query: " + text);
                        AgentCore.INSTANCE.query(text);
                        emitTranscriptionEvent(text, true, true);
                        return true;
                    }
                    
                    if (hasWakeWord) {
                        String command = text.replaceAll("(?i)robbie|robi|rubi|robin|robe|robby", "").trim();
                        if (command.isEmpty()) {
                            AgentCore.INSTANCE.tts("¿Sí? ¿En qué te puedo ayudar?", 10000, null);
                        } else {
                            Log.d(TAG, "Wake word detected → query: " + command);
                            AgentCore.INSTANCE.query(command);
                        }
                        emitTranscriptionEvent(text, true, true);
                        return true;
                    }
                    
                    return true;
                }
                
                @Override
                public boolean onTTSResult(Transcription transcription) {
                    if (transcription.getFinal()) {
                        Log.d(TAG, "TTS final: " + transcription.getText());
                        mainHandler.removeCallbacks(restoreDefaultLight);
                        mainHandler.postDelayed(restoreDefaultLight, 1000);
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
                                for (Product p : allProducts) {
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
        if (allProducts.isEmpty()) return;
        StringBuilder info = new StringBuilder("Catalogo - " + allProducts.size() + " productos:\n");
        int max = Math.min(allProducts.size(), 20);
        for (int i = 0; i < max; i++) {
            Product p = allProducts.get(i);
            info.append("- ").append(p.getName())
                .append(" ($").append(String.format("%.0f", p.getPrice())).append(")")
                .append(" [").append(p.getCategory()).append("]\n");
        }
        AgentCore.INSTANCE.uploadInterfaceInfo(info.toString());
    }

    private void triggerRecommendation(String userNeed, String restriction) {
        if (recommendationEngine == null) {
            Log.w(TAG, "RecommendationEngine not initialized");
            return;
        }
        
        Log.d(TAG, "AI recommendation: need=" + userNeed + " restriction=" + restriction);
        recommendationEngine.recommend(allProducts, userNeed, restriction,
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
        for (Product p : allProducts) {
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
        String q = query.toLowerCase();
        List<String> foundIds = new ArrayList<>();
        for (Product p : allProducts) {
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

    public static void launch(Activity fromActivity) {
        Intent intent = new Intent(fromActivity, EveActivity.class);
        fromActivity.startActivity(intent);
    }
}
