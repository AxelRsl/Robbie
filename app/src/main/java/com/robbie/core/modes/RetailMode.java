package com.robbie.core.modes;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ainirobot.agent.AgentCore;
import com.robbie.core.hardware.LedController;
import com.robbie.core.navigation.NavigationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modo Retail - Interaccion con clientes en tienda.
 *
 * Comportamientos:
 * - Saludo automatico a clientes detectados
 * - Promocion de productos configurados
 * - Patrullaje por ruta definida
 * - Respuestas automaticas a preguntas frecuentes
 * - Analytics de interacciones
 */
public class RetailMode {

    private static final String TAG = "RetailMode";

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean active = false;

    private String greetingMessage = "Hola, bienvenido. Soy Robbie, puedo ayudarte a encontrar lo que necesitas.";
    private List<String> promotedProducts = new ArrayList<>();
    private List<String> patrolRoute = new ArrayList<>();
    private boolean autoGreetEnabled = true;
    private boolean patrolEnabled = false;
    private int interactionCount = 0;
    private long sessionStartTime = 0;

    public RetailMode(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        if (active) return;
        active = true;
        sessionStartTime = System.currentTimeMillis();
        interactionCount = 0;

        Log.i(TAG, "Retail mode started");

        // Saludo inicial
        handler.postDelayed(() -> {
            if (active) {
                try {
                    AgentCore.INSTANCE.tts(greetingMessage, 20000, null);
                } catch (Exception e) {
                    Log.w(TAG, "Could not play greeting", e);
                }
            }
        }, 1000);

        // Iniciar patrullaje si esta configurado
        if (patrolEnabled && !patrolRoute.isEmpty()) {
            handler.postDelayed(() -> {
                if (active) startPatrol();
            }, 5000);
        }
    }

    public void stop() {
        if (!active) return;
        active = false;

        try {
            NavigationManager.getInstance().stopNavigation();
        } catch (Exception e) {
            Log.w(TAG, "Could not stop navigation", e);
        }

        Log.i(TAG, "Retail mode stopped. Interactions: " + interactionCount);
    }

    private void startPatrol() {
        if (!active || patrolRoute.isEmpty()) return;
        try {
            NavigationManager nav = NavigationManager.getInstance();
            nav.startPatrol(patrolRoute);
        } catch (Exception e) {
            Log.w(TAG, "Could not start patrol", e);
        }
    }

    public void onPersonDetected() {
        if (!active || !autoGreetEnabled) return;
        interactionCount++;

        try {
            LedController.getInstance().onPersonDetected();
        } catch (Exception e) {
            Log.w(TAG, "LED error on person detected", e);
        }
    }

    public void recordInteraction() {
        interactionCount++;
    }

    // -- Configuration --

    public void setGreetingMessage(String message) {
        this.greetingMessage = message;
    }

    public String getGreetingMessage() {
        return greetingMessage;
    }

    public void setPromotedProducts(List<String> productIds) {
        this.promotedProducts = productIds != null ? new ArrayList<>(productIds) : new ArrayList<>();
    }

    public List<String> getPromotedProducts() {
        return promotedProducts;
    }

    public void setPatrolRoute(List<String> route) {
        this.patrolRoute = route != null ? new ArrayList<>(route) : new ArrayList<>();
    }

    public List<String> getPatrolRoute() {
        return patrolRoute;
    }

    public void setAutoGreetEnabled(boolean enabled) {
        this.autoGreetEnabled = enabled;
    }

    public boolean isAutoGreetEnabled() {
        return autoGreetEnabled;
    }

    public void setPatrolEnabled(boolean enabled) {
        this.patrolEnabled = enabled;
    }

    public boolean isPatrolEnabled() {
        return patrolEnabled;
    }

    public boolean isActive() {
        return active;
    }

    public int getInteractionCount() {
        return interactionCount;
    }

    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("greetingMessage", greetingMessage);
        config.put("promotedProducts", promotedProducts);
        config.put("patrolRoute", patrolRoute);
        config.put("autoGreetEnabled", autoGreetEnabled);
        config.put("patrolEnabled", patrolEnabled);
        config.put("interactionCount", interactionCount);
        config.put("active", active);
        if (sessionStartTime > 0) {
            config.put("sessionDurationMs", System.currentTimeMillis() - sessionStartTime);
        }
        return config;
    }

    public void applyConfig(Map<String, Object> config) {
        if (config == null) return;

        if (config.containsKey("greetingMessage")) {
            greetingMessage = String.valueOf(config.get("greetingMessage"));
        }
        if (config.containsKey("autoGreetEnabled")) {
            autoGreetEnabled = Boolean.parseBoolean(String.valueOf(config.get("autoGreetEnabled")));
        }
        if (config.containsKey("patrolEnabled")) {
            patrolEnabled = Boolean.parseBoolean(String.valueOf(config.get("patrolEnabled")));
        }
        if (config.containsKey("patrolRoute") && config.get("patrolRoute") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> route = (List<String>) config.get("patrolRoute");
            patrolRoute = new ArrayList<>(route);
        }
        if (config.containsKey("promotedProducts") && config.get("promotedProducts") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> products = (List<String>) config.get("promotedProducts");
            promotedProducts = new ArrayList<>(products);
        }
    }
}
