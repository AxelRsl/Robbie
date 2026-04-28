package com.robbie.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.robbie.data.local.entity.SceneProjectEntity;

import java.util.List;

@Dao
public interface SceneProjectDao {

    @Query("SELECT * FROM scene_projects ORDER BY createdAt DESC")
    List<SceneProjectEntity> getAllProjects();

    @Query("SELECT * FROM scene_projects WHERE id = :id")
    SceneProjectEntity getProjectById(String id);

    @Query("SELECT * FROM scene_projects WHERE isActive = 1 LIMIT 1")
    SceneProjectEntity getActiveProject();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertProject(SceneProjectEntity project);

    @Update
    void updateProject(SceneProjectEntity project);

    @Delete
    void deleteProject(SceneProjectEntity project);

    @Query("DELETE FROM scene_projects WHERE id = :id")
    void deleteProjectById(String id);

    @Query("UPDATE scene_projects SET isActive = 0")
    void deactivateAll();

    @Query("UPDATE scene_projects SET isActive = 1 WHERE id = :id")
    void activateProject(String id);

    @Query("SELECT COUNT(*) FROM scene_projects")
    int getProjectCount();
}
