package com.robbie.platform.react.modules;

import android.os.RemoteException;
import android.util.Log;

import com.robbie.platform.robot.ChargingStateManager;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

/**
 * ChargingModule - Modulo nativo para control de carga del robot.
 *
 * Expone a React Native:
 * - startAutoCharge(): Inicia navegacion automatica al cargador
 * - stopAutoCharge(): Detiene la accion de carga automatica
 * - leaveChargingPile(): Sale de la estacion de carga
 * - getBatteryInfo(): Obtiene estado actual de bateria
 * - startBatteryMonitor(): Inicia monitoreo continuo de bateria
 * - stopBatteryMonitor(): Detiene monitoreo de bateria
 *
 * Usa RobotApi.startNaviToAutoChargeAction / stopAutoChargeAction / leaveChargingPile
 * y RobotApi.getBatteryLevel() para consultar estado de bateria.
 */
public class ChargingModule extends ReactContextBaseJavaModule {

    private static final String TAG = "ChargingModule";
    private static final String MODULE_OWNER = "ChargingModule";
    private final ChargingStateManager chargingStateManager = ChargingStateManager.getInstance();

    public ChargingModule(ReactApplicationContext reactContext) {
        super(reactContext);
        chargingStateManager.start(reactContext, MODULE_OWNER);
    }

    @Override
    public String getName() {
        return "ChargingModule";
    }

    /**
     * Inicia la navegacion automatica hacia el cargador.
     * Usa RobotApi.startNaviToAutoChargeAction().
     */
    @ReactMethod
    public void startAutoCharge(Promise promise) {
        try {
            chargingStateManager.requestStartCharging(false);
            promise.resolve("Navigation to charger started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting auto-charge", e);
            promise.reject("ERROR", "Failed to start auto-charge: " + e.getMessage());
        }
    }

    /**
     * Detiene la accion de carga automatica (navegacion y/o carga).
     * Usa RobotApi.stopAutoChargeAction().
     */
    @ReactMethod
    public void stopAutoCharge(Promise promise) {
        try {
            chargingStateManager.requestStopCharging();
            promise.resolve("Auto-charge stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping auto-charge", e);
            promise.reject("ERROR", "Failed to stop auto-charge: " + e.getMessage());
        }
    }

    /**
     * Sale de la estacion de carga.
     * Usa RobotApi.leaveChargingPile().
     * NOTA: Requiere haber llamado RobotApi.disableBattery() previamente.
     */
    @ReactMethod
    public void leaveChargingPile(Promise promise) {
        try {
            chargingStateManager.requestStopCharging();
            promise.resolve("Leaving charging pile");
        } catch (Exception e) {
            Log.e(TAG, "Error leaving charging pile", e);
            promise.reject("ERROR", "Failed to leave charging pile: " + e.getMessage());
        }
    }

    /**
     * Obtiene informacion actual de la bateria.
     * Retorna JSON string con level, isCharging, etc.
     */
    @ReactMethod
    public void getBatteryInfo(Promise promise) {
        try {
            ChargingStateManager.Snapshot snapshot = chargingStateManager.snapshot();
            WritableMap info = new WritableNativeMap();
            info.putInt("level", snapshot.batteryLevel);
            info.putBoolean("isCharging", snapshot.isCharging);
            info.putBoolean("isNavigatingToCharger", snapshot.isNavigatingToCharger);
            info.putString("status", snapshot.status);
            info.putString("message", snapshot.message);
            info.putBoolean("robotApiConnected", snapshot.robotApiConnected);
            info.putBoolean("autoTriggered", snapshot.autoTriggered);
            promise.resolve(info);
        } catch (Exception e) {
            Log.e(TAG, "Error getting battery info", e);
            promise.reject("ERROR", "Failed to get battery info: " + e.getMessage());
        }
    }

    /**
     * Inicia monitoreo de bateria. Emite eventos 'onBatteryStatus' a React Native.
     */
    @ReactMethod
    public void startBatteryMonitor(Promise promise) {
        try {
            chargingStateManager.start(getReactApplicationContext(), MODULE_OWNER);
            promise.resolve("Battery monitor started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting battery monitor", e);
            promise.reject("ERROR", "Failed to start battery monitor: " + e.getMessage());
        }
    }

    /**
     * Detiene monitoreo de bateria.
     */
    @ReactMethod
    public void stopBatteryMonitor(Promise promise) {
        try {
            chargingStateManager.stop(MODULE_OWNER);
            promise.resolve("Battery monitor stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping battery monitor", e);
            promise.reject("ERROR", "Failed to stop battery monitor: " + e.getMessage());
        }
    }

    @Override
    public void invalidate() {
        chargingStateManager.stop(MODULE_OWNER);
        super.invalidate();
    }

    /**
     * Deshabilita la pantalla del sistema de bateria para que la app mantenga
     * el control de la UI mientras carga.
     */
    @ReactMethod
    public void disableBatteryUI(Promise promise) {
        try {
            chargingStateManager.disableBatteryUi();
            promise.resolve("Battery UI disabled");
        } catch (Exception e) {
            Log.e(TAG, "Error disabling battery UI", e);
            promise.reject("ERROR", "Failed to disable battery UI: " + e.getMessage());
        }
    }
}
