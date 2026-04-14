package com.robbie.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.robbie.data.local.entity.ConfigEntity;

import java.util.List;

@Dao
public interface ConfigDao {
    
    @Query("SELECT * FROM config")
    LiveData<List<ConfigEntity>> getAllConfig();
    
    @Query("SELECT * FROM config")
    List<ConfigEntity> getAllConfigSync();
    
    @Query("SELECT * FROM config WHERE `key` = :key")
    ConfigEntity getConfig(String key);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void setConfig(ConfigEntity config);
    
    @Query("DELETE FROM config WHERE `key` = :key")
    void deleteConfig(String key);
}
