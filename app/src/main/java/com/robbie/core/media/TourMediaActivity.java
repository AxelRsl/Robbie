package com.robbie.core.media;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.io.File;
import java.util.ArrayList;

/**
 * Fullscreen Activity that plays videos or shows image slideshows
 * during tour guide stops.
 *
 * Uses SurfaceView + MediaPlayer instead of VideoView for better
 * compatibility and error handling on the robot's Android build.
 */
public class TourMediaActivity extends Activity {

    private static final String TAG = "TourMediaActivity";

    public static final String EXTRA_MEDIA_TYPE = "media_type";     // "video" or "image"
    public static final String EXTRA_VIDEO_PATH = "video_path";
    public static final String EXTRA_IMAGE_LIST = "image_list";
    public static final String ACTION_CLOSE = "com.robbie.CLOSE_TOUR_MEDIA";

    private static final int SLIDESHOW_INTERVAL_MS = 4000; // 4 seconds per image

    private SurfaceView surfaceView;
    private ImageView imageView;
    private MediaPlayer mediaPlayer;
    private final Handler handler = new Handler();
    private ArrayList<String> imageList;
    private int currentImageIndex = 0;
    private boolean surfaceReady = false;
    private String pendingVideoPath = null;

    private final BroadcastReceiver closeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received close broadcast");
            finishAndNotify();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate started");

        try {
            // Fullscreen immersive + keep screen on
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

            // Register close receiver
            IntentFilter filter = new IntentFilter(ACTION_CLOSE);
            registerReceiver(closeReceiver, filter);

            // Create layout dynamically
            RelativeLayout layout = new RelativeLayout(this);
            layout.setBackgroundColor(Color.BLACK);

            // SurfaceView for video (more reliable than VideoView on custom Android builds)
            surfaceView = new SurfaceView(this);
            RelativeLayout.LayoutParams surfaceParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT);
            surfaceParams.addRule(RelativeLayout.CENTER_IN_PARENT);
            surfaceView.setLayoutParams(surfaceParams);
            surfaceView.setVisibility(View.GONE);
            layout.addView(surfaceView);

            // ImageView for slideshows
            imageView = new ImageView(this);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setBackgroundColor(Color.BLACK);
            RelativeLayout.LayoutParams imgParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT);
            imgParams.addRule(RelativeLayout.CENTER_IN_PARENT);
            imageView.setLayoutParams(imgParams);
            imageView.setVisibility(View.GONE);
            layout.addView(imageView);

            setContentView(layout);

            // Handle intent
            String mediaType = getIntent().getStringExtra(EXTRA_MEDIA_TYPE);
            Log.i(TAG, "Media type: " + mediaType);

            if ("video".equals(mediaType)) {
                startVideo();
            } else if ("image".equals(mediaType)) {
                startSlideshow();
            } else {
                Log.w(TAG, "Unknown media type: " + mediaType);
                finishAndNotify();
            }
        } catch (Exception e) {
            Log.e(TAG, "CRASH in onCreate!", e);
            finishAndNotify();
        }
    }

    private void startVideo() {
        String videoPath = getIntent().getStringExtra(EXTRA_VIDEO_PATH);
        if (videoPath == null || videoPath.isEmpty()) {
            Log.e(TAG, "Video path is null or empty");
            finishAndNotify();
            return;
        }

        File videoFile = new File(videoPath);
        if (!videoFile.exists()) {
            Log.e(TAG, "Video file not found: " + videoPath);
            finishAndNotify();
            return;
        }

        Log.i(TAG, "Starting video: " + videoPath + " (size: " + videoFile.length() + " bytes)");
        surfaceView.setVisibility(View.VISIBLE);

        // Setup SurfaceView holder callback — MediaPlayer needs the surface to be ready
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.i(TAG, "Surface created, starting MediaPlayer");
                surfaceReady = true;
                playVideoOnSurface(holder, videoPath);
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

        // Safety timeout: if surface never becomes ready, close after 5s
        handler.postDelayed(() -> {
            if (!surfaceReady) {
                Log.w(TAG, "Surface never became ready, closing");
                finishAndNotify();
            }
        }, 5000);
    }

    private void playVideoOnSurface(SurfaceHolder holder, String videoPath) {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDisplay(holder);
            mediaPlayer.setDataSource(videoPath);

            mediaPlayer.setOnPreparedListener(mp -> {
                Log.i(TAG, "MediaPlayer prepared, duration=" + mp.getDuration() + "ms, starting playback");
                // Scale video to fill screen while maintaining aspect ratio
                mp.start();
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.i(TAG, "Video playback completed");
                finishAndNotify();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: what=" + what + " extra=" + extra);
                finishAndNotify();
                return true;
            });

            mediaPlayer.setOnInfoListener((mp, what, extra) -> {
                Log.d(TAG, "MediaPlayer info: what=" + what + " extra=" + extra);
                return false;
            });

            // Prepare asynchronously to avoid blocking the UI thread
            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            Log.e(TAG, "Error setting up MediaPlayer", e);
            finishAndNotify();
        }
    }

    private void startSlideshow() {
        imageList = getIntent().getStringArrayListExtra(EXTRA_IMAGE_LIST);
        if (imageList == null || imageList.isEmpty()) {
            Log.w(TAG, "No images for slideshow");
            finishAndNotify();
            return;
        }

        Log.i(TAG, "Starting slideshow with " + imageList.size() + " images");
        imageView.setVisibility(View.VISIBLE);
        currentImageIndex = 0;
        showCurrentImage();

        if (imageList.size() > 1) {
            Runnable slideshowRunnable = new Runnable() {
                @Override
                public void run() {
                    currentImageIndex++;
                    if (currentImageIndex >= imageList.size()) {
                        Log.i(TAG, "Slideshow completed");
                        finishAndNotify();
                        return;
                    }
                    showCurrentImage();
                    handler.postDelayed(this, SLIDESHOW_INTERVAL_MS);
                }
            };
            handler.postDelayed(slideshowRunnable, SLIDESHOW_INTERVAL_MS);
        } else {
            // Single image — show for 8 seconds then close
            handler.postDelayed(this::finishAndNotify, SLIDESHOW_INTERVAL_MS * 2);
        }
    }

    private void showCurrentImage() {
        if (currentImageIndex >= imageList.size()) return;
        String imagePath = imageList.get(currentImageIndex);
        Log.d(TAG, "Showing image " + (currentImageIndex + 1) + "/" + imageList.size() + ": " + imagePath);

        try {
            imageView.setImageBitmap(BitmapFactory.decodeFile(imagePath));
        } catch (Exception e) {
            Log.e(TAG, "Error loading image: " + imagePath, e);
        }
    }

    private void finishAndNotify() {
        Log.i(TAG, "finishAndNotify called");
        handler.removeCallbacksAndMessages(null);

        // Release MediaPlayer
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.w(TAG, "Error releasing MediaPlayer", e);
            }
            mediaPlayer = null;
        }

        try {
            TourMediaPlayer.getInstance().onMediaActivityFinished();
        } catch (Exception e) {
            Log.w(TAG, "Error notifying media player", e);
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        handler.removeCallbacksAndMessages(null);

        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }

        try {
            unregisterReceiver(closeReceiver);
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finishAndNotify();
    }
}
