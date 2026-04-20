package com.robbie.core.hardware;

import android.util.Log;

import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.CommandListener;


import java.util.HashMap;
import java.util.Map;

/**
 * Gestor de actuadores del robot OrionStar.
 *
 * Controla:
 * - Movimiento (avance, retroceso, rotacion, velocidad)
 * - Cabeza (pan y tilt)
 * - Joystick virtual (desplazamiento libre)
 */
public class ActuatorManager {

    private static final String TAG = "ActuatorManager";
    private static volatile ActuatorManager sInstance;

    private float maxSpeed = 1.5f;
    private float acceleration = 0.5f;
    private int headPanRange = 180;
    private int headTiltRange = 45;
    private int moveReqId = 4001;
    private int headReqId = 5001;
    private boolean isMoving = false;

    private ActuatorManager() {
    }

    public static ActuatorManager getInstance() {
        if (sInstance == null) {
            synchronized (ActuatorManager.class) {
                if (sInstance == null) {
                    sInstance = new ActuatorManager();
                }
            }
        }
        return sInstance;
    }

    /**
     * Mueve el robot hacia adelante una distancia dada (metros).
     */
    public void moveForward(float distance) {
        move(distance, 0);
    }

    /**
     * Mueve el robot hacia atras una distancia dada (metros).
     */
    public void moveBackward(float distance) {
        move(-distance, 0);
    }

    /**
     * Rota el robot un angulo dado (grados, positivo = izquierda).
     */
    public void rotate(float angleDegrees) {
        move(0, angleDegrees);
    }

    /**
     * Movimiento combinado: distancia lineal + angulo de rotacion.
     */
    public void move(float linearDistance, float angularDegrees) {
        try {
            RobotApi api = RobotApi.getInstance();
            if (api == null) {
                Log.w(TAG, "RobotApi not available");
                return;
            }
            isMoving = true;
            api.goForward(moveReqId++, linearDistance, new CommandListener() {
                @Override
                public void onResult(int result, String message) {
                    isMoving = false;
                    Log.d(TAG, "Move completed: " + message);
                }
            });
        } catch (Exception e) {
            isMoving = false;
            Log.e(TAG, "Error executing move", e);
        }
    }

    /**
     * Control de joystick virtual: desplazamiento libre por velocidades angulares y lineales.
     * @param linearSpeed velocidad lineal (-1.0 a 1.0)
     * @param angularSpeed velocidad angular (-1.0 a 1.0)
     */
    public void joystickMove(float linearSpeed, float angularSpeed) {
        try {
            RobotApi api = RobotApi.getInstance();
            if (api == null) return;

            float clampedLinear = Math.max(-1.0f, Math.min(1.0f, linearSpeed)) * maxSpeed;
            float clampedAngular = Math.max(-1.0f, Math.min(1.0f, angularSpeed)) * maxSpeed;

            // Note: Using moveHead for joystick control may not be correct API usage
            // This should likely use a different movement API method
            Log.d(TAG, "Joystick move: linear=" + clampedLinear + " angular=" + clampedAngular);
        } catch (Exception e) {
            Log.e(TAG, "Joystick move error", e);
        }
    }

    /**
     * Detiene todo movimiento inmediatamente.
     */
    public void stopMovement() {
        try {
            RobotApi api = RobotApi.getInstance();
            if (api != null) {
                api.stopMove(moveReqId++, new CommandListener() {
                    @Override
                    public void onResult(int result, String message) {
                        Log.d(TAG, "Stop movement completed: " + message);
                    }
                });
            }
            isMoving = false;
        } catch (Exception e) {
            Log.e(TAG, "Error stopping movement", e);
        }
    }

    /**
     * Mueve la cabeza del robot.
     * @param pan angulo horizontal (grados, -90 a 90)
     * @param tilt angulo vertical (grados, -30 a 30)
     */
    public void moveHead(int pan, int tilt) {
        try {
            RobotApi api = RobotApi.getInstance();
            if (api == null) return;

            int clampedPan = Math.max(-headPanRange / 2, Math.min(headPanRange / 2, pan));
            int clampedTilt = Math.max(-headTiltRange, Math.min(headTiltRange, tilt));

            api.moveHead(headReqId++, "absolute", "head", clampedPan, clampedTilt, new CommandListener() {
                @Override
                public void onResult(int result, String message) {
                    Log.d(TAG, "Head movement completed: " + message);
                }
            });
            Log.d(TAG, "Head moved: pan=" + clampedPan + " tilt=" + clampedTilt);
        } catch (Exception e) {
            Log.e(TAG, "Error moving head", e);
        }
    }

    /**
     * Centra la cabeza del robot.
     */
    public void resetHead() {
        moveHead(0, 0);
    }

    /**
     * Navega a un punto en el mapa por nombre/ID.
     */
    public void navigateToPoint(String pointName) {
        try {
            RobotApi api = RobotApi.getInstance();
            if (api == null) return;

            isMoving = true;
            api.startNavigation(moveReqId++, pointName, 0.1, 10000, new CommandListener() {
                @Override
                public void onResult(int result, String message) {
                    isMoving = false;
                    Log.d(TAG, "Navigation to '" + pointName + "' completed: " + message);
                }
            });
        } catch (Exception e) {
            isMoving = false;
            Log.e(TAG, "Error navigating to point", e);
        }
    }

    public boolean isMoving() {
        return isMoving;
    }

    public float getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(float maxSpeed) {
        this.maxSpeed = Math.max(0.1f, Math.min(3.0f, maxSpeed));
    }

    public float getAcceleration() {
        return acceleration;
    }

    public void setAcceleration(float acceleration) {
        this.acceleration = Math.max(0.1f, Math.min(2.0f, acceleration));
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isMoving", isMoving);
        status.put("maxSpeed", maxSpeed);
        status.put("acceleration", acceleration);
        status.put("headPanRange", headPanRange);
        status.put("headTiltRange", headTiltRange);
        return status;
    }

    public void destroy() {
        stopMovement();
        sInstance = null;
    }
}
