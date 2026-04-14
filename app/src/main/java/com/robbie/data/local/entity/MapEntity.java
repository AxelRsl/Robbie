package com.robbie.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "maps")
public class MapEntity {
    
    @PrimaryKey
    @NonNull
    private String id;
    
    @NonNull
    private String name;
    
    @NonNull
    private String description;
    
    @NonNull
    private String mapData;
    
    @NonNull
    private String imageUrl;
    
    private boolean isActive;
    
    private long createdAt;
    
    private long updatedAt;
    
    public MapEntity() {
        this.id = "";
        this.name = "";
        this.description = "";
        this.mapData = "";
        this.imageUrl = "";
        this.isActive = false;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
    
    public MapEntity(@NonNull String id, @NonNull String name, @NonNull String description,
                    @NonNull String mapData, @NonNull String imageUrl, boolean isActive,
                    long createdAt, long updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.mapData = mapData;
        this.imageUrl = imageUrl;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    @NonNull public String getId() { return id; }
    @NonNull public String getName() { return name; }
    @NonNull public String getDescription() { return description; }
    @NonNull public String getMapData() { return mapData; }
    @NonNull public String getImageUrl() { return imageUrl; }
    public boolean getIsActive() { return isActive; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    
    public void setId(@NonNull String id) { this.id = id; }
    public void setName(@NonNull String name) { this.name = name; }
    public void setDescription(@NonNull String description) { this.description = description; }
    public void setMapData(@NonNull String mapData) { this.mapData = mapData; }
    public void setImageUrl(@NonNull String imageUrl) { this.imageUrl = imageUrl; }
    public void setIsActive(boolean isActive) { this.isActive = isActive; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
