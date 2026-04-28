package com.mgallo17.conga;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class LoginActivity extends AppCompatActivity {

    private EditText    etEmail, etPassword;
    private Button      btnLogin;
    private ProgressBar progressBar;
    private TextView    tvLoginStatus;

    private CongaService service;
    private boolean      bound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((CongaService.LocalBinder) binder).getService();
            bound   = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case CongaCommands.ACTION_LOGIN_SUCCESS:
                    // Save credentials only after server confirms login
                    saveCredentials(
                            etEmail.getText().toString().trim(),
                            etPassword.getText().toString());
                    goToMain();
                    break;

                case CongaCommands.ACTION_LOGIN_FAILED:
                    String reason = intent.getStringExtra(CongaCommands.EXTRA_LOGIN_MSG);
                    showError(reason != null ? reason : "Login failed");
                    break;

                case CongaCommands.ACTION_DISCONNECTED:
                    showError("Could not connect to server");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail      = findViewById(R.id.etEmail);
        etPassword   = findViewById(R.id.etPassword);
        btnLogin     = findViewById(R.id.btnLogin);
        progressBar  = findViewById(R.id.progressBar);
        tvLoginStatus = findViewById(R.id.tvLoginStatus);

        // Register for login result broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(CongaCommands.ACTION_LOGIN_SUCCESS);
        filter.addAction(CongaCommands.ACTION_LOGIN_FAILED);
        filter.addAction(CongaCommands.ACTION_DISCONNECTED);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);

        // Bind service
        Intent svc = new Intent(this, CongaService.class);
        bindService(svc, connection, BIND_AUTO_CREATE);

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pwd   = etPassword.getText().toString();

            if (email.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show loading
            btnLogin.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);
            tvLoginStatus.setText("Connecting to Cecotec servers…");
            tvLoginStatus.setVisibility(View.VISIBLE);

            // Initiate TCP connection + login
            Intent svcIntent = new Intent(this, CongaService.class);
            startForegroundService(svcIntent);

            if (bound) {
                service.connect(email, pwd, "0");
            } else {
                // Not bound yet — wait a moment and retry
                etEmail.postDelayed(() -> {
                    if (bound) {
                        service.connect(email, pwd, "0");
                    } else {
                        showError("Service not ready, try again");
                    }
                }, 500);
            }
        });
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        if (bound) { unbindService(connection); bound = false; }
        super.onDestroy();
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void showError(String msg) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            tvLoginStatus.setText("❌ " + msg);
            tvLoginStatus.setTextColor(0xFFE53935);
            tvLoginStatus.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(true);
        });
    }

    private void saveCredentials(String email, String password) {
        getSharedPreferences(CongaCommands.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(CongaCommands.PREF_EMAIL, email)
                .putString(CongaCommands.PREF_PASSWORD, password)
                .apply();
    }
}
