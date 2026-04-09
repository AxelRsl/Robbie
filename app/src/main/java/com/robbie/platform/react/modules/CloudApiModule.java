package com.robbie.platform.react.modules;

import android.util.Log;

import com.robbie.base.config.RemoteConfigManager;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import org.json.JSONObject;

public class CloudApiModule extends ReactContextBaseJavaModule {
    
    private static final String TAG = "CloudApiModule";
    private final RemoteConfigManager configManager;
    private final OrionAuthManager authManager;
    private boolean isConnected = false;
    
    public CloudApiModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.configManager = RemoteConfigManager.getInstance();
        this.authManager = OrionAuthManager.getInstance();
    }
    
    @Override
    public String getName() {
        return "CloudApiModule";
    }
    
    @ReactMethod
    public void connect(Promise promise) {
        String serverDomain = configManager.getServerDomain();
        String tokenUrl = configManager.getTokenUrl();
        Log.i(TAG, "Conectando a nube OrionStar: " + serverDomain);
        Log.i(TAG, "Token URL: " + tokenUrl);
        
        new Thread(() -> {
            try {
                String token = authManager.getValidToken();
                isConnected = (token != null && !token.isEmpty());
                
                if (isConnected) {
                    Log.i(TAG, "Conexion exitosa, token obtenido");
                } else {
                    Log.w(TAG, "Conexion fallida, sin token");
                }
                promise.resolve(isConnected);
            } catch (Exception e) {
                Log.e(TAG, "Error conectando a la nube: " + e.getMessage());
                isConnected = false;
                promise.resolve(false);
            }
        }).start();
    }
    
    @ReactMethod
    public void getStatus(Promise promise) {
        try {
            JSONObject status = new JSONObject();
            status.put("connected", isConnected);
            status.put("hasToken", authManager.hasValidToken());
            status.put("region", configManager.getActiveRegion());
            status.put("serverDomain", configManager.getServerDomain());
            status.put("aiOpenUrl", configManager.getAiOpenUrl());
            status.put("agentPoiUrl", configManager.getAgentPoiUrl());
            
            promise.resolve(status.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error getting status", e);
            promise.reject("ERROR", "Failed to get status: " + e.getMessage());
        }
    }
    
    @ReactMethod
    public void apiGet(String endpoint, Promise promise) {
        new Thread(() -> {
            try {
                String response = authManager.authenticatedGet(endpoint);
                promise.resolve(response);
            } catch (Exception e) {
                Log.e(TAG, "Error en GET " + endpoint + ": " + e.getMessage());
                promise.reject("API_ERROR", e.getMessage());
            }
        }).start();
    }
    
    @ReactMethod
    public void apiPost(String endpoint, String jsonBody, Promise promise) {
        new Thread(() -> {
            try {
                String response = authManager.authenticatedPost(endpoint, jsonBody);
                promise.resolve(response);
            } catch (Exception e) {
                Log.e(TAG, "Error en POST " + endpoint + ": " + e.getMessage());
                promise.reject("API_ERROR", e.getMessage());
            }
        }).start();
    }
    
    @ReactMethod
    public void getApps(int page, int pageSize, Promise promise) {
        new Thread(() -> {
            try {
                String endpoint = configManager.getString(
                    RemoteConfigManager.KEY_ENDPOINT_APPS_PAGE,
                    RemoteConfigManager.DEFAULT_ENDPOINT_APPS_PAGE
                );
                String response = authManager.authenticatedGet(
                    endpoint + "&page=" + page + "&pageSize=" + pageSize
                );
                promise.resolve(response);
            } catch (Exception e) {
                Log.e(TAG, "Error obteniendo apps: " + e.getMessage());
                promise.reject("API_ERROR", e.getMessage());
            }
        }).start();
    }
}
