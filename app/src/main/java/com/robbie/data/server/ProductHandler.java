package com.robbie.data.server;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.robbie.data.local.RobbieDatabase;
import com.robbie.data.local.entity.ProductEntity;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

public class ProductHandler extends BaseHandler {

    private static final String TAG = "ProductHandler";

    public ProductHandler(RobbieDatabase db, Gson gson) {
        super(db, gson);
    }

    @Override
    public Response handle(Method method, List<String> parts, IHTTPSession session) {
        String id = parts.size() > 2 ? parts.get(2) : null;

        if (method == Method.POST && "bulk".equals(id)) {
            return bulkCreate(session);
        }

        if (method == Method.DELETE && "all".equals(id)) {
            return deleteAll();
        }

        switch (method) {
            case GET:
                return id != null ? getById(id) : getAll();
            case POST:
                return create(session);
            case PUT:
                return id != null ? update(id, session) :
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Product ID required"));
            case DELETE:
                return id != null ? delete(id) :
                    jsonResponse(Response.Status.BAD_REQUEST, mapOf("error", "Product ID required"));
            default:
                return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error", "Method not allowed"));
        }
    }

    private Response getAll() {
        List<ProductEntity> products = db.productDao().getAllProductsSync();
        return jsonResponse(Response.Status.OK, products);
    }

    private Response getById(String id) {
        ProductEntity product = db.productDao().getProductById(id);
        if (product != null) {
            return jsonResponse(Response.Status.OK, product);
        }
        return jsonResponse(Response.Status.NOT_FOUND, mapOf("error", "Product not found"));
    }

    private Response create(IHTTPSession session) {
        String body = getRequestBody(session);
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> json = gson.fromJson(body, mapType);

        ProductEntity product = buildProduct(json);
        product.setCreatedAt(System.currentTimeMillis());
        product.setUpdatedAt(System.currentTimeMillis());

        db.productDao().insertProduct(product);
        return jsonResponse(Response.Status.CREATED, product);
    }

    private Response update(String id, IHTTPSession session) {
        ProductEntity existing = db.productDao().getProductById(id);
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

        db.productDao().updateProduct(existing);
        return jsonResponse(Response.Status.OK, existing);
    }

    private Response delete(String id) {
        db.productDao().deleteProductById(id);
        return jsonResponse(Response.Status.OK, mapOf("message", "Product deleted"));
    }

    private Response deleteAll() {
        db.productDao().deleteAllProducts();
        return jsonResponse(Response.Status.OK, mapOf("message", "All products deleted"));
    }

    private Response bulkCreate(IHTTPSession session) {
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
            ProductEntity product = buildProduct(json);
            product.setCreatedAt(System.currentTimeMillis());
            product.setUpdatedAt(System.currentTimeMillis());
            products.add(product);
        }

        db.productDao().insertProducts(products);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Products created");
        result.put("count", products.size());
        return jsonResponse(Response.Status.CREATED, result);
    }

    private ProductEntity buildProduct(Map<String, Object> json) {
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
        return product;
    }
}
