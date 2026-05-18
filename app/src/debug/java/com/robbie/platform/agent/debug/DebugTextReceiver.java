package com.robbie.platform.agent.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

public class DebugTextReceiver extends BroadcastReceiver {
    public static final String ACTION_DEBUG_TEXT = "DEBUG_TEXT";
    private static final String TAG = "DebugTextReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Log.w(TAG, "Ignoring broadcast because intent is null");
            return;
        }

        String action = intent.getAction();
        if (!ACTION_DEBUG_TEXT.equals(action)) {
            Log.w(TAG, "Ignoring unexpected action: " + action);
            return;
        }

        String text = intent.getStringExtra("text");
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(text.trim())) {
            Log.w(TAG, "Ignoring DEBUG_TEXT broadcast because text is empty");
            return;
        }

        String normalizedText = text.trim();
        Log.i(TAG, "Received DEBUG_TEXT broadcast: " + normalizedText);
        DebugTextSkillManager.getInstance().submitText(context, normalizedText);
    }
}
