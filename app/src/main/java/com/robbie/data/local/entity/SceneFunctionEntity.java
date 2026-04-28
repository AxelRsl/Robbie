package com.robbie.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Representa una funcion/tarjeta dentro de un proyecto de escena.
 * Cada funcion se muestra como una tarjeta en el menu, con su nombre,
 * icono y comando de activacion que determina que pantalla o accion ejecutar.
 * 
 * activationCommand mapea a los modos de la app:
 *   - "retail"      : Pantalla de productos
 *   - "promo"       : Pantalla de promociones
 *   - "navigating"  : Pantalla de navegacion
 *   - "config"      : Pantalla de configuracion
 *   - "tour"        : Pantalla de tour/video
 *   - O cualquier comando custom que se agregue en el futuro.
 */
@Entity(
    tableName = "scene_functions",
    foreignKeys = @ForeignKey(
        entity = SceneProjectEntity.class,
        parentColumns = "id",
        childColumns = "projectId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index(value = "projectId")
)
public class SceneFunctionEntity {

    @PrimaryKey
    @NonNull
    private String id;

    @NonNull
    private String projectId;

    @NonNull
    private String name;

    @NonNull
    private String icon;

    @NonNull
    private String activationCommand;

    private int orderIndex;

    private String color;

    private String description;

    private long createdAt;

    private long updatedAt;

    public SceneFunctionEntity() {
        this.id = "";
        this.projectId = "";
        this.name = "";
        this.icon = "settings";
        this.activationCommand = "";
        this.orderIndex = 0;
        this.color = "#E4027C";
        this.description = "";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public SceneFunctionEntity(@NonNull String id, @NonNull String projectId,
                               @NonNull String name, @NonNull String icon,
                               @NonNull String activationCommand, int orderIndex,
                               String color, String description,
                               long createdAt, long updatedAt) {
        this.id = id;
        this.projectId = projectId;
        this.name = name;
        this.icon = icon;
        this.activationCommand = activationCommand;
        this.orderIndex = orderIndex;
        this.color = color;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getProjectId() { return projectId; }
    @NonNull public String getName() { return name; }
    @NonNull public String getIcon() { return icon; }
    @NonNull public String getActivationCommand() { return activationCommand; }
    public int getOrderIndex() { return orderIndex; }
    public String getColor() { return color; }
    public String getDescription() { return description; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }

    public void setId(@NonNull String id) { this.id = id; }
    public void setProjectId(@NonNull String projectId) { this.projectId = projectId; }
    public void setName(@NonNull String name) { this.name = name; }
    public void setIcon(@NonNull String icon) { this.icon = icon; }
    public void setActivationCommand(@NonNull String activationCommand) { this.activationCommand = activationCommand; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
    public void setColor(String color) { this.color = color; }
    public void setDescription(String description) { this.description = description; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
