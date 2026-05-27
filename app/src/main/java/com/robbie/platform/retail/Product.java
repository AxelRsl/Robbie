package com.robbie.platform.retail;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Product {

    private String id;
    private String name;
    private String category;
    private String subcategory;
    private double price;
    private int discount;
    private String image;
    private String description;
    private String ingredients;
    private List<String> tags;
    private boolean inStock;
    private String sku;
    private String brand;

    private boolean aiRecommended;

    public Product() {
        tags = new ArrayList<>();
        inStock = true;
        brand = "";
    }

    public static Product fromMap(String docId, Map<String, Object> data) {
        Product p = new Product();
        String productId = firstStr(data, "id", "productID");
        String category = firstStr(data, "category", "mainCategory");
        String subcategory = firstStr(data, "subcategory", "subCategory", "thirdCategory");
        String thirdCategory = firstStr(data, "thirdCategory");
        String stockStatus = firstStr(data, "stockStatus");
        String discountInfo = firstStr(data, "discountInfo");
        double currentPrice = firstNum(data, "price", "currentPrice");
        double originalPrice = firstNum(data, "originalPrice");

        p.id = productId.isEmpty() ? docId : productId;
        p.name = firstStr(data, "name", "productName");
        if (p.name.isEmpty()) {
            p.name = "Producto";
        }
        p.category = category;
        p.subcategory = subcategory;
        p.price = currentPrice;
        p.discount = resolveDiscount(data, currentPrice, originalPrice, discountInfo);
        p.image = firstStr(data, "image", "imageUrl1", "imageUrl2", "imageUrl3");
        p.description = firstStr(data, "description");
        p.ingredients = firstStr(data, "ingredients");
        p.inStock = resolveInStock(data, stockStatus);
        p.sku = firstStr(data, "sku", "productID");
        p.brand = firstStr(data, "brand");

        p.tags.addAll(buildStructuredTags(data, category, subcategory, thirdCategory, p.brand, stockStatus, discountInfo));
        if (p.description.isEmpty()) {
            p.description = buildStructuredDescription(category, subcategory, thirdCategory, p.brand, stockStatus, discountInfo, data);
        }
        if (p.ingredients.isEmpty()) {
            p.ingredients = buildAttributeSummary(data);
        }
        return p;
    }

    public String toAiSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (!category.isEmpty()) sb.append(" | Cat: ").append(category);
        sb.append(" | $").append(String.format("%.2f", price)).append(" MXN");
        if (!tags.isEmpty()) sb.append(" | Tags: ").append(String.join(", ", tags));
        if (!ingredients.isEmpty()) sb.append(" | Ingredientes: ").append(ingredients);
        return sb.toString();
    }

    public double getDiscountedPrice() {
        if (discount > 0) return price * (1.0 - discount / 100.0);
        return price;
    }

    public String getId()           { return id; }
    public String getName()         { return name; }
    public String getCategory()     { return category; }
    public String getSubcategory()  { return subcategory; }
    public double getPrice()        { return price; }
    public int    getDiscount()     { return discount; }
    public String getImage()        { return image; }
    public String getDescription()  { return description; }
    public String getIngredients()  { return ingredients; }
    public List<String> getTags()   { return tags; }
    public boolean isInStock()      { return inStock; }
    public String getSku()          { return sku; }
    public String getBrand()        { return brand; }
    public boolean isAiRecommended(){ return aiRecommended; }

    public void setAiRecommended(boolean recommended) { this.aiRecommended = recommended; }

    private static String getStr(Map<String, Object> m, String key, String fallback) {
        Object v = m.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    private static String firstStr(Map<String, Object> m, String... keys) {
        for (String key : keys) {
            Object v = m.get(key);
            if (v == null) {
                continue;
            }
            String value = String.valueOf(v).trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return "";
    }

    private static double getNum(Map<String, Object> m, String key, double fallback) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return fallback;
    }

    private static double firstNum(Map<String, Object> m, String... keys) {
        for (String key : keys) {
            Object v = m.get(key);
            if (v instanceof Number) {
                return ((Number) v).doubleValue();
            }
            if (v instanceof String) {
                try {
                    return Double.parseDouble(((String) v).trim().replace(",", ""));
                } catch (Exception ignored) {
                }
            }
        }
        return 0.0;
    }

    private static boolean getBool(Map<String, Object> m, String key, boolean fallback) {
        Object v = m.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        return fallback;
    }

    private static boolean resolveInStock(Map<String, Object> m, String stockStatus) {
        if (m.containsKey("inStock")) {
            return getBool(m, "inStock", true);
        }
        String normalized = normalize(stockStatus);
        if (normalized.isEmpty()) {
            return true;
        }
        return normalized.contains("in stock") || normalized.contains("available") || normalized.contains("disponible");
    }

    private static int resolveDiscount(Map<String, Object> m, double currentPrice, double originalPrice, String discountInfo) {
        if (m.containsKey("discount")) {
            return (int) getNum(m, "discount", 0);
        }
        if (originalPrice > 0.0 && currentPrice > 0.0 && originalPrice > currentPrice) {
            return (int) Math.round(((originalPrice - currentPrice) / originalPrice) * 100.0);
        }
        String normalized = normalize(discountInfo);
        if (!normalized.isEmpty()) {
            String digits = normalized.replaceAll("[^0-9]", " ").trim();
            if (!digits.isEmpty()) {
                try {
                    return Integer.parseInt(digits.split("\\s+")[0]);
                } catch (Exception ignored) {
                }
            }
        }
        return 0;
    }

    private static List<String> buildStructuredTags(Map<String, Object> data,
                                                    String category,
                                                    String subcategory,
                                                    String thirdCategory,
                                                    String brand,
                                                    String stockStatus,
                                                    String discountInfo) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        Object rawTags = data.get("tags");
        if (rawTags instanceof List) {
            for (Object t : (List<?>) rawTags) {
                addTag(tags, String.valueOf(t));
            }
        }
        addTag(tags, category);
        addTag(tags, subcategory);
        addTag(tags, thirdCategory);
        addTag(tags, brand);
        addTag(tags, stockStatus);
        addTag(tags, discountInfo);

        String attributeSummary = buildAttributeSummary(data);
        addTag(tags, attributeSummary);

        String normalizedCategory = normalize(category);
        String normalizedSubcategory = normalize(subcategory);
        String normalizedThirdCategory = normalize(thirdCategory);

        if (normalizedCategory.contains("alimentos y bebidas") || normalizedSubcategory.contains("snack")) {
            addTag(tags, "snack");
            addTag(tags, "portable");
            addTag(tags, "algo rapido");
        }
        if (normalizedSubcategory.contains("snack") || normalizedThirdCategory.contains("bar")) {
            addTag(tags, "barrita");
            addTag(tags, "barra");
            addTag(tags, "quick energy");
        }
        if (normalizedCategory.contains("proteina") || normalizedSubcategory.contains("whey")) {
            addTag(tags, "proteina");
            addTag(tags, "protein");
            addTag(tags, "gym");
        }
        if (normalizedSubcategory.contains("movilidad")) {
            addTag(tags, "articulaciones");
            addTag(tags, "movilidad");
        }
        if (!normalize(discountInfo).isEmpty()) {
            addTag(tags, "oferta");
        }

        return new ArrayList<>(tags);
    }

    private static String buildStructuredDescription(String category,
                                                     String subcategory,
                                                     String thirdCategory,
                                                     String brand,
                                                     String stockStatus,
                                                     String discountInfo,
                                                     Map<String, Object> data) {
        List<String> parts = new ArrayList<>();
        if (!category.isEmpty()) parts.add(category);
        if (!subcategory.isEmpty()) parts.add(subcategory);
        if (!thirdCategory.isEmpty()) parts.add(thirdCategory);
        if (!brand.isEmpty()) parts.add("Marca " + brand);
        String attributeSummary = buildAttributeSummary(data);
        if (!attributeSummary.isEmpty()) parts.add("Presentacion " + attributeSummary);
        if (!discountInfo.isEmpty()) parts.add("Promocion " + discountInfo);
        if (!stockStatus.isEmpty()) parts.add("Stock " + stockStatus);
        return String.join(". ", parts);
    }

    private static String buildAttributeSummary(Map<String, Object> data) {
        String[] keys = {
            "attribute-1(eg:capacity)",
            "attribute-2(eg:color)",
            "attribute-3(eg:screenResolution)",
            "attribute-4(eg:weight)",
            "attribute-5(eg:size)"
        };
        List<String> values = new ArrayList<>();
        for (String key : keys) {
            String value = firstStr(data, key);
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return String.join(" ", values);
    }

    private static void addTag(LinkedHashSet<String> tags, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            tags.add(trimmed);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
