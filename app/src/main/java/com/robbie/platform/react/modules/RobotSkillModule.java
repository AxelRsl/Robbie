package com.robbie.platform.react.modules;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import org.json.JSONObject;

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
    public void executeAction(String action, String paramsJson, Promise promise) {
        try {
            JSONObject params = new JSONObject(paramsJson);
            Log.i(TAG, "Executing action: " + action + " with params: " + paramsJson);
            
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    
                    JSONObject result = new JSONObject();
                    result.put("success", true);
                    result.put("action", action);
                    result.put("message", "Action executed successfully");
                    
                    promise.resolve(result.toString());
                    
                } catch (Exception e) {
                    promise.reject("ERROR", "Action execution failed: " + e.getMessage());
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "Error executing action", e);
            promise.reject("ERROR", "Failed to execute action: " + e.getMessage());
        }
    }
}
