package com.robbie.platform.react.modules;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.robbie.core.modes.ModeManager;

import com.google.gson.Gson;

/**
 * Puente React Native para gestion de modos operativos.
 */
public class ModeModule extends ReactContextBaseJavaModule {

    private static final String TAG = "ModeModule";
    private final Gson gson = new Gson();

    public ModeModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "ModeModule";
    }

    @ReactMethod
    public void getCurrentMode(Promise promise) {
        try {
            ModeManager mm = ModeManager.getInstance();
            promise.resolve(mm.getCurrentMode().name());
        } catch (Exception e) {
            promise.reject("MODE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void switchMode(String modeName, Promise promise) {
        try {
            ModeManager mm = ModeManager.getInstance();
            ModeManager.RobotMode mode = ModeManager.RobotMode.valueOf(modeName.toUpperCase());
            mm.switchMode(mode);
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error switching mode", e);
            promise.reject("MODE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void getStatus(Promise promise) {
        try {
            ModeManager mm = ModeManager.getInstance();
            String json = gson.toJson(mm.getStatus());
            promise.resolve(json);
        } catch (Exception e) {
            promise.reject("MODE_ERROR", e.getMessage());
        }
    }
}
