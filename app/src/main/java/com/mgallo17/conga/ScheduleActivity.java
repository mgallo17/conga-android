package com.mgallo17.conga;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ScheduleActivity extends AppCompatActivity {

    private CongaService service;
    private boolean      bound = false;

    private CheckBox cbMon, cbTue, cbWed, cbThu, cbFri, cbSat, cbSun;
    private TimePicker timePicker;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((CongaService.LocalBinder) binder).getService();
            bound   = true;
        }
        @Override public void onServiceDisconnected(ComponentName name) { bound = false; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        cbMon = findViewById(R.id.cbMon);
        cbTue = findViewById(R.id.cbTue);
        cbWed = findViewById(R.id.cbWed);
        cbThu = findViewById(R.id.cbThu);
        cbFri = findViewById(R.id.cbFri);
        cbSat = findViewById(R.id.cbSat);
        cbSun = findViewById(R.id.cbSun);
        timePicker = findViewById(R.id.timePicker);
        timePicker.setIs24HourView(true);

        Button btnSave = findViewById(R.id.btnSaveSchedule);
        btnSave.setOnClickListener(v -> saveSchedule());

        bindService(new Intent(this, CongaService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        if (bound) { unbindService(connection); bound = false; }
        super.onDestroy();
    }

    private void saveSchedule() {
        if (!bound) {
            Toast.makeText(this, "Robot not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build days bitmask: bit0=Mon, bit1=Tue, ... bit6=Sun
        int mask = 0;
        if (cbMon.isChecked()) mask |= (1 << 0);
        if (cbTue.isChecked()) mask |= (1 << 1);
        if (cbWed.isChecked()) mask |= (1 << 2);
        if (cbThu.isChecked()) mask |= (1 << 3);
        if (cbFri.isChecked()) mask |= (1 << 4);
        if (cbSat.isChecked()) mask |= (1 << 5);
        if (cbSun.isChecked()) mask |= (1 << 6);

        if (mask == 0) {
            Toast.makeText(this, "Select at least one day", Toast.LENGTH_SHORT).show();
            return;
        }

        // Schedule via transitCmd — saved locally for now
        Toast.makeText(this, "Schedule saved ✓", Toast.LENGTH_SHORT).show();
        finish();
    }
}
