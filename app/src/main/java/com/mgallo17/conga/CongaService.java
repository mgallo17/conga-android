package com.mgallo17.conga;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class CongaService extends Service implements CongaClient.Listener {

    private static final String TAG             = "CongaService";
    private static final String CHANNEL_ID      = "conga_service";
    private static final int    NOTIFICATION_ID = 1;

    private final IBinder binder = new LocalBinder();
    private CongaClient           client;
    private LocalBroadcastManager broadcaster;

    public class LocalBinder extends Binder {
        public CongaService getService() { return CongaService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        client      = new CongaClient();
        broadcaster = LocalBroadcastManager.getInstance(this);
        client.setListener(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID,
                    buildNotification("Connecting…"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Connecting…"));
        }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        if (client != null) client.disconnect();
        super.onDestroy();
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    public void connect(String email, String password, String deviceId) {
        client.connect(email, password, deviceId);
    }

    public void disconnect() { client.disconnect(); }

    public void sendCommand(int cmd) { client.sendCommand(cmd); }

    public void sendSchedule(int daysMask, int hour, int minute) {
        client.sendSchedule(daysMask, hour, minute);
    }

    public boolean isConnected() { return client != null && client.isConnected(); }

    // ---------------------------------------------------------------
    // CongaClient.Listener
    // ---------------------------------------------------------------

    @Override public void onConnected() {
        Log.d(TAG, "Connected");
        updateNotification("Connected — logging in…");
        broadcast(CongaCommands.ACTION_CONNECTED);
    }

    @Override public void onDisconnected() {
        Log.d(TAG, "Disconnected");
        updateNotification("Disconnected");
        broadcast(CongaCommands.ACTION_DISCONNECTED);
    }

    @Override public void onLoginSuccess(int userId) {
        Log.d(TAG, "Login OK");
        updateNotification("Conga connected ✓");
        broadcast(CongaCommands.ACTION_CONNECTED);
    }

    @Override public void onLoginFailed(String reason) {
        Log.w(TAG, "Login failed: " + reason);
        updateNotification("Login failed");
        broadcast(CongaCommands.ACTION_DISCONNECTED);
    }

    @Override public void onStatus(CongaMessage.StatusResponse status) {
        Intent intent = new Intent(CongaCommands.ACTION_STATUS_UPDATE);
        intent.putExtra(CongaCommands.EXTRA_STATE,      status.state);
        intent.putExtra(CongaCommands.EXTRA_BATTERY,    status.battery);
        intent.putExtra(CongaCommands.EXTRA_POS_X,      status.posX);
        intent.putExtra(CongaCommands.EXTRA_POS_Y,      status.posY);
        intent.putExtra(CongaCommands.EXTRA_CLEAN_TIME, status.cleanTime);
        intent.putExtra(CongaCommands.EXTRA_CLEAN_AREA, status.cleanArea);
        broadcaster.sendBroadcast(intent);
    }

    @Override public void onError(String message) {
        Log.e(TAG, "Error: " + message);
        updateNotification("Error: " + message);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void broadcast(String action) {
        broadcaster.sendBroadcast(new Intent(action));
    }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Conga Service", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        Intent ni = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, ni, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Conga")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification(String text) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(text));
    }
}
