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
 * Modo Exhibicion - Demostraciones automaticas y presentaciones.
 *
 * Comportamientos:
 * - Demostraciones programadas por horario
 * - Rutinas de entretenimiento (LED + movimiento + speech)
 * - Interaccion educativa con visitantes
 * - Ciclo automatico de presentaciones
 */
public class ExhibitionMode {

    private static final String TAG = "ExhibitionMode";

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean active = false;
    private boolean demoRunning = false;
    private int currentDemoIndex = 0;
    private Runnable demoRunnable;

    private List<DemoStep> demonstrations = new ArrayList<>();
    private String interactionLevel = "high";
    private boolean autoLoop = true;
    private long demoIntervalMs = 30000;

    public static class DemoStep {
        public String speech;
        public String ledEffect;
        public int ledColor;
        public String navigationPoint;
        public long durationMs;

        public DemoStep(String speech, String ledEffect, int ledColor, long durationMs) {
            this.speech = speech;
            this.ledEffect = ledEffect;
            this.ledColor = ledColor;
            this.durationMs = durationMs;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("speech", speech);
            map.put("ledEffect", ledEffect);
            map.put("ledColor", String.format("#%06X", ledColor));
            map.put("navigationPoint", navigationPoint);
            map.put("durationMs", durationMs);
            return map;
        }
    }

    public ExhibitionMode(Context context) {
        this.context = context.getApplicationContext();
        initializeDefaultDemos();
    }

    private void initializeDefaultDemos() {
        demonstrations.clear();
        demonstrations.add(new DemoStep(
            "Hola a todos, soy Robbie, un robot de servicio inteligente. Puedo ayudarles con informacion, navegacion y mucho mas.",
            "RAINBOW", 0xE4027C, 8000));
        demonstrations.add(new DemoStep(
            "Puedo reconocer personas, seguir rostros y navegar de forma autonoma por cualquier espacio.",
            "BREATHING", 0x0066FF, 6000));
        demonstrations.add(new DemoStep(
            "Mis sensores me permiten detectar obstaculos, medir distancias y garantizar una navegacion segura.",
            "PULSE", 0x00BFA5, 6000));
        demonstrations.add(new DemoStep(
            "Tambien puedo cambiar mis luces para comunicar diferentes estados y emociones.",
            "WAVE", 0x6200EA, 5000));
    }

    public void start() {
        if (active) return;
        active = true;
        currentDemoIndex = 0;

        Log.i(TAG, "Exhibition mode started with " + demonstrations.size() + " demo steps");

        handler.postDelayed(() -> {
            if (active) startDemoSequence();
        }, 2000);
    }

    public void stop() {
        active = false;
        demoRunning = false;
        if (demoRunnable != null) {
            handler.removeCallbacks(demoRunnable);
            demoRunnable = null;
        }

        try {
            LedController.getInstance().restoreDefault();
        } catch (Exception e) {
            Log.w(TAG, "Could not restore LED", e);
        }

        Log.i(TAG, "Exhibition mode stopped");
    }

    private void startDemoSequence() {
        if (!active || demonstrations.isEmpty()) return;
        demoRunning = true;
        executeDemoStep(currentDemoIndex);
    }

    private void executeDemoStep(int index) {
        if (!active || index >= demonstrations.size()) {
            demoRunning = false;
            if (autoLoop && active) {
                currentDemoIndex = 0;
                handler.postDelayed(() -> {
                    if (active) startDemoSequence();
                }, demoIntervalMs);
            }
            return;
        }

        DemoStep step = demonstrations.get(index);
        Log.d(TAG, "Demo step " + (index + 1) + "/" + demonstrations.size() + ": " + step.speech);

        // Aplicar efecto LED
        try {
            LedController led = LedController.getInstance();
            LedController.LedEffect effect = LedController.LedEffect.valueOf(step.ledEffect);
            led.startEffect(effect, step.ledColor);
        } catch (Exception e) {
            Log.w(TAG, "Could not apply LED effect", e);
        }

        // Navegar si hay punto definido
        if (step.navigationPoint != null && !step.navigationPoint.isEmpty()) {
            try {
                NavigationManager.getInstance().navigateTo(step.navigationPoint);
            } catch (Exception e) {
                Log.w(TAG, "Could not navigate", e);
            }
        }

        // Hablar
        if (step.speech != null && !step.speech.isEmpty()) {
            try {
                AgentCore.INSTANCE.tts(step.speech, (int) step.durationMs, null);
            } catch (Exception e) {
                Log.w(TAG, "Could not play TTS", e);
            }
        }

        // Programar siguiente paso
        currentDemoIndex = index + 1;
        demoRunnable = () -> {
            if (active) executeDemoStep(currentDemoIndex);
        };
        handler.postDelayed(demoRunnable, step.durationMs);
    }

    // -- Configuration --

    public void setDemonstrations(List<DemoStep> demos) {
        this.demonstrations = demos != null ? new ArrayList<>(demos) : new ArrayList<>();
    }

    public List<DemoStep> getDemonstrations() {
        return demonstrations;
    }

    public void setInteractionLevel(String level) {
        this.interactionLevel = level;
    }

    public String getInteractionLevel() {
        return interactionLevel;
    }

    public void setAutoLoop(boolean loop) {
        this.autoLoop = loop;
    }

    public boolean isAutoLoop() {
        return autoLoop;
    }

    public void setDemoIntervalMs(long interval) {
        this.demoIntervalMs = Math.max(5000, interval);
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDemoRunning() {
        return demoRunning;
    }

    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("interactionLevel", interactionLevel);
        config.put("autoLoop", autoLoop);
        config.put("demoIntervalMs", demoIntervalMs);
        config.put("active", active);
        config.put("demoRunning", demoRunning);
        config.put("currentStep", currentDemoIndex);
        config.put("totalSteps", demonstrations.size());

        List<Map<String, Object>> demoList = new ArrayList<>();
        for (DemoStep step : demonstrations) {
            demoList.add(step.toMap());
        }
        config.put("demonstrations", demoList);
        return config;
    }
}
