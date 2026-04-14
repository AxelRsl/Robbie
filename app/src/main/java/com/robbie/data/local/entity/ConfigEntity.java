package com.robbie.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "config")
public class ConfigEntity {
    
    @PrimaryKey
    @NonNull
    private String key;
    
    @NonNull
    private String value;
    
    private long updatedAt;
    
    public ConfigEntity() {
        this.key = "";
        this.value = "";
        this.updatedAt = System.currentTimeMillis();
    }
    
    public ConfigEntity(@NonNull String key, @NonNull String value, long updatedAt) {
        this.key = key;
        this.value = value;
        this.updatedAt = updatedAt;
    }
    
    @NonNull public String getKey() { return key; }
    @NonNull public String getValue() { return value; }
    public long getUpdatedAt() { return updatedAt; }
    
    public void setKey(@NonNull String key) { this.key = key; }
    public void setValue(@NonNull String value) { this.value = value; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
