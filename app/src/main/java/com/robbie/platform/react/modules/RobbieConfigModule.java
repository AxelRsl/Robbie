package com.robbie.platform.react.modules;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.robbie.base.config.RobbieConfigApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

public class RobbieConfigModule extends ReactContextBaseJavaModule {
    
    private static final String TAG = "RobbieConfigModule";
    private final RobbieConfigApiClient apiClient;
    
    public RobbieConfigModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.apiClient = new RobbieConfigApiClient();
    }
    
    @Override
    public String getName() {
        return "RobbieConfig";
    }
    
    @ReactMethod
    public void getConfig(Promise promise) {
        Log.i(TAG, "[RobbieConfig] getConfig llamado");
        apiClient.getConfig(new RobbieConfigApiClient.ConfigCallback() {
            @Override
            public void onSuccess(JSONObject config) {
                try {
                    Log.i(TAG, "[RobbieConfig] Configuración recibida");
                    promise.resolve(config.toString());
                } catch (Exception e) {
                    Log.e(TAG, "[RobbieConfig] Error converting config", e);
                    promise.reject("PARSE_ERROR", e.getMessage());
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "[RobbieConfig] Error obteniendo configuración: " + error);
                promise.reject("API_ERROR", error);
            }
        });
    }
    
    @ReactMethod
    public void getProducts(Promise promise) {
        Log.i(TAG, "[RobbieConfig] getProducts llamado");
        apiClient.getProducts(new RobbieConfigApiClient.ProductsCallback() {
            @Override
            public void onSuccess(List<JSONObject> products) {
                Log.i(TAG, "[RobbieConfig] Productos recibidos: " + products.size() + " items");
                try {
                    WritableArray array = new WritableNativeArray();
                    for (JSONObject product : products) {
                        Log.d(TAG, "[RobbieConfig] Producto: " + product.optString("name", "sin nombre"));
                        array.pushMap(jsonToWritableMap(product));
                    }
                    Log.i(TAG, "[RobbieConfig] Retornando " + products.size() + " productos a React Native");
                    promise.resolve(array);
                } catch (Exception e) {
                    Log.e(TAG, "[RobbieConfig] Error converting products", e);
                    promise.reject("PARSE_ERROR", e.getMessage());
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "[RobbieConfig] Error obteniendo productos: " + error);
                promise.reject("API_ERROR", error);
            }
        });
    }
    
    @ReactMethod
    public void updateConfig(ReadableMap config, Promise promise) {
        try {
            JSONObject jsonConfig = readableMapToJson(config);
            apiClient.updateConfig(jsonConfig, new RobbieConfigApiClient.ConfigCallback() {
                @Override
                public void onSuccess(JSONObject updatedConfig) {
                    try {
                        WritableMap map = jsonToWritableMap(updatedConfig);
                        promise.resolve(map);
                    } catch (Exception e) {
                        Log.e(TAG, "Error converting updated config", e);
                        promise.reject("PARSE_ERROR", e.getMessage());
                    }
                }
                
                @Override
                public void onError(String error) {
                    promise.reject("API_ERROR", error);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error parsing config", e);
            promise.reject("PARSE_ERROR", e.getMessage());
        }
    }
    
    @ReactMethod
    public void resetConfig(Promise promise) {
        apiClient.resetConfig(new RobbieConfigApiClient.ConfigCallback() {
            @Override
            public void onSuccess(JSONObject config) {
                try {
                    WritableMap map = jsonToWritableMap(config);
                    promise.resolve(map);
                } catch (Exception e) {
                    Log.e(TAG, "Error converting reset config", e);
                    promise.reject("PARSE_ERROR", e.getMessage());
                }
            }
            
            @Override
            public void onError(String error) {
                promise.reject("API_ERROR", error);
            }
        });
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
    
    private JSONObject readableMapToJson(ReadableMap readableMap) throws Exception {
        JSONObject jsonObject = new JSONObject();
        com.facebook.react.bridge.ReadableMapKeySetIterator iterator = readableMap.keySetIterator();
        
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            com.facebook.react.bridge.ReadableType type = readableMap.getType(key);
            
            switch (type) {
                case Null:
                    jsonObject.put(key, JSONObject.NULL);
                    break;
                case Boolean:
                    jsonObject.put(key, readableMap.getBoolean(key));
                    break;
                case Number:
                    jsonObject.put(key, readableMap.getDouble(key));
                    break;
                case String:
                    jsonObject.put(key, readableMap.getString(key));
                    break;
                case Map:
                    jsonObject.put(key, readableMapToJson(readableMap.getMap(key)));
                    break;
                case Array:
                    jsonObject.put(key, readableArrayToJson(readableMap.getArray(key)));
                    break;
            }
        }
        
        return jsonObject;
    }
    
    private JSONArray readableArrayToJson(com.facebook.react.bridge.ReadableArray readableArray) throws Exception {
        JSONArray jsonArray = new JSONArray();
        
        for (int i = 0; i < readableArray.size(); i++) {
            com.facebook.react.bridge.ReadableType type = readableArray.getType(i);
            
            switch (type) {
                case Null:
                    jsonArray.put(JSONObject.NULL);
                    break;
                case Boolean:
                    jsonArray.put(readableArray.getBoolean(i));
                    break;
                case Number:
                    jsonArray.put(readableArray.getDouble(i));
                    break;
                case String:
                    jsonArray.put(readableArray.getString(i));
                    break;
                case Map:
                    jsonArray.put(readableMapToJson(readableArray.getMap(i)));
                    break;
                case Array:
                    jsonArray.put(readableArrayToJson(readableArray.getArray(i)));
                    break;
            }
        }
        
        return jsonArray;
    }
}
