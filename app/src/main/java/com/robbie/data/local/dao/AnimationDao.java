package com.robbie.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.robbie.data.local.entity.AnimationEntity;

import java.util.List;

@Dao
public interface AnimationDao {

    @Query("SELECT * FROM animations ORDER BY trigger, priority ASC")
    List<AnimationEntity> getAllAnimationsSync();

    @Query("SELECT * FROM animations WHERE id = :id LIMIT 1")
    AnimationEntity getAnimationById(String id);

    @Query("SELECT * FROM animations WHERE trigger = :trigger AND enabled = 1 ORDER BY priority ASC")
    List<AnimationEntity> getAnimationsByTrigger(String trigger);

    @Query("SELECT * FROM animations WHERE enabled = 1 ORDER BY trigger, priority ASC")
    List<AnimationEntity> getEnabledAnimations();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAnimation(AnimationEntity animation);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAnimations(List<AnimationEntity> animations);

    @Update
    void updateAnimation(AnimationEntity animation);

    @Query("DELETE FROM animations WHERE id = :id")
    void deleteAnimationById(String id);

    @Query("DELETE FROM animations")
    void deleteAllAnimations();

    @Query("SELECT COUNT(*) FROM animations")
    int getAnimationCount();
}
