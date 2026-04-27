package com.robbie.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entidad Room para configuraciones de animaciones del robot.
 *
 * Cada animacion define una combinacion de:
 * - Movimientos de cabeza (pan/tilt secuencias)
 * - Efectos LED (color, modo)
 * - Expresion en pantalla (nombre del drawable o tipo)
 *
 * Se asigna a un trigger (emocion, saludo, despedida, idle, etc.)
 * y puede ser activada por el AgentOS o manualmente desde el panel.
 */
@Entity(tableName = "animations")
public class AnimationEntity {

    @PrimaryKey
    @NonNull
    private String id;

    @NonNull
    private String name;

    // Trigger: happy, sad, angry, greeting, farewell, idle, dancing, custom
    @NonNull
    private String trigger;

    // Secuencia de movimientos de cabeza como JSON array:
    // [{"pan":30,"tilt":10,"durationMs":500},{"pan":-30,"tilt":0,"durationMs":500}]
    private String headMovements;

    // LED config
    private String ledColor;    // hex e.g. "#FF0000"
    private String ledMode;     // solid, breathing, rainbow, pulse

    // Expresion en pantalla (nombre del drawable o URL de imagen)
    private String screenExpression;

    // TTS opcional que se dice al activar la animacion
    private String ttsText;

    // Descripcion legible para el panel
    private String description;

    // Duracion total en ms (0 = auto-calcular de los pasos)
    private int durationMs;

    // Si esta habilitada
    private boolean enabled;

    // Orden de prioridad para el mismo trigger (menor = mayor prioridad)
    private int priority;

    private long createdAt;
    private long updatedAt;

    public AnimationEntity() {
        this.id = "";
        this.name = "";
        this.trigger = "custom";
        this.enabled = true;
        this.priority = 0;
        this.durationMs = 0;
    }

    // ─── Getters ───

    @NonNull public String getId() { return id; }
    @NonNull public String getName() { return name; }
    @NonNull public String getTrigger() { return trigger; }
    public String getHeadMovements() { return headMovements; }
    public String getLedColor() { return ledColor; }
    public String getLedMode() { return ledMode; }
    public String getScreenExpression() { return screenExpression; }
    public String getTtsText() { return ttsText; }
    public String getDescription() { return description; }
    public int getDurationMs() { return durationMs; }
    public boolean isEnabled() { return enabled; }
    public int getPriority() { return priority; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }

    // ─── Setters ───

    public void setId(@NonNull String id) { this.id = id; }
    public void setName(@NonNull String name) { this.name = name; }
    public void setTrigger(@NonNull String trigger) { this.trigger = trigger; }
    public void setHeadMovements(String headMovements) { this.headMovements = headMovements; }
    public void setLedColor(String ledColor) { this.ledColor = ledColor; }
    public void setLedMode(String ledMode) { this.ledMode = ledMode; }
    public void setScreenExpression(String screenExpression) { this.screenExpression = screenExpression; }
    public void setTtsText(String ttsText) { this.ttsText = ttsText; }
    public void setDescription(String description) { this.description = description; }
    public void setDurationMs(int durationMs) { this.durationMs = durationMs; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setPriority(int priority) { this.priority = priority; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
