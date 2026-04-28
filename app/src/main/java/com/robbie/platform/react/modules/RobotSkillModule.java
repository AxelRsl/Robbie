package com.robbie.platform.react.modules;

import android.os.Bundle;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.robbie.platform.agent.RobotActionHandler;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;

import org.json.JSONObject;

import java.util.List;

public class RobotSkillModule extends ReactContextBaseJavaModule {
    
    private static final String TAG = "RobotSkillModule";
    
    public RobotSkillModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }
    
    @Override
    public String getName() {
        return "RobotSkillModule";
    }
    
    @ReactMethod
    public void getMapPlaces(Promise promise) {
        try {
            RobotActionHandler actionHandler = RobotActionHandler.getInstance();
            if (actionHandler == null) {
                promise.resolve(Arguments.createArray());
                return;
            }
            List<String> places = actionHandler.getMapPlaces();
            WritableArray result = Arguments.createArray();
            for (String place : places) {
                result.pushString(place);
            }
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error getting map places", e);
            promise.resolve(Arguments.createArray());
        }
    }

    @ReactMethod
    public void executeAction(String action, String paramsJson, Promise promise) {
        try {
            Log.i(TAG, "Executing action: " + action + " with params: " + paramsJson);
            
            RobotActionHandler actionHandler = RobotActionHandler.getInstance();
            if (actionHandler == null) {
                Log.e(TAG, "RobotActionHandler not initialized");
                promise.reject("ERROR", "RobotActionHandler not initialized");
                return;
            }
            
            // Convert JSON to Bundle
            Bundle params = new Bundle();
            if (paramsJson != null && !paramsJson.isEmpty() && !paramsJson.equals("{}")) {
                JSONObject json = new JSONObject(paramsJson);
                java.util.Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = json.get(key);
                    if (value instanceof String) {
                        params.putString(key, (String) value);
                    } else if (value instanceof Integer) {
                        params.putInt(key, (Integer) value);
                    } else if (value instanceof Double) {
                        params.putDouble(key, (Double) value);
                    } else if (value instanceof Boolean) {
                        params.putBoolean(key, (Boolean) value);
                    }
                }
            }
            
            boolean success = actionHandler.handleAction(action, params);
            
            JSONObject result = new JSONObject();
            result.put("success", success);
            result.put("action", action);
            result.put("message", success ? "Action executed successfully" : "Action execution failed");
            
            promise.resolve(result.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error executing action", e);
            promise.reject("ERROR", "Failed to execute action: " + e.getMessage());
        }
    }
}
