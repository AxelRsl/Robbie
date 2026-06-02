package com.robbie.platform.react.modules;

import android.util.Log;

import com.robbie.base.config.RemoteConfigManager;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

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
            
            promise.resolve(jsonToWritableMap(config));
            
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

    private WritableMap jsonToWritableMap(JSONObject jsonObject) throws Exception {
        WritableMap map = new WritableNativeMap();
        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = jsonObject.get(key);
            if (value instanceof JSONObject) {
                map.putMap(key, jsonToWritableMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.putArray(key, jsonToWritableArray((JSONArray) value));
            } else if (value instanceof String) {
                map.putString(key, (String) value);
            } else if (value instanceof Integer) {
                map.putInt(key, (Integer) value);
            } else if (value instanceof Double) {
                map.putDouble(key, (Double) value);
            } else if (value instanceof Boolean) {
                map.putBoolean(key, (Boolean) value);
            } else if (value == JSONObject.NULL) {
                map.putNull(key);
            }
        }
        return map;
    }

    private WritableArray jsonToWritableArray(JSONArray jsonArray) throws Exception {
        WritableArray array = new WritableNativeArray();
        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            if (value instanceof JSONObject) {
                array.pushMap(jsonToWritableMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                array.pushArray(jsonToWritableArray((JSONArray) value));
            } else if (value instanceof String) {
                array.pushString((String) value);
            } else if (value instanceof Integer) {
                array.pushInt((Integer) value);
            } else if (value instanceof Double) {
                array.pushDouble((Double) value);
            } else if (value instanceof Boolean) {
                array.pushBoolean((Boolean) value);
            } else if (value == JSONObject.NULL) {
                array.pushNull();
            }
        }
        return array;
    }
}
