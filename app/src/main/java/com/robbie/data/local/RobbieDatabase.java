package com.robbie.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.robbie.data.local.converter.StringListConverter;
import com.robbie.data.local.dao.AnimationDao;
import com.robbie.data.local.dao.ConfigDao;
import com.robbie.data.local.dao.MapDao;
import com.robbie.data.local.dao.ProductDao;
import com.robbie.data.local.dao.SceneFunctionDao;
import com.robbie.data.local.dao.SceneProjectDao;
import com.robbie.data.local.dao.TourStopDao;
import com.robbie.data.local.entity.AnimationEntity;
import com.robbie.data.local.entity.ConfigEntity;
import com.robbie.data.local.entity.MapEntity;
import com.robbie.data.local.entity.ProductEntity;
import com.robbie.data.local.entity.SceneFunctionEntity;
import com.robbie.data.local.entity.SceneProjectEntity;
import com.robbie.data.local.entity.TourStopEntity;

@Database(
    entities = {
        ProductEntity.class,
        MapEntity.class,
        TourStopEntity.class,
        ConfigEntity.class,
        AnimationEntity.class,
        SceneProjectEntity.class,
        SceneFunctionEntity.class
    },
    version = 5,
    exportSchema = false
)
@TypeConverters(StringListConverter.class)
public abstract class RobbieDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "robbie_database";
    
    public abstract ProductDao productDao();
    public abstract MapDao mapDao();
    public abstract TourStopDao tourStopDao();
    public abstract ConfigDao configDao();
    public abstract AnimationDao animationDao();
    public abstract SceneProjectDao sceneProjectDao();
    public abstract SceneFunctionDao sceneFunctionDao();
    
    private static volatile RobbieDatabase INSTANCE = null;
    
    public static RobbieDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (RobbieDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        RobbieDatabase.class,
                        DATABASE_NAME
                    )
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
