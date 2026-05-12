package com.robbie.platform.react.modules;

import android.os.RemoteException;
import android.util.Log;

import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.listener.ActionListener;
import com.ainirobot.coreservice.client.listener.CommandListener;
import com.ainirobot.coreservice.client.StatusListener;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.json.JSONObject;

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
    private static final int DEFAULT_CHARGE_TIMEOUT = 120000; // 2 minutes to reach dock

    private int chargeReqId = 8001;
    private boolean isCharging = false;
    private boolean isNavigatingToCharger = false;
    private StatusListener batteryStatusListener;

    public ChargingModule(ReactApplicationContext reactContext) {
        super(reactContext);
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
            RobotApi api = RobotApi.getInstance();
            if (api == null) {
                promise.reject("ERROR", "RobotApi not available");
                return;
            }

            if (isNavigatingToCharger || isCharging) {
                promise.reject("ALREADY_CHARGING", "Already navigating to charger or charging");
                return;
            }

            isNavigatingToCharger = true;
            emitChargingEvent("navigating_to_charger", "Navegando al cargador...");
            Log.i(TAG, "Starting auto-charge navigation");

            api.startNaviToAutoChargeAction(chargeReqId++, DEFAULT_CHARGE_TIMEOUT, new ActionListener() {
                @Override
                public void onResult(int status, String responseString) throws RemoteException {
                    isNavigatingToCharger = false;
                    switch (status) {
                        case Definition.RESULT_OK:
                            isCharging = true;
                            Log.i(TAG, "Auto-charge: arrived at dock and charging");
                            emitChargingEvent("charging", "Cargando...");
                            break;
                        case Definition.RESULT_FAILURE:
                            isCharging = false;
                            Log.w(TAG, "Auto-charge: failed to reach dock");
                            emitChargingEvent("charge_failed", "No se pudo llegar al cargador");
                            break;
                        default:
                            Log.w(TAG, "Auto-charge: unknown result status=" + status);
                            emitChargingEvent("charge_failed", "Error desconocido");
                            break;
                    }
                }

                @Override
                public void onError(int errorCode, String errorString) throws RemoteException {
                    isNavigatingToCharger = false;
                    isCharging = false;
                    Log.e(TAG, "Auto-charge error: code=" + errorCode + " msg=" + errorString);
                    emitChargingEvent("charge_failed", "Error: " + errorString);
                }

                @Override
                public void onStatusUpdate(int status, String data) throws RemoteException {
                    Log.d(TAG, "Auto-charge status: " + status + " data=" + data);
                    switch (status) {
                        case Definition.STATUS_NAVI_GLOBAL_PATH_FAILED:
                            emitChargingEvent("charge_error", "Ruta al cargador no encontrada");
                            break;
                        case Definition.STATUS_NAVI_OUT_MAP:
                            emitChargingEvent("charge_error", "Cargador fuera del mapa");
                            break;
                        case Definition.STATUS_NAVI_AVOID:
                            emitChargingEvent("charge_obstacle", "Obstaculo en el camino al cargador");
                            break;
                        case Definition.STATUS_NAVI_AVOID_END:
                            emitChargingEvent("charge_obstacle_cleared", "Obstaculo removido, continuando...");
                            break;
                    }
                }
            });

            promise.resolve("Navigation to charger started");
        } catch (Exception e) {
            isNavigatingToCharger = false;
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
            RobotApi api = RobotApi.getInstance();
            if (api == null) {
                promise.reject("ERROR", "RobotApi not available");
                return;
            }

            Log.i(TAG, "Stopping auto-charge");
            api.stopAutoChargeAction(chargeReqId++, true);
            isCharging = false;
            isNavigatingToCharger = false;
            emitChargingEvent("charge_stopped", "Carga detenida");
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
            RobotApi api = RobotApi.getInstance();
            if (api == null) {
                promise.reject("ERROR", "RobotApi not available");
                return;
            }

            Log.i(TAG, "Leaving charging pile");
            // Disable battery UI takeover first
            api.disableBattery();

            api.leaveChargingPile(chargeReqId++, 0.7f, 0.2f, new CommandListener() {
                @Override
                public void onStatusUpdate(int status, String data, String extraData) {
                    Log.d(TAG, "leaveChargingPile status: " + status + " data=" + data);
                }

                @Override
                public void onError(int errorCode, String errorString, String extraData) {
                    Log.e(TAG, "leaveChargingPile error: " + errorCode + " msg=" + errorString);
                    emitChargingEvent("leave_pile_failed", "Error al salir del cargador: " + errorString);
                }

                @Override
                public void onResult(int result, String message, String extraData) {
                    Log.i(TAG, "leaveChargingPile result: " + result + " msg=" + message);
                    if (result == Definition.RESULT_OK) {
                        isCharging = false;
                        emitChargingEvent("left_pile", "Salio del cargador exitosamente");
                    }
                }
            });

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
            RobotApi api = RobotApi.getInstance();
            int level = -1;
            if (api != null) {
                level = api.getBatteryLevel();
            }
            JSONObject info = new JSONObject();
            info.put("level", level);
            info.put("isCharging", isCharging);
            info.put("isNavigatingToCharger", isNavigatingToCharger);
            promise.resolve(info.toString());
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
            RobotApi api = RobotApi.getInstance();
            if (api == null) {
                promise.reject("ERROR", "RobotApi not available");
                return;
            }

            if (batteryStatusListener != null) {
                // Already monitoring
                promise.resolve("Already monitoring");
                return;
            }

            batteryStatusListener = new StatusListener() {
                @Override
                public void onStatusUpdate(String type, String data) throws RemoteException {
                    Log.d(TAG, "Battery status update: " + data);
                    emitBatteryEvent(data);
                }
            };

            api.registerStatusListener(Definition.STATUS_BATTERY, batteryStatusListener);
            Log.i(TAG, "Battery monitor started");
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
            if (batteryStatusListener != null) {
                RobotApi api = RobotApi.getInstance();
                if (api != null) {
                    api.unregisterStatusListener(batteryStatusListener);
                }
                batteryStatusListener = null;
                Log.i(TAG, "Battery monitor stopped");
            }
            promise.resolve("Battery monitor stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping battery monitor", e);
            promise.reject("ERROR", "Failed to stop battery monitor: " + e.getMessage());
        }
    }

    /**
     * Deshabilita la pantalla del sistema de bateria para que la app mantenga
     * el control de la UI mientras carga.
     */
    @ReactMethod
    public void disableBatteryUI(Promise promise) {
        try {
            RobotApi api = RobotApi.getInstance();
            if (api == null) {
                promise.reject("ERROR", "RobotApi not available");
                return;
            }
            api.disableBattery();
            Log.i(TAG, "Battery UI disabled");
            promise.resolve("Battery UI disabled");
        } catch (Exception e) {
            Log.e(TAG, "Error disabling battery UI", e);
            promise.reject("ERROR", "Failed to disable battery UI: " + e.getMessage());
        }
    }

    // ==================== Helpers ====================

    public boolean isCharging() {
        return isCharging;
    }

    public boolean isNavigatingToCharger() {
        return isNavigatingToCharger;
    }

    private void emitChargingEvent(String status, String message) {
        try {
            ReactApplicationContext ctx = getReactApplicationContext();
            if (ctx == null || !ctx.hasActiveReactInstance()) return;

            WritableMap params = Arguments.createMap();
            params.putString("status", status);
            params.putString("message", message);
            params.putBoolean("isCharging", isCharging);
            params.putBoolean("isNavigatingToCharger", isNavigatingToCharger);

            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onChargingStatus", params);
            Log.d(TAG, "Emitted charging event: " + status);
        } catch (Exception e) {
            Log.w(TAG, "Could not emit charging event", e);
        }
    }

    private void emitBatteryEvent(String data) {
        try {
            ReactApplicationContext ctx = getReactApplicationContext();
            if (ctx == null || !ctx.hasActiveReactInstance()) return;

            WritableMap params = Arguments.createMap();
            params.putString("data", data);

            // Try to parse battery data and extract level
            try {
                JSONObject json = new JSONObject(data);
                if (json.has("level")) {
                    params.putInt("level", json.getInt("level"));
                }
                if (json.has("isCharging")) {
                    params.putBoolean("isCharging", json.getBoolean("isCharging"));
                }
            } catch (Exception ignored) {}

            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onBatteryStatus", params);
        } catch (Exception e) {
            Log.w(TAG, "Could not emit battery event", e);
        }
    }
}
