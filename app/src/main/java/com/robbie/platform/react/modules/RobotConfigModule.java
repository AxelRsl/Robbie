package com.robbie.platform.react.modules;

import android.util.Log;

import com.robbie.base.config.RemoteConfigManager;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.google.gson.Gson;

import org.json.JSONObject;

public class RobotConfigModule extends ReactContextBaseJavaModule {
    
    private static final String TAG = "RobotConfigModule";
    private final RemoteConfigManager configManager;
    private final Gson gson;
    
    public RobotConfigModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.configManager = RemoteConfigManager.getInstance();
        this.gson = new Gson();
    }
    
    @Override
    public String getName() {
        return "RobotConfigModule";
    }
    
    @ReactMethod
    public void getConfig(Promise promise) {
        try {
            JSONObject config = new JSONObject();
            
            JSONObject cloudConfig = new JSONObject();
            cloudConfig.put("apiDomain", configManager.getServerDomain());
            cloudConfig.put("biDomain", configManager.getBiDomain());
            cloudConfig.put("aiOpenDomain", configManager.getAiOpenUrl());
            cloudConfig.put("appId", configManager.getAppId());
            cloudConfig.put("appSecret", configManager.getAppSecret());
            cloudConfig.put("region", configManager.getActiveRegion());
            
            config.put("cloudConfig", cloudConfig);
            config.put("retailTemplate", "grid");
            config.put("menuTemplate", "classic");
            config.put("promoTemplate", "video");
            
            promise.resolve(config.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting config", e);
            promise.reject("ERROR", "Failed to get config: " + e.getMessage());
        }
    }
    
    @ReactMethod
    public void updateConfig(String configJson, Promise promise) {
        try {
            JSONObject config = new JSONObject(configJson);
            
            if (config.has("cloudConfig")) {
                JSONObject cloudConfig = config.getJSONObject("cloudConfig");
                
                if (cloudConfig.has("region")) {
                    String region = cloudConfig.getString("region");
                    configManager.setActiveRegion(region);
                }
                
                if (cloudConfig.has("apiDomain")) {
                    String apiDomain = cloudConfig.getString("apiDomain");
                    String region = configManager.getActiveRegion();
                    configManager.putString("server_domain_" + region, apiDomain);
                }
            }
            
            promise.resolve(true);
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating config", e);
            promise.reject("ERROR", "Failed to update config: " + e.getMessage());
        }
    }
}
