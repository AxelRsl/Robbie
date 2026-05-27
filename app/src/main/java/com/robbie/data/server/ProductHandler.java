package com.robbie.data.server;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.robbie.data.local.RobbieDatabase;
import com.robbie.data.local.entity.ProductEntity;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

public class ProductHandler extends BaseHandler {

    private static final String TAG = "ProductHandler";

    public interface OnProductsChangedListener {
        void onProductsChanged();
    }

    private OnProductsChangedListener changedListener;

    public ProductHandler(RobbieDatabase db, Gson gson) {
        super(db, gson);
    }

    public void setOnProductsChangedListener(OnProductsChangedListener listener) {
        this.changedListener = listener;
    }

    private void notifyProductsChanged() {
        if (changedListener != null) {
            try {
                changedListener.onProductsChanged();
            } catch (Exception e) {
                Log.w(TAG, "Error notifying products changed", e);
            }
        }
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
        notifyProductsChanged();
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

        ProductEntity incoming = buildProduct(json);

        if (hasAnyKey(json, "name", "productName")) {
            existing.setName(incoming.getName());
        }
        if (hasAnyKey(json, "category", "mainCategory")) {
            existing.setCategory(incoming.getCategory());
        }
        if (hasAnyKey(json, "subcategory", "subCategory", "thirdCategory")) {
            existing.setSubcategory(incoming.getSubcategory());
        }
        if (hasAnyKey(json, "price", "currentPrice")) {
            existing.setPrice(incoming.getPrice());
        }
        if (hasAnyKey(json, "discount", "discountInfo", "originalPrice", "currentPrice")) {
            existing.setDiscount(incoming.getDiscount());
        }
        if (hasAnyKey(json, "image", "imageUrl1", "imageUrl2")) {
            existing.setImage(incoming.getImage());
        }
        if (hasAnyKey(json, "description", "mainCategory", "subCategory", "thirdCategory", "brand", "stockStatus", "discountInfo",
            "attribute-1(eg:capacity)", "attribute-2(eg:color)", "attribute-3(eg:screenResolution)", "attribute-4(eg:weight)", "attribute-5(eg:size)")) {
            existing.setDescription(incoming.getDescription());
        }
        if (hasAnyKey(json, "ingredients", "attribute-1(eg:capacity)", "attribute-2(eg:color)", "attribute-3(eg:screenResolution)", "attribute-4(eg:weight)", "attribute-5(eg:size)")) {
            existing.setIngredients(incoming.getIngredients());
        }
        if (hasAnyKey(json, "tags", "mainCategory", "subCategory", "thirdCategory", "brand", "stockStatus", "discountInfo",
            "attribute-1(eg:capacity)", "attribute-2(eg:color)", "attribute-3(eg:screenResolution)", "attribute-4(eg:weight)", "attribute-5(eg:size)")) {
            existing.setTags(incoming.getTags());
        }
        if (hasAnyKey(json, "inStock", "stockStatus")) {
            existing.setInStock(incoming.getInStock());
        }
        if (hasAnyKey(json, "sku", "productID")) {
            existing.setSku(incoming.getSku());
        }
        if (hasAnyKey(json, "brand")) {
            existing.setBrand(incoming.getBrand());
        }
        existing.setUpdatedAt(System.currentTimeMillis());

        db.productDao().updateProduct(existing);
        notifyProductsChanged();
        return jsonResponse(Response.Status.OK, existing);
    }

    private Response delete(String id) {
        db.productDao().deleteProductById(id);
        notifyProductsChanged();
        return jsonResponse(Response.Status.OK, mapOf("message", "Product deleted"));
    }

    private Response deleteAll() {
        db.productDao().deleteAllProducts();
        notifyProductsChanged();
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
        notifyProductsChanged();

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Products created");
        result.put("count", products.size());
        return jsonResponse(Response.Status.CREATED, result);
    }

    private ProductEntity buildProduct(Map<String, Object> json) {
        ProductEntity product = new ProductEntity();
        String productId = firstString(json, "id", "productID");
        String name = firstString(json, "name", "productName");
        String category = firstString(json, "category", "mainCategory");
        String subcategory = firstString(json, "subcategory", "subCategory", "thirdCategory");
        String thirdCategory = firstString(json, "thirdCategory");
        double currentPrice = firstDouble(json, "price", "currentPrice");
        double originalPrice = firstDouble(json, "originalPrice");
        String discountInfo = firstString(json, "discountInfo");
        String brand = firstString(json, "brand");
        String stockStatus = firstString(json, "stockStatus");
        String image = firstString(json, "image", "imageUrl1", "imageUrl2", "imageUrl3");
        String description = firstString(json, "description");
        String ingredients = firstString(json, "ingredients");
        List<String> tags = buildStructuredTags(json, category, subcategory, thirdCategory, brand, stockStatus, discountInfo);

        if (description.isEmpty()) {
            description = buildStructuredDescription(category, subcategory, thirdCategory, brand, stockStatus, discountInfo, json);
        }
        if (ingredients.isEmpty()) {
            ingredients = buildAttributeSummary(json);
        }

        product.setId(productId.isEmpty() ? UUID.randomUUID().toString() : productId);
        product.setName(name);
        product.setCategory(category);
        product.setSubcategory(subcategory);
        product.setPrice(currentPrice);
        product.setDiscount(resolveDiscount(json, currentPrice, originalPrice, discountInfo));
        product.setImage(image);
        product.setDescription(description);
        product.setIngredients(ingredients);
        product.setTags(tags);
        product.setInStock(resolveInStock(json, stockStatus));
        product.setSku(firstString(json, "sku", "productID"));
        product.setBrand(brand);
        return product;
    }

    private boolean hasAnyKey(Map<String, Object> json, String... keys) {
        for (String key : keys) {
            if (json.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private String firstString(Map<String, Object> json, String... keys) {
        for (String key : keys) {
            Object value = json.get(key);
            if (value == null) {
                continue;
            }
            String stringValue = String.valueOf(value).trim();
            if (!stringValue.isEmpty() && !"null".equalsIgnoreCase(stringValue)) {
                return stringValue;
            }
        }
        return "";
    }

    private double firstDouble(Map<String, Object> json, String... keys) {
        for (String key : keys) {
            Object value = json.get(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value instanceof String) {
                String text = ((String) value).trim().replace(",", "");
                if (text.isEmpty()) {
                    continue;
                }
                try {
                    return Double.parseDouble(text);
                } catch (Exception ignored) {
                }
            }
        }
        return 0.0;
    }

    private boolean resolveInStock(Map<String, Object> json, String stockStatus) {
        if (json.containsKey("inStock")) {
            return getBooleanOrDefault(json, "inStock", true);
        }
        String normalized = normalize(stockStatus);
        if (normalized.isEmpty()) {
            return true;
        }
        return normalized.contains("in stock") || normalized.contains("available") || normalized.contains("disponible");
    }

    private int resolveDiscount(Map<String, Object> json, double currentPrice, double originalPrice, String discountInfo) {
        if (json.containsKey("discount")) {
            return getIntOrDefault(json, "discount", 0);
        }
        if (originalPrice > 0.0 && currentPrice > 0.0 && originalPrice > currentPrice) {
            return (int) Math.round(((originalPrice - currentPrice) / originalPrice) * 100.0);
        }
        String normalized = normalize(discountInfo);
        if (!normalized.isEmpty()) {
            String digits = normalized.replaceAll("[^0-9]", " ").trim();
            if (!digits.isEmpty()) {
                String[] parts = digits.split("\\s+");
                try {
                    return Integer.parseInt(parts[0]);
                } catch (Exception ignored) {
                }
            }
        }
        return 0;
    }

    private List<String> buildStructuredTags(Map<String, Object> json,
                                             String category,
                                             String subcategory,
                                             String thirdCategory,
                                             String brand,
                                             String stockStatus,
                                             String discountInfo) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String tag : getStringListOrDefault(json, "tags")) {
            addTag(tags, tag);
        }

        addTag(tags, category);
        addTag(tags, subcategory);
        addTag(tags, thirdCategory);
        addTag(tags, brand);
        addTag(tags, stockStatus);
        addTag(tags, discountInfo);

        String attributeSummary = buildAttributeSummary(json);
        addTag(tags, attributeSummary);

        String normalizedCategory = normalize(category);
        String normalizedSubcategory = normalize(subcategory);
        String normalizedThirdCategory = normalize(thirdCategory);

        if (normalizedCategory.contains("alimentos y bebidas") || normalizedSubcategory.contains("snack")) {
            addTag(tags, "snack");
            addTag(tags, "snacks");
            addTag(tags, "algo rapido");
            addTag(tags, "portable");
        }
        if (normalizedSubcategory.contains("snack") || normalizedThirdCategory.contains("bar") || normalize(attributeSummary).contains("gramos")) {
            addTag(tags, "barrita");
            addTag(tags, "barra");
            addTag(tags, "quick energy");
        }
        if (normalizedCategory.contains("proteina") || normalizedSubcategory.contains("whey")) {
            addTag(tags, "proteina");
            addTag(tags, "protein");
            addTag(tags, "gym");
            addTag(tags, "recovery");
        }
        if (normalizedThirdCategory.contains("shake")) {
            addTag(tags, "shake");
            addTag(tags, "bebida");
        }
        if (normalizedSubcategory.contains("movilidad")) {
            addTag(tags, "articulaciones");
            addTag(tags, "movilidad");
        }
        if (normalize(stockStatus).contains("in stock")) {
            addTag(tags, "disponible");
        }
        if (!normalize(discountInfo).isEmpty()) {
            addTag(tags, "oferta");
            addTag(tags, "promocion");
        }

        return new ArrayList<>(tags);
    }

    private String buildStructuredDescription(String category,
                                              String subcategory,
                                              String thirdCategory,
                                              String brand,
                                              String stockStatus,
                                              String discountInfo,
                                              Map<String, Object> json) {
        List<String> parts = new ArrayList<>();
        if (!category.isEmpty()) {
            parts.add(category);
        }
        if (!subcategory.isEmpty()) {
            parts.add(subcategory);
        }
        if (!thirdCategory.isEmpty()) {
            parts.add(thirdCategory);
        }
        if (!brand.isEmpty()) {
            parts.add("Marca " + brand);
        }

        String attributeSummary = buildAttributeSummary(json);
        if (!attributeSummary.isEmpty()) {
            parts.add("Presentacion " + attributeSummary);
        }
        if (!discountInfo.isEmpty()) {
            parts.add("Promocion " + discountInfo);
        }
        if (!stockStatus.isEmpty()) {
            parts.add("Stock " + stockStatus);
        }
        return String.join(". ", parts);
    }

    private String buildAttributeSummary(Map<String, Object> json) {
        String[] keys = {
            "attribute-1(eg:capacity)",
            "attribute-2(eg:color)",
            "attribute-3(eg:screenResolution)",
            "attribute-4(eg:weight)",
            "attribute-5(eg:size)"
        };
        List<String> values = new ArrayList<>();
        for (String key : keys) {
            String value = firstString(json, key);
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return String.join(" ", values);
    }

    private void addTag(LinkedHashSet<String> tags, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            tags.add(trimmed);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
