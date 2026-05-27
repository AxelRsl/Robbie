package com.robbie.platform.speech;

import android.os.RemoteException;
import android.util.Log;

import com.ainirobot.coreservice.client.speech.SkillCallback;

public class SpeechApiCallback extends SkillCallback {
    private static final String TAG = "SpeechApiCallback";

    private static volatile SpeechApiCallback instance;

    public static SpeechApiCallback getInstance() {
        if (instance == null) {
            synchronized (SpeechApiCallback.class) {
                if (instance == null) {
                    instance = new SpeechApiCallback();
                }
            }
        }
        return instance;
    }

    @Override
    public void onSpeechParResult(String text) throws RemoteException {
        Log.v(TAG, "onSpeechParResult: " + text);
        SpeechRegister.getInstance().handleSpeechParResult(text);
    }

    @Override
    public void onStart() throws RemoteException {
        Log.d(TAG, "onSpeechRecognitionStart");
        SpeechRegister.getInstance().handleRecognitionStart();
    }

    @Override
    public void onStop() throws RemoteException {
        Log.d(TAG, "onSpeechRecognitionStop");
        SpeechRegister.getInstance().handleRecognitionStop();
    }

    @Override
    public void onVolumeChange(int volume) throws RemoteException {
        SpeechRegister.getInstance().handleVolumeChange(volume);
    }

    @Override
    public void onQueryEnded(int code) throws RemoteException {
        Log.d(TAG, "onQueryEnded: " + code);
        SpeechRegister.getInstance().handleQueryEnded(code);
    }

    @Override
    public void onQueryAsrResult(String text) throws RemoteException {
        super.onQueryAsrResult(text);
        Log.d(TAG, "onQueryAsrResult: " + text);
        SpeechRegister.getInstance().handleAsrResult(text);
    }

    @Override
    public void onSpeechStreamData(String data) throws RemoteException {
        SpeechRegister.getInstance().handleSpeechStreamData(data);
    }

    @Override
    public String onGetMultipleModeInfos(int index) throws RemoteException {
        return SpeechRegister.getInstance().onGetMultipleModeInfos(index);
    }
}
