package com.mgallo17.conga;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Receives AlarmManager broadcasts and starts the cleaning sequence.
 */
public class ScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(LoginActivity.PREFS_NAME, Context.MODE_PRIVATE);
        int userId   = prefs.getInt(LoginActivity.KEY_USER_ID, 0);
        int deviceId = prefs.getInt(LoginActivity.KEY_DEVICE_ID, 0);
        if (userId == 0 || deviceId == 0) return;

        Intent serviceIntent = new Intent(context, CongaService.class);
        serviceIntent.putExtra(CongaService.EXTRA_USER_ID, userId);
        serviceIntent.putExtra(CongaService.EXTRA_DEVICE_ID, deviceId);
        context.startForegroundService(serviceIntent);

        // Give the service a moment to connect, then auto-start cleaning
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Intent startClean = new Intent("com.mgallo17.conga.AUTO_CLEAN");
            context.sendBroadcast(startClean);
        }, 5000);
    }
}
