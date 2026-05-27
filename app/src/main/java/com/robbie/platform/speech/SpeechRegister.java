package com.robbie.platform.speech;

import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

public class SpeechRegister {
    private static final String TAG = "SpeechRegister";

    public interface SpeechRecognitionListener {
        void onSpeechRecognitionStart();

        void onSpeechRecognitionStop();
    }

    public interface SpeechStatusListener {
        void onAsrResult(String text);

        void onQueryEnded(int code);

        void onSpeechParResult(String text);
    }

    public interface SpeechStreamListener {
        void onSpeechStreamData(String data);
    }

    public interface TTSSpeechStatusListener {
        void onSpeechCompleteTTS(boolean success);

        void onStartTTS();

        void onStopTTS(boolean success);
    }

    public interface VolumeChangeListener {
        void onVolumeChange(int volume);
    }

    private static volatile SpeechRegister instance;

    private final CopyOnWriteArrayList<SpeechRecognitionListener> recognitionListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SpeechStatusListener> statusListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SpeechStreamListener> streamListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<TTSSpeechStatusListener> ttsStatusListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<VolumeChangeListener> volumeChangeListeners = new CopyOnWriteArrayList<>();

    public static SpeechRegister getInstance() {
        if (instance == null) {
            synchronized (SpeechRegister.class) {
                if (instance == null) {
                    instance = new SpeechRegister();
                }
            }
        }
        return instance;
    }

    public void handleSpeechParResult(String text) {
        for (SpeechStatusListener listener : statusListeners) {
            listener.onSpeechParResult(text);
        }
    }

    public void handleRecognitionStart() {
        for (SpeechRecognitionListener listener : recognitionListeners) {
            listener.onSpeechRecognitionStart();
        }
    }

    public void handleRecognitionStop() {
        for (SpeechRecognitionListener listener : recognitionListeners) {
            listener.onSpeechRecognitionStop();
        }
    }

    public void handleVolumeChange(int volume) {
        for (VolumeChangeListener listener : volumeChangeListeners) {
            listener.onVolumeChange(volume);
        }
    }

    public void handleQueryEnded(int code) {
        for (SpeechStatusListener listener : statusListeners) {
            listener.onQueryEnded(code);
        }
    }

    public void handleAsrResult(String text) {
        for (SpeechStatusListener listener : statusListeners) {
            listener.onAsrResult(text);
        }
    }

    public void handleTtsStart() {
        for (TTSSpeechStatusListener listener : ttsStatusListeners) {
            listener.onStartTTS();
        }
    }

    public void handleTtsStop(boolean success) {
        for (TTSSpeechStatusListener listener : ttsStatusListeners) {
            listener.onStopTTS(success);
        }
    }

    public void handleTtsComplete(boolean success) {
        for (TTSSpeechStatusListener listener : ttsStatusListeners) {
            listener.onSpeechCompleteTTS(success);
        }
    }

    public void handleSpeechStreamData(String data) {
        for (SpeechStreamListener listener : streamListeners) {
            listener.onSpeechStreamData(data);
        }
    }

    public String onGetMultipleModeInfos(int index) {
        Log.d(TAG, "onGetMultipleModeInfos index=" + index);
        return null;
    }

    public void registerVolumeChangeListener(VolumeChangeListener listener) {
        if (listener == null || volumeChangeListeners.contains(listener)) {
            return;
        }
        volumeChangeListeners.add(listener);
    }

    public void unRegisterVolumeChangeListener(VolumeChangeListener listener) {
        if (listener == null) {
            return;
        }
        volumeChangeListeners.remove(listener);
    }

    public void registerRecognitionListener(SpeechRecognitionListener listener) {
        if (listener == null || recognitionListeners.contains(listener)) {
            return;
        }
        recognitionListeners.add(listener);
    }

    public void unRegisterRecognitionListener(SpeechRecognitionListener listener) {
        if (listener == null) {
            return;
        }
        recognitionListeners.remove(listener);
    }

    public void registerStatusListener(SpeechStatusListener listener) {
        if (listener == null || statusListeners.contains(listener)) {
            return;
        }
        statusListeners.add(listener);
    }

    public void unRegisterStatusListener(SpeechStatusListener listener) {
        if (listener == null) {
            return;
        }
        statusListeners.remove(listener);
    }

    public void registerTTSStatusListener(TTSSpeechStatusListener listener) {
        if (listener == null || ttsStatusListeners.contains(listener)) {
            return;
        }
        ttsStatusListeners.add(listener);
    }

    public void unRegisterTTSStatusListener(TTSSpeechStatusListener listener) {
        if (listener == null) {
            return;
        }
        ttsStatusListeners.remove(listener);
    }

    public void registerStreamListener(SpeechStreamListener listener) {
        if (listener == null || streamListeners.contains(listener)) {
            return;
        }
        streamListeners.add(listener);
    }

    public void unRegisterStreamListener(SpeechStreamListener listener) {
        if (listener == null) {
            return;
        }
        streamListeners.remove(listener);
    }
}
