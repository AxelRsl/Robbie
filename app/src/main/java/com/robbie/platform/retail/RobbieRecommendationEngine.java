package com.robbie.platform.retail;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ainirobot.agent.AgentCore;
import com.ainirobot.agent.base.llm.LLMConfig;
import com.ainirobot.agent.base.llm.LLMMessage;
import com.robbie.data.local.entity.ProductEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Motor de recomendaciones AI para ROBBIE usando AgentCore LLM.
 * LLM integrado de AgentOS.
 */
public class RobbieRecommendationEngine {

    private static final String TAG = "RobbieRecommendation";
    private static final long LLM_RESPONSE_TIMEOUT = 30000; // 30 segundos
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private RecommendationCallback currentCallback;

    public interface RecommendationCallback {
        void onResult(String explanation, List<String> recommendedProductIds);
        void onError(String error);
    }
    

    /**
     * Genera recomendaciones de productos usando AgentCore.query() del SDK.
     * Envía el prompt al LLM y usa timeout con fallback inteligente a simulación
     * si no se recibe respuesta en el tiempo esperado.
     * 
     * @param allProducts Lista completa de productos disponibles
     * @param userNeed Necesidad expresada por el usuario
     * @param restriction Restricciones dietéticas o preferencias (puede ser null)
     * @param callback Callback para recibir el resultado
     */
    public void recommend(List<ProductEntity> allProducts,
                          String userNeed,
                          String restriction,
                          RecommendationCallback callback) {

        try {
            // LOG: Input parameters
            Log.i(TAG, "=== LLM RECOMMENDATION REQUEST ===");
            Log.i(TAG, "User need: " + userNeed);
            Log.i(TAG, "Restriction: " + (restriction != null ? restriction : "none"));
            Log.i(TAG, "Total products in catalog: " + allProducts.size());
            
            // Guardar callback
            this.currentCallback = callback;
            
            // Construir catálogo optimizado para el LLM
            StringBuilder catalog = new StringBuilder();
            int maxProducts = Math.min(allProducts.size(), 15); // Limitar para evitar prompt muy largo
            
            for (int i = 0; i < maxProducts; i++) {
                ProductEntity p = allProducts.get(i);
                catalog.append("[").append(p.getId()).append("] ")
                       .append(p.getName()).append(" - ")
                       .append(p.getCategory()).append(" - ")
                       .append(p.getBrand()).append(" - ")
                       .append("$").append(String.format("%.2f", p.getPrice()));
                
                if (p.getDiscount() > 0) {
                    catalog.append(" (").append(p.getDiscount()).append("% desc)");
                }
                
                catalog.append(" - ").append(p.getInStock() ? "Disponible" : "Agotado");
                catalog.append("\n");
            }

            // Construir prompt optimizado para el LLM
            String llmPrompt = "Eres un asesor experto de productos. Recomienda máximo 3 productos del catálogo para: " + userNeed;
            
            if (restriction != null && !restriction.isEmpty()) {
                llmPrompt += ". Restricción: " + restriction;
            }
            
            llmPrompt += "\n\nCATÁLOGO:\n" + catalog.toString() + 
                "\n\nRESPONDE en español con una explicación breve y al final incluye: PRODUCT_IDS: [id1, id2, id3]";
            
            // LOG: Query being sent to LLM
            Log.i(TAG, "LLM Query length: " + llmPrompt.length() + " characters");
            Log.d(TAG, "LLM Query preview: " + llmPrompt.substring(0, Math.min(300, llmPrompt.length())) + "...");

            // Usar la API LLM real del SDK siguiendo el patrón de AgentSDKSample
            Log.i(TAG, "Using AgentCore.llm() for real LLM recommendation");
            
            // Construir mensajes estructurados para el LLM
            List<LLMMessage> messages = new ArrayList<>();
            
            // Mensaje del sistema con contexto del asesor
            messages.add(new LLMMessage(
                com.ainirobot.agent.base.llm.Role.SYSTEM,
                "Eres un asesor experto de productos de suplementos y nutrición. " +
                "Analiza las necesidades del usuario y recomienda máximo 3 productos del catálogo. " +
                "Responde en español con una explicación breve y al final incluye: PRODUCT_IDS: [id1, id2, id3]"
            ));
            
            // Mensaje del usuario con el prompt completo
            messages.add(new LLMMessage(
                com.ainirobot.agent.base.llm.Role.USER,
                llmPrompt
            ));
            
            // Configuración del LLM
            LLMConfig config = new LLMConfig(
                0.7f,  // temperature - balance entre creatividad y consistencia
                200,   // maxTokens - suficiente para explicación + IDs
                10,    // timeout en segundos
                false, // fileSearch - no necesario
                null   // businessInfo
            );
            
            // Llamar al LLM real (sin streaming para obtener respuesta completa)
            AgentCore.INSTANCE.llm(messages, config, LLM_RESPONSE_TIMEOUT, false, null);
            
            // Configurar timeout para fallback automático a simulación
            mainHandler.postDelayed(() -> {
                Log.w(TAG, "LLM response timeout after " + LLM_RESPONSE_TIMEOUT + "ms, falling back to simulation");
                fallbackToSimulation(allProducts, userNeed, restriction);
            }, LLM_RESPONSE_TIMEOUT);

        } catch (Exception e) {
            Log.e(TAG, "Error setting up LLM recommendation", e);
            callback.onError("Error configurando recomendación: " + e.getMessage());
        }
    }
    
    /**
     * Simulación de recomendación basada en palabras clave hasta que tengamos
     * acceso completo a las APIs LLM del SDK.
     */
    private List<String> simulateRecommendation(List<ProductEntity> allProducts, 
                                               String userNeed, 
                                               String restriction) {
        List<String> recommended = new ArrayList<>();
        String needLower = userNeed.toLowerCase();
        
        // Buscar productos que coincidan con la necesidad
        for (ProductEntity product : allProducts) {
            if (recommended.size() >= 3) break;
            
            String productText = (product.getName() + " " + product.getCategory() + 
                                " " + product.getDescription() + " " + product.getBrand()).toLowerCase();
            
            // Verificar si el producto coincide con la necesidad
            if (productText.contains(needLower) || 
                needLower.contains(product.getCategory().toLowerCase()) ||
                containsKeywords(productText, needLower)) {
                
                // Verificar restricciones si existen
                if (restriction == null || restriction.isEmpty() || 
                    !violatesRestriction(product, restriction)) {
                    recommended.add(product.getId());
                }
            }
        }
        
        // Si no encontramos suficientes, agregar productos populares de categorías relacionadas
        if (recommended.size() < 3) {
            for (ProductEntity product : allProducts) {
                if (recommended.size() >= 3) break;
                if (!recommended.contains(product.getId()) && product.getInStock()) {
                    recommended.add(product.getId());
                }
            }
        }
        
        return recommended;
    }
    
    private boolean containsKeywords(String productText, String need) {
        String[] keywords = need.split("\\s+");
        for (String keyword : keywords) {
            if (keyword.length() > 2 && productText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean violatesRestriction(ProductEntity product, String restriction) {
        String restrictionLower = restriction.toLowerCase();
        String productInfo = (product.getDescription() + " " + product.getIngredients()).toLowerCase();
        
        if (restrictionLower.contains("vegano") && productInfo.contains("animal")) return true;
        if (restrictionLower.contains("sin lactosa") && productInfo.contains("lactosa")) return true;
        if (restrictionLower.contains("sin gluten") && productInfo.contains("gluten")) return true;
        
        return false;
    }

    
    /**
     * Procesa la respuesta del LLM y extrae las recomendaciones.
     */
    private void processLLMResponse(String response) {
        try {
            Log.i(TAG, "Processing LLM response: " + response);
            
            List<String> ids = parseProductIds(response);
            String explanation = response.replaceAll("(?i)PRODUCT_IDS:.*", "").trim();
            
            if (explanation.isEmpty()) {
                explanation = "Basándome en tu consulta, te recomiendo estos productos.";
            }
            
            Log.i(TAG, "LLM recommended " + ids.size() + " products: " + ids);
            
            if (currentCallback != null) {
                currentCallback.onResult(explanation, ids);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing LLM response", e);
            if (currentCallback != null) {
                currentCallback.onError("Error procesando respuesta del LLM: " + e.getMessage());
            }
        } finally {
            currentCallback = null;
        }
    }
    
    /**
     * Fallback a simulación si el LLM no responde o falla.
     */
    private void fallbackToSimulation(List<ProductEntity> allProducts, String userNeed, String restriction) {
        try {
            Log.i(TAG, "Using fallback simulation for recommendation");
            
            List<String> recommendedIds = simulateRecommendation(allProducts, userNeed, restriction);
            String explanation = "Basándome en tu necesidad de " + userNeed + 
                ", te recomiendo estos productos que mejor se adaptan a lo que buscas.";
            
            if (currentCallback != null) {
                currentCallback.onResult(explanation, recommendedIds);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in fallback simulation", e);
            if (currentCallback != null) {
                currentCallback.onError("Error en recomendación de respaldo: " + e.getMessage());
            }
        } finally {
            currentCallback = null;
        }
    }
    
    /**
     * Método público para procesar respuestas del LLM capturadas externamente.
     * Útil cuando se implementa un mecanismo de captura personalizado.
     * 
     * @param response Respuesta del LLM
     */
    public void processExternalLLMResponse(String response) {
        if (currentCallback != null) {
            processLLMResponse(response);
        }
    }

    /**
     * Parsea los IDs de productos de la respuesta del LLM.
     * Busca el patrón "PRODUCT_IDS: [id1, id2, id3]"
     */
    private List<String> parseProductIds(String content) {
        List<String> ids = new ArrayList<>();
        try {
            int idx = content.toUpperCase().indexOf("PRODUCT_IDS:");
            if (idx >= 0) {
                String tail = content.substring(idx + 12).trim();
                tail = tail.replace("[", "").replace("]", "").trim();
                String[] parts = tail.split(",");
                for (String part : parts) {
                    String id = part.trim();
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing product IDs from: " + content, e);
        }
        return ids;
    }
}
