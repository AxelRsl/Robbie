package com.robbie.platform.speech;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.ainirobot.coreservice.client.ApiListener;
import com.ainirobot.coreservice.client.speech.SkillApi;
import com.robbie.RobotApp;

import java.util.Timer;
import java.util.TimerTask;

public class SkillService extends Service {
    private static final long RECONNECT_INTERVAL = 5000L;
    private static final String TAG = "SkillService";

    private final SkillApi skillApi = new SkillApi();
    private Timer reconnectTimer;
    private boolean listenersRegistered;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        connectSpeechServer();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        cancelReconnectTimer();
        super.onDestroy();
    }

    private synchronized void connectSpeechServer() {
        if (!listenersRegistered) {
            skillApi.addApiEventListener(new ApiListener() {
                @Override
                public void handleApiDisabled() {
                }

                @Override
                public void handleApiConnected() {
                    Log.i(TAG, "handleApiConnected");
                    cancelReconnectTimer();
                    SpeechApi.getInstance().setSpeechApi(skillApi);
                }

                @Override
                public void handleApiDisconnected() {
                    Log.e(TAG, "handleApiDisconnected");
                    reconnect();
                }
            });
            listenersRegistered = true;
        }
        skillApi.connectApi(getApplicationContext());
    }

    private synchronized void reconnect() {
        cancelReconnectTimer();
        reconnectTimer = new Timer();
        reconnectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.e(TAG, "Reconnect to speech");
                if (skillApi.isApiConnectedService()) {
                    Log.e(TAG, "Already connected to speech");
                    cancelReconnectTimer();
                    return;
                }
                try {
                    skillApi.connectApi(RobotApp.getInstance().getApplicationContext());
                } catch (Exception e) {
                    Log.e(TAG, "Reconnect to speech failed", e);
                }
            }
        }, 0L, RECONNECT_INTERVAL);
    }

    private synchronized void cancelReconnectTimer() {
        if (reconnectTimer != null) {
            reconnectTimer.cancel();
            reconnectTimer = null;
        }
    }
}
