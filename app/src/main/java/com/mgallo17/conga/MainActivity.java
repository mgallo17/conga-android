package com.mgallo17.conga;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements CongaClient.Listener {

    private TextView tvStatus, tvBattery, tvState, tvMode, tvVersion, tvError;
    private Button   btnStart, btnHome, btnStatus;

    private CongaClient client;
    private String token, userId, robotId, authCode, deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus  = findViewById(R.id.tvConnectionStatus);
        tvBattery = findViewById(R.id.tvBattery);
        tvState   = findViewById(R.id.tvRobotState);
        tvMode    = findViewById(R.id.tvMode);
        tvVersion = findViewById(R.id.tvVersion);
        tvError   = findViewById(R.id.tvError);
        btnStart  = findViewById(R.id.btnStart);
        btnHome   = findViewById(R.id.btnHome);
        btnStatus = findViewById(R.id.btnStatus);

        // Get credentials from intent or prefs
        token    = getIntent().getStringExtra(CongaCommands.PREF_SESSION_ID);
        userId   = getIntent().getStringExtra(CongaCommands.PREF_USER_ID);
        robotId  = getIntent().getStringExtra("robot_id");
        authCode = getIntent().getStringExtra("auth_code");
        deviceId = getIntent().getStringExtra("device_id");

        // Fallback to prefs
        if (token == null || token.isEmpty()) {
            SharedPreferences p = getSharedPreferences(CongaCommands.PREFS_NAME, MODE_PRIVATE);
            token    = p.getString(CongaCommands.PREF_SESSION_ID, "");
            userId   = p.getString(CongaCommands.PREF_USER_ID, "");
            robotId  = p.getString("robot_id", "");
            authCode = p.getString("auth_code", "");
            deviceId = p.getString("device_id", "");
        }
        // Fallback deviceId to Android ID if not set
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        }

        btnStart.setOnClickListener(v -> {
            if (client != null) client.sendCmd(CongaProtocol.CMD_START_CLEAN);
        });
        btnHome.setOnClickListener(v -> {
            if (client != null) client.sendCmd(CongaProtocol.CMD_GO_HOME);
        });
        btnStatus.setOnClickListener(v -> {
            if (client != null) client.sendCmd(CongaProtocol.CMD_GET_STATUS);
        });

        setUiState("Connecting…", false);
        connectSocket();
    }

    private void connectSocket() {
        client = new CongaClient(this);
        client.connect(token, userId, robotId, authCode, deviceId);
    }

    // --- CongaClient.Listener ---

    @Override public void onConnected() {
        runOnUiThread(() -> tvStatus.setText("🔌 Connected — logging in…"));
    }

    @Override public void onLoginSuccess() {
        runOnUiThread(() -> {
            tvStatus.setText("✅ Connected to Conga");
            setUiState("Connected", true);
        });
    }

    @Override public void onLoginFailed(String reason) {
        runOnUiThread(() -> {
            tvStatus.setText("❌ Login failed: " + reason);
            Toast.makeText(this, "Login failed: " + reason, Toast.LENGTH_LONG).show();
        });
    }

    @Override public void onStatusUpdate(CongaClient.RobotStatus status) {
        runOnUiThread(() -> {
            tvBattery.setText("🔋 Battery: " + status.battery + "%");
            tvState.setText("⚙️ State: " + status.workStateLabel());
            tvMode.setText("🔄 Mode: " + (status.workMode == 0 ? "Normal" : "Smart"));
            tvError.setText(status.error != 0 ? "⚠️ Error: " + status.error : "");
            tvError.setVisibility(status.error != 0 ? View.VISIBLE : View.GONE);
            tvVersion.setText("📱 v" + status.version);

            // Update authCode if refreshed
            if (status.authCode != null && !status.authCode.isEmpty()) {
                authCode = status.authCode;
            }

            // Update button state based on workState
            boolean cleaning = (status.workState == 1 || status.workState == 2);
            btnStart.setText(cleaning ? "⏹ Stop" : "▶️ Start");
            btnStart.setOnClickListener(v -> {
                if (client != null) {
                    client.sendCmd(cleaning
                            ? CongaProtocol.CMD_GO_HOME
                            : CongaProtocol.CMD_START_CLEAN);
                }
            });
        });
    }

    @Override public void onDisconnected(String reason) {
        runOnUiThread(() -> {
            tvStatus.setText("🔴 Disconnected: " + reason);
            setUiState("Disconnected", false);
        });
    }

    @Override public void onError(String error) {
        runOnUiThread(() -> Toast.makeText(this, error, Toast.LENGTH_SHORT).show());
    }

    private void setUiState(String statusText, boolean enabled) {
        tvStatus.setText(statusText);
        btnStart.setEnabled(enabled);
        btnHome.setEnabled(enabled);
        btnStatus.setEnabled(enabled);
        if (!enabled) {
            tvBattery.setText("🔋 Battery: --");
            tvState.setText("⚙️ State: --");
            tvMode.setText("🔄 Mode: --");
            tvVersion.setText("");
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (client != null) client.disconnect();
    }
}
