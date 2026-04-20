package com.robbie.core.hardware;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ainirobot.coreservice.client.RobotApi;

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

    private static final int IKALP_PRIMARY = 0xE4027C;
    private static final int COLOR_LISTENING = 0xE4027C;
    private static final int COLOR_SPEAKING = 0x00BFA5;
    private static final int COLOR_PERSON_DETECTED = 0x4CAF50;
    private static final int COLOR_ERROR = 0xF44336;
    private static final int COLOR_IDLE = 0x455A64;

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
            if (api == null) return;

            float bFactor = brightness / 100.0f;
            int r = (int) (((color >> 16) & 0xFF) * bFactor);
            int g = (int) (((color >> 8) & 0xFF) * bFactor);
            int b = (int) ((color & 0xFF) * bFactor);
            int adjusted = (r << 16) | (g << 8) | b;

            switch (zone) {
                case HEAD:
                    JSONObject params = new JSONObject();
                    params.put("type", 1);
                    params.put("target", 0);
                    params.put("color_rgb_value", adjusted);
                    api.setLight(requestId++, params.toString(), null);
                    break;
                case CLAVICLE:
                    api.setClavicleLedEffect(requestId++, adjusted, null);
                    break;
                case BOTTOM:
                    api.setBottomLedEffect(requestId++, adjusted, null);
                    break;
                case ALL:
                default:
                    JSONObject allParams = new JSONObject();
                    allParams.put("type", 1);
                    allParams.put("target", 0);
                    allParams.put("color_rgb_value", adjusted);
                    api.setLight(requestId++, allParams.toString(), null);
                    api.setClavicleLedEffect(requestId++, adjusted, null);
                    api.setBottomLedEffect(requestId++, adjusted, null);
                    break;
            }
            notifyColorChanged(adjusted, zone);
        } catch (Exception e) {
            Log.e(TAG, "Error applying LED color", e);
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
