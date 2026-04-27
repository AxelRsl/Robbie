package com.robbie.platform.react.modules;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.robbie.core.media.TourMediaPlayer;

/**
 * TourMediaModule — React Native bridge for tour media playback.
 *
 * Allows the RN side to notify Java when a video finishes playing,
 * so TourExecutor can proceed to the next stop.
 */
public class TourMediaModule extends ReactContextBaseJavaModule {

    private static final String TAG = "TourMediaModule";

    public TourMediaModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "TourMediaModule";
    }

    /**
     * Called by React Native when the video finishes playing.
     * This tells TourMediaPlayer → TourExecutor that media is done.
     */
    @ReactMethod
    public void onVideoFinished(Promise promise) {
        try {
            Log.i(TAG, "onVideoFinished called from React Native");
            TourMediaPlayer.getInstance().onVideoFinished();
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error in onVideoFinished", e);
            promise.reject("ERROR", e.getMessage());
        }
    }

    /**
     * Called by React Native when the video encounters an error.
     */
    @ReactMethod
    public void onVideoError(String error, Promise promise) {
        try {
            Log.w(TAG, "onVideoError from React Native: " + error);
            TourMediaPlayer.getInstance().onVideoFinished(); // Treat error as completion
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }
}
