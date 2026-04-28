package com.mgallo17.conga;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText    etEmail, etPassword;
    private Button      btnLogin;
    private ProgressBar progressBar;
    private TextView    tvStatus;

    private final HctApiClient apiClient = new HctApiClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail     = findViewById(R.id.etEmail);
        etPassword  = findViewById(R.id.etPassword);
        btnLogin    = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);
        tvStatus    = findViewById(R.id.tvLoginStatus);

        // Pre-fill saved email
        SharedPreferences prefs = getSharedPreferences(CongaCommands.PREFS_NAME, MODE_PRIVATE);
        String savedEmail = prefs.getString(CongaCommands.PREF_EMAIL, "");
        if (!savedEmail.isEmpty()) {
            etEmail.setText(savedEmail);
            // Auto-login if session still valid
            String savedToken = prefs.getString(CongaCommands.PREF_SESSION_ID, "");
            String savedUser  = prefs.getString(CongaCommands.PREF_USER_ID, "");
            String savedRobot = prefs.getString("robot_id", "");
            String savedAuth  = prefs.getString("auth_code", "");
            if (!savedToken.isEmpty() && !savedUser.isEmpty()) {
                goToMain(savedToken, savedUser, savedRobot, savedAuth);
                return;
            }
        }

        btnLogin.setOnClickListener(v -> doLogin());
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
            public void onSuccess(String token, String userId, String robotId, String authCode) {
                // Save all credentials
                getSharedPreferences(CongaCommands.PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putString(CongaCommands.PREF_EMAIL,      email)
                        .putString(CongaCommands.PREF_PASSWORD,   pwd)
                        .putString(CongaCommands.PREF_SESSION_ID, token)
                        .putString(CongaCommands.PREF_USER_ID,    userId)
                        .putString("robot_id",  robotId)
                        .putString("auth_code", authCode)
                        .apply();
                goToMain(token, userId, robotId, authCode);
            }

            @Override
            public void onFailure(String reason) {
                setLoading(false, null);
                tvStatus.setText("❌ " + reason);
                tvStatus.setTextColor(0xFFE53935);
                tvStatus.setVisibility(View.VISIBLE);
            }
        });
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

    private void goToMain(String token, String userId, String robotId, String authCode) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(CongaCommands.PREF_SESSION_ID, token);
        intent.putExtra(CongaCommands.PREF_USER_ID,    userId);
        intent.putExtra("robot_id",  robotId);
        intent.putExtra("auth_code", authCode);
        startActivity(intent);
        finish();
    }
}
