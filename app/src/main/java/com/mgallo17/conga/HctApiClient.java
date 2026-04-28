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
 * REST client for hc-app-eu.hctrobot.com/baole-web/
 *
 * Login flow:
 *   POST /personal/getAppToken.do
 *     Headers: X-Requested-With: XMLHttpRequest
 *     Params:  loginName, loginPassword (MD5), companyId=35, appKey, imei
 *   Response: {"code":0,"data":{"token":"...","userId":"...","robotId":"...","authCode":"..."}}
 */
public class HctApiClient {

    private static final String TAG        = "HctApiClient";
    // Real URL from plist: Jdomain=https://hc-app-eu.hctrobot.com JPort=8080
    // Mobile API runs on HTTP port 8080, not HTTPS 443 (which is BMS admin)
    private static final String BASE_URL   = "http://hc-app-eu.hctrobot.com:8080/baole-web/";
    private static final String COMPANY_ID = "35";
    static final String APP_KEY   = "7ebc52168d6242c4868b27907c797865";

    public interface LoginCallback {
        void onSuccess(String token, String userId, String robotId, String authCode, String deviceId);
        void onFailure(String reason);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void login(String email, String password, String imei, LoginCallback cb) {
        new Thread(() -> {
            try {
                String params = "loginName=" + encode(email)
                        + "&loginPassword=" + encode(CongaProtocol.md5(password))
                        + "&companyId=" + COMPANY_ID
                        + "&appKey=" + APP_KEY
                        + "&imei=" + encode(imei);

                String response = post("personal/getAppToken.do", params);
                Log.d(TAG, "Login response: " + response);

                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);

                if (code == 0) {
                    JSONObject data  = json.optJSONObject("data");
                    if (data == null) {
                        mainHandler.post(() -> cb.onFailure("Empty data in response"));
                        return;
                    }
                    String token    = data.optString("token", "");
                    String userId   = data.optString("userId", "");
                    String robotId  = data.optString("robotId", "");
                    String authCode = data.optString("authCode", "");
                    mainHandler.post(() -> cb.onSuccess(token, userId, robotId, authCode, imei));
                } else {
                    String msg = json.optString("msg", "Login failed (code " + code + ")");
                    mainHandler.post(() -> cb.onFailure(msg));
                }
            } catch (Exception e) {
                Log.e(TAG, "Login error", e);
                mainHandler.post(() -> cb.onFailure("Network error: " + e.getMessage()));
            }
        }).start();
    }

    private String post(String endpoint, String params) throws Exception {
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("User-Agent", "okhttp/3.12.0");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(20_000);
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(false);

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
