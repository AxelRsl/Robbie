package com.robbie.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.robbie.data.local.entity.ProductEntity;

import java.util.List;

@Dao
public interface ProductDao {
    
    @Query("SELECT * FROM products ORDER BY name ASC")
    LiveData<List<ProductEntity>> getAllProducts();
    
    @Query("SELECT * FROM products ORDER BY name ASC")
    List<ProductEntity> getAllProductsSync();
    
    @Query("SELECT * FROM products ORDER BY name ASC")
    List<ProductEntity> getAllProductsBlocking();
    
    @Query("SELECT * FROM products WHERE id = :id")
    ProductEntity getProductById(String id);
    
    @Query("SELECT * FROM products WHERE category = :category ORDER BY name ASC")
    List<ProductEntity> getProductsByCategory(String category);
    
    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%'")
    List<ProductEntity> searchProducts(String query);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertProduct(ProductEntity product);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertProducts(List<ProductEntity> products);
    
    @Update
    void updateProduct(ProductEntity product);
    
    @Delete
    void deleteProduct(ProductEntity product);
    
    @Query("DELETE FROM products WHERE id = :id")
    void deleteProductById(String id);
    
    @Query("DELETE FROM products")
    void deleteAllProducts();
    
    @Query("SELECT COUNT(*) FROM products")
    int getProductCount();
}
