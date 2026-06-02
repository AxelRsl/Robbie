package com.robbie.platform.agent.debug;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.ainirobot.agent.AgentCore;

public class DebugTextSkillManager {
    private static final String TAG = "DebugTextSkillManager";

    private static DebugTextSkillManager instance;
    private static volatile DebugQueryListener debugQueryListener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface DebugQueryListener {
        void onDebugQuery(String text);
    }

    public static void setDebugQueryListener(DebugQueryListener listener) {
        debugQueryListener = listener;
    }

    public static synchronized DebugTextSkillManager getInstance() {
        if (instance == null) {
            instance = new DebugTextSkillManager();
        }
        return instance;
    }

    public void submitText(Context context, String text) {
        if (context == null) {
            Log.w(TAG, "Ignoring debug text because context is null");
            return;
        }
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(text.trim())) {
            Log.w(TAG, "Ignoring debug text because it is empty");
            return;
        }
        String normalizedText = text.trim();

        // Handle debug commands: __cmd:wakefree_off, __cmd:wakefree_on, __cmd:mic_unmute
        if (normalizedText.startsWith("__cmd:")) {
            String cmd = normalizedText.substring(6);
            Log.i(TAG, "Debug command: " + cmd);
            mainHandler.post(() -> {
                switch (cmd) {
                    case "wakefree_off":
                        AgentCore.INSTANCE.setEnableWakeFree(false);
                        Log.i(TAG, "Wake-free DISABLED (unconditional listening)");
                        break;
                    case "wakefree_on":
                        AgentCore.INSTANCE.setEnableWakeFree(true);
                        Log.i(TAG, "Wake-free ENABL ED");
                        break;
                    case "mic_unmute":
                        AgentCore.INSTANCE.setMicrophoneMuted(false);
                        Log.i(TAG, "Microphone UNMUTED");
                        break;
                    default:
                        Log.w(TAG, "Unknown debug command: " + cmd);
                }
            });
            return;
        }

        Log.i(TAG, "Dispatching debug query through AgentCore: " + normalizedText);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (debugQueryListener != null) {
                        debugQueryListener.onDebugQuery(normalizedText);
                    }
                    AgentCore.INSTANCE.query(normalizedText);
                } catch (Exception e) {
                    Log.e(TAG, "Error sending debug query through AgentCore", e);
                }
            }
        });
    }
}
