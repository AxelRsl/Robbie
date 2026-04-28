package com.robbie.core.hardware;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.listener.CommandListener;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador de LEDs del robot OrionStar.
 *
 * Gestiona:
 * - Color solido por zona (cabeza, claviculas, base)
 * - Efectos predefinidos (respiracion, parpadeo, arcoiris, pulso)
 * - Sincronizacion con eventos (ASR escuchando, TTS hablando, persona detectada)
 * - Paleta Ikalp por defecto (#E4027C)
 */
public class LedController {

    private static final String TAG = "LedController";
    private static volatile LedController sInstance;

// private static final int IKALP_PRIMARY = 0xE4027C;
    private static final int IKALP_PRIMARY = 0xFFFF00; // Color por defecto (Amarillo)
    private static final int COLOR_LISTENING = 0xE4027C;
    private static final int COLOR_SPEAKING = 0x00BFA5;
    private static final int COLOR_PERSON_DETECTED = 0x4CAF50;
    private static final int COLOR_ERROR = 0xF44336;
    private static final int COLOR_IDLE = 0x455A64;

    // Constantes de efectos LED según documentación OrionStar
    public static final class LedEffects {
        public static final int ZCB2UARTLED_GREENBREATH = 0xDE10;  // Green breathing effect
        public static final int ZCB2UARTLED_BLUEBREATH = 0xDE11;   // Blue breathing effect
        public static final int ZCB2UARTLED_ORANGEBREATH = 0xDE12; // Orange breathing effect
        public static final int ZCB2UARTLED_YELLOWBREATH = 0xDE13; // Yellow breathing effect
        public static final int ZCB2UARTLED_BLUENORMAL = 0xDE14;   // Blue normal effect
        public static final int ZCB2UARTLED_REDNORMAL = 0xDE15;    // Red normal effect
        public static final int ZCB2UARTLED_ORANGENORMAL = 0xDE16; // Orange normal effect
        public static final int ZCB2UARTLED_YELLOWNORMAL = 0xDE17; // Yellow normal effect
        public static final int ZCB2UARTLED_GREENNORMAL = 0xDE18;  // Green normal effect
        public static final int ZCB2UARTLED_TURNRIGHT = 0xDE19;    // Right turn effect
        public static final int ZCB2UARTLED_TURNLEFT = 0xDE20;     // Left turn effect
        public static final int ZCB2UARTLED_REGFLASH = 0xDE21;     // Red flashing effect
        public static final int ZCB2UARTLED_YELLOWFLASH = 0xDE22;  // Yellow flashing effect
        public static final int ZCB2UARTLED_ALLOFF = 0xDE00;       // Turn off all ZCB effects
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int requestId = 3001;
    private int currentColor = IKALP_PRIMARY;
    private LedEffect currentEffect = LedEffect.SOLID;
    private boolean effectRunning = false;
    private int brightness = 80;
    private Runnable effectRunnable;

    public enum LedZone {
        HEAD,
        CLAVICLE,
        BOTTOM,
        ALL
    }

    public enum LedEffect {
        SOLID,
        BREATHING,
        BLINK,
        RAINBOW,
        PULSE,
        WAVE
    }

    public interface LedEventListener {
        void onColorChanged(int color, LedZone zone);
        void onEffectChanged(LedEffect effect);
    }

    private final List<LedEventListener> listeners = new ArrayList<>();

    private LedController() {
    }

    public static LedController getInstance() {
        if (sInstance == null) {
            synchronized (LedController.class) {
                if (sInstance == null) {
                    sInstance = new LedController();
                }
            }
        }
        return sInstance;
    }

    /**
     * Establece un color solido en todas las zonas.
     */
    public void setSolidColor(int color) {
        stopEffect();
        currentColor = color;
        currentEffect = LedEffect.SOLID;
        applyColor(color, LedZone.ALL);
    }

    /**
     * Establece un color solido en una zona especifica.
     */
    public void setSolidColor(int color, LedZone zone) {
        currentColor = color;
        applyColor(color, zone);
    }

    /**
     * Inicia un efecto LED.
     */
    public void startEffect(LedEffect effect, int color) {
        stopEffect();
        currentEffect = effect;
        currentColor = color;
        effectRunning = true;

        switch (effect) {
            case BREATHING:
                startBreathingEffect(color);
                break;
            case BLINK:
                startBlinkEffect(color);
                break;
            case RAINBOW:
                startRainbowEffect();
                break;
            case PULSE:
                startPulseEffect(color);
                break;
            case WAVE:
                startWaveEffect(color);
                break;
            case SOLID:
            default:
                applyColor(color, LedZone.ALL);
                break;
        }

        notifyEffectChanged(effect);
        Log.d(TAG, "Effect started: " + effect.name() + " color=0x" + Integer.toHexString(color));
    }

    /**
     * Detiene el efecto actual y vuelve a color por defecto.
     */
    public void stopEffect() {
        effectRunning = false;
        if (effectRunnable != null) {
            handler.removeCallbacks(effectRunnable);
            effectRunnable = null;
        }
    }

    /**
     * Restaura el color por defecto de la paleta Ikalp.
     */
    public void restoreDefault() {
        stopEffect();
        setSolidColor(IKALP_PRIMARY);
    }

    /**
     * Color para evento: robot escuchando.
     */
    public void onListeningStarted() {
        startEffect(LedEffect.BREATHING, COLOR_LISTENING);
    }

    /**
     * Color para evento: robot hablando.
     */
    public void onSpeakingStarted() {
        startEffect(LedEffect.PULSE, COLOR_SPEAKING);
    }

    /**
     * Color para evento: persona detectada.
     */
    public void onPersonDetected() {
        setSolidColor(COLOR_PERSON_DETECTED);
        handler.postDelayed(this::restoreDefault, 2000);
    }

    /**
     * Color para evento: error.
     */
    public void onError() {
        startEffect(LedEffect.BLINK, COLOR_ERROR);
        handler.postDelayed(this::restoreDefault, 3000);
    }

    /**
     * Restaura al color por defecto despues de un delay.
     */
    public void restoreDefaultDelayed(long delayMs) {
        handler.postDelayed(this::restoreDefault, delayMs);
    }

    public int getCurrentColor() {
        return currentColor;
    }

    public LedEffect getCurrentEffect() {
        return currentEffect;
    }

    public int getBrightness() {
        return brightness;
    }

    public void setBrightness(int brightness) {
        this.brightness = Math.max(0, Math.min(100, brightness));
    }

    /**
     * Aplica un efecto LED predefinido usando setLight (único comando soportado)
     * Mapea efectos ZCB2UARTLED a colores RGB equivalentes
     */
    public void setPredefinedEffect(int effect) {
        try {
            RobotApi api = RobotApi.getInstance();
            if (api == null) {
                Log.w(TAG, "RobotApi is null, cannot apply predefined LED effect");
                return;
            }

            Log.d(TAG, "Applying predefined LED effect: 0x" + Integer.toHexString(effect));
            
            // Mapear efectos predefinidos a colores RGB
            int color = mapEffectToColor(effect);
            
            // Aplicar usando setLight (único comando soportado por hardware)
            applyColor(color, LedZone.ALL);
            
        } catch (Exception e) {
            Log.e(TAG, "Error applying predefined LED effect", e);
        }
    }
    
    /**
     * Mapea efectos predefinidos ZCB2UARTLED a colores RGB equivalentes
     */
    private int mapEffectToColor(int effect) {
        switch (effect) {
            case LedEffects.ZCB2UARTLED_GREENBREATH:
            case LedEffects.ZCB2UARTLED_GREENNORMAL:
                return 0x00FF00; // Verde
            case LedEffects.ZCB2UARTLED_BLUEBREATH:
            case LedEffects.ZCB2UARTLED_BLUENORMAL:
                return 0x0000FF; // Azul
            case LedEffects.ZCB2UARTLED_ORANGEBREATH:
            case LedEffects.ZCB2UARTLED_ORANGENORMAL:
                return 0xFFA500; // Naranja
            case LedEffects.ZCB2UARTLED_YELLOWBREATH:
            case LedEffects.ZCB2UARTLED_YELLOWNORMAL:
            case LedEffects.ZCB2UARTLED_YELLOWFLASH:
                return 0xFFFF00; // Amarillo
            case LedEffects.ZCB2UARTLED_REDNORMAL:
            case LedEffects.ZCB2UARTLED_REGFLASH:
                return 0xFF0000; // Rojo
            case LedEffects.ZCB2UARTLED_ALLOFF:
                return 0x000000; // Negro (apagado)
            case LedEffects.ZCB2UARTLED_TURNRIGHT:
            case LedEffects.ZCB2UARTLED_TURNLEFT:
                return 0xFFFFFF; // Blanco (giro)
            default:
                return IKALP_PRIMARY; // Color por defecto
        }
    }

    /**
     * Activa efecto de respiración verde
     */
    public void setGreenBreathingEffect() {
        setPredefinedEffect(LedEffects.ZCB2UARTLED_GREENBREATH);
    }

    /**
     * Activa efecto de respiración azul
     */
    public void setBlueBreathingEffect() {
        setPredefinedEffect(LedEffects.ZCB2UARTLED_BLUEBREATH);
    }

    /**
     * Activa efecto de respiración naranja
     */
    public void setOrangeBreathingEffect() {
        setPredefinedEffect(LedEffects.ZCB2UARTLED_ORANGEBREATH);
    }

    /**
     * Activa efecto normal rojo
     */
    public void setRedNormalEffect() {
        setPredefinedEffect(LedEffects.ZCB2UARTLED_REDNORMAL);
    }

    /**
     * Activa efecto de giro a la derecha
     */
    public void setTurnRightEffect() {
        setPredefinedEffect(LedEffects.ZCB2UARTLED_TURNRIGHT);
    }

    /**
     * Activa efecto de giro a la izquierda
     */
    public void setTurnLeftEffect() {
        setPredefinedEffect(LedEffects.ZCB2UARTLED_TURNLEFT);
    }

    /**
     * Apaga todos los efectos LED
     */
    public void turnOffAllEffects() {
        setPredefinedEffect(LedEffects.ZCB2UARTLED_ALLOFF);
    }

    /**
     * Verifica las capacidades LED del robot
     * Nota: Hardware solo soporta setLight, no ProZcbLed
     */
    public Map<String, Boolean> getLedCapabilities() {
        Map<String, Boolean> capabilities = new HashMap<>();
        capabilities.put("hasProZcbLed", false); // Hardware no soporta ProZcbLed
        capabilities.put("hasClavicleLight", false); // Hardware no soporta clavícula
        Log.d(TAG, "LED Capabilities: Using only setLight (ProZcbLed not supported)");
        return capabilities;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("currentColor", String.format("#%06X", currentColor));
        status.put("currentEffect", currentEffect.name());
        status.put("brightness", brightness);
        status.put("effectRunning", effectRunning);
        status.put("defaultColor", String.format("#%06X", IKALP_PRIMARY));
        return status;
    }

    // -- Effect implementations --

    private void startBreathingEffect(int color) {
        final int r = (color >> 16) & 0xFF;
        final int g = (color >> 8) & 0xFF;
        final int b = color & 0xFF;
        final int[] step = {0};
        final boolean[] increasing = {true};

        effectRunnable = new Runnable() {
            @Override
            public void run() {
                if (!effectRunning) return;
                float factor = step[0] / 20.0f;
                int rr = (int) (r * factor);
                int gg = (int) (g * factor);
                int bb = (int) (b * factor);
                applyColor((rr << 16) | (gg << 8) | bb, LedZone.ALL);

                if (increasing[0]) {
                    step[0]++;
                    if (step[0] >= 20) increasing[0] = false;
                } else {
                    step[0]--;
                    if (step[0] <= 0) increasing[0] = true;
                }
                handler.postDelayed(this, 80);
            }
        };
        handler.post(effectRunnable);
    }

    private void startBlinkEffect(int color) {
        final boolean[] on = {true};
        effectRunnable = new Runnable() {
            @Override
            public void run() {
                if (!effectRunning) return;
                applyColor(on[0] ? color : 0x000000, LedZone.ALL);
                on[0] = !on[0];
                handler.postDelayed(this, 500);
            }
        };
        handler.post(effectRunnable);
    }

    private void startRainbowEffect() {
        final float[] hue = {0};
        effectRunnable = new Runnable() {
            @Override
            public void run() {
                if (!effectRunning) return;
                int color = hsvToRgb(hue[0], 1.0f, 1.0f);
                applyColor(color, LedZone.ALL);
                hue[0] += 5;
                if (hue[0] >= 360) hue[0] = 0;
                handler.postDelayed(this, 50);
            }
        };
        handler.post(effectRunnable);
    }

    private void startPulseEffect(int color) {
        final int r = (color >> 16) & 0xFF;
        final int g = (color >> 8) & 0xFF;
        final int b = color & 0xFF;
        final float[] factor = {0.3f};
        final boolean[] increasing = {true};

        effectRunnable = new Runnable() {
            @Override
            public void run() {
                if (!effectRunning) return;
                int rr = (int) (r * factor[0]);
                int gg = (int) (g * factor[0]);
                int bb = (int) (b * factor[0]);
                applyColor((rr << 16) | (gg << 8) | bb, LedZone.ALL);

                if (increasing[0]) {
                    factor[0] += 0.1f;
                    if (factor[0] >= 1.0f) increasing[0] = false;
                } else {
                    factor[0] -= 0.1f;
                    if (factor[0] <= 0.3f) increasing[0] = true;
                }
                handler.postDelayed(this, 60);
            }
        };
        handler.post(effectRunnable);
    }

    private void startWaveEffect(int color) {
        final int[] zone = {0};
        final LedZone[] zones = {LedZone.HEAD, LedZone.CLAVICLE, LedZone.BOTTOM};

        effectRunnable = new Runnable() {
            @Override
            public void run() {
                if (!effectRunning) return;
                for (int i = 0; i < zones.length; i++) {
                    if (i == zone[0]) {
                        applyColor(color, zones[i]);
                    } else {
                        applyColor(0x000000, zones[i]);
                    }
                }
                zone[0] = (zone[0] + 1) % zones.length;
                handler.postDelayed(this, 300);
            }
        };
        handler.post(effectRunnable);
    }

    private void applyColor(int color, LedZone zone) {
        try {
            RobotApi api = RobotApi.getInstance();
            if (api == null) {
                Log.w(TAG, "RobotApi is null, cannot apply LED color");
                return;
            }

            // Log del color original solicitado
            int origR = (color >> 16) & 0xFF;
            int origG = (color >> 8) & 0xFF;
            int origB = color & 0xFF;
            String origHex = String.format("%06X", color);
            Log.d(TAG, "=== COLOR SOLICITADO ===");
            Log.d(TAG, "  Original: 0x" + origHex + " (R:" + origR + " G:" + origG + " B:" + origB + ")");

            // Ajustar por brillo
            float bFactor = brightness / 100.0f;
            int r = (int) (((color >> 16) & 0xFF) * bFactor);
            int g = (int) (((color >> 8) & 0xFF) * bFactor);
            int b = (int) ((color & 0xFF) * bFactor);
            int adjusted = (r << 16) | (g << 8) | b;
            
            // Convertir color a formato hexadecimal string para la API
            String colorHex = String.format("%06X", adjusted);
            Log.d(TAG, "  Brillo: " + brightness + "% (factor: " + bFactor + ")");
            Log.d(TAG, "  Ajustado: 0x" + colorHex + " (R:" + r + " G:" + g + " B:" + b + ")");
            Log.d(TAG, "  Zona: " + zone.name());
            Log.d(TAG, "======================");

            switch (zone) {
                case HEAD:
                    applyHeadLedEffect(api, adjusted, colorHex);
                    break;
                case CLAVICLE:
                    applyClavicleLedEffect(api, adjusted);
                    break;
                case BOTTOM:
                    applyBottomLedEffect(api, adjusted);
                    break;
                case ALL:
                default:
                    // Para ALL, usar target=-1 para aplicar a todas las zonas de una vez
                    applyAllZonesLedEffect(api, adjusted, colorHex);
                    break;
            }
            notifyColorChanged(adjusted, zone);
        } catch (Exception e) {
            Log.e(TAG, "Error applying LED color", e);
        }
    }

    /**
     * Aplica efecto LED a la cabeza usando setLight
     */
    private void applyHeadLedEffect(RobotApi api, int color, String colorHex) {
        try {
            // Usar setLight para cabeza
            JSONObject params = new JSONObject();
            params.put(Definition.JSON_LAMB_TYPE, 0);              // type: Fill 0
            params.put(Definition.JSON_LAMB_TARGET, 0);            // target: 0 = HEAD
            params.put(Definition.JSON_LAMB_RGB_START, colorHex);  // Color inicial
            params.put(Definition.JSON_LAMB_RGB_END, colorHex);    // Color final
            params.put(Definition.JSON_LAMB_START_TIME, 1000);     // Duración color inicial (ms)
            params.put(Definition.JSON_LAMB_END_TIME, 1000);       // Duración color final (ms)
            params.put(Definition.JSON_LAMB_REPEAT, 1);            // Repeticiones
            params.put(Definition.JSON_LAMB_ON_TIME, 500);         // Tiempo gradiente (ms)
            params.put(Definition.JSON_LAMB_RGB_FREEZE, colorHex); // Color final transición
            
            Log.d(TAG, ">>> HEAD: setLight params: " + params.toString());
            int result = api.setLight(requestId++, params.toString(), null);
            Log.d(TAG, ">>> HEAD RESULT: " + result);
        } catch (Exception e) {
            Log.e(TAG, "Error setting head LED effect", e);
        }
    }

    /**
     * Aplica efecto LED a las clavículas - NO SOPORTADO por hardware
     */
    private void applyClavicleLedEffect(RobotApi api, int color) {
        Log.w(TAG, ">>> CLAVICLE LED: Not supported by hardware - skipping");
        // El hardware reporta "LED effect is not supported" para clavícula
        // No intentar aplicar color a esta zona
    }

    /**
     * Aplica efecto LED a la base/chasis - NO SOPORTADO por hardware
     */
    private void applyBottomLedEffect(RobotApi api, int color) {
        Log.w(TAG, ">>> BOTTOM LED: Not supported by hardware - skipping");
        // El hardware reporta "LED effect is not supported" para base
        // No intentar aplicar color a esta zona
    }

    /**
     * Aplica efecto LED a todas las zonas usando setLight (único comando soportado)
     */
    private void applyAllZonesLedEffect(RobotApi api, int color, String colorHex) {
        try {
            // Primero detener cualquier efecto en ejecución
            stopEffect();
            
            // Usar solo setLight que es el comando soportado (cmd_can_lamp_anim)
            JSONObject params = new JSONObject();
            params.put(Definition.JSON_LAMB_TYPE, 0);              // type: Fill 0
            params.put(Definition.JSON_LAMB_TARGET, -1);           // target: -1 = ALL
            params.put(Definition.JSON_LAMB_RGB_START, colorHex);  // Color inicial
            params.put(Definition.JSON_LAMB_RGB_END, colorHex);    // Color final
            params.put(Definition.JSON_LAMB_START_TIME, 1000);     // Duración color inicial (ms)
            params.put(Definition.JSON_LAMB_END_TIME, 1000);       // Duración color final (ms)
            params.put(Definition.JSON_LAMB_REPEAT, 1);            // Repeticiones
            params.put(Definition.JSON_LAMB_ON_TIME, 500);         // Tiempo gradiente (ms)
            params.put(Definition.JSON_LAMB_RGB_FREEZE, colorHex); // Color final transición
            
            Log.d(TAG, ">>> ALL ZONES: setLight params: " + params.toString());
            int result = api.setLight(requestId++, params.toString(), null);
            Log.d(TAG, ">>> ALL ZONES RESULT: " + result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting all zones LED effect", e);
        }
    }

    /**
     * Obtiene el efecto predefinido más cercano al color solicitado
     */
    private int getClosestPredefinedEffect(int color) {
        // Extraer componentes RGB
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        // Determinar color dominante y retornar efecto apropiado
        if (r > g && r > b) {
            return LedEffects.ZCB2UARTLED_REDNORMAL;
        } else if (g > r && g > b) {
            return LedEffects.ZCB2UARTLED_GREENNORMAL;
        } else if (b > r && b > g) {
            return LedEffects.ZCB2UARTLED_BLUENORMAL;
        } else if (r > 200 && g > 100 && b < 50) {
            return LedEffects.ZCB2UARTLED_ORANGENORMAL;
        } else if (r > 200 && g > 200 && b < 100) {
            return LedEffects.ZCB2UARTLED_YELLOWNORMAL;
        } else {
            return LedEffects.ZCB2UARTLED_BLUENORMAL; // Default
        }
    }

    private int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60) % 2 - 1));
        float m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        int ri = (int) ((r + m) * 255);
        int gi = (int) ((g + m) * 255);
        int bi = (int) ((b + m) * 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    public void addListener(LedEventListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(LedEventListener listener) {
        listeners.remove(listener);
    }

    private void notifyColorChanged(int color, LedZone zone) {
        for (LedEventListener l : listeners) {
            try { l.onColorChanged(color, zone); } catch (Exception e) { /* ignore */ }
        }
    }

    private void notifyEffectChanged(LedEffect effect) {
        for (LedEventListener l : listeners) {
            try { l.onEffectChanged(effect); } catch (Exception e) { /* ignore */ }
        }
    }

    public void destroy() {
        stopEffect();
        listeners.clear();
        sInstance = null;
    }
}
