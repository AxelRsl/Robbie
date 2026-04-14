package com.robbie.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.robbie.data.local.entity.TourStopEntity;

import java.util.List;

@Dao
public interface TourStopDao {
    
    @Query("SELECT * FROM tour_stops ORDER BY orderIndex ASC")
    LiveData<List<TourStopEntity>> getAllTourStops();
    
    @Query("SELECT * FROM tour_stops ORDER BY orderIndex ASC")
    List<TourStopEntity> getAllTourStopsSync();
    
    @Query("SELECT * FROM tour_stops WHERE id = :id")
    TourStopEntity getTourStopById(String id);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTourStop(TourStopEntity tourStop);
    
    @Update
    void updateTourStop(TourStopEntity tourStop);
    
    @Delete
    void deleteTourStop(TourStopEntity tourStop);
    
    @Query("DELETE FROM tour_stops WHERE id = :id")
    void deleteTourStopById(String id);
}
