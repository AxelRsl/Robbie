package com.robbie.platform.react.modules;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.robbie.data.local.RobbieDatabase;
import com.robbie.data.local.dao.ProductDao;
import com.robbie.data.local.entity.ProductEntity;
import com.robbie.platform.retail.ProductSemanticMatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * ProductSearchModule - Módulo React Native para búsqueda inteligente de productos.
 *
 * Expone métodos para buscar productos en la base de datos local Room,
 * con capacidades de filtrado por categoría, precio, marca, etc.
 *
 * Funcionalidades:
 * - searchProducts(query): búsqueda por texto (nombre, categoría, marca)
 * - getProductsByCategory(category): filtrar por categoría
 * - getTopProducts(limit): obtener productos destacados
 * - getAllProducts(): obtener todos los productos
 */
public class ProductSearchModule extends ReactContextBaseJavaModule {

    private static final String TAG = "ProductSearchModule";
    private ProductDao productDao;

    public ProductSearchModule(ReactApplicationContext reactContext) {
        super(reactContext);
        try {
            productDao = RobbieDatabase.getInstance(reactContext).productDao();
            Log.i(TAG, "ProductSearchModule inicializado con ProductDao");
        } catch (Exception e) {
            Log.e(TAG, "Error inicializando ProductSearchModule", e);
        }
    }

    @Override
    public String getName() {
        return "ProductSearchModule";
    }

    /**
     * Busca productos por texto en nombre, categoría o marca.
     * @param query Texto de búsqueda
     * @param promise Promise con resultados en formato JSON
     */
    @ReactMethod
    public void searchProducts(String query, Promise promise) {
        try {
            Log.i(TAG, "Buscando productos: " + query);
            List<ProductEntity> products = ProductSemanticMatcher.search(
                productDao.getAllProductsBlocking(),
                query,
                50
            );
            WritableArray results = convertProductsToWritableArray(products);
            
            WritableMap response = new WritableNativeMap();
            response.putArray("products", results);
            response.putInt("totalResults", products.size());
            response.putString("query", query);
            
            Log.i(TAG, "Encontrados " + products.size() + " productos");
            promise.resolve(response);
        } catch (Exception e) {
            Log.e(TAG, "Error en searchProducts", e);
            promise.reject("SEARCH_ERROR", e.getMessage());
        }
    }

    /**
     * Obtiene productos filtrados por categoría.
     * @param category Nombre de la categoría
     * @param promise Promise con resultados
     */
    @ReactMethod
    public void getProductsByCategory(String category, Promise promise) {
        try {
            Log.i(TAG, "Buscando productos por categoría: " + category);
            List<ProductEntity> products = productDao.getProductsByCategory(category);
            WritableArray results = convertProductsToWritableArray(products);
            
            WritableMap response = new WritableNativeMap();
            response.putArray("products", results);
            response.putInt("totalResults", products.size());
            response.putString("category", category);
            
            promise.resolve(response);
        } catch (Exception e) {
            Log.e(TAG, "Error en getProductsByCategory", e);
            promise.reject("CATEGORY_ERROR", e.getMessage());
        }
    }

    /**
     * Obtiene los primeros N productos (para destacados).
     * @param limit Número máximo de productos
     * @param promise Promise con resultados
     */
    @ReactMethod
    public void getTopProducts(int limit, Promise promise) {
        try {
            Log.i(TAG, "Obteniendo top " + limit + " productos");
            List<ProductEntity> allProducts = productDao.getAllProductsBlocking();
            int actualLimit = Math.min(limit, allProducts.size());
            List<ProductEntity> topProducts = allProducts.subList(0, actualLimit);
            
            WritableArray results = convertProductsToWritableArray(topProducts);
            
            WritableMap response = new WritableNativeMap();
            response.putArray("products", results);
            response.putInt("totalResults", topProducts.size());
            
            promise.resolve(response);
        } catch (Exception e) {
            Log.e(TAG, "Error en getTopProducts", e);
            promise.reject("TOP_ERROR", e.getMessage());
        }
    }

    /**
     * Obtiene todos los productos de la base de datos.
     * @param promise Promise con todos los productos
     */
    @ReactMethod
    public void getAllProducts(Promise promise) {
        try {
            Log.i(TAG, "Obteniendo todos los productos");
            List<ProductEntity> products = productDao.getAllProductsBlocking();
            WritableArray results = convertProductsToWritableArray(products);
            
            WritableMap response = new WritableNativeMap();
            response.putArray("products", results);
            response.putInt("totalResults", products.size());
            
            promise.resolve(response);
        } catch (Exception e) {
            Log.e(TAG, "Error en getAllProducts", e);
            promise.reject("ALL_ERROR", e.getMessage());
        }
    }

    /**
     * Obtiene un producto específico por ID.
     * @param productId ID del producto
     * @param promise Promise con el producto
     */
    @ReactMethod
    public void getProductById(String productId, Promise promise) {
        try {
            Log.i(TAG, "Buscando producto por ID: " + productId);
            ProductEntity product = productDao.getProductById(productId);
            
            if (product != null) {
                WritableMap productMap = convertProductToWritableMap(product);
                promise.resolve(productMap);
            } else {
                promise.resolve(null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en getProductById", e);
            promise.reject("ID_ERROR", e.getMessage());
        }
    }

    /**
     * Convierte una lista de ProductEntity a WritableArray para React Native.
     */
    private WritableArray convertProductsToWritableArray(List<ProductEntity> products) {
        WritableArray array = new WritableNativeArray();
        for (ProductEntity product : products) {
            array.pushMap(convertProductToWritableMap(product));
        }
        return array;
    }

    /**
     * Convierte un ProductEntity a WritableMap para React Native.
     */
    private WritableMap convertProductToWritableMap(ProductEntity product) {
        WritableMap map = new WritableNativeMap();
        
        map.putString("id", product.getId());
        map.putString("name", product.getName());
        map.putString("category", product.getCategory());
        map.putString("subcategory", product.getSubcategory());
        map.putDouble("price", product.getPrice());
        map.putInt("discount", product.getDiscount());
        map.putString("imageUrl", product.getImage());
        map.putString("description", product.getDescription());
        map.putString("ingredients", product.getIngredients());
        map.putBoolean("inStock", product.getInStock());
        map.putString("sku", product.getSku());
        map.putString("brand", product.getBrand());
        
        // Convertir tags a array
        WritableArray tagsArray = new WritableNativeArray();
        for (String tag : product.getTags()) {
            tagsArray.pushString(tag);
        }
        map.putArray("tags", tagsArray);
        
        // Calcular precio con descuento
        double finalPrice = product.getPrice() * (1 - product.getDiscount() / 100.0);
        map.putDouble("finalPrice", finalPrice);
        
        return map;
    }
}
