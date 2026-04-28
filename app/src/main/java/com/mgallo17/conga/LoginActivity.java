package com.mgallo17.conga;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText    etEmail, etPassword;
    private Button      btnLogin, btnManualLogin;
    private ProgressBar progressBar;
    private TextView    tvStatus;

    private final HctApiClient apiClient = new HctApiClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail        = findViewById(R.id.etEmail);
        etPassword     = findViewById(R.id.etPassword);
        btnLogin       = findViewById(R.id.btnLogin);
        btnManualLogin = findViewById(R.id.btnManualLogin);
        progressBar    = findViewById(R.id.progressBar);
        tvStatus       = findViewById(R.id.tvLoginStatus);

        // Pre-fill saved email
        SharedPreferences prefs = getSharedPreferences(CongaCommands.PREFS_NAME, MODE_PRIVATE);
        String savedEmail = prefs.getString(CongaCommands.PREF_EMAIL, "");
        if (!savedEmail.isEmpty()) {
            etEmail.setText(savedEmail);
            // Auto-login if session still valid
            String savedToken  = prefs.getString(CongaCommands.PREF_SESSION_ID, "");
            String savedUser   = prefs.getString(CongaCommands.PREF_USER_ID, "");
            String savedRobot  = prefs.getString("robot_id", "");
            String savedAuth   = prefs.getString("auth_code", "");
            String savedDevice = prefs.getString("device_id", "");
            if (!savedToken.isEmpty() && !savedUser.isEmpty()) {
                goToMain(savedToken, savedUser, savedRobot, savedAuth, savedDevice);
                return;
            }
        }

        btnLogin.setOnClickListener(v -> doLogin());
        btnManualLogin.setOnClickListener(v -> showManualLoginDialog());
    }

    private void doLogin() {
        String email = etEmail.getText().toString().trim();
        String pwd   = etPassword.getText().toString();

        if (email.isEmpty() || pwd.isEmpty()) {
            Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true, "Connecting to Cecotec servers…");

        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        apiClient.login(email, pwd, deviceId, new HctApiClient.LoginCallback() {
            @Override
            public void onSuccess(String token, String userId, String robotId,
                                  String authCode, String devId) {
                saveAndGo(email, pwd, token, userId, robotId, authCode, devId);
            }

            @Override
            public void onFailure(String reason) {
                setLoading(false, null);
                tvStatus.setText("❌ " + reason + "\n\nTry \"Manual Setup\" below.");
                tvStatus.setTextColor(0xFFE53935);
                tvStatus.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Manual setup dialog — enter token, userId, deviceId, authCode, robotId directly */
    private void showManualLoginDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_manual_login, null);
        EditText etToken    = v.findViewById(R.id.etToken);
        EditText etUserId   = v.findViewById(R.id.etUserId);
        EditText etDeviceId = v.findViewById(R.id.etDeviceId);
        EditText etAuthCode = v.findViewById(R.id.etAuthCode);
        EditText etRobotId  = v.findViewById(R.id.etRobotId);

        // Pre-fill known values from plist
        SharedPreferences prefs = getSharedPreferences(CongaCommands.PREFS_NAME, MODE_PRIVATE);
        etToken.setText(prefs.getString(CongaCommands.PREF_SESSION_ID, ""));
        etUserId.setText(prefs.getString(CongaCommands.PREF_USER_ID, ""));
        etDeviceId.setText(prefs.getString("device_id", ""));
        etAuthCode.setText(prefs.getString("auth_code", ""));
        etRobotId.setText(prefs.getString("robot_id", ""));

        new AlertDialog.Builder(this)
                .setTitle("Manual Setup")
                .setMessage("Enter the values from your Conga app (iOS plist or app settings):")
                .setView(v)
                .setPositiveButton("Connect", (d, w) -> {
                    String token    = etToken.getText().toString().trim();
                    String userId   = etUserId.getText().toString().trim();
                    String deviceId = etDeviceId.getText().toString().trim();
                    String authCode = etAuthCode.getText().toString().trim();
                    String robotId  = etRobotId.getText().toString().trim();

                    if (token.isEmpty() || userId.isEmpty() || deviceId.isEmpty()) {
                        Toast.makeText(this, "Token, User ID and Device ID are required",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveAndGo("", "", token, userId, robotId, authCode, deviceId);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveAndGo(String email, String pwd,
                           String token, String userId, String robotId,
                           String authCode, String deviceId) {
        getSharedPreferences(CongaCommands.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(CongaCommands.PREF_EMAIL,      email)
                .putString(CongaCommands.PREF_PASSWORD,   pwd)
                .putString(CongaCommands.PREF_SESSION_ID, token)
                .putString(CongaCommands.PREF_USER_ID,    userId)
                .putString("robot_id",  robotId)
                .putString("auth_code", authCode)
                .putString("device_id", deviceId)
                .apply();
        goToMain(token, userId, robotId, authCode, deviceId);
    }

    private void setLoading(boolean loading, String msg) {
        btnLogin.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (msg != null) {
            tvStatus.setText(msg);
            tvStatus.setTextColor(0xFF1565C0);
            tvStatus.setVisibility(View.VISIBLE);
        } else {
            tvStatus.setVisibility(View.GONE);
        }
    }

    private void goToMain(String token, String userId, String robotId,
                          String authCode, String deviceId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(CongaCommands.PREF_SESSION_ID, token);
        intent.putExtra(CongaCommands.PREF_USER_ID,    userId);
        intent.putExtra("robot_id",  robotId);
        intent.putExtra("auth_code", authCode);
        intent.putExtra("device_id", deviceId);
        startActivity(intent);
        finish();
    }
}
