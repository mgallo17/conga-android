package com.mgallo17.conga;

import android.content.Context;
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
    private TextView    tvLoginStatus;

    private final HctApiClient apiClient = new HctApiClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail       = findViewById(R.id.etEmail);
        etPassword    = findViewById(R.id.etPassword);
        btnLogin      = findViewById(R.id.btnLogin);
        progressBar   = findViewById(R.id.progressBar);
        tvLoginStatus = findViewById(R.id.tvLoginStatus);

        // Auto-fill saved email
        SharedPreferences prefs = getSharedPreferences(CongaCommands.PREFS_NAME, MODE_PRIVATE);
        String savedEmail = prefs.getString(CongaCommands.PREF_EMAIL, "");
        if (!savedEmail.isEmpty()) {
            etEmail.setText(savedEmail);

            // Auto-login if session still valid
            String savedSession = prefs.getString(CongaCommands.PREF_SESSION_ID, "");
            if (!savedSession.isEmpty()) {
                goToMain(savedSession);
                return;
            }
        }

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pwd   = etPassword.getText().toString();

            if (email.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            setLoading(true, "Connecting to Cecotec servers…");

            // Android ID como identificador único do dispositivo
            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

            apiClient.login(email, pwd, deviceId, new HctApiClient.LoginCallback() {
                @Override
                public void onSuccess(String sessionId, String userId) {
                    // Save credentials
                    getSharedPreferences(CongaCommands.PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putString(CongaCommands.PREF_EMAIL,      email)
                            .putString(CongaCommands.PREF_PASSWORD,   pwd)
                            .putString(CongaCommands.PREF_SESSION_ID, sessionId)
                            .putString(CongaCommands.PREF_USER_ID,    userId)
                            .apply();
                    goToMain(sessionId);
                }

                @Override
                public void onFailure(String reason) {
                    setLoading(false, null);
                    tvLoginStatus.setText("❌ " + reason);
                    tvLoginStatus.setTextColor(0xFFE53935);
                    tvLoginStatus.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void setLoading(boolean loading, String msg) {
        btnLogin.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (msg != null) {
            tvLoginStatus.setText(msg);
            tvLoginStatus.setTextColor(0xFF1565C0);
            tvLoginStatus.setVisibility(View.VISIBLE);
        } else {
            tvLoginStatus.setVisibility(View.GONE);
        }
    }

    private void goToMain(String sessionId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(CongaCommands.PREF_SESSION_ID, sessionId);
        startActivity(intent);
        finish();
    }
}
