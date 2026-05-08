package com.robbie.core.animation;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.CommandListener;

import java.lang.ref.WeakReference;

/**
 * Manages procedural animations by physically moving the robot's head.
 * 
 * Replaces the previous hallucinated Intent-based implementation.
 * Uses OrionStar's RobotApi.getInstance().moveHead() to simulate emotions
 * through physical servos (nodding, shaking head, looking down).
 */
public class ProceduralAnimationManager {

    private static final String TAG = "ProceduralAnimationMgr";
    
    // Kawaii mascot emotions (Dasai Mochi style)
    public enum Emotion {
        IDLE("idle"),
        HAPPY("happy"),
        SAD("sad"),
        THINKING("thinking"),
        LISTENING("listening"),
        SPEAKING("speaking"),
        PROCESSING("processing"),
        SLEEPING("sleeping"),
        SURPRISED("surprised");
        
        public final String value;
        
        Emotion(String value) {
            this.value = value;
        }

        /** Resolve from string with backward-compat aliases for old emotions */
        public static Emotion fromString(String name) {
            if (name == null) return IDLE;
            switch (name.trim().toLowerCase()) {
                case "idle": case "neutral": case "calm": return IDLE;
                case "happy": case "in_love": return HAPPY;
                case "sad": case "broken": return SAD;
                case "thinking": case "confused": case "sceptic": return THINKING;
                case "listening": case "interested": return LISTENING;
                case "speaking": return SPEAKING;
                case "processing": return PROCESSING;
                case "sleeping": case "tired": case "sleepy": return SLEEPING;
                case "surprised": case "afraid": case "angry": case "disgusted": return SURPRISED;
                default:
                    try { return Emotion.valueOf(name.trim().toUpperCase()); }
                    catch (IllegalArgumentException e) { return IDLE; }
            }
        }
    }
    
    private static final long DEFAULT_FACE_DURATION_MS = 5000;
    
    private static ProceduralAnimationManager instance;
    
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int animationReqId = 5000;
    private AnimationStateListener stateListener;
    private WeakReference<Activity> activityRef;
    private boolean systemFaceVisible = false;
    private Runnable pendingBringBack;
    
    private ProceduralAnimationManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public static synchronized ProceduralAnimationManager getInstance(Context context) {
        if (instance == null) {
            instance = new ProceduralAnimationManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Set the current Activity so we can send it to background to reveal the system face.
     * Uses WeakReference to avoid memory leaks.
     */
    public void setActivity(Activity activity) {
        this.activityRef = new WeakReference<>(activity);
        Log.d(TAG, "Activity registered for system face reveal");
    }
    
    /**
     * Check if procedural animations are supported
     */
    public boolean isAvailable() {
        return RobotApi.getInstance().isApiConnectedService();
    }
    
    /**
     * Play a procedural emotion/expression animation using head movements.
     */
    public void playExpression(Emotion emotion) {
        playExpression(emotion, -1);
    }
    
    /**
     * Play a procedural emotion/expression animation with custom duration
     */
    public void playExpression(Emotion emotion, long durationMs) {
        if (!isAvailable()) {
            Log.w(TAG, "RobotApi not connected, cannot move head");
            if (stateListener != null) stateListener.onAnimationError("RobotApi disconnected");
            return;
        }
        
        Log.d(TAG, "Playing physical emotion: " + emotion.value);
        if (stateListener != null) stateListener.onAnimationStarted(emotion.value);
        
        switch (emotion) {
            case HAPPY:
                // Bouncy nod (kawaii energy)
                executeHeadSequence(
                    new HeadMove(Definition.JSON_HEAD_RELATIVE, Definition.JSON_HEAD_RELATIVE, 0, -12, 250),
                    new HeadMove(Definition.JSON_HEAD_RELATIVE, Definition.JSON_HEAD_RELATIVE, 0, 24, 300),
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, 0, 0, 350)
                );
                break;
            case SAD:
                // Gentle droop down
                executeHeadSequence(
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, -5, 20, 900),
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, 0, 0, 800)
                );
                break;
            case THINKING:
                // Tilt head to the side (curious)
                executeHeadSequence(
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, 12, -8, 500),
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, 0, 0, 600)
                );
                break;
            case LISTENING:
                // Slight lean forward (attentive)
                executeHeadSequence(
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, 0, -10, 400),
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, 0, 0, 500)
                );
                break;
            case SPEAKING:
                // Small nod rhythm (conversational)
                executeHeadSequence(
                    new HeadMove(Definition.JSON_HEAD_RELATIVE, Definition.JSON_HEAD_RELATIVE, 0, -8, 200),
                    new HeadMove(Definition.JSON_HEAD_RELATIVE, Definition.JSON_HEAD_RELATIVE, 0, 16, 250),
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, 0, 0, 300)
                );
                break;
            case PROCESSING:
                // Stay still, slight down-look
                executeHeadSequence(
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, 0, 5, 400),
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, 0, 0, 600)
                );
                break;
            case SLEEPING:
                // Slow droop and stay
                executeHeadSequence(
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, -3, 25, 1200),
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, 0, 0, 1000)
                );
                break;
            case SURPRISED:
                // Quick pop up (shock)
                executeHeadSequence(
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, 0, -18, 200),
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, 0, 0, 500)
                );
                break;
            case IDLE:
            default:
                // Reset to center
                executeHeadSequence(
                    new HeadMove(Definition.JSON_HEAD_ABSOLUTE, Definition.JSON_HEAD_ABSOLUTE, 0, 0, 500)
                );
                break;
        }
    }
    
    /**
     * Play a custom procedural animation by name
     */
    public void playAnimation(String animationName) {
        playAnimation(animationName, 1.0f, false);
    }
    
    /**
     * Play a custom procedural animation with speed and looping
     */
    public void playAnimation(String animationName, float speed, boolean loop) {
        playExpression(Emotion.fromString(animationName));
    }
    
    /**
     * Show the system's 2D animated face by temporarily sending our Activity to background.
     * The OrionStar Agent OS renders its own face (eyes, mouth) which is normally hidden
     * behind our React Native Activity. This reveals it for a set duration.
     *
     * @param durationMs how long to show the system face (ms). 0 = stay until hideSystemFace() is called.
     */
    public void showSystemFace(long durationMs) {
        handler.post(() -> {
            Activity activity = activityRef != null ? activityRef.get() : null;
            if (activity == null || activity.isFinishing()) {
                Log.w(TAG, "No activity available to reveal system face");
                if (stateListener != null) stateListener.onAnimationError("No activity reference");
                return;
            }
            
            // Cancel any pending bring-back
            if (pendingBringBack != null) {
                handler.removeCallbacks(pendingBringBack);
                pendingBringBack = null;
            }
            
            systemFaceVisible = true;
            
            // Send our activity to background - the system's face is rendered underneath
            activity.moveTaskToBack(true);
            
            if (stateListener != null) stateListener.onAnimationStarted("system_face");
            
            // Schedule bringing our app back to foreground
            if (durationMs > 0) {
                pendingBringBack = () -> hideSystemFace();
                handler.postDelayed(pendingBringBack, durationMs);
            }
        });
    }
    
    /**
     * Show the system face with the default duration (5 seconds).
     */
    public void showSystemFace() {
        showSystemFace(DEFAULT_FACE_DURATION_MS);
    }
    
    /**
     * Show the system face AND play a physical head animation simultaneously.
     */
    public void showSystemFaceWithEmotion(Emotion emotion, long durationMs) {
        showSystemFace(durationMs > 0 ? durationMs : DEFAULT_FACE_DURATION_MS);
        playExpression(emotion);
    }
    
    /**
     * Hide the system face by bringing our Activity back to the foreground.
     */
    public void hideSystemFace() {
        handler.post(() -> {
            if (pendingBringBack != null) {
                handler.removeCallbacks(pendingBringBack);
                pendingBringBack = null;
            }
            
            Activity activity = activityRef != null ? activityRef.get() : null;
            if (activity == null || activity.isFinishing()) {
                Log.w(TAG, "No activity to bring back");
                return;
            }
            
            Log.i(TAG, "Hiding system face, bringing app to foreground");
            systemFaceVisible = false;
            
            // Bring our activity back to front safely without restarting it
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.moveTaskToFront(activity.getTaskId(), ActivityManager.MOVE_TASK_WITH_HOME);
            }
            
            if (stateListener != null) stateListener.onAnimationEnded("system_face");
        });
    }
    
    /**
     * Check if the system face is currently visible.
     */
    public boolean isSystemFaceVisible() {
        return systemFaceVisible;
    }
    
    public void enableAvatarMode() {
        showSystemFace(0); // Show indefinitely until disableAvatarMode
    }
    
    public void disableAvatarMode() {
        hideSystemFace();
    }
    
    public void enableLipSync() {
        Log.d(TAG, "enableLipSync - handled by system face automatically during TTS");
    }
    
    public void disableLipSync() {
        Log.d(TAG, "disableLipSync");
    }
    
    public void startRecordingFaceData() {
        Log.d(TAG, "startRecordingFaceData not supported");
    }
    
    public void stopRecordingFaceData() {
        Log.d(TAG, "stopRecordingFaceData not supported");
    }
    
    public void setStateListener(AnimationStateListener listener) {
        this.stateListener = listener;
    }
    
    public interface AnimationStateListener {
        void onAnimationStarted(String animationName);
        void onAnimationEnded(String animationName);
        void onAnimationError(String error);
    }
    
    private static class HeadMove {
        String hmode;
        String vmode;
        int hangle;
        int vangle;
        long durationMs;
        
        HeadMove(String hmode, String vmode, int hangle, int vangle, long durationMs) {
            this.hmode = hmode;
            this.vmode = vmode;
            this.hangle = hangle;
            this.vangle = vangle;
            this.durationMs = durationMs;
        }
    }
    
    private void executeHeadSequence(HeadMove... moves) {
        handler.post(() -> executeNextMove(moves, 0));
    }
    
    private void executeNextMove(HeadMove[] moves, int index) {
        if (index >= moves.length) {
            if (stateListener != null) stateListener.onAnimationEnded("sequence");
            return;
        }
        
        HeadMove move = moves[index];
        try {
            RobotApi.getInstance().moveHead(
                animationReqId++,
                move.hmode,
                move.vmode,
                move.hangle,
                move.vangle,
                new CommandListener() {
                    @Override
                    public void onResult(int result, String message) {
                        // Move command accepted, wait for duration before next move
                    }
                }
            );
            
            // Wait for the duration of this move, then execute the next one
            handler.postDelayed(() -> executeNextMove(moves, index + 1), move.durationMs);
        } catch (Exception e) {
            Log.e(TAG, "Error moving head", e);
            if (stateListener != null) stateListener.onAnimationError(e.getMessage());
        }
    }
}
