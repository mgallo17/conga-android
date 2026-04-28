package com.mgallo17.conga;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private RadioGroup  rgMode;
    private RadioButton rbCloud, rbLocal;
    private EditText    etRobotIp, etRobotPort, etDeviceId, etAuthCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        rgMode      = findViewById(R.id.rgMode);
        rbCloud     = findViewById(R.id.rbCloud);
        rbLocal     = findViewById(R.id.rbLocal);
        etRobotIp   = findViewById(R.id.etRobotIp);
        etRobotPort = findViewById(R.id.etRobotPort);
        etDeviceId  = findViewById(R.id.etDeviceId);
        etAuthCode  = findViewById(R.id.etAuthCode);

        // Load saved settings
        var prefs = getSharedPreferences(CongaCommands.PREFS_NAME, MODE_PRIVATE);
        boolean isLocal = prefs.getBoolean(CongaCommands.PREF_LOCAL_MODE, false);
        rbLocal.setChecked(isLocal);
        rbCloud.setChecked(!isLocal);

        etRobotIp.setText(prefs.getString(CongaCommands.PREF_ROBOT_IP,
                CongaClient.DEFAULT_LOCAL_IP));
        etRobotPort.setText(String.valueOf(prefs.getInt(CongaCommands.PREF_ROBOT_PORT,
                CongaClient.DEFAULT_LOCAL_PORT)));
        etDeviceId.setText(prefs.getString("device_id", "86A4CF12C80809"));
        etAuthCode.setText(prefs.getString("auth_code", ""));

        // Toggle field visibility based on mode
        rgMode.setOnCheckedChangeListener((group, checkedId) -> updateFieldVisibility());
        updateFieldVisibility();

        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> saveAndExit());
    }

    private void updateFieldVisibility() {
        boolean local = rbLocal.isChecked();
        int vis = local ? android.view.View.VISIBLE : android.view.View.GONE;
        findViewById(R.id.layoutLocalFields).setVisibility(vis);
    }

    private void saveAndExit() {
        boolean local = rbLocal.isChecked();
        String ip     = etRobotIp.getText().toString().trim();
        String portStr= etRobotPort.getText().toString().trim();
        String devId  = etDeviceId.getText().toString().trim();
        String auth   = etAuthCode.getText().toString().trim();

        if (local && ip.isEmpty()) {
            Toast.makeText(this, "Enter robot IP address", Toast.LENGTH_SHORT).show();
            return;
        }

        int port = CongaClient.DEFAULT_LOCAL_PORT;
        try { port = Integer.parseInt(portStr); } catch (Exception ignored) {}

        getSharedPreferences(CongaCommands.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(CongaCommands.PREF_LOCAL_MODE, local)
                .putString(CongaCommands.PREF_ROBOT_IP,    ip)
                .putInt(CongaCommands.PREF_ROBOT_PORT,     port)
                .putString("device_id", devId)
                .putString("auth_code", auth)
                .apply();

        Toast.makeText(this,
                local ? "✅ Local mode saved — " + ip + ":" + port
                       : "✅ Cloud mode saved",
                Toast.LENGTH_SHORT).show();
        finish();
    }
}
