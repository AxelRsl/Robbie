package com.robbie.platform.speech;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.List;
import java.util.Map;

public class SpeechManager {
    private static final String TAG = "SpeechManager";
    private static final String PREFS_NAME = "developer_configuration_item";
    private static final String ANGLE_CENTER_ITEM = "angle_center_item";
    private static final String ANGLE_RANGE_ITEM = "angle_range_item";
    private static final String BEAMFORMING_ITEM = "beamfoming_item";

    private static volatile SpeechManager instance;

    private Context applicationContext;
    private volatile boolean alreadyOpenAsr;
    private volatile boolean inRemoteControl;

    public static SpeechManager getInstance() {
        if (instance == null) {
            synchronized (SpeechManager.class) {
                if (instance == null) {
                    instance = new SpeechManager();
                }
            }
        }
        return instance;
    }

    public void initialize(Context context) {
        if (context != null) {
            applicationContext = context.getApplicationContext();
        }
    }

    public boolean isAlreadyOpenAsr() {
        return alreadyOpenAsr;
    }

    public void setAlreadyOpenAsr(boolean alreadyOpenAsr) {
        this.alreadyOpenAsr = alreadyOpenAsr;
    }

    public void disableSkill() {
        Log.d(TAG, "in remotecontrol, disable skill");
        inRemoteControl = true;
        setRecognizable(false);
    }

    public void enableSkill() {
        Log.d(TAG, "not in remotecontrol, enable skill");
        inRemoteControl = false;
        setRecognizable(true);
    }

    public void openSpeechAsrRecognize() {
        if (inRemoteControl) {
            Log.d(TAG, "in remotecontrol, openSpeechAsrRecognize is not working");
            return;
        }
        setRecognizable(true);
        setAlreadyOpenAsr(true);
    }

    public void closeSpeechAsrRecognize() {
        setRecognizable(false);
        setAlreadyOpenAsr(false);
    }

    public void openRobotRecognize() {
        Log.d(TAG, "set recognize mode true");
        setRecognizeMode(true);
    }

    public void closeRobotRecognize() {
        Log.d(TAG, "set recognize mode false");
        setRecognizeMode(false);
    }

    public void switchRecognizeMode() {
        Log.i(TAG, "robot speech recognize mode switch false");
        setRecognizeMode(false);
    }

    public void resetAngleCenterRange() {
        float defaultAngleCenter = getDefautAngleCenter();
        float defaultAngleRange = getDefautAngleRange();
        setAngleCenterRange(defaultAngleCenter, defaultAngleRange);
        Log.d(TAG, "defautAngleCenter defautAngleRange :" + defaultAngleCenter + " " + defaultAngleRange);
    }

    public float getDefautAngleCenter() {
        float value = getPrefs().getFloat(ANGLE_CENTER_ITEM, 0.0f);
        Log.d(TAG, "getDefautAngleCenter from sp angleCenter : " + value);
        return value;
    }

    public float getDefautAngleRange() {
        float value = getPrefs().getFloat(ANGLE_RANGE_ITEM, 120.0f);
        Log.d(TAG, "getDefautAngleRange from sp angle_range : " + value);
        return value;
    }

    private boolean getSpeechBeamFormingFlag() {
        boolean value = getPrefs().getBoolean(BEAMFORMING_ITEM, true);
        Log.d(TAG, "getSpeechBeamFormingFlag beamFormingSwitch : " + value);
        return value;
    }

    private SharedPreferences getPrefs() {
        if (applicationContext == null) {
            throw new IllegalStateException("SpeechManager not initialized");
        }
        return applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setAngleCenterRange(float center, float range) {
        if (getSpeechBeamFormingFlag() && center >= 0.0f && range >= 0.0f) {
            SpeechApi.getInstance().setAngleCenterRange(center, range);
        }
    }

    public void setRecognizeMode(boolean enabled) {
        SpeechApi.getInstance().setRecognizeMode(enabled);
    }

    public void setRecognizeModeForce(boolean enabled) {
        SpeechApi.getInstance().setRecognizeModeForce(enabled);
    }

    public void setRecognizeModeNew(boolean enabled, boolean closeStreamData) {
        SpeechApi.getInstance().setRecognizeModeNew(enabled, closeStreamData);
    }

    public void setRecognizable(boolean enabled) {
        SpeechApi.getInstance().setRecognizable(enabled);
    }

    public void setASREnabled(boolean enabled) {
        SpeechApi.getInstance().setASREnabled(enabled);
    }

    public void setASRParams(String key, String value) {
        SpeechApi.getInstance().setASRParams(key, value);
    }

    public boolean setAsrExtendProperty(String property) {
        return SpeechApi.getInstance().setAsrExtendProperty(property);
    }

    public void queryByText(String text) {
        SpeechApi.getInstance().queryByText(text);
    }

    public void getActiveAsk(String key, String value) {
        SpeechApi.getInstance().getActiveAsk(key, value);
    }

    public void queryByTextWithThinking(String text, boolean showThinking) {
        SpeechApi.getInstance().queryByTextWithThinking(text, showThinking);
    }

    public void setMultipleModeEnable(boolean enabled) {
        SpeechApi.getInstance().setMultipleModeEnable(enabled);
    }

    public void startApp(String appId) {
        SpeechApi.getInstance().startApp(appId);
    }

    public void moveToForeground(String appId) {
        SpeechApi.getInstance().moveToForeground(appId);
    }

    public void moveToBack(String appId) {
        SpeechApi.getInstance().moveToBack(appId);
    }

    public void destroyApp(String appId) {
        SpeechApi.getInstance().destroyApp(appId);
    }

    public void setAppPath(String appId, String path) {
        SpeechApi.getInstance().setAppPath(appId, path);
    }

    public void sendAgentMessage(String type, int code, String message) {
        SpeechApi.getInstance().sendAgentMessage(type, code, message);
    }

    public void setAppVersion(String appId, String version) {
        SpeechApi.getInstance().setAppVersion(appId, version);
    }

    public void setSyncCustomNlpData(Map map) {
        SpeechApi.getInstance().setSyncCustomNlpData(map);
    }

    public String setAsyncCustomNlpData(String key, String value) {
        return SpeechApi.getInstance().setAsyncCustomNlpData(key, value);
    }

    public void resetNlpState() {
        SpeechApi.getInstance().resetNlpState();
    }

    public void setServerApp(List<String> apps) {
        SpeechApi.getInstance().setServerApp(apps);
    }

    public void setNLPDebug(boolean enabled) {
        SpeechApi.getInstance().setNLPDebug(enabled);
    }

    public void closeStreamDataReceived(String streamId) {
        SpeechApi.getInstance().closeStreamDataReceived(streamId);
    }

    public void onAgentActionFinish(String actionName, int code, String payload) {
        SpeechApi.getInstance().onAgentActionFinish(actionName, code, payload);
    }

    public void onAgentActionState(String actionName, int code, String payload) {
        SpeechApi.getInstance().onAgentActionState(actionName, code, payload);
    }

    public boolean isRecognizable() {
        return SpeechApi.getInstance().isRecognizable();
    }

    public boolean isRecognizeContinue() {
        return SpeechApi.getInstance().isRecognizeContinue();
    }

    public String getForegroundApp() {
        return SpeechApi.getInstance().getForegroundApp();
    }
}
