package com.robbie.platform.react.modules;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.robbie.core.hardware.LedController;

import com.google.gson.Gson;

/**
 * Puente React Native para control de LEDs del robot.
 */
public class LedModule extends ReactContextBaseJavaModule {

    private static final String TAG = "LedModule";
    private final Gson gson = new Gson();

    public LedModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "LedModule";
    }

    @ReactMethod
    public void setSolidColor(String hexColor, Promise promise) {
        try {
            int color = parseColor(hexColor);
            LedController.getInstance().setSolidColor(color);
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error setting color", e);
            promise.reject("LED_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void startEffect(String effect, String hexColor, Promise promise) {
        try {
            int color = parseColor(hexColor);
            LedController.LedEffect ledEffect = LedController.LedEffect.valueOf(effect.toUpperCase());
            LedController.getInstance().startEffect(ledEffect, color);
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error starting effect", e);
            promise.reject("LED_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void stopEffect(Promise promise) {
        try {
            LedController.getInstance().stopEffect();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("LED_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void restoreDefault(Promise promise) {
        try {
            LedController.getInstance().restoreDefault();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("LED_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void setBrightness(int brightness, Promise promise) {
        try {
            LedController.getInstance().setBrightness(brightness);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("LED_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void getStatus(Promise promise) {
        try {
            String json = gson.toJson(LedController.getInstance().getStatus());
            promise.resolve(json);
        } catch (Exception e) {
            promise.reject("LED_ERROR", e.getMessage());
        }
    }

    private int parseColor(String hex) {
        if (hex.startsWith("#")) hex = hex.substring(1);
        return Integer.parseInt(hex, 16);
    }
}
