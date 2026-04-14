package com.robbie.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tour_stops")
public class TourStopEntity {
    
    @PrimaryKey
    @NonNull
    private String id;
    
    @NonNull
    private String name;
    
    @NonNull
    private String description;
    
    @NonNull
    private String locationId;
    
    private int orderIndex;
    
    private int waitTime;
    
    @NonNull
    private String speech;
    
    private long createdAt;
    
    private long updatedAt;
    
    public TourStopEntity() {
        this.id = "";
        this.name = "";
        this.description = "";
        this.locationId = "";
        this.orderIndex = 0;
        this.waitTime = 0;
        this.speech = "";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
    
    public TourStopEntity(@NonNull String id, @NonNull String name, @NonNull String description,
                         @NonNull String locationId, int orderIndex, int waitTime,
                         @NonNull String speech, long createdAt, long updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.locationId = locationId;
        this.orderIndex = orderIndex;
        this.waitTime = waitTime;
        this.speech = speech;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    @NonNull public String getId() { return id; }
    @NonNull public String getName() { return name; }
    @NonNull public String getDescription() { return description; }
    @NonNull public String getLocationId() { return locationId; }
    public int getOrderIndex() { return orderIndex; }
    public int getWaitTime() { return waitTime; }
    @NonNull public String getSpeech() { return speech; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    
    public void setId(@NonNull String id) { this.id = id; }
    public void setName(@NonNull String name) { this.name = name; }
    public void setDescription(@NonNull String description) { this.description = description; }
    public void setLocationId(@NonNull String locationId) { this.locationId = locationId; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
    public void setWaitTime(int waitTime) { this.waitTime = waitTime; }
    public void setSpeech(@NonNull String speech) { this.speech = speech; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
