package com.robbie.platform.retail;

import java.util.ArrayList;
import java.util.List;
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
        p.id = docId;
        p.name        = getStr(data, "name", "Producto");
        p.category    = getStr(data, "category", "");
        p.subcategory = getStr(data, "subcategory", "");
        p.price       = getNum(data, "price", 0);
        p.discount    = (int) getNum(data, "discount", 0);
        p.image       = getStr(data, "image", "");
        p.description = getStr(data, "description", "");
        p.ingredients = getStr(data, "ingredients", "");
        p.inStock     = getBool(data, "inStock", true);
        p.sku         = getStr(data, "sku", "");
        p.brand       = getStr(data, "brand", "");

        Object rawTags = data.get("tags");
        if (rawTags instanceof List) {
            for (Object t : (List<?>) rawTags) {
                p.tags.add(String.valueOf(t));
            }
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

    private static double getNum(Map<String, Object> m, String key, double fallback) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return fallback;
    }

    private static boolean getBool(Map<String, Object> m, String key, boolean fallback) {
        Object v = m.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        return fallback;
    }
}
