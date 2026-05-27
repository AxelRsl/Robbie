package com.robbie.platform.speech;

import android.util.Log;

import com.ainirobot.coreservice.client.speech.SkillApi;
import com.ainirobot.coreservice.client.speech.SkillServerCheckListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SpeechApi {
    private static final String TAG = "SpeechApi";

    private static volatile SpeechApi instance;

    private SkillApi skillApi;
    private SkillServerCheckListener outServerCheckCallback;
    private final Map<String, String> pendingAsrParams = new ConcurrentHashMap<>();
    private volatile String pendingAsrExtendProperty;
    private volatile String foregroundAppId;

    public static SpeechApi getInstance() {
        if (instance == null) {
            synchronized (SpeechApi.class) {
                if (instance == null) {
                    instance = new SpeechApi();
                }
            }
        }
        return instance;
    }

    public synchronized void setSpeechApi(SkillApi skillApi) {
        this.skillApi = skillApi;
        registerCallbackProxy();
        init();
    }

    private synchronized void registerCallbackProxy() {
        if (checkNotNull()) {
            skillApi.registerCallBack(SpeechApiCallback.getInstance());
            if (outServerCheckCallback != null) {
                skillApi.registerServerCheck(outServerCheckCallback);
            }
        }
    }

    public synchronized void setSkillServerCheckCallback(SkillServerCheckListener listener) {
        outServerCheckCallback = listener;
        registerCallbackProxy();
    }

    public synchronized void unRegisterServerCheck() {
        outServerCheckCallback = null;
    }

    public synchronized void init() {
        if (!checkNotNull()) {
            return;
        }
        if (!SpeechManager.getInstance().isAlreadyOpenAsr()) {
            skillApi.setRecognizable(false);
            SpeechManager.getInstance().resetAngleCenterRange();
        }
        applyPendingSpeechConfig();
        if (foregroundAppId != null && !foregroundAppId.isEmpty()) {
            startApp(foregroundAppId);
            moveToForeground(foregroundAppId);
        }
    }

    private synchronized void applyPendingSpeechConfig() {
        if (!checkNotNull()) {
            return;
        }
        for (Map.Entry<String, String> entry : pendingAsrParams.entrySet()) {
            skillApi.setASRParams(entry.getKey(), entry.getValue());
        }
        if (pendingAsrExtendProperty != null && !pendingAsrExtendProperty.isEmpty()) {
            skillApi.setAsrExtendProperty(pendingAsrExtendProperty);
        }
    }

    public synchronized boolean checkConnectSpeechService() {
        return checkNotNull();
    }

    private synchronized boolean checkNotNull() {
        boolean connected = skillApi != null;
        Log.i(TAG, "check not null result: " + connected);
        return connected;
    }

    public synchronized void setRecognizeMode(boolean enabled) {
        if (checkNotNull()) {
            skillApi.setRecognizeMode(enabled);
        }
    }

    public synchronized void setRecognizeModeForce(boolean enabled) {
        if (checkNotNull()) {
            skillApi.setRecognizeModeForce(enabled);
        }
    }

    public synchronized void setRecognizeModeNew(boolean enabled, boolean closeStreamData) {
        if (checkNotNull()) {
            skillApi.setRecognizeModeNew(enabled, closeStreamData);
        }
    }

    public synchronized void setASREnabled(boolean enabled) {
        if (checkNotNull()) {
            skillApi.setASREnabled(enabled);
        }
    }

    public synchronized void setRecognizable(boolean enabled) {
        if (checkNotNull()) {
            skillApi.setRecognizable(enabled);
        }
    }

    public synchronized void queryByText(String text) {
        if (checkNotNull()) {
            skillApi.queryByText(text);
        }
    }

    public synchronized void getActiveAsk(String key, String value) {
        if (checkNotNull()) {
            skillApi.getActiveAsk(key, value);
        }
    }

    public synchronized void queryByTextWithThinking(String text, boolean showThinking) {
        if (checkNotNull()) {
            skillApi.queryByTextWithThinking(text, showThinking);
        }
    }

    public synchronized void setAngleCenterRange(float center, float range) {
        if (checkNotNull()) {
            skillApi.setAngleCenterRange(center, range);
        }
    }

    public synchronized void setMultipleModeEnable(boolean enabled) {
        if (checkNotNull()) {
            skillApi.setMultipleModeEnable(enabled);
        }
    }

    public synchronized void setASRParams(String key, String value) {
        if (key != null && value != null) {
            pendingAsrParams.put(key, value);
        }
        if (checkNotNull()) {
            skillApi.setASRParams(key, value);
        }
    }

    public synchronized boolean setAsrExtendProperty(String property) {
        pendingAsrExtendProperty = property;
        if (checkNotNull()) {
            return skillApi.setAsrExtendProperty(property);
        }
        return false;
    }

    public synchronized void startApp(String appId) {
        if (checkNotNull()) {
            skillApi.onCreate(appId);
        }
    }

    public synchronized void moveToForeground(String appId) {
        foregroundAppId = appId;
        if (checkNotNull()) {
            skillApi.onForeground(appId);
        }
    }

    public synchronized void moveToBack(String appId) {
        if (checkNotNull()) {
            skillApi.onBackground(appId);
        }
        if (foregroundAppId != null && foregroundAppId.equals(appId)) {
            foregroundAppId = null;
        }
    }

    public synchronized void destroyApp(String appId) {
        if (checkNotNull()) {
            skillApi.onDestroy(appId);
        }
        if (foregroundAppId != null && foregroundAppId.equals(appId)) {
            foregroundAppId = null;
        }
    }

    public synchronized void setAppPath(String appId, String path) {
        if (checkNotNull()) {
            skillApi.setPath(appId, path);
        }
    }

    public synchronized void sendAgentMessage(String type, int code, String message) {
        if (checkNotNull()) {
            skillApi.sendAgentMessage(type, code, message);
        }
    }

    public synchronized void setAppVersion(String appId, String version) {
        if (checkNotNull()) {
            skillApi.setVersion(appId, version);
        }
    }

    public synchronized void setSyncCustomNlpData(Map map) {
        if (checkNotNull()) {
            skillApi.setSyncCustomNlpData(map);
        }
    }

    public synchronized String setAsyncCustomNlpData(String key, String value) {
        if (checkNotNull()) {
            return skillApi.setAsyncCustomNlpData(key, value);
        }
        return "";
    }

    public synchronized void resetNlpState() {
        if (checkNotNull()) {
            skillApi.resetNlpState();
        }
    }

    public synchronized void setServerApp(List<String> apps) {
        if (checkNotNull()) {
            skillApi.setServerApp(apps);
        }
    }

    public synchronized void setNLPDebug(boolean enabled) {
        if (checkNotNull()) {
            skillApi.setDebug(enabled);
        }
    }

    public synchronized void closeStreamDataReceived(String streamId) {
        if (checkNotNull()) {
            skillApi.closeStreamDataReceived(streamId);
        }
    }

    public synchronized void onAgentActionFinish(String actionName, int code, String payload) {
        if (checkNotNull()) {
            skillApi.onAgentActionFinish(actionName, code, payload);
        }
    }

    public synchronized void onAgentActionState(String actionName, int code, String payload) {
        if (checkNotNull()) {
            skillApi.onAgentActionState(actionName, code, payload);
        }
    }

    public synchronized boolean isRecognizable() {
        return checkNotNull() && skillApi.isRecognizable();
    }

    public synchronized boolean isRecognizeContinue() {
        return checkNotNull() && skillApi.isRecognizeContinue();
    }

    public synchronized SkillApi getRawSkillApi() {
        return skillApi;
    }

    public synchronized String getForegroundApp() {
        return foregroundAppId;
    }
}
