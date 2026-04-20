package com.robbie.core.hardware;

import android.content.Context;
import android.util.Log;

import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.person.PersonApi;
import com.ainirobot.coreservice.client.listener.Person;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstraccion de sensores del robot OrionStar.
 *
 * Proporciona acceso unificado a:
 * - Camaras (frontal, inferior)
 * - LIDAR (navegacion y mapeo)
 * - Sensores ultrasonicos (deteccion de obstaculos)
 * - Giroscopio/IMU (orientacion)
 * - Deteccion de personas (PersonApi)
 * - Bateria
 */
public class SensorManager {

    private static final String TAG = "SensorManager";
    private static volatile SensorManager sInstance;

    private final Context context;
    private boolean initialized = false;
    private final List<SensorStatusListener> listeners = new ArrayList<>();
    private final Map<String, SensorState> sensorStates = new HashMap<>();

    public enum SensorType {
        CAMERA_FRONT,
        CAMERA_BOTTOM,
        LIDAR,
        ULTRASONIC,
        GYROSCOPE,
        PERSON_DETECTION,
        BATTERY
    }

    public enum SensorStatus {
        ACTIVE,
        INACTIVE,
        ERROR,
        UNKNOWN
    }

    public interface SensorStatusListener {
        void onSensorStatusChanged(SensorType type, SensorStatus status);
        void onBatteryLevelChanged(int level, boolean isCharging);
        void onPersonDetected(int personCount);
    }

    public static class SensorState {
        public SensorType type;
        public SensorStatus status;
        public boolean enabled;
        public String resolution;
        public float range;
        public float sensitivity;
        public long lastUpdate;

        public SensorState(SensorType type) {
            this.type = type;
            this.status = SensorStatus.UNKNOWN;
            this.enabled = true;
            this.lastUpdate = System.currentTimeMillis();
        }
    }

    private SensorManager(Context context) {
        this.context = context.getApplicationContext();
        initializeSensorStates();
    }

    public static SensorManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (SensorManager.class) {
                if (sInstance == null) {
                    sInstance = new SensorManager(context);
                }
            }
        }
        return sInstance;
    }

    public static SensorManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("SensorManager not initialized. Call getInstance(Context) first.");
        }
        return sInstance;
    }

    private void initializeSensorStates() {
        for (SensorType type : SensorType.values()) {
            SensorState state = new SensorState(type);
            switch (type) {
                case CAMERA_FRONT:
                    state.resolution = "1080p";
                    break;
                case CAMERA_BOTTOM:
                    state.resolution = "720p";
                    break;
                case LIDAR:
                    state.range = 10.0f;
                    break;
                case ULTRASONIC:
                    state.sensitivity = 0.8f;
                    break;
                default:
                    break;
            }
            sensorStates.put(type.name(), state);
        }
    }

    public void initialize() {
        if (initialized) return;
        try {
            for (SensorState state : sensorStates.values()) {
                state.status = SensorStatus.ACTIVE;
                state.lastUpdate = System.currentTimeMillis();
            }
            initialized = true;
            Log.i(TAG, "SensorManager initialized with " + sensorStates.size() + " sensors");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing SensorManager", e);
        }
    }

    public SensorState getSensorState(SensorType type) {
        return sensorStates.get(type.name());
    }

    public Map<String, SensorState> getAllSensorStates() {
        return new HashMap<>(sensorStates);
    }

    public void setSensorEnabled(SensorType type, boolean enabled) {
        SensorState state = sensorStates.get(type.name());
        if (state != null) {
            state.enabled = enabled;
            state.status = enabled ? SensorStatus.ACTIVE : SensorStatus.INACTIVE;
            state.lastUpdate = System.currentTimeMillis();
            notifySensorStatusChanged(type, state.status);
            Log.d(TAG, "Sensor " + type.name() + " enabled=" + enabled);
        }
    }

    public int getBatteryLevel() {
        try {
            RobotApi api = RobotApi.getInstance();
            if (api != null) {
                return api.getBatteryLevel();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not get battery level", e);
        }
        return -1;
    }

    public boolean isBatteryCharging() {
        try {
            // Note: isCharging() method not available in current RobotApi version
            // This would need to be implemented using a different API method or sensor reading
            Log.w(TAG, "Battery charging status not available in current API");
        } catch (Exception e) {
            Log.w(TAG, "Could not get charging status", e);
        }
        return false;
    }

    public int getDetectedPersonCount() {
        try {
            List<Person> faces = PersonApi.getInstance().getAllFaceList();
            return faces != null ? faces.size() : 0;
        } catch (Exception e) {
            Log.w(TAG, "Could not get person count", e);
            return 0;
        }
    }

    public Map<String, Object> getSensorSummary() {
        Map<String, Object> summary = new HashMap<>();
        int battery = getBatteryLevel();
        boolean charging = isBatteryCharging();
        int persons = getDetectedPersonCount();

        summary.put("batteryLevel", battery);
        summary.put("isCharging", charging);
        summary.put("detectedPersons", persons);
        summary.put("initialized", initialized);

        Map<String, Object> sensors = new HashMap<>();
        for (Map.Entry<String, SensorState> entry : sensorStates.entrySet()) {
            Map<String, Object> sensorInfo = new HashMap<>();
            SensorState state = entry.getValue();
            sensorInfo.put("status", state.status.name());
            sensorInfo.put("enabled", state.enabled);
            if (state.resolution != null) sensorInfo.put("resolution", state.resolution);
            if (state.range > 0) sensorInfo.put("range", state.range);
            if (state.sensitivity > 0) sensorInfo.put("sensitivity", state.sensitivity);
            sensorInfo.put("lastUpdate", state.lastUpdate);
            sensors.put(entry.getKey(), sensorInfo);
        }
        summary.put("sensors", sensors);
        return summary;
    }

    public void addListener(SensorStatusListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(SensorStatusListener listener) {
        listeners.remove(listener);
    }

    private void notifySensorStatusChanged(SensorType type, SensorStatus status) {
        for (SensorStatusListener listener : listeners) {
            try {
                listener.onSensorStatusChanged(type, status);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying sensor listener", e);
            }
        }
    }

    public void destroy() {
        listeners.clear();
        sensorStates.clear();
        initialized = false;
        sInstance = null;
        Log.i(TAG, "SensorManager destroyed");
    }
}
