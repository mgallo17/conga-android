package com.mgallo17.conga;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainActivity extends AppCompatActivity {

    private CongaService    service;
    private boolean         bound = false;
    private RobotPathView   pathView;
    private TextView        tvStatus, tvBattery, tvCleanTime, tvCleanArea;
    private Button          btnStart, btnStop, btnHome;
    private Spinner         spinnerMode;

    private final String[] MODES = {"Auto", "Spiral", "Edge", "Spot"};
    private final int[]    MODE_CMDS = {
            CongaCommands.CMD_AUTO,
            CongaCommands.CMD_SPIRAL,
            CongaCommands.CMD_EDGE,
            CongaCommands.CMD_SPOT
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            CongaService.LocalBinder lb = (CongaService.LocalBinder) binder;
            service = lb.getService();
            bound   = true;

            // Connect with credentials passed from LoginActivity
            Intent src = getIntent();
            String email  = src.getStringExtra(CongaCommands.PREF_EMAIL);
            String pwd    = src.getStringExtra(CongaCommands.PREF_PASSWORD);
            String devId  = getSharedPreferences(CongaCommands.PREFS_NAME, MODE_PRIVATE)
                    .getString(CongaCommands.PREF_DEVICE_ID, "0");
            service.connect(email, pwd, devId);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case CongaCommands.ACTION_CONNECTED:
                    tvStatus.setText("● Connected");
                    tvStatus.setTextColor(0xFF4CAF50);
                    break;
                case CongaCommands.ACTION_DISCONNECTED:
                    tvStatus.setText("● Disconnected");
                    tvStatus.setTextColor(0xFFE53935);
                    break;
                case CongaCommands.ACTION_STATUS_UPDATE:
                    handleStatus(intent);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus    = findViewById(R.id.tvStatus);
        tvBattery   = findViewById(R.id.tvBattery);
        tvCleanTime = findViewById(R.id.tvCleanTime);
        tvCleanArea = findViewById(R.id.tvCleanArea);
        pathView    = findViewById(R.id.robotPathView);
        btnStart    = findViewById(R.id.btnStart);
        btnStop     = findViewById(R.id.btnStop);
        btnHome     = findViewById(R.id.btnHome);
        spinnerMode = findViewById(R.id.spinnerMode);

        spinnerMode.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, MODES));

        btnStart.setOnClickListener(v -> {
            if (!bound) return;
            int modeCmd = MODE_CMDS[spinnerMode.getSelectedItemPosition()];
            service.sendCommand(modeCmd);
            pathView.startTracking();
        });

        btnStop.setOnClickListener(v -> {
            if (bound) service.sendCommand(CongaCommands.CMD_STOP);
        });

        btnHome.setOnClickListener(v -> {
            if (bound) service.sendCommand(CongaCommands.CMD_HOME);
        });

        findViewById(R.id.btnSchedule).setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleActivity.class)));

        // Start & bind service
        Intent svcIntent = new Intent(this, CongaService.class);
        startForegroundService(svcIntent);
        bindService(svcIntent, connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(CongaCommands.ACTION_CONNECTED);
        filter.addAction(CongaCommands.ACTION_DISCONNECTED);
        filter.addAction(CongaCommands.ACTION_STATUS_UPDATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (bound) { unbindService(connection); bound = false; }
        super.onDestroy();
    }

    private void handleStatus(Intent intent) {
        int   state     = intent.getIntExtra(CongaCommands.EXTRA_STATE, 0);
        int   battery   = intent.getIntExtra(CongaCommands.EXTRA_BATTERY, 0);
        float posX      = intent.getFloatExtra(CongaCommands.EXTRA_POS_X, 0f);
        float posY      = intent.getFloatExtra(CongaCommands.EXTRA_POS_Y, 0f);
        int   cleanTime = intent.getIntExtra(CongaCommands.EXTRA_CLEAN_TIME, 0);
        int   cleanArea = intent.getIntExtra(CongaCommands.EXTRA_CLEAN_AREA, 0);

        String stateStr;
        switch (state) {
            case CongaCommands.STATE_CLEANING:  stateStr = "🧹 Cleaning";  break;
            case CongaCommands.STATE_RETURNING: stateStr = "🏠 Returning"; break;
            case CongaCommands.STATE_CHARGING:  stateStr = "🔋 Charging";  break;
            case CongaCommands.STATE_ERROR:     stateStr = "⚠️ Error";      break;
            default:                            stateStr = "💤 Idle";       break;
        }

        tvStatus.setText(stateStr);
        tvBattery.setText("Battery: " + battery + "%");
        tvCleanTime.setText(formatTime(cleanTime));
        tvCleanArea.setText(cleanArea + " cm²");

        pathView.addPoint(posX, posY);
    }

    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
