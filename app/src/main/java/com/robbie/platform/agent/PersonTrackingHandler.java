package com.robbie.platform.agent;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.ActionListener;
import com.ainirobot.coreservice.client.listener.Person;
import com.ainirobot.coreservice.client.person.PersonApi;
import com.ainirobot.coreservice.client.person.PersonListener;
import java.util.Collections;
import java.util.List;

/**
 * Maneja toda la logica de deteccion y seguimiento de personas.
 *
 * Extraido de RobotActionHandler para separar responsabilidades.
 * Encapsula:
 * - Polling de PersonApi (offloaded a background thread)
 * - FocusFollow del SDK (startFocusFollow/stopFocusFollow)
 * - Face-lost timeout antes de disengage
 * - Decision de tracking basada en distancia de persona
 * - Stubs de proximidad y LIDAR (SDK no los expone actualmente)
 */
public class PersonTrackingHandler {

    private static final String TAG = "PersonTracking";

    private static final int NO_PERSON_THRESHOLD = 3;
    private static final long FOCUS_LOST_TIMEOUT_MS = 3000;
    private static final float FOCUS_MAX_DISTANCE = 3.0f;
    private static final float PROXIMITY_STOP_DISTANCE = 0.5f;
    private static final long DISENGAGE_HYSTERESIS_MS = 1000L;
    private static final long SAFETY_POLL_INTERVAL_MS = 1500L;

    // ── Injected dependencies ──
    private RobotApi robotApi;
    private final Handler mainHandler;
    private final Context context;
    private PersonTrackingListener trackingListener;
    private NavigationStateSupplier navStateSupplier;

    // ── State (volatile for binder thread access) ──
    private volatile boolean focusFollowActive = false;
    private volatile int lastTrackedFaceId = -1;
    private volatile boolean personPollRunning = false;
    private volatile boolean isPersonNearby = false;

    // ── Internal state ──
    private int noPersonCount = 0;
    private int focusReqId = -1;
    private boolean robotApiConnected = false;
    private float lastKnownDistance = 0f;
    private long faceLostTimestamp = 0;
    private Runnable faceLostTimeoutRunnable;
    private TrackingAction currentAction = TrackingAction.DISENGAGE;
    private long lastFaceSeenTimeMs = 0L;
    private long outOfRangeStartMs = 0L;

    // ── Worker thread (person detection) ──
    private final HandlerThread workerThread;
    private final Handler workerHandler;
    private PersonListener personListener;

    public PersonTrackingHandler(Handler mainHandler, Context context) {
        this.mainHandler = mainHandler;
        this.context = context.getApplicationContext();
        workerThread = new HandlerThread("PersonTrackingWorker");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
    }

    // ══════════════════════════════════════════
    // SETTERS
    // ══════════════════════════════════════════

    public void setRobotApi(RobotApi api) {
        this.robotApi = api;
        this.robotApiConnected = api != null;
    }

    public void setTrackingListener(PersonTrackingListener l) {
        this.trackingListener = l;
    }

    public void setNavigationStateSupplier(NavigationStateSupplier s) {
        this.navStateSupplier = s;
    }

    // ══════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════

    public void startTracking() {
        if (personPollRunning) return;
        personPollRunning = true;
        noPersonCount = 0;
        registerPersonListener();
        Log.d(TAG, "TRACKING_START");
    }

    public void stopTracking() {
        personPollRunning = false;
        if (personListener != null) {
            try {
                PersonApi.getInstance().unregisterPersonListener(personListener);
                personListener = null;
            } catch (Exception e) {
                Log.w(TAG, "UNREGISTER_WARN: " + e.getMessage());
            }
        }
        workerHandler.removeCallbacksAndMessages(null);
        mainHandler.removeCallbacks(faceLostTimeoutRunnable);
        stopFocusFollowIfActive();
        Log.d(TAG, "TRACKING_STOP");
    }

    public boolean isPersonNearby() {
        return isPersonNearby;
    }

    public boolean isFocusFollowActive() {
        return focusFollowActive;
    }

    public void onRobotApiDisconnected() {
        this.robotApi = null;
        this.robotApiConnected = false;
        focusFollowActive = false;
        lastTrackedFaceId = -1;
    }

    public void destroy() {
        stopTracking();
        workerThread.quitSafely();
        trackingListener = null;
        navStateSupplier = null;
        robotApi = null;
        robotApiConnected = false;
    }

    private void processPersonPollResult(List<Person> faces) {
        boolean faceDetected = faces != null && !faces.isEmpty();

        if (faceDetected) {
            noPersonCount = 0;
            cancelFaceLostTimeout();
            lastFaceSeenTimeMs = System.currentTimeMillis();
            Person f = findClosestPerson(faces);
            if (f == null) return;
            lastKnownDistance = (float) f.getDistance();

            if (!isPersonNearby) {
                isPersonNearby = true;
                Log.d(TAG, "PERSON_ARRIVED: faceId=" + f.getId()
                    + " dist=" + String.format("%.2f", f.getDistance()));
                notifyPersonVisibilityChanged(true);
            }

            TrackingAction action = decideTrackingAction(f);
            if (action != currentAction) {
                currentAction = action;
                Log.d(TAG, "ACTION_CHANGED: " + action.name());
                if (trackingListener != null) {
                    trackingListener.onTrackingActionChanged(action);
                }
            }

            if (action == TrackingAction.ENGAGE || action == TrackingAction.VISUAL) {
                startFocusFollowIfNeeded(f.getId());
            } else if (action == TrackingAction.DISENGAGE) {
                stopFocusFollowIfActive();
            }

        } else {
            noPersonCount++;
            if (isPersonNearby) {
                if (noPersonCount < NO_PERSON_THRESHOLD) {
                    return;
                }
                if (noPersonCount >= NO_PERSON_THRESHOLD && faceLostTimeoutRunnable == null) {
                    Log.d(TAG, "PERSON_LEFT: count=" + noPersonCount);
                    faceLostTimestamp = System.currentTimeMillis();

                    TrackingAction action = decideTrackingAction(null);
                    if (action != currentAction) {
                        currentAction = action;
                        Log.d(TAG, "ACTION_CHANGED: " + action.name());
                        if (trackingListener != null) {
                            trackingListener.onTrackingActionChanged(action);
                        }
                    }

                    faceLostTimeoutRunnable = () -> {
                        Log.d(TAG, "FACE_LOST_TIMEOUT: disengaging");
                        stopFocusFollowIfActive();
                        isPersonNearby = false;
                        lastTrackedFaceId = -1;
                        notifyPersonVisibilityChanged(false);
                        currentAction = TrackingAction.DISENGAGE;
                        if (trackingListener != null) {
                            trackingListener.onTrackingActionChanged(TrackingAction.DISENGAGE);
                        }
                        faceLostTimeoutRunnable = null;
                    };
                    mainHandler.postDelayed(faceLostTimeoutRunnable, FOCUS_LOST_TIMEOUT_MS);
                }
            }
        }
    }

    // ══════════════════════════════════════════
    // PERSON LISTENER (event-driven detection)
    // ══════════════════════════════════════════

    private void registerPersonListener() {
        try {
            personListener = new PersonListener() {
                @Override
                public void personChanged() {
                    workerHandler.post(() -> refreshPersonData());
                }
            };
            boolean ok = PersonApi.getInstance()
                .registerPersonListener(personListener);
            Log.d(TAG, ok ? "LISTENER_REGISTERED: event-driven" : "LISTENER_FAILED: poll-only");
        } catch (Exception e) {
            Log.e(TAG, "LISTENER_EXCEPTION: " + e.getMessage());
        }
        startSafetyPoll();
    }

    private void refreshPersonData() {
        if (!personPollRunning) return;
        try {
            List<Person> faces = PersonApi.getInstance().getCompleteFaceList();
            if (faces == null || faces.isEmpty()) {
                faces = PersonApi.getInstance().getAllFaceList();
            }
            final List<Person> result = faces != null ? faces : Collections.emptyList();
            mainHandler.post(() -> processPersonPollResult(result));
        } catch (Exception e) {
            Log.e(TAG, "REFRESH_ERROR: " + e.getMessage());
        }
    }

    private void startSafetyPoll() {
        workerHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!personPollRunning) return;
                refreshPersonData();
                workerHandler.postDelayed(this, SAFETY_POLL_INTERVAL_MS);
            }
        }, SAFETY_POLL_INTERVAL_MS);
        Log.d(TAG, "SAFETY_POLL: " + SAFETY_POLL_INTERVAL_MS + "ms");
    }

    private Person findClosestPerson(List<Person> faces) {
        Person closest = null;
        double minDist = Double.MAX_VALUE;
        for (Person p : faces) {
            if (p.getId() < 0) continue;
            double d = p.getDistance();
            if (d > 0 && d < minDist) {
                minDist = d;
                closest = p;
            }
        }
        if (closest == null) {
            for (Person p : faces) {
                if (p.getId() >= 0) return p;
            }
            return faces.isEmpty() ? null : faces.get(0);
        }
        return closest;
    }

    // ══════════════════════════════════════════
    // DECISION ENGINE
    // ══════════════════════════════════════════

    private TrackingAction decideTrackingAction(Person person) {
        if (person == null) {
            long elapsed = System.currentTimeMillis() - lastFaceSeenTimeMs;
            if (lastFaceSeenTimeMs == 0) return TrackingAction.DISENGAGE;
            return elapsed < FOCUS_LOST_TIMEOUT_MS
                ? TrackingAction.PAUSE
                : TrackingAction.DISENGAGE;
        }
        float distance = (float) person.getDistance();
        if (distance <= 0f || distance > FOCUS_MAX_DISTANCE) {
            if (outOfRangeStartMs == 0L) outOfRangeStartMs = System.currentTimeMillis();
            long outMs = System.currentTimeMillis() - outOfRangeStartMs;
            return outMs < DISENGAGE_HYSTERESIS_MS
                ? TrackingAction.PAUSE
                : TrackingAction.DISENGAGE;
        }
        outOfRangeStartMs = 0L;
        lastFaceSeenTimeMs = System.currentTimeMillis();
        return TrackingAction.ENGAGE;
    }

    // ══════════════════════════════════════════
    // FOCUS FOLLOW
    // ══════════════════════════════════════════

    private void startFocusFollowIfNeeded(int faceId) {
        if (!isPersonNearby) return;
        if (robotApi == null || !robotApiConnected) return;
        if (navStateSupplier != null &&
            (navStateSupplier.isNavigating() || navStateSupplier.isNavigatingToCharger())) {
            return;
        }
        if (focusFollowActive) {
            if (faceId != lastTrackedFaceId) {
                Log.d(TAG, "FOCUS_SWITCH: " + lastTrackedFaceId + " -> " + faceId);
                stopFocusFollowIfActive();
            } else {
                return;
            }
        }
        focusFollowActive = true;
        lastTrackedFaceId = faceId;
        int reqId = ++focusReqId;
        Log.d(TAG, "FOCUS_FOLLOW_START: reqId=" + reqId);
        try {
            robotApi.startFocusFollow(reqId, faceId, (int) FOCUS_LOST_TIMEOUT_MS, (int) FOCUS_MAX_DISTANCE,
                new ActionListener() {
                    @Override
                    public void onStatusUpdate(int status, String data) throws android.os.RemoteException {
                        if (status == Definition.STATUS_GUEST_LOST) {
                            mainHandler.post(() -> {
                                focusFollowActive = false;
                                lastTrackedFaceId = -1;
                            });
                        }
                    }

                    @Override
                    public void onError(int errorCode, String errorString) throws android.os.RemoteException {
                        Log.w(TAG, "FOCUS_FOLLOW_FAILED: reqId=" + reqId
                            + " error=" + errorCode + " msg=" + errorString);
                        mainHandler.post(() -> {
                            focusFollowActive = false;
                            lastTrackedFaceId = -1;
                        });
                    }

                    @Override
                    public void onResult(int status, String responseString) throws android.os.RemoteException {
                        // No-op: focus follow remains active until GUEST_LOST,
                        // explicit stop, or error. Resetting here caused the
                        // 200ms re-call spam.
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "startFocusFollow error: " + e.getMessage());
            focusFollowActive = false;
            lastTrackedFaceId = -1;
        }
    }

    private void stopFocusFollowIfActive() {
        if (!focusFollowActive || robotApi == null) return;
        focusFollowActive = false;
        lastTrackedFaceId = -1;
        Log.d(TAG, "FOCUS_FOLLOW_STOP");
        try {
            robotApi.stopFocusFollow(++focusReqId);
        } catch (Exception e) {
            Log.w(TAG, "stopFocusFollow error: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════
    // NOTIFICATIONS
    // ══════════════════════════════════════════

    private void notifyPersonVisibilityChanged(boolean visible) {
        if (trackingListener != null) {
            trackingListener.onPersonVisibilityChanged(visible, lastTrackedFaceId);
        }
    }

    // ══════════════════════════════════════════
    // FACE LOST TIMEOUT
    // ══════════════════════════════════════════

    private void cancelFaceLostTimeout() {
        if (faceLostTimeoutRunnable != null) {
            mainHandler.removeCallbacks(faceLostTimeoutRunnable);
            faceLostTimeoutRunnable = null;
        }
        faceLostTimestamp = 0;
    }

    // ══════════════════════════════════════════
    // SENSOR STUBS
    // ══════════════════════════════════════════

    public void onProximityData(float distanceMeters) {
        // TODO: conectar cuando SDK exponga OnObstacleStatusListener
        // Por ahora usar Person.getDistance() como proxy
        Log.v(TAG, "PROXIMITY_STUB: " + distanceMeters);
    }

    public void onLidarObstacleDetected(Object obstacle) {
        // TODO: conectar cuando SDK exponga LidarDataListener
        Log.v(TAG, "LIDAR_STUB: obstacle detected");
    }

    // ══════════════════════════════════════════
    // INTERFACES
    // ══════════════════════════════════════════

    public interface PersonTrackingListener {
        void onPersonVisibilityChanged(boolean visible, int faceId);
        void onTrackingActionChanged(TrackingAction action);
    }

    public interface NavigationStateSupplier {
        boolean isNavigating();
        boolean isNavigatingToCharger();
    }

    public enum TrackingAction {
        ENGAGE, VISUAL, PAUSE, DISENGAGE
    }
}
