package com.mgallo17.conga;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * REST client for com-app.hctrobot.com/baole-web/
 * Handles login (getAppToken.do) and robot info.
 */
public class HctApiClient {

    private static final String TAG      = "HctApiClient";
    // Servidor EU — extraído do BuildConfig da app oficial
    private static final String BASE_URL = "https://hc-app-eu.hctrobot.com/baole-web/";
    private static final String COMPANY_ID = "35";
    private static final String APP_KEY  = "7ebc52168d6242c4868b27907c797865";

    public interface LoginCallback {
        void onSuccess(String sessionId, String userId);
        void onFailure(String reason);
    }

    public interface RobotInfoCallback {
        void onSuccess(String robotId, String robotName, boolean online);
        void onFailure(String reason);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ---------------------------------------------------------------
    // Login
    // ---------------------------------------------------------------

    public void login(String email, String password, String imei, LoginCallback cb) {
        new Thread(() -> {
            try {
                // Formato real extraído do LoginApi.login(username, password, imei, callback)
                // O servidor EU usa o endpoint baole-web com companyId=35
                String params = "loginName=" + encode(email)
                        + "&loginPassword=" + encode(CongaProtocol.md5(password))
                        + "&imei=" + encode(imei)
                        + "&companyId=" + COMPANY_ID
                        + "&appKey=" + APP_KEY;

                String response = post("personal/getAppToken.do", params);
                Log.d(TAG, "Login response: " + response);

                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);

                if (code == 0) {
                    JSONObject data  = json.optJSONObject("data");
                    String sessionId = data != null ? data.optString("sessionId", "") : "";
                    String userId    = data != null ? data.optString("userId", "") : "";
                    mainHandler.post(() -> cb.onSuccess(sessionId, userId));
                } else {
                    String msg = json.optString("msg", "Login failed (code " + code + ")");
                    mainHandler.post(() -> cb.onFailure(msg));
                }

            } catch (Exception e) {
                Log.e(TAG, "Login error: " + e.getMessage());
                mainHandler.post(() -> cb.onFailure("Network error: " + e.getMessage()));
            }
        }).start();
    }

    // ---------------------------------------------------------------
    // Robot info
    // ---------------------------------------------------------------

    public void getDefaultRobotInfo(String sessionId, RobotInfoCallback cb) {
        new Thread(() -> {
            try {
                String params   = "sessionId=" + encode(sessionId);
                String response = post("index/getMyDefaultRobotInfo.do", params);
                Log.d(TAG, "Robot info: " + response);

                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);

                if (code == 0) {
                    JSONObject data  = json.optJSONObject("data");
                    String robotId   = data != null ? data.optString("robotId", "") : "";
                    String robotName = data != null ? data.optString("name", "Conga") : "Conga";
                    boolean online   = data != null && data.optInt("onlineState", 0) == 1;
                    mainHandler.post(() -> cb.onSuccess(robotId, robotName, online));
                } else {
                    mainHandler.post(() -> cb.onFailure("No robot found"));
                }

            } catch (Exception e) {
                Log.e(TAG, "Robot info error: " + e.getMessage());
                mainHandler.post(() -> cb.onFailure("Network error: " + e.getMessage()));
            }
        }).start();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String post(String endpoint, String params) throws Exception {
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("User-Agent", "Conga/1.0 Android");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setDoOutput(true);

        byte[] body = params.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }

        int code = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    private static String encode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }
}
