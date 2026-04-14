package com.robbie.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.robbie.data.local.converter.StringListConverter;

import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "products")
@TypeConverters(StringListConverter.class)
public class ProductEntity {
    
    @PrimaryKey
    @NonNull
    private String id;
    
    @NonNull
    private String name;
    
    @NonNull
    private String category;
    
    @NonNull
    private String subcategory;
    
    private double price;
    
    private int discount;
    
    @NonNull
    private String image;
    
    @NonNull
    private String description;
    
    @NonNull
    private String ingredients;
    
    @NonNull
    private List<String> tags;
    
    private boolean inStock;
    
    @NonNull
    private String sku;
    
    @NonNull
    private String brand;
    
    private long createdAt;
    
    private long updatedAt;
    
    public ProductEntity() {
        this.id = "";
        this.name = "";
        this.category = "";
        this.subcategory = "";
        this.price = 0.0;
        this.discount = 0;
        this.image = "";
        this.description = "";
        this.ingredients = "";
        this.tags = new ArrayList<>();
        this.inStock = true;
        this.sku = "";
        this.brand = "";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
    
    public ProductEntity(@NonNull String id, @NonNull String name, @NonNull String category,
                        @NonNull String subcategory, double price, int discount,
                        @NonNull String image, @NonNull String description,
                        @NonNull String ingredients, @NonNull List<String> tags,
                        boolean inStock, @NonNull String sku, @NonNull String brand,
                        long createdAt, long updatedAt) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.subcategory = subcategory;
        this.price = price;
        this.discount = discount;
        this.image = image;
        this.description = description;
        this.ingredients = ingredients;
        this.tags = tags;
        this.inStock = inStock;
        this.sku = sku;
        this.brand = brand;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getters
    @NonNull public String getId() { return id; }
    @NonNull public String getName() { return name; }
    @NonNull public String getCategory() { return category; }
    @NonNull public String getSubcategory() { return subcategory; }
    public double getPrice() { return price; }
    public int getDiscount() { return discount; }
    @NonNull public String getImage() { return image; }
    @NonNull public String getDescription() { return description; }
    @NonNull public String getIngredients() { return ingredients; }
    @NonNull public List<String> getTags() { return tags; }
    public boolean getInStock() { return inStock; }
    @NonNull public String getSku() { return sku; }
    @NonNull public String getBrand() { return brand; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    
    // Setters
    public void setId(@NonNull String id) { this.id = id; }
    public void setName(@NonNull String name) { this.name = name; }
    public void setCategory(@NonNull String category) { this.category = category; }
    public void setSubcategory(@NonNull String subcategory) { this.subcategory = subcategory; }
    public void setPrice(double price) { this.price = price; }
    public void setDiscount(int discount) { this.discount = discount; }
    public void setImage(@NonNull String image) { this.image = image; }
    public void setDescription(@NonNull String description) { this.description = description; }
    public void setIngredients(@NonNull String ingredients) { this.ingredients = ingredients; }
    public void setTags(@NonNull List<String> tags) { this.tags = tags; }
    public void setInStock(boolean inStock) { this.inStock = inStock; }
    public void setSku(@NonNull String sku) { this.sku = sku; }
    public void setBrand(@NonNull String brand) { this.brand = brand; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
