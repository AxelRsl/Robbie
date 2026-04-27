package com.robbie.core.media;

import android.app.Activity;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages multimedia playback during tour guide stops.
 *
 * For video: shows a fullscreen VideoPlayerDialog (within the current Activity).
 * For background music: uses Android MediaPlayer directly.
 *
 * Uses Dialog approach instead of Activity/Overlay/ReactNative because:
 * - EveActivity has singleInstance launch mode (blocks other Activities)
 * - WindowManager overlay failed on the robot's Android build
 * - react-native-video crashes the RN bundle on import
 * - A Dialog lives within the host Activity's window = works always
 */
public class TourMediaPlayer {

    private static final String TAG = "TourMediaPlayer";
    private static final String MODULEDATA_BASE = "/storage/emulated/0/moduledata/module_guide/";
    private static volatile TourMediaPlayer sInstance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Context appContext;
    private Activity currentActivity;
    private MediaPlayer musicPlayer;
    private VideoPlayerDialog videoDialog;
    private boolean isPlaying = false;
    private MediaCompletionCallback completionCallback;

    public interface MediaCompletionCallback {
        void onMediaComplete();
    }

    private TourMediaPlayer() {}

    public static TourMediaPlayer getInstance() {
        if (sInstance == null) {
            synchronized (TourMediaPlayer.class) {
                if (sInstance == null) {
                    sInstance = new TourMediaPlayer();
                }
            }
        }
        return sInstance;
    }

    public void initialize(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Set the current foreground Activity.
     * Must be called from EveActivity.onResume() so we have a valid
     * Activity reference for showing the video Dialog.
     */
    public void setCurrentActivity(Activity activity) {
        this.currentActivity = activity;
    }

    /**
     * Play multimedia for a tour stop.
     */
    public void playStopMedia(String tourId, Map<String, Object> stopData, MediaCompletionCallback callback) {
        this.completionCallback = callback;

        String mediaType = getStr(stopData, "mediaType", "tts");
        String videoUrl = getStr(stopData, "videoUrl", "");
        String musicTrack = getStr(stopData, "musicTrack", "");

        Log.i(TAG, "playStopMedia: type=" + mediaType + " video=" + videoUrl + " music=" + musicTrack);

        // Start background music if configured
        if (!musicTrack.isEmpty()) {
            playBackgroundMusic(tourId, musicTrack);
        }

        switch (mediaType) {
            case "video":
                if (!videoUrl.isEmpty()) {
                    playVideo(tourId, videoUrl);
                } else {
                    Log.w(TAG, "Video type but no videoUrl");
                    notifyComplete();
                }
                break;
            case "tts":
            default:
                notifyComplete();
                break;
        }
    }

    /**
     * Play video using a fullscreen Dialog within the current Activity.
     */
    private void playVideo(String tourId, String videoUrl) {
        String videoPath = resolveMediaPath(tourId, videoUrl);
        File videoFile = new File(videoPath);

        if (!videoFile.exists()) {
            Log.e(TAG, "Video file not found: " + videoPath);
            notifyComplete();
            return;
        }

        if (currentActivity == null || currentActivity.isFinishing()) {
            Log.e(TAG, "No active Activity — cannot show video dialog");
            notifyComplete();
            return;
        }

        Log.i(TAG, "Showing video dialog: " + videoPath + " (" + videoFile.length() + " bytes)");
        isPlaying = true;

        // Must run on UI thread
        handler.post(() -> {
            try {
                // Dismiss any previous dialog
                dismissVideoDialog();

                videoDialog = new VideoPlayerDialog(currentActivity, videoPath, () -> {
                    Log.i(TAG, "Video dialog completed callback");
                    isPlaying = false;
                    videoDialog = null;
                    notifyComplete();
                });
                videoDialog.setCancelable(false); // Don't dismiss on touch outside
                videoDialog.show();
                Log.i(TAG, "Video dialog shown successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error showing video dialog", e);
                isPlaying = false;
                notifyComplete();
            }
        });
    }

    private void dismissVideoDialog() {
        if (videoDialog != null) {
            try {
                videoDialog.stopAndDismiss();
            } catch (Exception e) {
                Log.w(TAG, "Error dismissing video dialog: " + e.getMessage());
            }
            videoDialog = null;
        }
    }

    /** Kept for backward compatibility with TourMediaActivity. */
    public void onMediaActivityFinished() {
        isPlaying = false;
        notifyComplete();
    }

    /** Called by RN TourMediaModule (kept for compatibility). */
    public void onVideoFinished() {
        isPlaying = false;
        notifyComplete();
    }

    // ─── Background Music ────────────────────────────────────────────────────

    private void playBackgroundMusic(String tourId, String musicTrack) {
        stopBackgroundMusic();

        String musicPath = resolveMediaPath(tourId, musicTrack);
        File musicFile = new File(musicPath);

        if (!musicFile.exists()) {
            Log.w(TAG, "Music file not found: " + musicPath);
            return;
        }

        try {
            musicPlayer = new MediaPlayer();
            musicPlayer.setDataSource(musicPath);
            musicPlayer.setVolume(0.3f, 0.3f);
            musicPlayer.setLooping(true);
            musicPlayer.prepare();
            musicPlayer.start();
            Log.i(TAG, "Background music started: " + musicPath);
        } catch (Exception e) {
            Log.e(TAG, "Error playing background music", e);
            if (musicPlayer != null) {
                musicPlayer.release();
                musicPlayer = null;
            }
        }
    }

    // ─── Stop / Cleanup ──────────────────────────────────────────────────────

    public void stopAll() {
        Log.i(TAG, "stopAll called");
        handler.post(this::dismissVideoDialog);
        stopBackgroundMusic();
        isPlaying = false;
    }

    public void stopBackgroundMusic() {
        if (musicPlayer != null) {
            try {
                if (musicPlayer.isPlaying()) {
                    musicPlayer.stop();
                }
                musicPlayer.release();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping music", e);
            }
            musicPlayer = null;
        }
    }

    public boolean isMediaPlaying() {
        return isPlaying;
    }

    private void notifyComplete() {
        if (completionCallback != null) {
            handler.post(() -> completionCallback.onMediaComplete());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String resolveMediaPath(String tourId, String filename) {
        if (filename.startsWith("/")) return filename;
        return MODULEDATA_BASE + tourId + "/" + filename;
    }

    private static String getStr(Map<String, Object> map, String key, String def) {
        if (map == null || !map.containsKey(key)) return def;
        Object v = map.get(key);
        return v != null ? v.toString() : def;
    }
}
