package com.robbie.platform.react.modules;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

public class RNFSModule extends ReactContextBaseJavaModule {

    private static final String MODULE_NAME = "RNFSManager";

    public RNFSModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Nullable
    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("RNFSFileTypeRegular", 0);
        constants.put("RNFSFileTypeDirectory", 1);
        constants.put("MainBundlePath", getReactApplicationContext().getApplicationInfo().dataDir);
        constants.put("CachesDirectoryPath", getReactApplicationContext().getCacheDir().getAbsolutePath());
        constants.put("DocumentDirectoryPath", getReactApplicationContext().getFilesDir().getAbsolutePath());
        constants.put("ExternalDirectoryPath", getReactApplicationContext().getExternalFilesDir(null) != null ? 
            getReactApplicationContext().getExternalFilesDir(null).getAbsolutePath() : "");
        constants.put("ExternalStorageDirectoryPath", 
            android.os.Environment.getExternalStorageDirectory().getAbsolutePath());
        constants.put("TemporaryDirectoryPath", getReactApplicationContext().getCacheDir().getAbsolutePath());
        constants.put("LibraryDirectoryPath", getReactApplicationContext().getFilesDir().getAbsolutePath());
        constants.put("PicturesDirectoryPath", 
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).getAbsolutePath());
        constants.put("DownloadDirectoryPath", 
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
        return constants;
    }

    @ReactMethod
    public void readFile(String filepath, Promise promise) {
        try {
            java.io.File file = new java.io.File(filepath);
            if (file.exists()) {
                byte[] bytes = new byte[(int) file.length()];
                java.io.FileInputStream fis = new java.io.FileInputStream(file);
                fis.read(bytes);
                fis.close();
                promise.resolve(new String(bytes));
            } else {
                promise.reject("ENOENT", "File does not exist");
            }
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void writeFile(String filepath, String content, Promise promise) {
        try {
            java.io.File file = new java.io.File(filepath);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            fos.write(content.getBytes());
            fos.close();
            promise.resolve(null);
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void exists(String filepath, Promise promise) {
        try {
            java.io.File file = new java.io.File(filepath);
            promise.resolve(file.exists());
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void mkdir(String filepath, Promise promise) {
        try {
            java.io.File file = new java.io.File(filepath);
            boolean success = file.mkdirs();
            promise.resolve(success);
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void unlink(String filepath, Promise promise) {
        try {
            java.io.File file = new java.io.File(filepath);
            boolean success = file.delete();
            promise.resolve(success);
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }
}
