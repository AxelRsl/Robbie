package com.robbie.platform.react;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.ReactActivity;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.robbie.core.hardware.LedController;
import com.robbie.core.navigation.TourExecutor;
import com.robbie.data.local.entity.ProductEntity;
import com.robbie.platform.agent.IAgentBridge;
import com.robbie.platform.agent.RobbieAgentBridge;
import com.robbie.platform.agent.RobotActionHandler;
import com.robbie.platform.retail.Product;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * EveActivity - Activity principal del motor React Native.
 *
 * Ejecutada en proceso :sandbox, se encarga de:
 * 1. Cargar el entorno React Native en el motor Hermes
 * 2. Ejecutar el bundle JavaScript (platform.android.bundle)
 * 3. Mostrar la interfaz React Native (MenuScreen como pantalla inicial)
 * 4. Conectar el IAgentBridge con el RobotActionHandler
 * 5. Emitir eventos de resultado hacia React Native
 *
 * Toda la logica del Agent OS esta encapsulada en RobbieAgentBridge.
 * Toda la logica de acciones del robot esta en RobotActionHandler.
 * Cuando se cambie de Agent OS, solo se reemplaza el IAgentBridge.
 */
public class EveActivity extends ReactActivity {

    private static final String TAG = "EveActivity";
    private static WeakReference<EveActivity> currentInstance;
    private String lastChargingEventSignature = "";

    private RobbieAgentBridge agentBridge;
    private RobotActionHandler actionHandler;

    @Override
    protected String getMainComponentName() {
        return "xiabao-retail-app";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currentInstance = new WeakReference<>(this);
        actionHandler = new RobotActionHandler(this);
        agentBridge = new RobbieAgentBridge();

        actionHandler.setAgentBridge(agentBridge);
        actionHandler.setResultCallback(createResultCallback());

        TourExecutor.getInstance().setAgentBridge(agentBridge);

        agentBridge.initialize(
            this,
            (actionName, params) -> actionHandler.handleAction(actionName, params),
            createTranscriptionCallback()
        );

        // Register this activity for video dialog display during tours
        com.robbie.core.media.TourMediaPlayer.getInstance().setCurrentActivity(this);

        // Register activity with animation manager so it can reveal the system face
        actionHandler.registerActivityForFaceReveal(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        actionHandler.uploadCatalogInfoToAgent();
        agentBridge.onActivityStart();
        actionHandler.startFaceTracking();
        agentBridge.speakWelcome();
    }

    @Override
    protected void onStop() {
        super.onStop();
        actionHandler.stopFaceTracking();
    }

    @Override
    protected void onDestroy() {
        actionHandler.destroy();
        agentBridge.destroy();
        super.onDestroy();
    }

    // ==================== Callbacks ====================

    private IAgentBridge.TranscriptionCallback createTranscriptionCallback() {
        return new IAgentBridge.TranscriptionCallback() {
            @Override
            public void onASRPartial(String text) {
                emitTranscriptionEvent(text, true, false);
            }

            @Override
            public void onASRFinal(String text) {
                emitTranscriptionEvent(text, true, true);
            }

            @Override
            public void onTTSUpdate(String text, boolean isFinal) {
                emitTranscriptionEvent(text, false, isFinal);
            }
        };
    }

    private RobotActionHandler.ActionResultCallback createResultCallback() {
        return new RobotActionHandler.ActionResultCallback() {
            @Override
            public void onEmotionAction(String emotion, String sentence) {
                emitEmotionEvent(emotion, sentence);
            }

            @Override
            public void onNavigationEvent(String destination, boolean isNavigating) {
                emitNavigationEvent(destination, isNavigating);
            }

            @Override
            public void onProductSearch(String query, List<Product> products) {
                emitProductSearchEvent(query, products);
            }

            @Override
            public void onProductSearchFromDb(String query, String recommendation, List<ProductEntity> products) {
                emitProductSearchEvent(query, recommendation, products);
            }

            @Override
            public void onProductRecommendation(String explanation, List<Product> products) {
                emitProductRecommendationEvent(explanation, products);
            }

            @Override
            public void onProductDetail(String productId) {
                emitProductDetailEvent(productId);
            }

            @Override
            public void onLedEvent(String eventType, String data) {
                emitLedEventToRN(eventType, data);
            }

            @Override
            public void onModeSwitch(String mode) {
                emitModeSwitchEvent(mode);
            }

            @Override
            public void onChargingEvent(String status, String message, int batteryLevel, boolean isCharging, boolean isNavigatingToCharger, boolean robotApiConnected, boolean autoTriggered) {
                emitChargingEvent(status, message, batteryLevel, isCharging, isNavigatingToCharger, robotApiConnected, autoTriggered);
            }
        };
    }

    // ==================== Event Emitters ====================

    private ReactContext getReactCtx() {
        try {
            ReactContext ctx = getReactInstanceManager().getCurrentReactContext();
            if (ctx != null && ctx.hasActiveReactInstance()) return ctx;
        } catch (Exception ignored) {}
        return null;
    }

    private void emitTranscriptionEvent(String text, boolean isUserSpeaking, boolean isFinal) {
        ReactContext ctx = getReactCtx();
        if (ctx == null) return;
        try {
            WritableMap params = Arguments.createMap();
            params.putString("text", text);
            params.putBoolean("isUserSpeaking", isUserSpeaking);
            params.putBoolean("isFinal", isFinal);
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onTranscription", params);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo emitir evento de transcripcion: " + e.getMessage());
        }
    }

    private void emitEmotionEvent(String emotion, String sentence) {
        ReactContext ctx = getReactCtx();
        if (ctx == null) return;
        try {
            WritableMap params = Arguments.createMap();
            params.putString("emotion", emotion);
            params.putString("sentence", sentence);
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onEmotionAction", params);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo emitir evento de emocion: " + e.getMessage());
        }
    }

    private void emitNavigationEvent(String destination, boolean isNavigating) {
        ReactContext ctx = getReactCtx();
        if (ctx == null) return;
        try {
            WritableMap params = Arguments.createMap();
            params.putString("destination", destination);
            params.putBoolean("isNavigating", isNavigating);
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onNavigation", params);
            Log.d(TAG, "Emitted navigation event: dest=" + destination + " navigating=" + isNavigating);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo emitir evento de navegacion: " + e.getMessage());
        }
    }

    private void emitProductSearchEvent(String query, List<Product> products) {
        ReactContext ctx = getReactCtx();
        if (ctx == null) return;
        try {
            WritableMap params = Arguments.createMap();
            params.putString("query", query);

            WritableArray productsArray = Arguments.createArray();
            for (Product product : products) {
                WritableMap productMap = Arguments.createMap();
                productMap.putString("id", product.getId());
                productMap.putString("name", product.getName());
                productMap.putString("category", product.getCategory());
                productMap.putString("brand", product.getBrand());
                productMap.putDouble("price", product.getPrice());
                productMap.putInt("discount", product.getDiscount());
                productMap.putString("description", product.getDescription());
                productMap.putString("imageUrl", product.getImage());
                productMap.putBoolean("inStock", product.isInStock());
                productsArray.pushMap(productMap);
            }
            params.putArray("products", productsArray);
            params.putInt("totalResults", products.size());

            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onProductSearch", params);
        } catch (Exception e) {
            Log.w(TAG, "Could not emit product search event", e);
        }
    }

    private void emitProductSearchEvent(String query, String recommendation, List<ProductEntity> products) {
        ReactContext ctx = getReactCtx();
        if (ctx == null) return;
        try {
            WritableMap params = Arguments.createMap();
            params.putString("query", query);
            params.putString("recommendation", recommendation);
            params.putInt("totalResults", products.size());

            WritableArray productsArray = Arguments.createArray();
            for (ProductEntity product : products) {
                WritableMap productMap = Arguments.createMap();
                productMap.putString("id", product.getId());
                productMap.putString("name", product.getName());
                productMap.putString("category", product.getCategory());
                productMap.putDouble("price", product.getPrice());
                productMap.putInt("discount", product.getDiscount());
                productMap.putString("imageUrl", product.getImage());
                productMap.putString("description", product.getDescription());
                boolean inStockValue = product.getInStock();
                Log.d(TAG, "Sending product " + product.getName() + " inStock=" + inStockValue);
                productMap.putBoolean("inStock", inStockValue);
                double finalPrice = product.getPrice() * (1 - product.getDiscount() / 100.0);
                productMap.putDouble("finalPrice", finalPrice);
                productsArray.pushMap(productMap);
            }
            params.putArray("products", productsArray);

            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onProductSearch", params);
        } catch (Exception e) {
            Log.w(TAG, "Could not emit product search event with full data", e);
        }
    }

    private void emitProductRecommendationEvent(String explanation, List<Product> products) {
        ReactContext ctx = getReactCtx();
        if (ctx == null) return;
        try {
            WritableMap params = Arguments.createMap();
            params.putString("explanation", explanation);
            
            // Enviar array completo de productos con flag aiRecommended
            WritableArray productsArray = Arguments.createArray();
            for (Product product : products) {
                WritableMap productMap = Arguments.createMap();
                productMap.putString("id", product.getId());
                productMap.putString("name", product.getName());
                productMap.putString("category", product.getCategory());
                productMap.putString("subcategory", product.getSubcategory());
                productMap.putDouble("price", product.getPrice());
                productMap.putInt("discount", product.getDiscount());
                productMap.putString("imageUrl", product.getImage());
                productMap.putString("description", product.getDescription());
                productMap.putBoolean("inStock", product.isInStock());
                productMap.putBoolean("aiRecommended", product.isAiRecommended());
                productMap.putString("brand", product.getBrand());
                productsArray.pushMap(productMap);
            }
            params.putArray("products", productsArray);
            
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onProductRecommendation", params);
        } catch (Exception e) {
            Log.w(TAG, "Could not emit product recommendation event", e);
        }
    }

    private void emitProductDetailEvent(String productId) {
        ReactContext ctx = getReactCtx();
        if (ctx == null) return;
        try {
            WritableMap params = Arguments.createMap();
            params.putString("productId", productId);
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onProductDetail", params);
        } catch (Exception e) {
            Log.w(TAG, "Could not emit product detail event", e);
        }
    }

    private void emitLedEventToRN(String eventType, String data) {
        ReactContext ctx = getReactCtx();
        if (ctx == null) return;
        try {
            WritableMap params = Arguments.createMap();
            params.putString("eventType", eventType);
            params.putString("data", data);
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onLedEvent", params);
            Log.d(TAG, "Emitted LED event: " + eventType + " data=" + data);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo emitir evento LED: " + e.getMessage());
        }
    }

    private void emitModeSwitchEvent(String mode) {
        ReactContext ctx = getReactCtx();
        if (ctx == null) return;
        try {
            WritableMap params = Arguments.createMap();
            params.putString("mode", mode);
            params.putBoolean("autoSwitch", true);
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onModeSwitch", params);
            Log.d(TAG, "Mode switch: " + mode);
        } catch (Exception e) {
            Log.w(TAG, "Could not emit mode switch event", e);
        }
    }

    private void emitChargingEvent(String status, String message, int batteryLevel, boolean isCharging, boolean isNavigatingToCharger, boolean robotApiConnected, boolean autoTriggered) {
        ReactContext ctx = getReactCtx();
        if (ctx == null) return;
        try {
            String signature = status + "|" + message + "|" + batteryLevel + "|" + isCharging + "|" + isNavigatingToCharger + "|" + robotApiConnected + "|" + autoTriggered;
            if (signature.equals(lastChargingEventSignature)) {
                return;
            }
            lastChargingEventSignature = signature;
            WritableMap params = Arguments.createMap();
            params.putString("status", status);
            params.putString("message", message);
            params.putInt("batteryLevel", batteryLevel);
            params.putBoolean("isCharging", isCharging);
            params.putBoolean("isNavigatingToCharger", isNavigatingToCharger);
            params.putBoolean("robotApiConnected", robotApiConnected);
            params.putBoolean("autoTriggered", autoTriggered);
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onChargingStatus", params);
            Log.d(TAG, "Charging event: " + status + " - " + message);
        } catch (Exception e) {
            Log.w(TAG, "Could not emit charging event", e);
        }
    }

    /**
     * Emit an emotion event to React Native from an external source (e.g. AvatarFaceHandler HTTP API).
     * This triggers the FaceOverlay component to show the 2D face animation.
     */
    public static void emitEmotionFromExternal(String emotion) {
        EveActivity instance = currentInstance != null ? currentInstance.get() : null;
        if (instance == null) {
            Log.w(TAG, "No EveActivity instance available to emit emotion event");
            return;
        }
        instance.emitEmotionEvent(emotion, "");
    }

    /**
     * Send text to the AgentOS as if the user said it. Used for TTS scene function commands.
     */
    public static void sendAgentQueryFromExternal(String text) {
        EveActivity instance = currentInstance != null ? currentInstance.get() : null;
        if (instance == null) {
            Log.w(TAG, "No EveActivity instance available to send agent query");
            return;
        }
        if (instance.agentBridge != null && instance.agentBridge.isReady()) {
            Log.i(TAG, "Sending agent query from scene function: " + text);
            instance.agentBridge.query(text);
        } else {
            Log.w(TAG, "Agent bridge not ready for query: " + text);
        }
    }

    public static void emitSceneReloadFromExternal() {
        EveActivity instance = currentInstance != null ? currentInstance.get() : null;
        if (instance == null) {
            Log.w(TAG, "No EveActivity instance available to emit scene reload");
            return;
        }
        instance.emitModeSwitchEvent("menu");
    }

    public static void launch(Activity fromActivity) {
        Intent intent = new Intent(fromActivity, EveActivity.class);
        fromActivity.startActivity(intent);
    }
}
