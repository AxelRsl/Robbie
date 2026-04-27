package com.robbie.core.media;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.io.FileInputStream;

/**
 * Fullscreen dialog that plays a video file.
 * Uses MediaPlayer + SurfaceView (not VideoView) for maximum codec compatibility.
 * Uses FileInputStream.getFD() to bypass URI parsing issues with corrupted headers.
 */
public class VideoPlayerDialog extends Dialog {

    private static final String TAG = "VideoPlayerDialog";

    private final String videoPath;
    private final Runnable onCompleteCallback;
    private SurfaceView surfaceView;
    private MediaPlayer mediaPlayer;
    private boolean completed = false;
    private boolean surfaceReady = false;
    private boolean playerPrepared = false;

    public VideoPlayerDialog(Context context, String videoPath, Runnable onComplete) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.videoPath = videoPath;
        this.onCompleteCallback = onComplete;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate: videoPath=" + videoPath);

        try {
            // Fullscreen + keep screen on
            Window window = getWindow();
            if (window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }

            // Create layout with SurfaceView
            FrameLayout layout = new FrameLayout(getContext());
            layout.setBackgroundColor(Color.BLACK);

            surfaceView = new SurfaceView(getContext());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER);
            layout.addView(surfaceView, params);

            setContentView(layout);

            // Setup surface callback
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    Log.i(TAG, "Surface created");
                    surfaceReady = true;
                    tryStartPlayback(holder);
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    Log.d(TAG, "Surface changed: " + width + "x" + height);
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    Log.d(TAG, "Surface destroyed");
                    surfaceReady = false;
                }
            });

            // Prepare MediaPlayer
            prepareMediaPlayer();

        } catch (Exception e) {
            Log.e(TAG, "Error in VideoPlayerDialog.onCreate", e);
            finishWithCallback();
        }
    }

    private void prepareMediaPlayer() {
        try {
            mediaPlayer = new MediaPlayer();

            // Use FileInputStream + FileDescriptor to bypass URI parsing
            FileInputStream fis = new FileInputStream(videoPath);
            mediaPlayer.setDataSource(fis.getFD());
            fis.close();

            mediaPlayer.setOnPreparedListener(mp -> {
                Log.i(TAG, "MediaPlayer prepared, duration=" + mp.getDuration() + "ms");
                playerPrepared = true;
                if (surfaceReady) {
                    mp.setDisplay(surfaceView.getHolder());
                    mp.start();
                    Log.i(TAG, "Video playback started");
                }
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.i(TAG, "Video playback completed");
                finishWithCallback();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: what=" + what + " extra=" + extra);
                finishWithCallback();
                return true;
            });

            mediaPlayer.setOnVideoSizeChangedListener((mp, width, height) -> {
                Log.i(TAG, "Video size: " + width + "x" + height);
            });

            mediaPlayer.prepareAsync();
            Log.i(TAG, "MediaPlayer prepareAsync called");

        } catch (Exception e) {
            Log.e(TAG, "Error preparing MediaPlayer", e);
            finishWithCallback();
        }
    }

    private void tryStartPlayback(SurfaceHolder holder) {
        if (mediaPlayer != null && playerPrepared) {
            mediaPlayer.setDisplay(holder);
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                Log.i(TAG, "Video playback started (surface was late)");
            }
        }
    }

    private void finishWithCallback() {
        if (completed) return;
        completed = true;

        releasePlayer();
        dismiss();

        if (onCompleteCallback != null) {
            new Handler(Looper.getMainLooper()).post(onCompleteCallback);
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.w(TAG, "Error releasing MediaPlayer: " + e.getMessage());
            }
            mediaPlayer = null;
        }
    }

    @Override
    public void onBackPressed() {
        finishWithCallback();
    }

    public void stopAndDismiss() {
        Log.i(TAG, "stopAndDismiss called");
        new Handler(Looper.getMainLooper()).post(this::finishWithCallback);
    }
}
