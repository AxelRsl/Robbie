package com.robbie.platform.react.modules;

import android.util.Log;

import com.robbie.base.config.RemoteConfigManager;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Modulo nativo que gestiona productos.
 *
 * Intenta obtener datos de la API real de OrionBase/AgentPOI.
 * Si la conexion falla, usa datos mock como fallback.
 * Los datos mock sirven para desarrollo y demo sin conexion a la nube.
 */
public class ProductsModule extends ReactContextBaseJavaModule {
    
    private static final String TAG = "ProductsModule";
    private final OrionAuthManager authManager;
    private final RemoteConfigManager configManager;
    private JSONArray cachedProducts = null;
    
    public ProductsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.authManager = OrionAuthManager.getInstance();
        this.configManager = RemoteConfigManager.getInstance();
    }
    
    @Override
    public String getName() {
        return "ProductsModule";
    }
    
    @ReactMethod
    public void getProducts(String category, Promise promise) {
        new Thread(() -> {
            try {
                JSONArray products = fetchProductsFromCloud();
                JSONArray results = new JSONArray();
                
                for (int i = 0; i < products.length(); i++) {
                    JSONObject product = products.getJSONObject(i);
                    if (category.isEmpty() || product.optString("category", "").equals(category)) {
                        results.put(product);
                    }
                }
                
                promise.resolve(results.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error getting products", e);
                promise.reject("ERROR", "Failed to get products: " + e.getMessage());
            }
        }).start();
    }
    
    @ReactMethod
    public void searchProducts(String query, Promise promise) {
        new Thread(() -> {
            try {
                JSONArray products = fetchProductsFromCloud();
                JSONArray results = new JSONArray();
                String lowerQuery = query.toLowerCase();
                
                for (int i = 0; i < products.length(); i++) {
                    JSONObject product = products.getJSONObject(i);
                    String name = product.optString("name", "").toLowerCase();
                    String description = product.optString("description", "").toLowerCase();
                    
                    boolean matches = name.contains(lowerQuery) || description.contains(lowerQuery);
                    
                    if (!matches) {
                        JSONArray tags = product.optJSONArray("tags");
                        if (tags != null) {
                            for (int j = 0; j < tags.length(); j++) {
                                if (tags.getString(j).toLowerCase().contains(lowerQuery)) {
                                    matches = true;
                                    break;
                                }
                            }
                        }
                    }
                    
                    if (matches) {
                        results.put(product);
                    }
                }
                
                JSONObject response = new JSONObject();
                response.put("products", results);
                response.put("query", query);
                response.put("totalResults", results.length());
                
                promise.resolve(response.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error searching products", e);
                promise.reject("ERROR", "Failed to search products: " + e.getMessage());
            }
        }).start();
    }
    
    @ReactMethod
    public void getProductDetails(String productId, Promise promise) {
        new Thread(() -> {
            try {
                JSONArray products = fetchProductsFromCloud();
                
                for (int i = 0; i < products.length(); i++) {
                    JSONObject product = products.getJSONObject(i);
                    if (product.optString("id", "").equals(productId)) {
                        promise.resolve(product.toString());
                        return;
                    }
                }
                promise.resolve(null);
            } catch (Exception e) {
                Log.e(TAG, "Error getting product details", e);
                promise.reject("ERROR", "Failed to get product details: " + e.getMessage());
            }
        }).start();
    }

    @ReactMethod
    public void refreshProducts(Promise promise) {
        cachedProducts = null;
        getProducts("", promise);
    }

    /**
     * Intenta obtener productos de la nube OrionStar.
     * Primero intenta la API de apps/page de OrionBase.
     * Luego intenta el portal AgentPOI.
     * Si ambos fallan, usa datos mock.
     */
    private JSONArray fetchProductsFromCloud() {
        if (cachedProducts != null) {
            Log.d(TAG, "Usando productos en cache (" + cachedProducts.length() + " items)");
            return cachedProducts;
        }

        // Intento 1: API OrionBase - obtener apps/contenido
        try {
            Log.d(TAG, "Consultando API OrionBase para productos...");
            String endpoint = configManager.getString(
                RemoteConfigManager.KEY_ENDPOINT_APPS_PAGE,
                RemoteConfigManager.DEFAULT_ENDPOINT_APPS_PAGE
            );
            String response = authManager.authenticatedGet(endpoint);
            JSONObject json = new JSONObject(response);
            
            if (json.has("data")) {
                JSONObject data = json.getJSONObject("data");
                if (data.has("list")) {
                    JSONArray apps = data.getJSONArray("list");
                    JSONArray products = convertAppsToProducts(apps);
                    if (products.length() > 0) {
                        cachedProducts = products;
                        Log.i(TAG, "Productos obtenidos de OrionBase: " + products.length());
                        return cachedProducts;
                    }
                }
            }
            Log.w(TAG, "OrionBase no devolvio productos validos");
        } catch (Exception e) {
            Log.w(TAG, "No se pudo conectar a OrionBase: " + e.getMessage());
        }

        // Fallback: datos mock para demo/desarrollo
        Log.i(TAG, "Usando datos mock como fallback");
        try {
            cachedProducts = getMockProductsArray();
        } catch (Exception e) {
            cachedProducts = new JSONArray();
        }
        return cachedProducts;
    }

    /**
     * Convierte la lista de apps/OPK de OrionBase en formato de productos.
     */
    private JSONArray convertAppsToProducts(JSONArray apps) throws Exception {
        JSONArray products = new JSONArray();
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.getJSONObject(i);
            JSONObject product = new JSONObject();
            product.put("id", app.optString("appId", String.valueOf(i)));
            product.put("name", app.optString("appName", "App " + i));
            product.put("description", app.optString("description", "Aplicacion OrionStar"));
            product.put("price", 0);
            product.put("currency", "MXN");
            product.put("imageUrl", app.optString("icon", "https://via.placeholder.com/300x300/607D8B/FFFFFF?text=OPK"));
            product.put("category", app.optString("category", "Apps"));
            product.put("stock", 1);
            product.put("tags", new JSONArray().put("opk").put("orionstar").put("app"));
            products.put(product);
        }
        return products;
    }
    
    private JSONArray getMockProductsArray() throws Exception {
        JSONArray products = new JSONArray();
        
        products.put(createProduct("1", "Laptop HP Pavilion", 
            "Laptop de alto rendimiento con procesador Intel Core i7", 
            15999, "https://via.placeholder.com/300x300/4CAF50/FFFFFF?text=Laptop",
            "Electronica", 15, new String[]{"laptop", "computadora", "hp", "tecnologia"}));
        
        products.put(createProduct("2", "Smartphone Samsung Galaxy", 
            "Telefono inteligente con camara de 108MP", 
            12999, "https://via.placeholder.com/300x300/2196F3/FFFFFF?text=Phone",
            "Electronica", 25, new String[]{"smartphone", "telefono", "samsung", "movil"}));
        
        products.put(createProduct("3", "Audifonos Sony WH-1000XM4", 
            "Audifonos con cancelacion de ruido premium", 
            6999, "https://via.placeholder.com/300x300/FF9800/FFFFFF?text=Headphones",
            "Audio", 30, new String[]{"audifonos", "sony", "audio", "musica"}));
        
        products.put(createProduct("4", "Smart TV LG 55 pulgadas", 
            "Televisor 4K UHD con webOS y HDR", 
            18999, "https://via.placeholder.com/300x300/9C27B0/FFFFFF?text=TV",
            "Electronica", 10, new String[]{"tv", "televisor", "lg", "4k"}));
        
        products.put(createProduct("5", "Tablet iPad Air", 
            "Tablet Apple con chip M1 y pantalla Liquid Retina", 
            14999, "https://via.placeholder.com/300x300/E91E63/FFFFFF?text=Tablet",
            "Electronica", 20, new String[]{"tablet", "ipad", "apple", "portatil"}));
        
        products.put(createProduct("6", "Camara Canon EOS R6", 
            "Camara mirrorless profesional full-frame", 
            45999, "https://via.placeholder.com/300x300/00BCD4/FFFFFF?text=Camera",
            "Fotografia", 5, new String[]{"camara", "fotografia", "canon", "profesional"}));
        
        return products;
    }
    
    private JSONObject createProduct(String id, String name, String description, 
            int price, String imageUrl, String category, int stock, String[] tags) throws Exception {
        JSONObject product = new JSONObject();
        product.put("id", id);
        product.put("name", name);
        product.put("description", description);
        product.put("price", price);
        product.put("currency", "MXN");
        product.put("imageUrl", imageUrl);
        product.put("category", category);
        product.put("stock", stock);
        JSONArray tagsArray = new JSONArray();
        for (String tag : tags) {
            tagsArray.put(tag);
        }
        product.put("tags", tagsArray);
        return product;
    }
}
