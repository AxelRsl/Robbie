package com.robbie.platform.storage;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;

public final class SharedStorageAccess {

    public static final int REQUEST_CODE = 4107;

    private SharedStorageAccess() {
    }

    public static boolean hasAccess(Context context) {
        if (context == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void ensureAccess(Activity activity) {
        if (activity == null || hasAccess(activity)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            try {
                activity.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
            return;
        }
        ActivityCompat.requestPermissions(
                activity,
                new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                REQUEST_CODE
        );
    }

    public static String describeDirectoryIssue(Context context, File directory) {
        if (directory == null) {
            return "Shared storage path is null";
        }
        if (!hasAccess(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return "Shared storage access is not granted. Grant All files access to Robbie.";
            }
            return "Shared storage permission is not granted. Grant storage permission to Robbie.";
        }
        if (directory.exists() && directory.isDirectory() && directory.listFiles() == null) {
            return "Shared storage directory could not be enumerated: " + directory.getAbsolutePath();
        }
        return null;
    }
}
