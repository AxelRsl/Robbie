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
import com.robbie.platform.oem.person.TrackedPerson;
import com.robbie.platform.retail.AsyncTaskHelper;

import java.util.ArrayList;
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
    private static final long PERSON_POLL_INTERVAL_MS = 1500;
    private static final long FOCUS_LOST_TIMEOUT_MS = 3000;
    private static final float FOCUS_MAX_DISTANCE = 3.0f;
    private static final float PROXIMITY_STOP_DISTANCE = 0.5f;
    private static final long DISENGAGE_HYSTERESIS_MS = 1000L;

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
    private Runnable personPollRunnable;
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
        this.personPollRunnable = () -> pollPersonApi();
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

    // ══════════════════════════════════════════
    // POLLING (offloaded to background thread)
    // ══════════════════════════════════════════

    private void pollPersonApi() {
        if (!personPollRunning) return;
        AsyncTaskHelper.execute(() -> {
            List<TrackedPerson> converted = new ArrayList<>();
            try {
                PersonApi pApi = PersonApi.getInstance();
                List<Person> faces = pApi.getAllFaceList();
                if (faces != null) {
                    for (Person p : faces) {
                        converted.add(personToTracked(p));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "[Poll] error: " + e.getMessage());
            }
            final List<TrackedPerson> result = converted;
            mainHandler.post(() -> {
                if (!personPollRunning) return;
                processPersonPollResult(result);
                if (personPollRunning) {
                    mainHandler.postDelayed(personPollRunnable, PERSON_POLL_INTERVAL_MS);
                }
            });
        });
    }

    private void processPersonPollResult(List<TrackedPerson> faces) {
        boolean faceDetected = faces != null && !faces.isEmpty();

        if (faceDetected) {
            noPersonCount = 0;
            cancelFaceLostTimeout();
            lastFaceSeenTimeMs = System.currentTimeMillis();
            TrackedPerson f = faces.get(0);
            lastKnownDistance = (float) f.getDistanceMeters();

            if (!isPersonNearby) {
                isPersonNearby = true;
                lastTrackedFaceId = f.getId();
                Log.d(TAG, "PERSON_ARRIVED: faceId=" + f.getId());
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
            if (ok) {
                Log.d(TAG, "LISTENER_REGISTERED: event-driven");
            } else {
                Log.w(TAG, "LISTENER_FAILED: fallback polling");
                startPollingFallback();
            }
        } catch (Exception e) {
            Log.e(TAG, "LISTENER_EXCEPTION: " + e.getMessage());
            startPollingFallback();
        }
    }

    private void refreshPersonData() {
        if (!personPollRunning) return;
        try {
            List<Person> faces = PersonApi.getInstance()
                .getCompleteFaceList(FOCUS_MAX_DISTANCE);
            if (faces == null || faces.isEmpty()) {
                faces = PersonApi.getInstance()
                    .getAllFaceList(FOCUS_MAX_DISTANCE);
            }
            final List<TrackedPerson> tracked =
                toTrackedPersons(faces != null ? faces : Collections.emptyList());
            mainHandler.post(() -> processPersonPollResult(tracked));
        } catch (Exception e) {
            Log.e(TAG, "REFRESH_ERROR: " + e.getMessage());
        }
    }

    private void startPollingFallback() {
        workerHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!personPollRunning) return;
                refreshPersonData();
                workerHandler.postDelayed(this, 200L);
            }
        }, 200L);
        Log.w(TAG, "POLLING_FALLBACK: 200ms");
    }

    private List<TrackedPerson> toTrackedPersons(List<Person> persons) {
        List<TrackedPerson> result = new ArrayList<>();
        for (Person p : persons) {
            result.add(personToTracked(p));
        }
        return result;
    }

    private TrackedPerson personToTracked(Person p) {
        return new TrackedPerson(
            p.getId(), p.getId(), null, null,
            p.getDistance(), 0, 0.0, 0.0, 0.0,
            true, false, false, false, false, false, false,
            0, 0, null, null, 0, null, null, null,
            0.0, 0, System.currentTimeMillis(), null, null
        );
    }

    // ══════════════════════════════════════════
    // DECISION ENGINE
    // ══════════════════════════════════════════

    private TrackingAction decideTrackingAction(TrackedPerson person) {
        if (person == null) {
            long elapsed = System.currentTimeMillis() - lastFaceSeenTimeMs;
            if (lastFaceSeenTimeMs == 0) return TrackingAction.DISENGAGE;
            return elapsed < FOCUS_LOST_TIMEOUT_MS
                ? TrackingAction.PAUSE
                : TrackingAction.DISENGAGE;
        }
        float distance = (float) person.getDistanceMeters();
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
            lastTrackedFaceId = faceId;
            return;
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
