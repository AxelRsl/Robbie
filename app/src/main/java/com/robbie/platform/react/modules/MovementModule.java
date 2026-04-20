package com.robbie.platform.react.modules;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.robbie.core.hardware.ActuatorManager;
import com.robbie.core.navigation.NavigationManager;

import com.google.gson.Gson;

/**
 * Puente React Native para control de movimiento y navegacion.
 */
public class MovementModule extends ReactContextBaseJavaModule {

    private static final String TAG = "MovementModule";
    private final Gson gson = new Gson();

    public MovementModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "MovementModule";
    }

    @ReactMethod
    public void moveForward(float distance, Promise promise) {
        try {
            ActuatorManager.getInstance().moveForward(distance);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("MOVE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void moveBackward(float distance, Promise promise) {
        try {
            ActuatorManager.getInstance().moveBackward(distance);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("MOVE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void rotate(float degrees, Promise promise) {
        try {
            ActuatorManager.getInstance().rotate(degrees);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("MOVE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void joystickMove(float linearSpeed, float angularSpeed, Promise promise) {
        try {
            ActuatorManager.getInstance().joystickMove(linearSpeed, angularSpeed);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("MOVE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void stopMovement(Promise promise) {
        try {
            ActuatorManager.getInstance().stopMovement();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("MOVE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void moveHead(int pan, int tilt, Promise promise) {
        try {
            ActuatorManager.getInstance().moveHead(pan, tilt);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("MOVE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void navigateTo(String pointName, Promise promise) {
        try {
            NavigationManager.getInstance().navigateTo(pointName);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("NAV_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void stopNavigation(Promise promise) {
        try {
            NavigationManager.getInstance().stopNavigation();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("NAV_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void getMovementStatus(Promise promise) {
        try {
            String json = gson.toJson(ActuatorManager.getInstance().getStatus());
            promise.resolve(json);
        } catch (Exception e) {
            promise.reject("MOVE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void getNavigationStatus(Promise promise) {
        try {
            String json = gson.toJson(NavigationManager.getInstance().getStatus());
            promise.resolve(json);
        } catch (Exception e) {
            promise.reject("NAV_ERROR", e.getMessage());
        }
    }
}
