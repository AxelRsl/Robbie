package com.robbie.platform.agent;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.robbie.core.animation.ProceduralAnimationManager;
import com.robbie.core.hardware.LedController;
import com.robbie.core.navigation.TourExecutor;
import com.robbie.RobotApp;
import com.robbie.platform.retail.Product;
import com.robbie.platform.retail.RobbieConfig;
import com.robbie.platform.retail.RobbieRecommendationEngine;
import com.robbie.data.local.RobbieDatabase;
import com.robbie.data.local.entity.ProductEntity;

import com.ainirobot.coreservice.client.ApiListener;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.ActionListener;
import com.ainirobot.coreservice.client.listener.CommandListener;
import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.person.PersonApi;
import com.ainirobot.coreservice.client.person.PersonListener;
import com.ainirobot.coreservice.client.listener.Person;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Centraliza la logica de ejecucion de acciones del robot.
 *
 * Esta clase es independiente del Agent OS. Recibe ordenes de alto nivel
 * (navegar, buscar productos, cambiar LEDs, etc.) y las ejecuta usando
 * los managers/APIs del robot.
 *
 * El IAgentBridge despacha las acciones del LLM hacia esta clase.
 * EveActivity escucha los eventos resultantes via ActionResultCallback.
 */
public class RobotActionHandler {

    private static final String TAG = "RobotActionHandler";
    private static RobotActionHandler instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final RobbieRecommendationEngine robbieRecommendationEngine;
    private final android.content.Context context;

    private RobotApi robotApi;
    private int faceTrackReqId = 1001;
    private int lightReqId = 2001;
    private int navReqId = 3001;

    private boolean robotApiConnected = false;
    private boolean faceTrackActive = false;
    private boolean isFollowing = false;
    private int currentFollowId = -1;
    private PersonListener personListener;
    private boolean isNavigating = false;
    private final List<String> mapPlaces = new ArrayList<>();
    private final ProceduralAnimationManager animationManager;

    private IAgentBridge agentBridge;
    private ActionResultCallback resultCallback;

    /**
     * Callback para notificar resultados de acciones a EveActivity.
     */
    public interface ActionResultCallback {
        void onEmotionAction(String emotion, String sentence);
        void onNavigationEvent(String destination, boolean isNavigating);
        void onProductSearch(String query, List<Product> products);
        void onProductSearchFromDb(String query, String recommendation, List<ProductEntity> products);
        void onProductRecommendation(String explanation, List<Product> products);
        void onProductDetail(String productId);
        void onLedEvent(String eventType, String data);
        void onModeSwitch(String mode);
    }
    


    public RobotActionHandler(android.content.Context context) {
        this.context = context.getApplicationContext();
        this.robbieRecommendationEngine = new RobbieRecommendationEngine();
        this.animationManager = ProceduralAnimationManager.getInstance(this.context);
        instance = this;
    }

    /**
     * Register the current Activity so the animation manager can temporarily
     * send it to background to reveal the system's 2D animated face.
     */
    public void registerActivityForFaceReveal(android.app.Activity activity) {
        animationManager.setActivity(activity);
    }

    /**
     * Get the animation manager for direct access (e.g., from AvatarFaceHandler).
     */
    public ProceduralAnimationManager getAnimationManager() {
        return animationManager;
    }

    private void displayProceduralEmotion(String emotion) {
        ProceduralAnimationManager.Emotion resolvedEmotion = ProceduralAnimationManager.Emotion.fromString(emotion);

        // Physical head movement only - the 2D face overlay is handled by React Native's FaceOverlay component
        // which listens to the onEmotionAction event emitted by EveActivity
        animationManager.playExpression(resolvedEmotion);
        Log.d(TAG, "Playing head animation for emotion: " + resolvedEmotion.value);
    }

    public static RobotActionHandler getInstance() {
        return instance;
    }

    public void setAgentBridge(IAgentBridge bridge) {
        this.agentBridge = bridge;
    }

    public void setResultCallback(ActionResultCallback callback) {
        this.resultCallback = callback;
    }

    /**
     * Despacha una accion recibida del Agent OS.
     * Este es el punto central donde todas las acciones del LLM llegan.
     */
    public boolean handleAction(String actionName, Bundle params) {
        Log.d(TAG, "handleAction: " + actionName);
        switch (actionName) {
            case "com.robbie.action.SHOW_HAPPY":
                return handleEmotion("happy", params);
            case "com.robbie.action.SHOW_SAD":
                return handleEmotion("sad", params);
            case "com.robbie.action.SHOW_ANGRY":
                return handleEmotion("angry", params);
            case "com.robbie.action.RECOMMEND_PRODUCTS":
                return handleRecommendProducts(params);
            case "com.robbie.action.SHOW_PRODUCT_DETAIL":
                return handleShowProductDetail(params);
            case "com.robbie.action.NAVIGATE_TO_LOCATION":
                return handleNavigateToLocation(params);
            case "com.robbie.action.STOP_NAVIGATION":
                return handleStopNavigation();
            case "com.robbie.action.SEARCH_PRODUCTS":
                return handleSearchProductsFromDb(params);
            case "com.robbie.action.search_products":
                return handleSearchProductsFromDb(params);
            case "com.robbie.action.SET_LED_COLOR":
                return handleSetLedColor(params);
            case "com.robbie.action.START_LED_EFFECT":
                return handleStartLedEffect(params);
            case "com.robbie.action.RESTORE_LED_DEFAULT":
                return handleRestoreLedDefault();
            case "com.robbie.action.START_TOUR":
                return handleStartTour(params);
            case "com.robbie.action.STOP_TOUR":
                return handleStopTour();
            case "com.robbie.action.SWITCH_MODE":
                return handleSwitchMode(params);
            case "com.robbie.action.GO_TO_MENU":
                return handleGoToMenu();
            case "com.robbie.action.ENTER_RETAIL_MODE":
                return handleEnterRetailMode();
            case "com.robbie.action.ENTER_EXHIBITION_MODE":
                return handleEnterExhibitionMode();
            default:
                Log.w(TAG, "Unknown action: " + actionName);
                return false;
        }
    }

    // ==================== Emociones ====================

    private boolean handleEmotion(String emotion, Bundle params) {
        String sentence = params != null ? params.getString("sentence", "") : "";
        if (resultCallback != null) resultCallback.onEmotionAction(emotion, sentence);
        mainHandler.post(() -> displayProceduralEmotion(emotion));
        if (!sentence.isEmpty() && agentBridge != null) {
            agentBridge.tts(sentence, 180000);
        }
        return true;
    }

    // ==================== Productos ====================

    private boolean handleRecommendProducts(Bundle params) {
        if (params == null) return false;
        String need = params.getString("user_need", "");
        String restriction = params.getString("dietary_restriction", "");
        if (need.isEmpty()) return false;

        Log.d(TAG, "RECOMMEND: need=" + need + " restriction=" + restriction);
        mainHandler.post(() -> triggerRecommendation(need, restriction));
        return true;
    }

    private boolean handleShowProductDetail(Bundle params) {
        if (params == null) return false;
        String name = params.getString("product_name", "").toLowerCase();
        if (name.isEmpty()) return false;

        Log.d(TAG, "DETAIL: name=" + name);
        mainHandler.post(() -> {
            List<Product> products = getProducts();
            for (Product p : products) {
                if (p.getName().toLowerCase().contains(name)) {
                    speakProductInfo(p);
                    break;
                }
            }
        });
        return true;
    }

    private boolean handleSearchProductsFromDb(Bundle params) {
        String query = params != null ? params.getString("query", "") : "";
        String recommendation = params != null ? params.getString("recommendation", "") : "";
        Log.d(TAG, "SEARCH_DB: query=" + query + ", recommendation=" + recommendation);

        if (query.isEmpty()) {
            if (agentBridge != null)
                agentBridge.tts("No entendi que producto buscas. Puedes decirme el nombre, categoria o marca.", 10000);
            return true;
        }

        mainHandler.post(() -> {
            try {
                RobbieDatabase db = RobbieDatabase.getInstance(context);
                
                // DEBUG: Verificar total de productos en BD
                List<ProductEntity> allProducts = db.productDao().getAllProductsSync();
                Log.i(TAG, "Total products in DB: " + allProducts.size());
                if (!allProducts.isEmpty()) {
                    ProductEntity first = allProducts.get(0);
                    Log.d(TAG, "Sample product: " + first.getName() + " | " + first.getCategory() + " | " + first.getBrand());
                }
                
                // Detectar si es una consulta de recomendación (palabras clave como "necesito", "recomienda", "ayuda")
                String queryLower = query.toLowerCase();
                boolean isRecommendationQuery = queryLower.contains("necesito") || 
                                              queryLower.contains("recomienda") || 
                                              queryLower.contains("ayuda") ||
                                              queryLower.contains("quiero") ||
                                              queryLower.contains("busco algo para");
                
                if (isRecommendationQuery && !allProducts.isEmpty()) {
                    // Usar motor de recomendaciones AI
                    Log.i(TAG, "Using AI recommendation engine for query: " + query);
                    robbieRecommendationEngine.recommend(allProducts, query, null, 
                        new RobbieRecommendationEngine.RecommendationCallback() {
                            @Override
                            public void onResult(String explanation, List<String> recommendedProductIds) {
                                mainHandler.post(() -> {
                                    // Convertir IDs a ProductEntity
                                    List<ProductEntity> recommendedProducts = new ArrayList<>();
                                    for (String id : recommendedProductIds) {
                                        for (ProductEntity p : allProducts) {
                                            if (p.getId().equals(id)) {
                                                recommendedProducts.add(p);
                                                break;
                                            }
                                        }
                                    }
                                    
                                    Log.i(TAG, "AI recommended " + recommendedProducts.size() + " products");
                                    
                                    if (resultCallback != null) {
                                        resultCallback.onProductSearchFromDb(query, explanation, recommendedProducts);
                                        resultCallback.onModeSwitch("retail");
                                    }

                                    String ttsResponse = explanation + ". Te muestro " + recommendedProducts.size() + " productos recomendados.";
                                    if (!recommendedProducts.isEmpty()) {
                                        ProductEntity first = recommendedProducts.get(0);
                                        ttsResponse += " El primero es " + first.getName();
                                        if (first.getPrice() > 0) {
                                            ttsResponse += " con precio de $" + String.format("%.2f", first.getPrice());
                                        }
                                    }
                                    if (agentBridge != null) agentBridge.tts(ttsResponse, 15000);
                                });
                            }

                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "AI recommendation error: " + error);
                                // Fallback a búsqueda normal
                                performNormalSearch(db, allProducts, query, recommendation);
                            }
                        });
                } else {
                    // Búsqueda normal por nombre/categoría
                    performNormalSearch(db, allProducts, query, recommendation);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in SEARCH_PRODUCTS", e);
                if (agentBridge != null)
                    agentBridge.tts("Lo siento, hubo un error al buscar productos.", 10000);
            }
        });
        return true;
    }
    
    private void performNormalSearch(RobbieDatabase db, List<ProductEntity> allProducts, String query, String recommendation) {
        // Buscar productos
        List<ProductEntity> products = db.productDao().searchProducts(query);
        Log.i(TAG, "Found " + products.size() + " products for query: '" + query + "'");
        
        // FALLBACK: Si no encuentra nada, usar estrategias alternativas
        if (products.isEmpty() && !allProducts.isEmpty()) {
            android.util.Log.w(TAG, "No results for '" + query + "', trying fallback strategies...");
            
            // Estrategia 1: Buscar por palabras individuales
            String[] words = query.toLowerCase().split("\\s+");
            for (String word : words) {
                if (word.length() >= 3) { // Solo palabras de 3+ caracteres
                    List<ProductEntity> wordResults = db.productDao().searchProducts(word);
                    if (!wordResults.isEmpty()) {
                        products = wordResults;
                        android.util.Log.i(TAG, "Found " + products.size() + " products with word: " + word);
                        break;
                    }
                }
            }
            
            // Estrategia 2: Si aún no hay resultados, buscar por categorías comunes
            if (products.isEmpty()) {
                String[] commonCategories = {"proteina", "vitamina", "suplemento", "energia", "salud"};
                String queryLower = query.toLowerCase();
                for (String category : commonCategories) {
                    if (queryLower.contains(category)) {
                        products = db.productDao().getProductsByCategory(category);
                        if (!products.isEmpty()) {
                            android.util.Log.i(TAG, "Found " + products.size() + " products in category: " + category);
                            break;
                        }
                    }
                }
            }
            
            // Estrategia 3: Como último recurso, mostrar productos aleatorios
            if (products.isEmpty()) {
                int maxShow = Math.min(5, allProducts.size());
                products = allProducts.subList(0, maxShow);
                android.util.Log.i(TAG, "Showing " + products.size() + " random products as fallback");
            }
        }
        
        Log.i(TAG, "Final results: " + products.size() + " products for: " + query);

        if (resultCallback != null) {
            resultCallback.onProductSearchFromDb(query, recommendation, products);
            resultCallback.onModeSwitch("retail");
        }

        String ttsResponse;
        if (!recommendation.isEmpty()) {
            ttsResponse = recommendation + ". Encontre " + products.size() + " productos relacionados.";
        } else {
            ttsResponse = "Encontre " + products.size() + " productos relacionados con " + query + ".";
        }
        if (!products.isEmpty()) {
            ProductEntity first = products.get(0);
            ttsResponse += " El primero es " + first.getName();
            if (first.getPrice() > 0) {
                ttsResponse += " con precio de $" + String.format("%.2f", first.getPrice());
            }
        }
        if (agentBridge != null) agentBridge.tts(ttsResponse, 15000);
    }

    private void triggerRecommendation(String userNeed, String restriction) {
        // Crear Bundle con los parámetros para handleSearchProductsFromDb
        Bundle params = new Bundle();
        params.putString("query", userNeed);
        if (restriction != null && !restriction.isEmpty()) {
            params.putString("recommendation", restriction);
        }
        
        // Usar el mismo flujo que handleSearchProductsFromDb
        handleSearchProductsFromDb(params);
    }

    private void showRecommendation(String explanation, List<String> productIds) {
        List<Product> products = getProducts();
        // Marcar productos recomendados
        for (Product p : products) {
            if (productIds.contains(p.getId())) {
                p.setAiRecommended(true);
            } else {
                p.setAiRecommended(false);
            }
        }
        if (resultCallback != null) {
            resultCallback.onModeSwitch("retail");
            // Enviar TODOS los productos con el flag aiRecommended marcado
            resultCallback.onProductRecommendation(explanation, products);
        }
        if (!explanation.isEmpty() && agentBridge != null) {
            String speech = explanation.length() > 200 ? explanation.substring(0, 200) : explanation;
            agentBridge.tts(speech, 30000);
        }
    }

    private void searchProducts(String query) {
        if (query == null || query.isEmpty()) return;
        List<Product> products = getProducts();
        if (products.isEmpty()) {
            if (agentBridge != null)
                agentBridge.tts("Aun no tengo productos cargados, intenta de nuevo en un momento", 10000);
            return;
        }

        String q = query.toLowerCase();
        List<Product> found = new ArrayList<>();
        for (Product p : products) {
            if (p.getName().toLowerCase().contains(q) ||
                p.getCategory().toLowerCase().contains(q) ||
                p.getBrand().toLowerCase().contains(q)) {
                found.add(p);
            }
        }

        if (resultCallback != null) {
            resultCallback.onProductSearch(query, found);
        }

        if (!found.isEmpty()) {
            if (resultCallback != null) resultCallback.onModeSwitch("retail");
            StringBuilder tts = new StringBuilder();
            if (found.size() == 1) {
                Product product = found.get(0);
                tts.append(product.getName());
                if (product.getDiscount() > 0) {
                    tts.append(" con ").append(product.getDiscount()).append("% de descuento, ");
                    tts.append("precio final ").append(String.format("%.2f", product.getDiscountedPrice())).append(" pesos");
                } else {
                    tts.append(" con precio de ").append(String.format("%.2f", product.getPrice())).append(" pesos");
                }
                if (!product.getDescription().isEmpty()) {
                    tts.append(". ").append(product.getDescription());
                }
            } else if (found.size() <= 3) {
                for (int i = 0; i < found.size(); i++) {
                    Product product = found.get(i);
                    if (i > 0) tts.append(", ");
                    tts.append(product.getName());
                    if (product.getDiscount() > 0) {
                        tts.append(" con descuento de ").append(product.getDiscount()).append("%");
                    }
                }
            } else {
                for (int i = 0; i < 3; i++) {
                    if (i > 0) tts.append(", ");
                    tts.append(found.get(i).getName());
                }
                tts.append(" y otros mas");
            }
            tts.append(". Te muestro la lista completa en pantalla.");
            if (agentBridge != null) agentBridge.tts(tts.toString(), 20000);
        } else {
            if (agentBridge != null)
                agentBridge.tts("No encontre productos que coincidan con " + query + ". Podrias ser mas especifico o probar con otro termino?", 10000);
        }
    }

    private void speakProductInfo(Product product) {
        String text = product.getName() + ". ";
        if (product.getDiscount() > 0) {
            text += "Tiene " + product.getDiscount() + " por ciento de descuento. ";
            text += "Precio: " + String.format("%.2f", product.getDiscountedPrice()) + " pesos. ";
        } else {
            text += "Precio: " + String.format("%.2f", product.getPrice()) + " pesos. ";
        }
        if (!product.getDescription().isEmpty()) text += product.getDescription();
        if (agentBridge != null) agentBridge.tts(text, 30000);
        if (resultCallback != null) resultCallback.onProductDetail(product.getId());
    }

    // ==================== Navegacion ====================

    private boolean handleNavigateToLocation(Bundle params) {
        String dest = params != null ? params.getString("destination", "") : "";
        if (dest.isEmpty()) {
            if (agentBridge != null)
                agentBridge.tts("No entendi a donde quieres ir. Dime el nombre de la seccion.", 10000);
            return true;
        }
        Log.d(TAG, "NAVIGATE: dest=" + dest);
        mainHandler.post(() -> navigateToLocation(dest));
        return true;
    }

    private boolean handleStopNavigation() {
        Log.d(TAG, "STOP_NAVIGATION");
        mainHandler.post(() -> {
            stopNavigation();
            if (resultCallback != null) resultCallback.onNavigationEvent("", false);
        });
        if (agentBridge != null) agentBridge.tts("De acuerdo, me detengo.", 10000);
        return true;
    }

    private void navigateToLocation(String destination) {
        if (!robotApiConnected || robotApi == null) {
            if (agentBridge != null)
                agentBridge.tts("No puedo navegar en este momento, el sistema de movimiento no esta conectado.", 10000);
            return;
        }

        if (isNavigating) {
            stopNavigation();
        }

        String matchedPlace = findBestMatchingPlace(destination);
        if (matchedPlace == null) {
            String available = mapPlaces.isEmpty() ? "No hay puntos configurados."
                : "Los puntos disponibles son: " + String.join(", ", mapPlaces);
            if (agentBridge != null)
                agentBridge.tts("No encontre la seccion " + destination + ". " + available, 15000);
            return;
        }

        isNavigating = true;
        stopFaceTracking();

        if (resultCallback != null) resultCallback.onNavigationEvent(matchedPlace, true);
        if (agentBridge != null) agentBridge.tts("Vamos, te llevo a " + matchedPlace, 10000);
        Log.i(TAG, "Starting navigation to: " + matchedPlace);

        try {
            robotApi.startNavigation(navReqId++, matchedPlace, 0.2, 30000,
                new ActionListener() {
                    @Override
                    public void onResult(int status, String response) {
                        isNavigating = false;
                        if (resultCallback != null) resultCallback.onNavigationEvent(matchedPlace, false);
                        if (status == Definition.RESULT_OK && "true".equals(response)) {
                            Log.i(TAG, "Navigation SUCCESS to " + matchedPlace);
                            if (agentBridge != null)
                                agentBridge.tts("Llegamos a " + matchedPlace + ". Aqui esta la seccion que buscabas.", 15000);
                        } else {
                            Log.w(TAG, "Navigation ended: status=" + status + " response=" + response);
                            if (agentBridge != null)
                                agentBridge.tts("No pude llegar a " + matchedPlace + ", algo salio mal.", 10000);
                        }
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
                        if (agentBridge != null) agentBridge.tts(errorMsg, 10000);
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
            if (agentBridge != null) agentBridge.tts("Error al intentar navegar.", 10000);
            mainHandler.postDelayed(() -> startFaceTracking(), 2000);
        }
    }

    public void stopNavigation() {
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

    // ==================== LEDs ====================

    private boolean handleSetLedColor(Bundle params) {
        String color = params != null ? params.getString("color", "") : "";
        Log.d(TAG, "SET_LED_COLOR: color=" + color);
        if (color.isEmpty()) {
            if (agentBridge != null)
                agentBridge.tts("No entendi que color quieres. Puedes decir rojo, azul, verde, o cualquier color.", 10000);
            return true;
        }

        mainHandler.post(() -> {
            try {
                String hexColor = parseColorName(color);
                LedController.getInstance().setSolidColor(Integer.parseInt(hexColor.substring(1), 16));
                if (agentBridge != null)
                    agentBridge.tts("Perfecto, he cambiado las luces a color " + color, 8000);
                if (resultCallback != null) resultCallback.onLedEvent("color_changed", hexColor);
            } catch (Exception e) {
                Log.e(TAG, "Error changing LED color", e);
                if (agentBridge != null)
                    agentBridge.tts("Lo siento, no pude cambiar el color de las luces.", 8000);
            }
        });
        return true;
    }

    private boolean handleStartLedEffect(Bundle params) {
        String effect = params != null ? params.getString("effect", "BREATHING") : "BREATHING";
        String color = params != null ? params.getString("color", "#E4027C") : "#E4027C";
        Log.d(TAG, "START_LED_EFFECT: effect=" + effect + " color=" + color);

        mainHandler.post(() -> {
            try {
                String hexColor = parseColorName(color);
                LedController.LedEffect ledEffect = LedController.LedEffect.valueOf(effect.toUpperCase());
                LedController.getInstance().startEffect(ledEffect, Integer.parseInt(hexColor.substring(1), 16));
                String effectName = getEffectDisplayName(effect);
                if (agentBridge != null)
                    agentBridge.tts("Iniciando efecto " + effectName + " en las luces", 8000);
                if (resultCallback != null) resultCallback.onLedEvent("effect_started", effect);
            } catch (Exception e) {
                Log.e(TAG, "Error starting LED effect", e);
                if (agentBridge != null)
                    agentBridge.tts("Lo siento, no pude iniciar el efecto en las luces.", 8000);
            }
        });
        return true;
    }

    private boolean handleRestoreLedDefault() {
        Log.d(TAG, "RESTORE_LED_DEFAULT");
        mainHandler.post(() -> {
            LedController.getInstance().restoreDefault();
            if (agentBridge != null)
                agentBridge.tts("He restaurado las luces al color por defecto", 8000);
            if (resultCallback != null) resultCallback.onLedEvent("restored_default", "#E4027C");
        });
        return true;
    }

    private boolean handleSwitchMode(Bundle params) {
        String mode = params != null ? params.getString("mode", "") : "";
        Log.d(TAG, "SWITCH_MODE: mode=" + mode);
        
        if (mode.isEmpty()) {
            if (agentBridge != null)
                agentBridge.tts("No entendi a que modo quieres cambiar. Puedes decir retail, exhibition o idle.", 10000);
            return true;
        }

        mainHandler.post(() -> {
            if (resultCallback != null) {
                resultCallback.onModeSwitch(mode);
            }
            
            String modeName = mode.toLowerCase();
            String ttsResponse = "";
            switch (modeName) {
                case "retail":
                    ttsResponse = "Modo retail activado. Estoy listo para ayudar a los clientes.";
                    break;
                case "exhibition":
                    ttsResponse = "Modo exhibicion activado. Preparado para demostraciones.";
                    break;
                case "idle":
                    ttsResponse = "Modo reposo activado.";
                    break;
                default:
                    ttsResponse = "Cambiando a modo " + mode;
                    break;
            }
            
            if (agentBridge != null) {
                agentBridge.tts(ttsResponse, 10000);
            }
        });
        return true;
    }

    private boolean handleGoToMenu() {
        Log.d(TAG, "GO_TO_MENU");
        
        mainHandler.post(() -> {
            if (resultCallback != null) {
                resultCallback.onModeSwitch("menu");
            }
            
            if (agentBridge != null) {
                agentBridge.tts("Regresando al menu principal.", 8000);
            }
        });
        return true;
    }

    private boolean handleEnterRetailMode() {
        Log.d(TAG, "ENTER_RETAIL_MODE");
        
        mainHandler.post(() -> {
            if (resultCallback != null) {
                resultCallback.onModeSwitch("retail");
            }
            
            if (agentBridge != null) {
                agentBridge.tts("Entrando en modo retail. Te muestro todos los productos disponibles.", 10000);
            }
        });
        return true;
    }

    private boolean handleEnterExhibitionMode() {
        Log.d(TAG, "ENTER_EXHIBITION_MODE");
        
        mainHandler.post(() -> {
            if (resultCallback != null) {
                resultCallback.onModeSwitch("exhibition");
            }
            
            if (agentBridge != null) {
                agentBridge.tts("Entrando en modo promocion. Te muestro las ofertas y promociones especiales.", 10000);
            }
        });
        return true;
    }

    // ==================== Face Tracking ====================

    public void startFaceTracking() {
        try {
            robotApi = RobotApi.getInstance();
            robotApi.connectServer(context, new ApiListener() {
                @Override
                public void handleApiDisabled() {
                    Log.w(TAG, "RobotApi disabled");
                }

                @Override
                public void handleApiConnected() {
                    robotApiConnected = true;
                    faceTrackActive = true;
                    Log.d(TAG, "RobotApi connected - starting person detection");
                    registerPersonDetection();
                    tryFollowVisiblePerson();
                    loadPlaceList();

                    // Wire chassis controller on the TourExecutor singleton so tours
                    // started from both voice AND the HTTP panel can stop/start face tracking
                    TourExecutor.getInstance().setChassisController(new TourExecutor.ChassisController() {
                        @Override
                        public void stopFaceTracking() {
                            RobotActionHandler.this.stopFaceTracking();
                        }
                        @Override
                        public void startFaceTracking() {
                            RobotActionHandler.this.startFaceTracking();
                        }
                    });
                    mainHandler.post(() -> LedController.getInstance().restoreDefault());
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
            // Try complete face list first (best quality), then all faces within 3m
            List<Person> faces = PersonApi.getInstance().getCompleteFaceList();
            if (faces == null || faces.isEmpty()) {
                faces = PersonApi.getInstance().getAllFaceList(3);
            }
            if (faces != null && !faces.isEmpty()) {
                // Pick the closest person for more natural tracking
                Person best = faces.get(0);
                for (Person p : faces) {
                    if (p.getDistance() > 0 && p.getDistance() < best.getDistance()) {
                        best = p;
                    }
                }
                int personId = best.getId();
                if (personId >= 0) {
                    Log.d(TAG, "Person found (id=" + personId + ", dist=" + best.getDistance() + "m), starting focus follow");
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
                faceTrackReqId, personId, 8000L, 5.0f, true,
                new ActionListener() {
                    @Override
                    public void onResult(int reqId, String result) {
                        Log.d(TAG, "FocusFollow ended: " + result);
                        isFollowing = false;
                        currentFollowId = -1;
                        if (faceTrackActive) {
                            mainHandler.postDelayed(() -> tryFollowVisiblePerson(), 500);
                        }
                    }

                    @Override
                    public void onError(int reqId, String error) {
                        Log.w(TAG, "FocusFollow error: " + error);
                        isFollowing = false;
                        currentFollowId = -1;
                        // Retry finding a new person after error
                        if (faceTrackActive) {
                            mainHandler.postDelayed(() -> tryFollowVisiblePerson(), 500);
                        }
                    }

                    @Override
                    public void onStatusUpdate(int reqId, String status) {
                        Log.d(TAG, "FocusFollow status: " + status);
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(status);
                            String statusStr = json.optString("status", "");
                            if ("guest_lost".equalsIgnoreCase(statusStr)
                                || String.valueOf(Definition.STATUS_GUEST_LOST).equals(statusStr)) {
                                Log.i(TAG, "FocusFollow: target lost, stopping and retrying");
                                isFollowing = false;
                                currentFollowId = -1;
                                try { robotApi.stopFocusFollow(faceTrackReqId); } catch (Exception ignored) {}
                                if (faceTrackActive) {
                                    mainHandler.postDelayed(() -> tryFollowVisiblePerson(), 300);
                                }
                            }
                        } catch (Exception e) {
                            // Status might be plain text, check for known lost patterns
                            if (status != null && (status.contains("lost") || status.contains("LOST"))) {
                                Log.i(TAG, "FocusFollow: target lost (text), stopping and retrying");
                                isFollowing = false;
                                currentFollowId = -1;
                                try { robotApi.stopFocusFollow(faceTrackReqId); } catch (Exception ignored) {}
                                if (faceTrackActive) {
                                    mainHandler.postDelayed(() -> tryFollowVisiblePerson(), 300);
                                }
                            }
                        }
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

    public void stopFaceTracking() {
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

    // ==================== Context Upload ====================

    public void uploadCatalogInfoToAgent() {
        StringBuilder info = new StringBuilder();

        if (!mapPlaces.isEmpty()) {
            info.append("UBICACIONES DISPONIBLES EN LA TIENDA (el robot puede navegar a estos puntos):\n");
            for (String place : mapPlaces) {
                info.append("- ").append(place).append("\n");
            }
            info.append("\nCuando el usuario pida ir a una seccion, usa la accion NAVIGATE_TO_LOCATION con el nombre exacto del punto.\n\n");
        }

        // Leer productos de Room DB (fuente primaria, gestionada por el panel)
        List<ProductEntity> dbProducts = getProductsFromDb();
        if (!dbProducts.isEmpty()) {
            info.append("CATALOGO DE PRODUCTOS DISPONIBLES - ").append(dbProducts.size()).append(" productos:\n");
            int max = Math.min(dbProducts.size(), 30);
            for (int i = 0; i < max; i++) {
                ProductEntity p = dbProducts.get(i);
                info.append("- ").append(p.getName());
                if (p.getPrice() > 0) {
                    info.append(" ($").append(String.format("%.0f", p.getPrice())).append(")");
                }
                info.append(" [").append(p.getCategory()).append("]");
                if (p.getBrand() != null && !p.getBrand().isEmpty()) {
                    info.append(" marca: ").append(p.getBrand());
                }
                if (!p.getInStock()) {
                    info.append(" (AGOTADO)");
                }
                info.append("\n");
            }
            if (dbProducts.size() > max) {
                info.append("... y ").append(dbProducts.size() - max).append(" productos mas disponibles.\n");
            }
            info.append("\nCuando el usuario pregunte por productos, usa la accion SEARCH_PRODUCTS para buscar en la base de datos.\n");
        } else {
            // Fallback: productos de RobbieConfig (API remota)
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
        }

        if (info.length() > 0 && agentBridge != null) {
            agentBridge.uploadInterfaceInfo(info.toString());
            Log.d(TAG, "Interface info uploaded: " + mapPlaces.size() + " places, " + dbProducts.size() + " db products");
        }
    }

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
                            if (status == 0) {
                                mapPlaces.add(name);
                            }
                        }
                        Log.i(TAG, "Map places loaded: " + mapPlaces.size() + " -> " + mapPlaces);
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

    // ==================== Utilidades ====================

    private List<ProductEntity> getProductsFromDb() {
        try {
            RobbieDatabase db = RobbieDatabase.getInstance(context);
            List<ProductEntity> products = db.productDao().getAllProductsSync();
            return products != null ? products : new ArrayList<>();
        } catch (Exception e) {
            Log.w(TAG, "Error getting products from DB", e);
        }
        return new ArrayList<>();
    }

    private List<Product> getProducts() {
        try {
            RobotApp app = RobotApp.getInstance();
            if (app == null) return new ArrayList<>();
            RobbieConfig config = app.getRobbieConfig();
            if (config != null && config.getProducts() != null) {
                return config.getProducts();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting products from config", e);
        }
        return new ArrayList<>();
    }

    private String findBestMatchingPlace(String destination) {
        if (mapPlaces.isEmpty()) return null;
        String destLower = destination.toLowerCase().trim();

        for (String place : mapPlaces) {
            if (place.equalsIgnoreCase(destLower)) return place;
        }
        for (String place : mapPlaces) {
            String placeLower = place.toLowerCase();
            if (placeLower.contains(destLower) || destLower.contains(placeLower)) return place;
        }
        String[] destWords = destLower.split("\\s+");
        for (String place : mapPlaces) {
            String placeLower = place.toLowerCase();
            for (String word : destWords) {
                if (word.length() >= 3 && placeLower.contains(word)) return place;
            }
        }
        return null;
    }

    public static String parseColorName(String colorInput) {
        if (colorInput == null || colorInput.isEmpty()) return "#E4027C";
        String color = colorInput.toLowerCase().trim();
        if (color.startsWith("#")) {
            if (color.length() == 7) return color.toUpperCase();
            return "#E4027C";
        }
        switch (color) {
            case "rojo": case "red": return "#FF0000";
            case "verde": case "green": return "#00FF00";
            case "azul": case "blue": return "#0000FF";
            case "amarillo": case "yellow": return "#FFFF00";
            case "naranja": case "orange": return "#FFA500";
            case "morado": case "purple": case "violeta": return "#800080";
            case "rosa": case "pink": return "#FFC0CB";
            case "blanco": case "white": return "#FFFFFF";
            case "negro": case "black": return "#000000";
            case "cyan": case "cian": return "#00FFFF";
            case "magenta": return "#FF00FF";
            case "ikalp": case "default": return "#E4027C";
            default:
                try {
                    if (color.length() == 6) {
                        Integer.parseInt(color, 16);
                        return "#" + color.toUpperCase();
                    }
                } catch (NumberFormatException ignored) {}
                return "#E4027C";
        }
    }

    public static String getEffectDisplayName(String effect) {
        if (effect == null) return "desconocido";
        switch (effect.toUpperCase()) {
            case "BREATHING": return "respiracion";
            case "BLINK": return "parpadeo";
            case "RAINBOW": return "arcoiris";
            case "PULSE": return "pulso";
            case "WAVE": return "onda";
            case "SOLID": return "solido";
            default: return effect.toLowerCase();
        }
    }

    public void destroy() {
        stopFaceTracking();
        if (robotApi != null) robotApi.disconnectApi();
    }

    public boolean isNavigating() {
        return isNavigating;
    }

    public List<String> getMapPlaces() {
        return mapPlaces;
    }

    // ==================== Tour ====================

    private boolean handleStartTour(Bundle params) {
        String routeName = params != null ? params.getString("routeName", "") : "";
        Log.d(TAG, "START_TOUR: routeName=" + routeName);

        mainHandler.post(() -> {
            try {
                TourExecutor executor = TourExecutor.getInstance();
                if (executor.isRunning()) {
                    if (agentBridge != null)
                        agentBridge.tts("Ya hay un tour en curso. Si quieres, puedo detenerlo primero.", 10000);
                    return;
                }

                // Wire chassis controller so TourExecutor can stop/start face tracking
                executor.setChassisController(new TourExecutor.ChassisController() {
                    @Override
                    public void stopFaceTracking() {
                        RobotActionHandler.this.stopFaceTracking();
                    }
                    @Override
                    public void startFaceTracking() {
                        RobotActionHandler.this.startFaceTracking();
                    }
                });

                // Load published routes from DB
                RobbieDatabase db = RobbieDatabase.getInstance(context);
                com.robbie.data.local.entity.ConfigEntity entity = db.configDao().getConfig("tour_routes");
                if (entity == null || entity.getValue() == null) {
                    if (agentBridge != null)
                        agentBridge.tts("No hay tours configurados. Puedes crear uno desde el panel de administracion.", 10000);
                    return;
                }

                org.json.JSONArray routesArray = new org.json.JSONArray(entity.getValue());
                java.util.Map<String, Object> selectedRoute = null;

                for (int i = 0; i < routesArray.length(); i++) {
                    org.json.JSONObject routeJson = routesArray.getJSONObject(i);
                    boolean published = routeJson.optBoolean("published", false);
                    if (!published) continue;

                    String name = routeJson.optString("name", "");
                    if (!routeName.isEmpty() && name.toLowerCase().contains(routeName.toLowerCase())) {
                        selectedRoute = jsonObjectToMap(routeJson);
                        break;
                    }
                    if (selectedRoute == null) {
                        selectedRoute = jsonObjectToMap(routeJson);
                    }
                }

                if (selectedRoute == null) {
                    if (agentBridge != null)
                        agentBridge.tts("No encontre ningun tour publicado. Crea y publica uno desde el panel.", 10000);
                    return;
                }

                String tourName = (String) selectedRoute.get("name");
                Object stopsObj = selectedRoute.get("stops");
                if (!(stopsObj instanceof java.util.List)) {
                    if (agentBridge != null)
                        agentBridge.tts("El tour " + tourName + " no tiene paradas configuradas.", 10000);
                    return;
                }

                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> stops =
                    (java.util.List<java.util.Map<String, Object>>) stopsObj;

                String routeId = (String) selectedRoute.get("id");
                executor.startTour(routeId, tourName, stops);
                Log.i(TAG, "Tour started via voice: " + tourName);
            } catch (Exception e) {
                Log.e(TAG, "Error starting tour", e);
                if (agentBridge != null)
                    agentBridge.tts("Lo siento, hubo un error al iniciar el tour.", 10000);
            }
        });
        return true;
    }

    private boolean handleStopTour() {
        Log.d(TAG, "STOP_TOUR");
        mainHandler.post(() -> {
            TourExecutor executor = TourExecutor.getInstance();
            if (executor.isRunning()) {
                executor.stopTour();
            } else {
                if (agentBridge != null)
                    agentBridge.tts("No hay ningun tour en curso.", 8000);
            }
        });
        return true;
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> jsonObjectToMap(org.json.JSONObject json) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object val = json.opt(key);
            if (val instanceof org.json.JSONArray) {
                java.util.List<Object> list = new java.util.ArrayList<>();
                org.json.JSONArray arr = (org.json.JSONArray) val;
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (item instanceof org.json.JSONObject) {
                        list.add(jsonObjectToMap((org.json.JSONObject) item));
                    } else {
                        list.add(item);
                    }
                }
                map.put(key, list);
            } else if (val instanceof org.json.JSONObject) {
                map.put(key, jsonObjectToMap((org.json.JSONObject) val));
            } else {
                map.put(key, val);
            }
        }
        return map;
    }
}
