package com.robbie.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.robbie.data.local.entity.MapEntity;

import java.util.List;

@Dao
public interface MapDao {
    
    @Query("SELECT * FROM maps ORDER BY name ASC")
    LiveData<List<MapEntity>> getAllMaps();
    
    @Query("SELECT * FROM maps ORDER BY name ASC")
    List<MapEntity> getAllMapsSync();
    
    @Query("SELECT * FROM maps WHERE id = :id")
    MapEntity getMapById(String id);
    
    @Query("SELECT * FROM maps WHERE isActive = 1 LIMIT 1")
    MapEntity getActiveMap();
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMap(MapEntity map);
    
    @Update
    void updateMap(MapEntity map);
    
    @Query("UPDATE maps SET isActive = 0")
    void deactivateAllMaps();
    
    @Query("UPDATE maps SET isActive = 1 WHERE id = :id")
    void activateMap(String id);
    
    @Delete
    void deleteMap(MapEntity map);
    
    @Query("DELETE FROM maps WHERE id = :id")
    void deleteMapById(String id);
}
