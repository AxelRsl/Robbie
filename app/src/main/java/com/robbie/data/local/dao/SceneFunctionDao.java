package com.robbie.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.robbie.data.local.entity.SceneFunctionEntity;

import java.util.List;

@Dao
public interface SceneFunctionDao {

    @Query("SELECT * FROM scene_functions WHERE projectId = :projectId ORDER BY orderIndex ASC")
    List<SceneFunctionEntity> getFunctionsByProject(String projectId);

    @Query("SELECT * FROM scene_functions WHERE id = :id")
    SceneFunctionEntity getFunctionById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFunction(SceneFunctionEntity function);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFunctions(List<SceneFunctionEntity> functions);

    @Update
    void updateFunction(SceneFunctionEntity function);

    @Delete
    void deleteFunction(SceneFunctionEntity function);

    @Query("DELETE FROM scene_functions WHERE id = :id")
    void deleteFunctionById(String id);

    @Query("DELETE FROM scene_functions WHERE projectId = :projectId")
    void deleteFunctionsByProject(String projectId);

    @Query("SELECT COUNT(*) FROM scene_functions WHERE projectId = :projectId")
    int getFunctionCount(String projectId);
}
