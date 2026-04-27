package com.mgallo17.conga;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives AlarmManager / BOOT_COMPLETED broadcasts and starts cleaning.
 */
public class ScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, CongaService.class);
        context.startForegroundService(serviceIntent);
    }
}
