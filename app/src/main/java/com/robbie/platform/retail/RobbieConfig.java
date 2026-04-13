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
    private String storeName;
    private String firebaseCollection;
    private List<Product> products;
    private List<ActionConfig> actions;
    private String pageObjective;
    private List<String> validScreens;
    
    public RobbieConfig() {
        this.persona = "Tu nombre es Robbie. Eres un asistente de tienda retail amigable y profesional.";
        this.objective = "Ayudar a los clientes a encontrar productos ideales según sus necesidades.";
        this.storeName = "GNC";
        this.firebaseCollection = "products";
        this.products = new ArrayList<>();
        this.actions = new ArrayList<>();
        this.pageObjective = "Estas en la aplicacion de retail GNC. Puedes ayudar al cliente a encontrar productos, recomendar suplementos y navegar entre pantallas.";
        this.validScreens = new ArrayList<>();
        this.validScreens.add("Home");
        this.validScreens.add("Products");
        this.validScreens.add("Cart");
        this.validScreens.add("Profile");
        this.validScreens.add("Search");
        initializeDefaultActions();
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
                    config.storeName = json.optString("storeName", config.storeName);
                    config.firebaseCollection = json.optString("firebaseCollection", "products");
                    
                    // Cargar productos
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
                    
                    // Cargar configuracion de PageAgent
                    config.pageObjective = json.optString("pageObjective", config.pageObjective);
                    
                    // Cargar pantallas validas
                    JSONArray screensArray = json.optJSONArray("validScreens");
                    if (screensArray != null) {
                        config.validScreens.clear();
                        for (int i = 0; i < screensArray.length(); i++) {
                            config.validScreens.add(screensArray.getString(i));
                        }
                    }
                    
                    // Cargar Actions personalizadas
                    JSONArray actionsArray = json.optJSONArray("actions");
                    if (actionsArray != null) {
                        config.actions.clear();
                        for (int i = 0; i < actionsArray.length(); i++) {
                            JSONObject actionJson = actionsArray.getJSONObject(i);
                            ActionConfig actionConfig = ActionConfig.fromJson(actionJson);
                            config.actions.add(actionConfig);
                        }
                    } else {
                        config.initializeDefaultActions();
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
    
    private void initializeDefaultActions() {
        actions.clear();
        
        // Action 1: Recomendar productos
        ActionConfig recommend = new ActionConfig();
        recommend.actionId = "com.robbie.RECOMMEND_PRODUCTS";
        recommend.displayName = "Recomendar productos";
        recommend.description = "Recomienda productos GNC basandose en las necesidades del usuario y restricciones dieteticas";
        recommend.addParameter("user_need", "STRING", "Lo que necesita el usuario: proteina, vitaminas, energia, bajar de peso, ganar musculo, etc.", true);
        recommend.addParameter("dietary_restriction", "STRING", "Restriccion dietetica: sin lactosa, vegano, sin gluten, keto, diabetico. Vacio si no hay.", false);
        recommend.eventName = "recommend_products";
        actions.add(recommend);
        
        // Action 2: Buscar productos
        ActionConfig search = new ActionConfig();
        search.actionId = "com.robbie.SEARCH_PRODUCTS";
        search.displayName = "Buscar productos";
        search.description = "Busca productos en el catalogo GNC por nombre, categoria o marca";
        search.addParameter("query", "STRING", "Texto de busqueda: nombre del producto, categoria o marca", true);
        search.eventName = "search_products";
        actions.add(search);
        
        // Action 3: Mostrar detalle de producto
        ActionConfig detail = new ActionConfig();
        detail.actionId = "com.robbie.SHOW_PRODUCT_DETAIL";
        detail.displayName = "Detalle de producto";
        detail.description = "Muestra y describe vocalmente los detalles de un producto especifico por nombre";
        detail.addParameter("product_name", "STRING", "Nombre o parte del nombre del producto a mostrar", true);
        detail.eventName = "show_product_detail";
        actions.add(detail);
        
        // Action 4: Navegar a pantallas
        ActionConfig navigate = new ActionConfig();
        navigate.actionId = "com.robbie.NAVIGATE_TO_SCREEN";
        navigate.displayName = "Navegar a pantalla";
        navigate.description = "Navega a una pantalla especifica de la aplicacion";
        navigate.addParameter("screen", "ENUM", "Pantalla destino: Home, Products, Cart, Profile, Search", true);
        navigate.enumValues = new ArrayList<>(validScreens);
        navigate.eventName = "navigate_to_screen";
        actions.add(navigate);
    }
    
    public String getPersona() { return persona; }
    public String getObjective() { return objective; }
    public String getStoreName() { return storeName; }
    public String getFirebaseCollection() { return firebaseCollection; }
    public List<Product> getProducts() { return products; }
    public List<ActionConfig> getActions() { return actions; }
    public String getPageObjective() { return pageObjective; }
    public List<String> getValidScreens() { return validScreens; }
    
    public void setPersona(String persona) { this.persona = persona; }
    public void setObjective(String objective) { this.objective = objective; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public void setFirebaseCollection(String collection) { this.firebaseCollection = collection; }
    
    public static class ActionConfig {
        public String actionId;
        public String displayName;
        public String description;
        public List<ParameterConfig> parameters = new ArrayList<>();
        public List<String> enumValues;
        public String eventName;
        
        public void addParameter(String name, String type, String description, boolean required) {
            ParameterConfig param = new ParameterConfig();
            param.name = name;
            param.type = type;
            param.description = description;
            param.required = required;
            parameters.add(param);
        }
        
        public static ActionConfig fromJson(JSONObject json) throws Exception {
            ActionConfig config = new ActionConfig();
            config.actionId = json.getString("actionId");
            config.displayName = json.optString("displayName", "");
            config.description = json.optString("description", "");
            config.eventName = json.optString("eventName", "");
            
            JSONArray paramsArray = json.optJSONArray("parameters");
            if (paramsArray != null) {
                for (int i = 0; i < paramsArray.length(); i++) {
                    JSONObject paramJson = paramsArray.getJSONObject(i);
                    ParameterConfig param = ParameterConfig.fromJson(paramJson);
                    config.parameters.add(param);
                }
            }
            
            JSONArray enumArray = json.optJSONArray("enumValues");
            if (enumArray != null) {
                config.enumValues = new ArrayList<>();
                for (int i = 0; i < enumArray.length(); i++) {
                    config.enumValues.add(enumArray.getString(i));
                }
            }
            
            return config;
        }
    }
    
    public static class ParameterConfig {
        public String name;
        public String type;
        public String description;
        public boolean required;
        
        public static ParameterConfig fromJson(JSONObject json) throws Exception {
            ParameterConfig config = new ParameterConfig();
            config.name = json.getString("name");
            config.type = json.getString("type");
            config.description = json.optString("description", "");
            config.required = json.optBoolean("required", false);
            return config;
        }
    }
}
