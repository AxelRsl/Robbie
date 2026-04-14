package com.robbie.data.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class RobbieApiService extends Service {
    
    private static final String TAG = "RobbieApiService";
    private static final String CHANNEL_ID = "robbie_api_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    private RobbieApiServer server;
    private static boolean isRunning = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());
        startServer();
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void startServer() {
        if (server != null && server.isAlive()) {
            Log.w(TAG, "Server already running");
            return;
        }
        
        try {
            server = new RobbieApiServer(this);
            server.start();
            isRunning = true;
            
            String ip = getLocalIpAddress();
            String url = "http://" + (ip != null ? ip : "localhost") + ":" + RobbieApiServer.DEFAULT_PORT;
            Log.i(TAG, "API Server started at " + url);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start server", e);
            isRunning = false;
        }
    }
    
    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
            isRunning = false;
            Log.i(TAG, "API Server stopped");
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Robbie API Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Servidor API local del robot");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Robbie API")
            .setContentText("Servidor API ejecutándose")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IP address", e);
        }
        return null;
    }
    
    // Static methods for checking server status
    public static boolean isServerRunning() {
        return isRunning;
    }
    
    public static String getServerUrl(Context context) {
        if (!isRunning) return null;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        return "http://" + address.getHostAddress() + ":" + RobbieApiServer.DEFAULT_PORT;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting server URL", e);
        }
        return "http://localhost:" + RobbieApiServer.DEFAULT_PORT;
    }
}
