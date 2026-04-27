package com.mgallo17.conga;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail    = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);

        prefs = getEncryptedPrefs();

        // Auto-fill saved credentials
        String savedEmail = prefs.getString(CongaCommands.PREF_EMAIL, "");
        String savedPwd   = prefs.getString(CongaCommands.PREF_PASSWORD, "");
        if (!savedEmail.isEmpty()) {
            etEmail.setText(savedEmail);
            etPassword.setText(savedPwd);
            // Auto-proceed if credentials exist
            proceed(savedEmail, savedPwd);
            return;
        }

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pwd   = etPassword.getText().toString();
            if (email.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit()
                    .putString(CongaCommands.PREF_EMAIL, email)
                    .putString(CongaCommands.PREF_PASSWORD, pwd)
                    .apply();
            proceed(email, pwd);
        });
    }

    private void proceed(String email, String password) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(CongaCommands.PREF_EMAIL, email);
        intent.putExtra(CongaCommands.PREF_PASSWORD, password);
        startActivity(intent);
        finish();
    }

    private SharedPreferences getEncryptedPrefs() {
        try {
            String masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    CongaCommands.PREFS_NAME,
                    masterKey,
                    this,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            // Fallback to plain prefs if encryption fails
            return getSharedPreferences(CongaCommands.PREFS_NAME, Context.MODE_PRIVATE);
        }
    }
}
