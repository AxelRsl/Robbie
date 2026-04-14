package com.robbie.data.server;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.robbie.data.local.RobbieDatabase;
import com.robbie.data.local.entity.ConfigEntity;
import com.robbie.data.local.entity.MapEntity;
import com.robbie.data.local.entity.ProductEntity;
import com.robbie.data.local.entity.TourStopEntity;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;

public class RobbieApiServer extends NanoHTTPD {
    
    private static final String TAG = "RobbieApiServer";
    public static final int DEFAULT_PORT = 8080;
    
    private final Context context;
    private RobbieDatabase database;
    private final Gson gson;
    
    public RobbieApiServer(Context context) {
        this(context, DEFAULT_PORT);
    }
    
    public RobbieApiServer(Context context, int port) {
        super(port);
        this.context = context;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    private RobbieDatabase getDatabase() {
        if (database == null) {
            database = RobbieDatabase.getInstance(context);
        }
        return database;
    }
    
    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        
        Log.d(TAG, "Request: " + method + " " + uri);
        
        Response response;
        try {
            response = routeRequest(method, uri, session);
        } catch (Exception e) {
            Log.e(TAG, "Error processing request", e);
            response = jsonResponse(Response.Status.INTERNAL_ERROR, 
                mapOf("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
        
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        return response;
    }
    
    private Response routeRequest(Method method, String uri, IHTTPSession session) {
        if (method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "");
        }
        
        String[] parts = uri.split("/");
        List<String> partsList = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) partsList.add(part);
        }
        
        if (partsList.isEmpty() || !partsList.get(0).equals("api")) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Not found"));
        }
        
        if (partsList.size() < 2) {
            return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Invalid endpoint"));
        }
        
        String endpoint = partsList.get(1);
        switch (endpoint) {
            case "products":
                return handleProducts(method, partsList, session);
            case "maps":
                return handleMaps(method, partsList, session);
            case "tour-stops":
                return handleTourStops(method, partsList, session);
            case "config":
                return handleConfig(method, partsList, session);
            case "health":
                return handleHealth();
            default:
                return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Unknown endpoint"));
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // HEALTH
    // ─────────────────────────────────────────────────────────────────────────
    
    private Response handleHealth() {
        int productCount = getDatabase().productDao().getProductCount();
        Map<String, Object> health = new HashMap<>();
        health.put("status", "ok");
        health.put("server", "RobbieApiServer");
        health.put("version", "1.0.0");
        health.put("productCount", productCount);
        return jsonResponse(Response.Status.OK, health);
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // PRODUCTS
    // ─────────────────────────────────────────────────────────────────────────
    
    private Response handleProducts(Method method, List<String> parts, IHTTPSession session) {
        String id = parts.size() > 2 ? parts.get(2) : null;
        
        if (method == Method.POST && "bulk".equals(id)) {
            return bulkCreateProducts(session);
        }
        
        if (method == Method.DELETE && "all".equals(id)) {
            return deleteAllProducts();
        }
        
        switch (method) {
            case GET:
                return id != null ? getProductById(id) : getAllProducts();
            case POST:
                return createProduct(session);
            case PUT:
                return id != null ? updateProduct(id, session) : 
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Product ID required"));
            case DELETE:
                return id != null ? deleteProduct(id) : 
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Product ID required"));
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
        }
    }
    
    private Response getAllProducts() {
        List<ProductEntity> products = getDatabase().productDao().getAllProductsSync();
        return jsonResponse(Response.Status.OK, products);
    }
    
    private Response getProductById(String id) {
        ProductEntity product = getDatabase().productDao().getProductById(id);
        if (product != null) {
            return jsonResponse(Response.Status.OK, product);
        }
        return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Product not found"));
    }
    
    private Response createProduct(IHTTPSession session) {
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> json = gson.fromJson(body, mapType);
        
        ProductEntity product = new ProductEntity();
        product.setId(getStringOrDefault(json, "id", UUID.randomUUID().toString()));
        product.setName(getStringOrDefault(json, "name", ""));
        product.setCategory(getStringOrDefault(json, "category", ""));
        product.setSubcategory(getStringOrDefault(json, "subcategory", ""));
        product.setPrice(getDoubleOrDefault(json, "price", 0.0));
        product.setDiscount(getIntOrDefault(json, "discount", 0));
        product.setImage(getStringOrDefault(json, "image", ""));
        product.setDescription(getStringOrDefault(json, "description", ""));
        product.setIngredients(getStringOrDefault(json, "ingredients", ""));
        product.setTags(getStringListOrDefault(json, "tags"));
        product.setInStock(getBooleanOrDefault(json, "inStock", true));
        product.setSku(getStringOrDefault(json, "sku", ""));
        product.setBrand(getStringOrDefault(json, "brand", ""));
        product.setCreatedAt(System.currentTimeMillis());
        product.setUpdatedAt(System.currentTimeMillis());
        
        getDatabase().productDao().insertProduct(product);
        return jsonResponse(Response.Status.CREATED, product);
    }
    
    private Response updateProduct(String id, IHTTPSession session) {
        ProductEntity existing = getDatabase().productDao().getProductById(id);
        if (existing == null) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Product not found"));
        }
        
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> json = gson.fromJson(body, mapType);
        
        existing.setName(getStringOrDefault(json, "name", existing.getName()));
        existing.setCategory(getStringOrDefault(json, "category", existing.getCategory()));
        existing.setSubcategory(getStringOrDefault(json, "subcategory", existing.getSubcategory()));
        existing.setPrice(getDoubleOrDefault(json, "price", existing.getPrice()));
        existing.setDiscount(getIntOrDefault(json, "discount", existing.getDiscount()));
        existing.setImage(getStringOrDefault(json, "image", existing.getImage()));
        existing.setDescription(getStringOrDefault(json, "description", existing.getDescription()));
        existing.setIngredients(getStringOrDefault(json, "ingredients", existing.getIngredients()));
        if (json.containsKey("tags")) {
            existing.setTags(getStringListOrDefault(json, "tags"));
        }
        if (json.containsKey("inStock")) {
            existing.setInStock(getBooleanOrDefault(json, "inStock", existing.getInStock()));
        }
        existing.setSku(getStringOrDefault(json, "sku", existing.getSku()));
        existing.setBrand(getStringOrDefault(json, "brand", existing.getBrand()));
        existing.setUpdatedAt(System.currentTimeMillis());
        
        getDatabase().productDao().updateProduct(existing);
        return jsonResponse(Response.Status.OK, existing);
    }
    
    private Response deleteProduct(String id) {
        getDatabase().productDao().deleteProductById(id);
        return jsonResponse(Response.Status.OK, mapOf("message", "Product deleted"));
    }
    
    private Response deleteAllProducts() {
        getDatabase().productDao().deleteAllProducts();
        return jsonResponse(Response.Status.OK, mapOf("message", "All products deleted"));
    }
    
    private Response bulkCreateProducts(IHTTPSession session) {
        String body = getRequestBody(session);
        Log.d(TAG, "Bulk create: received " + body.length() + " bytes");
        
        if (body.equals("{}") || body.trim().isEmpty()) {
            return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Empty request body"));
        }
        
        List<Map<String, Object>> jsonArray;
        try {
            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            jsonArray = gson.fromJson(body, listType);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing JSON", e);
            return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Invalid JSON: " + e.getMessage()));
        }
        
        Log.d(TAG, "Bulk create: parsing " + jsonArray.size() + " products");
        
        List<ProductEntity> products = new ArrayList<>();
        for (Map<String, Object> json : jsonArray) {
            ProductEntity product = new ProductEntity();
            product.setId(getStringOrDefault(json, "id", UUID.randomUUID().toString()));
            product.setName(getStringOrDefault(json, "name", ""));
            product.setCategory(getStringOrDefault(json, "category", ""));
            product.setSubcategory(getStringOrDefault(json, "subcategory", ""));
            product.setPrice(getDoubleOrDefault(json, "price", 0.0));
            product.setDiscount(getIntOrDefault(json, "discount", 0));
            product.setImage(getStringOrDefault(json, "image", ""));
            product.setDescription(getStringOrDefault(json, "description", ""));
            product.setIngredients(getStringOrDefault(json, "ingredients", ""));
            product.setTags(getStringListOrDefault(json, "tags"));
            product.setInStock(getBooleanOrDefault(json, "inStock", true));
            product.setSku(getStringOrDefault(json, "sku", ""));
            product.setBrand(getStringOrDefault(json, "brand", ""));
            product.setCreatedAt(System.currentTimeMillis());
            product.setUpdatedAt(System.currentTimeMillis());
            products.add(product);
        }
        
        getDatabase().productDao().insertProducts(products);
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Products created");
        result.put("count", products.size());
        return jsonResponse(Response.Status.CREATED, result);
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // MAPS
    // ─────────────────────────────────────────────────────────────────────────
    
    private Response handleMaps(Method method, List<String> parts, IHTTPSession session) {
        String id = parts.size() > 2 ? parts.get(2) : null;
        String action = parts.size() > 3 ? parts.get(3) : null;
        
        if (method == Method.POST && id != null && "activate".equals(action)) {
            return activateMap(id);
        }
        
        switch (method) {
            case GET:
                return id != null ? getMapById(id) : getAllMaps();
            case POST:
                return createMap(session);
            case PUT:
                return id != null ? updateMap(id, session) : 
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Map ID required"));
            case DELETE:
                return id != null ? deleteMap(id) : 
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Map ID required"));
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
        }
    }
    
    private Response getAllMaps() {
        List<MapEntity> maps = getDatabase().mapDao().getAllMapsSync();
        return jsonResponse(Response.Status.OK, maps);
    }
    
    private Response getMapById(String id) {
        MapEntity map = getDatabase().mapDao().getMapById(id);
        if (map != null) {
            return jsonResponse(Response.Status.OK, map);
        }
        return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Map not found"));
    }
    
    private Response createMap(IHTTPSession session) {
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> json = gson.fromJson(body, mapType);
        
        MapEntity map = new MapEntity();
        map.setId(getStringOrDefault(json, "id", UUID.randomUUID().toString()));
        map.setName(getStringOrDefault(json, "name", ""));
        map.setDescription(getStringOrDefault(json, "description", ""));
        map.setMapData(getStringOrDefault(json, "mapData", ""));
        map.setImageUrl(getStringOrDefault(json, "imageUrl", ""));
        map.setIsActive(getBooleanOrDefault(json, "isActive", false));
        map.setCreatedAt(System.currentTimeMillis());
        map.setUpdatedAt(System.currentTimeMillis());
        
        getDatabase().mapDao().insertMap(map);
        return jsonResponse(Response.Status.CREATED, map);
    }
    
    private Response updateMap(String id, IHTTPSession session) {
        MapEntity existing = getDatabase().mapDao().getMapById(id);
        if (existing == null) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Map not found"));
        }
        
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> json = gson.fromJson(body, mapType);
        
        existing.setName(getStringOrDefault(json, "name", existing.getName()));
        existing.setDescription(getStringOrDefault(json, "description", existing.getDescription()));
        existing.setMapData(getStringOrDefault(json, "mapData", existing.getMapData()));
        existing.setImageUrl(getStringOrDefault(json, "imageUrl", existing.getImageUrl()));
        if (json.containsKey("isActive")) {
            existing.setIsActive(getBooleanOrDefault(json, "isActive", existing.getIsActive()));
        }
        existing.setUpdatedAt(System.currentTimeMillis());
        
        getDatabase().mapDao().updateMap(existing);
        return jsonResponse(Response.Status.OK, existing);
    }
    
    private Response activateMap(String id) {
        MapEntity map = getDatabase().mapDao().getMapById(id);
        if (map == null) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Map not found"));
        }
        
        getDatabase().mapDao().deactivateAllMaps();
        getDatabase().mapDao().activateMap(id);
        
        map.setIsActive(true);
        return jsonResponse(Response.Status.OK, map);
    }
    
    private Response deleteMap(String id) {
        getDatabase().mapDao().deleteMapById(id);
        return jsonResponse(Response.Status.OK, mapOf("message", "Map deleted"));
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // TOUR STOPS
    // ─────────────────────────────────────────────────────────────────────────
    
    private Response handleTourStops(Method method, List<String> parts, IHTTPSession session) {
        String id = parts.size() > 2 ? parts.get(2) : null;
        
        switch (method) {
            case GET:
                return id != null ? getTourStopById(id) : getAllTourStops();
            case POST:
                return createTourStop(session);
            case PUT:
                return id != null ? updateTourStop(id, session) : 
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Tour stop ID required"));
            case DELETE:
                return id != null ? deleteTourStop(id) : 
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Tour stop ID required"));
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
        }
    }
    
    private Response getAllTourStops() {
        List<TourStopEntity> stops = getDatabase().tourStopDao().getAllTourStopsSync();
        return jsonResponse(Response.Status.OK, stops);
    }
    
    private Response getTourStopById(String id) {
        TourStopEntity stop = getDatabase().tourStopDao().getTourStopById(id);
        if (stop != null) {
            return jsonResponse(Response.Status.OK, stop);
        }
        return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Tour stop not found"));
    }
    
    private Response createTourStop(IHTTPSession session) {
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> json = gson.fromJson(body, mapType);
        
        TourStopEntity stop = new TourStopEntity();
        stop.setId(getStringOrDefault(json, "id", UUID.randomUUID().toString()));
        stop.setName(getStringOrDefault(json, "name", ""));
        stop.setDescription(getStringOrDefault(json, "description", ""));
        stop.setLocationId(getStringOrDefault(json, "locationId", ""));
        stop.setOrderIndex(getIntOrDefault(json, "orderIndex", 0));
        stop.setWaitTime(getIntOrDefault(json, "waitTime", 0));
        stop.setSpeech(getStringOrDefault(json, "speech", ""));
        stop.setCreatedAt(System.currentTimeMillis());
        stop.setUpdatedAt(System.currentTimeMillis());
        
        getDatabase().tourStopDao().insertTourStop(stop);
        return jsonResponse(Response.Status.CREATED, stop);
    }
    
    private Response updateTourStop(String id, IHTTPSession session) {
        TourStopEntity existing = getDatabase().tourStopDao().getTourStopById(id);
        if (existing == null) {
            return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Tour stop not found"));
        }
        
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> json = gson.fromJson(body, mapType);
        
        existing.setName(getStringOrDefault(json, "name", existing.getName()));
        existing.setDescription(getStringOrDefault(json, "description", existing.getDescription()));
        existing.setLocationId(getStringOrDefault(json, "locationId", existing.getLocationId()));
        existing.setOrderIndex(getIntOrDefault(json, "orderIndex", existing.getOrderIndex()));
        existing.setWaitTime(getIntOrDefault(json, "waitTime", existing.getWaitTime()));
        existing.setSpeech(getStringOrDefault(json, "speech", existing.getSpeech()));
        existing.setUpdatedAt(System.currentTimeMillis());
        
        getDatabase().tourStopDao().updateTourStop(existing);
        return jsonResponse(Response.Status.OK, existing);
    }
    
    private Response deleteTourStop(String id) {
        getDatabase().tourStopDao().deleteTourStopById(id);
        return jsonResponse(Response.Status.OK, mapOf("message", "Tour stop deleted"));
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // CONFIG
    // ─────────────────────────────────────────────────────────────────────────
    
    private Response handleConfig(Method method, List<String> parts, IHTTPSession session) {
        String key = parts.size() > 2 ? parts.get(2) : null;
        
        switch (method) {
            case GET:
                return key != null ? getConfigByKey(key) : getAllConfig();
            case PUT:
                return key != null ? setConfig(key, session) : 
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Config key required"));
            case DELETE:
                return key != null ? deleteConfig(key) : 
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Config key required"));
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
        }
    }
    
    private Response getAllConfig() {
        List<ConfigEntity> configs = getDatabase().configDao().getAllConfigSync();
        Map<String, String> configMap = new HashMap<>();
        for (ConfigEntity config : configs) {
            configMap.put(config.getKey(), config.getValue());
        }
        return jsonResponse(Response.Status.OK, configMap);
    }
    
    private Response getConfigByKey(String key) {
        ConfigEntity config = getDatabase().configDao().getConfig(key);
        if (config != null) {
            Map<String, String> result = new HashMap<>();
            result.put("key", config.getKey());
            result.put("value", config.getValue());
            return jsonResponse(Response.Status.OK, result);
        }
        return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Config not found"));
    }
    
    private Response setConfig(String key, IHTTPSession session) {
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(body, mapType);
        String value = data.get("value") != null ? data.get("value").toString() : "";
        
        ConfigEntity config = new ConfigEntity(key, value, System.currentTimeMillis());
        getDatabase().configDao().setConfig(config);
        
        Map<String, String> result = new HashMap<>();
        result.put("key", key);
        result.put("value", value);
        return jsonResponse(Response.Status.OK, result);
    }
    
    private Response deleteConfig(String key) {
        getDatabase().configDao().deleteConfig(key);
        return jsonResponse(Response.Status.OK, mapOf("message", "Config deleted"));
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // UTILS
    // ─────────────────────────────────────────────────────────────────────────
    
    private String getRequestBody(IHTTPSession session) {
        String contentLengthStr = session.getHeaders().get("content-length");
        int contentLength = contentLengthStr != null ? Integer.parseInt(contentLengthStr) : 0;
        if (contentLength == 0) return "{}";
        
        try {
            byte[] buffer = new byte[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = session.getInputStream().read(buffer, totalRead, contentLength - totalRead);
                if (read == -1) break;
                totalRead += read;
            }
            return new String(buffer, 0, totalRead, "UTF-8");
        } catch (IOException e) {
            Log.e(TAG, "Error reading request body", e);
            return "{}";
        }
    }
    
    private Response jsonResponse(Response.Status status, Object data) {
        String json = gson.toJson(data);
        return newFixedLengthResponse(status, "application/json", json);
    }
    
    private Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
    
    private String getStringOrDefault(Map<String, Object> json, String key, String defaultValue) {
        Object value = json.get(key);
        if (value == null) return defaultValue;
        String str = value.toString();
        return str.isEmpty() ? defaultValue : str;
    }
    
    private double getDoubleOrDefault(Map<String, Object> json, String key, double defaultValue) {
        Object value = json.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private int getIntOrDefault(Map<String, Object> json, String key, int defaultValue) {
        Object value = json.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private boolean getBooleanOrDefault(Map<String, Object> json, String key, boolean defaultValue) {
        Object value = json.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }
    
    @SuppressWarnings("unchecked")
    private List<String> getStringListOrDefault(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value == null) return new ArrayList<>();
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item != null) result.add(item.toString());
            }
            return result;
        }
        return new ArrayList<>();
    }
}
