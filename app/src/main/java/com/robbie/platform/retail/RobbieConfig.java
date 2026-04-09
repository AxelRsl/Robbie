package com.robbie.platform.retail;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RobbieConfig {
    
    private static final String TAG = "RobbieConfig";
    
    private String persona;
    private String objective;
    private String firebaseCollection;
    private List<Product> products;
    
    public RobbieConfig() {
        this.persona = "Tu nombre es Robbie. Eres un asistente de tienda retail amigable y profesional.";
        this.objective = "Ayudar a los clientes a encontrar productos ideales según sus necesidades.";
        this.firebaseCollection = "products";
        this.products = new ArrayList<>();
    }
    
    public interface ConfigCallback {
        void onSuccess(RobbieConfig config);
        void onError(String error);
    }
    
    public static void loadFromApi(String configApiUrl, ConfigCallback callback) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(configApiUrl)
                        .get()
                        .build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        callback.onError("API error: " + response.code());
                        return;
                    }
                    
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    
                    RobbieConfig config = new RobbieConfig();
                    config.persona = json.optString("persona", config.persona);
                    config.objective = json.optString("objective", config.objective);
                    config.firebaseCollection = json.optString("firebaseCollection", "products");
                    
                    JSONArray productsArray = json.optJSONArray("products");
                    if (productsArray != null) {
                        for (int i = 0; i < productsArray.length(); i++) {
                            JSONObject prodJson = productsArray.getJSONObject(i);
                            Map<String, Object> prodMap = jsonToMap(prodJson);
                            String id = prodJson.optString("id", "prod_" + i);
                            Product product = Product.fromMap(id, prodMap);
                            config.products.add(product);
                        }
                    }
                    
                    Log.d(TAG, "Config loaded: " + config.products.size() + " products");
                    callback.onSuccess(config);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading config from API", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }
    
    private static Map<String, Object> jsonToMap(JSONObject json) throws Exception {
        Map<String, Object> map = new HashMap<>();
        JSONArray names = json.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String key = names.getString(i);
                Object value = json.get(key);
                
                if (value instanceof JSONArray) {
                    JSONArray arr = (JSONArray) value;
                    List<Object> list = new ArrayList<>();
                    for (int j = 0; j < arr.length(); j++) {
                        list.add(arr.get(j));
                    }
                    map.put(key, list);
                } else {
                    map.put(key, value);
                }
            }
        }
        return map;
    }
    
    public String getPersona() { return persona; }
    public String getObjective() { return objective; }
    public String getFirebaseCollection() { return firebaseCollection; }
    public List<Product> getProducts() { return products; }
    
    public void setPersona(String persona) { this.persona = persona; }
    public void setObjective(String objective) { this.objective = objective; }
    public void setFirebaseCollection(String collection) { this.firebaseCollection = collection; }
}
