package com.robbie.core.modes;

import android.content.Context;
import android.util.Log;

import com.robbie.core.hardware.LedController;
import com.robbie.core.navigation.NavigationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestor central de modos operativos del robot.
 *
 * Modos disponibles:
 * - IDLE: Robot en espera, sin comportamiento automatico
 * - RETAIL: Interaccion con clientes, promocion de productos, guia en tienda
 * - EXHIBITION: Demostraciones automaticas, presentaciones, interaccion educativa
 *
 * Gestiona transiciones entre modos, notificaciones y estado global.
 */
public class ModeManager {

    private static final String TAG = "ModeManager";
    private static volatile ModeManager sInstance;

    private final Context context;
    private RobotMode currentMode = RobotMode.IDLE;
    private RetailMode retailMode;
    private ExhibitionMode exhibitionMode;
    private final List<ModeChangeListener> listeners = new ArrayList<>();

    public enum RobotMode {
        IDLE,
        RETAIL,
        EXHIBITION
    }

    public interface ModeChangeListener {
        void onModeChanged(RobotMode oldMode, RobotMode newMode);
        void onModeError(RobotMode mode, String error);
    }

    private ModeManager(Context context) {
        this.context = context.getApplicationContext();
        this.retailMode = new RetailMode(context);
        this.exhibitionMode = new ExhibitionMode(context);
    }

    public static ModeManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ModeManager.class) {
                if (sInstance == null) {
                    sInstance = new ModeManager(context);
                }
            }
        }
        return sInstance;
    }

    public static ModeManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("ModeManager not initialized.");
        }
        return sInstance;
    }

    /**
     * Cambia el modo operativo del robot.
     */
    public void switchMode(RobotMode newMode) {
        if (newMode == currentMode) {
            Log.d(TAG, "Already in mode: " + newMode.name());
            return;
        }

        RobotMode oldMode = currentMode;
        Log.i(TAG, "Switching mode: " + oldMode.name() + " -> " + newMode.name());

        // Detener modo actual
        stopCurrentMode();

        // Iniciar nuevo modo
        currentMode = newMode;
        startCurrentMode();

        // Notificar cambio
        notifyModeChanged(oldMode, newMode);

        // Cambiar color LED segun modo
        updateLedForMode(newMode);
    }

    private void stopCurrentMode() {
        switch (currentMode) {
            case RETAIL:
                retailMode.stop();
                break;
            case EXHIBITION:
                exhibitionMode.stop();
                break;
            case IDLE:
            default:
                break;
        }
    }

    private void startCurrentMode() {
        switch (currentMode) {
            case RETAIL:
                retailMode.start();
                break;
            case EXHIBITION:
                exhibitionMode.start();
                break;
            case IDLE:
            default:
                break;
        }
    }

    private void updateLedForMode(RobotMode mode) {
        try {
            LedController led = LedController.getInstance();
            switch (mode) {
                case RETAIL:
                    led.setSolidColor(0xE4027C);
                    break;
                case EXHIBITION:
                    led.setSolidColor(0x6200EA);
                    break;
                case IDLE:
                default:
                    led.setSolidColor(0x455A64);
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not update LED for mode", e);
        }
    }

    public RobotMode getCurrentMode() {
        return currentMode;
    }

    public RetailMode getRetailMode() {
        return retailMode;
    }

    public ExhibitionMode getExhibitionMode() {
        return exhibitionMode;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("currentMode", currentMode.name());

        List<Map<String, Object>> modes = new ArrayList<>();
        for (RobotMode mode : RobotMode.values()) {
            Map<String, Object> modeInfo = new HashMap<>();
            modeInfo.put("id", mode.name());
            modeInfo.put("name", getModeDisplayName(mode));
            modeInfo.put("description", getModeDescription(mode));
            modeInfo.put("active", mode == currentMode);
            modes.add(modeInfo);
        }
        status.put("availableModes", modes);

        switch (currentMode) {
            case RETAIL:
                status.put("modeConfig", retailMode.getConfig());
                break;
            case EXHIBITION:
                status.put("modeConfig", exhibitionMode.getConfig());
                break;
            default:
                break;
        }

        return status;
    }

    private String getModeDisplayName(RobotMode mode) {
        switch (mode) {
            case RETAIL: return "Modo Retail";
            case EXHIBITION: return "Modo Exhibicion";
            case IDLE: return "En espera";
            default: return mode.name();
        }
    }

    private String getModeDescription(RobotMode mode) {
        switch (mode) {
            case RETAIL: return "Interaccion con clientes, promocion de productos, guia en tienda";
            case EXHIBITION: return "Demostraciones automaticas, presentaciones programadas";
            case IDLE: return "Robot en espera sin comportamiento automatico";
            default: return "";
        }
    }

    public void addListener(ModeChangeListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(ModeChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyModeChanged(RobotMode oldMode, RobotMode newMode) {
        for (ModeChangeListener l : listeners) {
            try { l.onModeChanged(oldMode, newMode); } catch (Exception e) { /* ignore */ }
        }
    }

    public void destroy() {
        stopCurrentMode();
        listeners.clear();
        sInstance = null;
    }
}
