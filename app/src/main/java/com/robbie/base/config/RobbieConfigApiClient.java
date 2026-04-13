package com.robbie.base.config;

import android.util.Log;

import com.robbie.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RobbieConfigApiClient {
    
    private static final String TAG = "RobbieConfigApiClient";
    private static final String BASE_URL = BuildConfig.ROBBIE_CONFIG_API_URL;
    private static final int TIMEOUT = 10000;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    public interface ConfigCallback {
        void onSuccess(JSONObject config);
        void onError(String error);
    }
    
    public interface ProductsCallback {
        void onSuccess(List<JSONObject> products);
        void onError(String error);
    }
    
    public void getConfig(ConfigCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "/robbie/config");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);
                
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONObject config = new JSONObject(response.toString());
                    callback.onSuccess(config);
                } else {
                    callback.onError("HTTP " + responseCode);
                }
                
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error getting config", e);
                callback.onError(e.getMessage());
            }
        });
    }
    
    public void updateConfig(JSONObject config, ConfigCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "/robbie/config");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);
                conn.setDoOutput(true);
                
                OutputStream os = conn.getOutputStream();
                os.write(config.toString().getBytes("UTF-8"));
                os.close();
                
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONObject result = new JSONObject(response.toString());
                    callback.onSuccess(result.getJSONObject("config"));
                } else {
                    callback.onError("HTTP " + responseCode);
                }
                
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error updating config", e);
                callback.onError(e.getMessage());
            }
        });
    }
    
    public void resetConfig(ConfigCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "/robbie/config/reset");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);
                
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONObject result = new JSONObject(response.toString());
                    callback.onSuccess(result.getJSONObject("config"));
                } else {
                    callback.onError("HTTP " + responseCode);
                }
                
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error resetting config", e);
                callback.onError(e.getMessage());
            }
        });
    }
    
    public void getProducts(ProductsCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "/robbie/config/products");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);
                
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONObject result = new JSONObject(response.toString());
                    JSONArray productsArray = result.getJSONArray("products");
                    
                    List<JSONObject> products = new ArrayList<>();
                    for (int i = 0; i < productsArray.length(); i++) {
                        products.add(productsArray.getJSONObject(i));
                    }
                    
                    callback.onSuccess(products);
                } else {
                    callback.onError("HTTP " + responseCode);
                }
                
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error getting products", e);
                callback.onError(e.getMessage());
            }
        });
    }
    
    public void addProduct(JSONObject product, ConfigCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "/robbie/config/products");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);
                conn.setDoOutput(true);
                
                OutputStream os = conn.getOutputStream();
                os.write(product.toString().getBytes("UTF-8"));
                os.close();
                
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONObject result = new JSONObject(response.toString());
                    callback.onSuccess(result.getJSONObject("product"));
                } else {
                    callback.onError("HTTP " + responseCode);
                }
                
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error adding product", e);
                callback.onError(e.getMessage());
            }
        });
    }
}
