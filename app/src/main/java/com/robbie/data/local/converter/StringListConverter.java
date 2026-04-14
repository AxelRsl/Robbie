package com.robbie.data.local.converter;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class StringListConverter {
    
    private static final Gson gson = new Gson();
    
    @TypeConverter
    public static String fromStringList(List<String> list) {
        if (list == null) {
            return gson.toJson(new ArrayList<String>());
        }
        return gson.toJson(list);
    }
    
    @TypeConverter
    public static List<String> toStringList(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<String>>() {}.getType();
        List<String> result = gson.fromJson(json, type);
        return result != null ? result : new ArrayList<>();
    }
}
