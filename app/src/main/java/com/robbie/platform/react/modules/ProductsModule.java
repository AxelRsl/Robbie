package com.robbie.platform.react.modules;

import android.util.Log;

import com.robbie.base.config.RemoteConfigManager;
import com.robbie.base.config.RobbieConfigApiClient;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import com.robbie.data.local.RobbieDatabase;
import com.robbie.data.local.entity.ProductEntity;

/**
 * Modulo nativo que gestiona productos.
 *
 * Intenta obtener datos de la API real de OrionBase/AgentPOI.
 * Si la conexion falla, usa datos mock como fallback.
 * Los datos mock sirven para desarrollo y demo sin conexion a la nube.
 */
public class ProductsModule extends ReactContextBaseJavaModule {
    
    private static final String TAG = "ProductsModule";
    private final RobbieConfigApiClient apiClient;
    private JSONArray cachedProducts = null;
    private boolean cachedFromDb = false;
    
    public ProductsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.apiClient = new RobbieConfigApiClient();
        Log.i(TAG, "ProductsModule inicializado");
    }
    
    @Override
    public String getName() {
        return "ProductsModule";
    }
    
    @ReactMethod
    public void getProducts(String category, Promise promise) {
        Log.d(TAG, "getProducts llamado con category: '" + category + "'");
        new Thread(() -> {
            try {
                JSONArray products = fetchProductsFromCloud();
                Log.d(TAG, "Productos obtenidos: " + products.length() + " items");
                JSONArray results = new JSONArray();
                
                for (int i = 0; i < products.length(); i++) {
                    JSONObject product = products.getJSONObject(i);
                    if (category.isEmpty() || product.optString("category", "").equals(category)) {
                        results.put(product);
                    }
                }
                
                Log.i(TAG, "Retornando " + results.length() + " productos (filtrados por category)");
                promise.resolve(results.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error getting products", e);
                promise.reject("ERROR", "Failed to get products: " + e.getMessage());
            }
        }).start();
    }
    
    @ReactMethod
    public void searchProducts(String query, Promise promise) {
        Log.d(TAG, "searchProducts llamado con query: '" + query + "'");
        new Thread(() -> {
            try {
                JSONArray products = fetchProductsFromCloud();
                Log.d(TAG, "Buscando en " + products.length() + " productos");
                JSONArray results = new JSONArray();
                String lowerQuery = normalizeAccents(query.toLowerCase());
                
                for (int i = 0; i < products.length(); i++) {
                    JSONObject product = products.getJSONObject(i);
                    String name = normalizeAccents(product.optString("name", "").toLowerCase());
                    String description = normalizeAccents(product.optString("description", "").toLowerCase());
                    String category = normalizeAccents(product.optString("category", "").toLowerCase());
                    String brand = normalizeAccents(product.optString("brand", "").toLowerCase());
                    
                    boolean matches = name.contains(lowerQuery) || 
                                     description.contains(lowerQuery) ||
                                     category.contains(lowerQuery) ||
                                     brand.contains(lowerQuery);
                    
                    if (!matches) {
                        JSONArray tags = product.optJSONArray("tags");
                        if (tags != null) {
                            for (int j = 0; j < tags.length(); j++) {
                                if (normalizeAccents(tags.getString(j).toLowerCase()).contains(lowerQuery)) {
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
                
                Log.i(TAG, "Busqueda completada: " + results.length() + " resultados para '" + query + "'");
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
        Log.i(TAG, "refreshProducts llamado - limpiando cache");
        cachedProducts = null;
        cachedFromDb = false;
        getProducts("", promise);
    }

    /**
     * Obtiene productos de la base de datos local.
     * Prioridad: 1) DB Local (siempre verificar), 2) Cache de API, 3) API remota, 4) Mock (sin cache)
     */
    private JSONArray fetchProductsFromCloud() {
        // SIEMPRE verificar Room DB primero - los productos pueden haber sido
        // subidos via CSV desde el panel despues del ultimo cache
        try {
            Log.d(TAG, "[DB LOCAL] Consultando base de datos local...");
            RobbieDatabase db = RobbieDatabase.getInstance(getReactApplicationContext());
            List<ProductEntity> localProducts = db.productDao().getAllProductsBlocking();
            
            if (localProducts != null && !localProducts.isEmpty()) {
                // Siempre releer de DB — es rapido y evita datos stale
                // cuando el panel actualiza nombres, precios, etc.
                Log.i(TAG, "[DB LOCAL] Encontrados " + localProducts.size() + " productos en DB local");
                JSONArray array = new JSONArray();
                for (ProductEntity p : localProducts) {
                    JSONObject product = new JSONObject();
                    product.put("id", p.getId());
                    product.put("name", p.getName());
                    product.put("description", p.getDescription());
                    product.put("price", p.getPrice());
                    product.put("currency", "MXN");
                    product.put("imageUrl", p.getImage());
                    product.put("category", p.getCategory());
                    product.put("subcategory", p.getSubcategory());
                    product.put("brand", p.getBrand());
                    product.put("sku", p.getSku());
                    product.put("inStock", p.getInStock());
                    product.put("discount", p.getDiscount());
                    JSONArray tagsArray = new JSONArray();
                    for (String tag : p.getTags()) {
                        tagsArray.put(tag);
                    }
                    product.put("tags", tagsArray);
                    array.put(product);
                }
                cachedProducts = array;
                cachedFromDb = true;
                Log.i(TAG, "[DB LOCAL] Retornando " + array.length() + " productos de DB local");
                return cachedProducts;
            } else {
                Log.w(TAG, "[DB LOCAL] No hay productos en la base de datos local");
            }
        } catch (Exception e) {
            Log.e(TAG, "[DB LOCAL] Error consultando DB local", e);
        }

        // Si hay cache de API, usarla mientras DB esta vacia
        if (cachedProducts != null && !cachedFromDb) {
            Log.d(TAG, "[CACHE-API] Usando cache de API (" + cachedProducts.length() + " items)");
            return cachedProducts;
        }

        // SEGUNDO: Intentar API remota
        Log.i(TAG, "[API] Consultando API de configuracion Robbie para productos...");
        
        final JSONArray[] result = new JSONArray[1];
        final boolean[] completed = new boolean[1];
        
        apiClient.getProducts(new RobbieConfigApiClient.ProductsCallback() {
            @Override
            public void onSuccess(List<JSONObject> products) {
                Log.i(TAG, "[API] Productos obtenidos exitosamente: " + products.size() + " items");
                try {
                    JSONArray array = new JSONArray();
                    for (JSONObject product : products) {
                        array.put(product);
                    }
                    result[0] = array;
                    cachedProducts = array;
                } catch (Exception e) {
                    Log.e(TAG, "[API] Error convirtiendo productos", e);
                    result[0] = null;
                }
                completed[0] = true;
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "[API] Error obteniendo productos: " + error);
                result[0] = null;
                completed[0] = true;
            }
        });
        
        // Esperar respuesta (max 5 segundos)
        int timeout = 0;
        while (!completed[0] && timeout < 50) {
            try {
                Thread.sleep(100);
                timeout++;
            } catch (InterruptedException e) {
                break;
            }
        }
        
        if (result[0] != null && result[0].length() > 0) {
            Log.i(TAG, "[API] Retornando " + result[0].length() + " productos de la API");
            return result[0];
        }
        
        // TERCERO: Fallback a datos mock (NO cachear mock - permite que DB los reemplace)
        Log.w(TAG, "[FALLBACK] API no respondio o fallo, usando datos mock");
        try {
            JSONArray mockProducts = getMockProductsArray();
            Log.i(TAG, "[FALLBACK] Usando " + mockProducts.length() + " productos mock (sin cache)");
            return mockProducts;
        } catch (Exception e) {
            Log.e(TAG, "[FALLBACK] Error creando productos mock", e);
            return new JSONArray();
        }
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
        product.put("inStock", true);
        product.put("stock", stock);
        JSONArray tagsArray = new JSONArray();
        for (String tag : tags) {
            tagsArray.put(tag);
        }
        product.put("tags", tagsArray);
        return product;
    }

    private String normalizeAccents(String text) {
        if (text == null) return "";
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }
}
