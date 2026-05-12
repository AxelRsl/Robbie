package com.robbie.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Configuracion del proyecto/escena principal.
 * Define el titulo, plantilla y fondo del menu de la app.
 * 
 * templateType puede ser:
 *   - THREE_CARDS      : Plantilla de tres tarjetas
 *   - FOUR_CARDS       : Plantillas de cuatro tarjetas
 *   - FIVE_CARDS       : Plantilla de cinco tarjetas
 *   - BUBBLE_LEFT      : Plantilla de burbuja del lado izquierdo
 *   - BUBBLE_BOTTOM    : Plantilla de burbuja inferior
 *   - FUNCTIONAL_GRID  : Plantilla de experiencia funcional (grid de iconos)
 * 
 * titleType puede ser:
 *   - TEXT  : El titulo se muestra como texto
 *   - IMAGE : El titulo se muestra como imagen
 */
@Entity(tableName = "scene_projects")
public class SceneProjectEntity {

    @PrimaryKey
    @NonNull
    private String id;

    @NonNull
    private String name;

    @NonNull
    private String titleType;

    private String titleText;

    private String titleImageUrl;

    @NonNull
    private String templateType;

    private String backgroundImageUrl;

    @NonNull
    @ColumnInfo(defaultValue = "#F5F5F5")
    private String backgroundColor;

    private boolean isActive;

    private long createdAt;

    private long updatedAt;

    public SceneProjectEntity() {
        this.id = "";
        this.name = "";
        this.titleType = "TEXT";
        this.titleText = "";
        this.titleImageUrl = "";
        this.templateType = "FUNCTIONAL_GRID";
        this.backgroundImageUrl = "";
        this.backgroundColor = "#F5F5F5";
        this.isActive = false;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public SceneProjectEntity(@NonNull String id, @NonNull String name,
                              @NonNull String titleType, String titleText,
                              String titleImageUrl, @NonNull String templateType,
                              String backgroundImageUrl, String backgroundColor,
                              boolean isActive,
                              long createdAt, long updatedAt) {
        this.id = id;
        this.name = name;
        this.titleType = titleType;
        this.titleText = titleText;
        this.titleImageUrl = titleImageUrl;
        this.templateType = templateType;
        this.backgroundImageUrl = backgroundImageUrl;
        this.backgroundColor = backgroundColor != null ? backgroundColor : "#F5F5F5";
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getName() { return name; }
    @NonNull public String getTitleType() { return titleType; }
    public String getTitleText() { return titleText; }
    public String getTitleImageUrl() { return titleImageUrl; }
    @NonNull public String getTemplateType() { return templateType; }
    public String getBackgroundImageUrl() { return backgroundImageUrl; }
    @NonNull public String getBackgroundColor() { return backgroundColor; }
    public boolean isActive() { return isActive; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }

    public void setId(@NonNull String id) { this.id = id; }
    public void setName(@NonNull String name) { this.name = name; }
    public void setTitleType(@NonNull String titleType) { this.titleType = titleType; }
    public void setTitleText(String titleText) { this.titleText = titleText; }
    public void setTitleImageUrl(String titleImageUrl) { this.titleImageUrl = titleImageUrl; }
    public void setTemplateType(@NonNull String templateType) { this.templateType = templateType; }
    public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; }
    public void setBackgroundColor(@NonNull String backgroundColor) { this.backgroundColor = backgroundColor; }
    public void setActive(boolean active) { isActive = active; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
