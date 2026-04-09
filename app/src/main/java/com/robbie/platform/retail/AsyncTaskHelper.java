package com.robbie.platform.retail;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncTaskHelper {
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void execute(Runnable task) {
        executor.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void executeDelayed(Runnable task, long delayMillis) {
        mainHandler.postDelayed(() -> execute(task), delayMillis);
    }

    public static void runOnMain(Runnable task) {
        mainHandler.post(task);
    }
}
