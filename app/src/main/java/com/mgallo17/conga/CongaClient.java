package com.mgallo17.conga;

import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP socket client for hc-s-eu.hctrobot.com:20008
 *
 * Protocol (from pcap):
 *   Frame = HEADER(20) + JSON + FOOTER(28)
 *
 * Login flow:
 *   1. Connect TCP
 *   2. Send LOGIN_REQ with token+userId+appKey+deviceId+deviceType
 *   3. Receive LOGIN_RSP {"msg":"login succeed","result":0}
 *   4. Send CMD_REQ with transitCmd and authCode to relay to robot
 */
public class CongaClient {

    private static final String TAG  = "CongaClient";
    private static final String HOST = "hc-s-eu.hctrobot.com";
    private static final int    PORT = 20008;

    private static final String DEVICE_TYPE = "3"; // Android

    public interface Listener {
        void onConnected();
        void onLoginSuccess();
        void onLoginFailed(String reason);
        void onStatusUpdate(RobotStatus status);
        void onDisconnected(String reason);
        void onError(String error);
    }

    // Robot status from STATUS_PUSH
    public static class RobotStatus {
        public int    battery;
        public int    workState;
        public int    workMode;
        public int    fan;
        public int    brush;
        public int    error;
        public String version;
        public String deviceIp;
        public int    devicePort;
        public String authCode;
        public String robotId;

        public String workStateLabel() {
            switch (workState) {
                case 0:  return "Standby";
                case 1:  case 2: return "Cleaning";
                case 9:  return "Returning";
                case 10: return "Charging";
                default: return "Unknown (" + workState + ")";
            }
        }
    }

    private Socket       socket;
    private OutputStream out;
    private InputStream  in;
    private boolean      running;
    private final Listener listener;

    // Session state
    private String userId;
    private String token;
    private String robotId;
    private String authCode;
    private String deviceId;
    private final AtomicInteger seqSend = new AtomicInteger(0);
    private volatile int seqRecv = 0;

    public CongaClient(Listener listener) {
        this.listener = listener;
    }

    // Connect and login
    public void connect(String token, String userId, String robotId,
                        String authCode, String deviceId) {
        this.token    = token;
        this.userId   = userId;
        this.robotId  = robotId;
        this.authCode = authCode;
        this.deviceId = deviceId;

        new Thread(this::runSocket).start();
    }

    private void runSocket() {
        try {
            Log.d(TAG, "Connecting to " + HOST + ":" + PORT);
            socket = new Socket();
            socket.connect(new InetSocketAddress(HOST, PORT), 10_000);
            socket.setSoTimeout(60_000);
            out = socket.getOutputStream();
            in  = socket.getInputStream();
            running = true;
            listener.onConnected();

            sendLoginReq();
            readLoop();

        } catch (Exception e) {
            Log.e(TAG, "Socket error: " + e.getMessage());
            listener.onDisconnected(e.getMessage());
        } finally {
            close();
        }
    }

    private void sendLoginReq() throws IOException {
        JSONObject ctrl = new JSONObject();
        JSONObject val  = new JSONObject();
        try {
            ctrl.put("targetId",   robotId);
            ctrl.put("targetType", "1");

            val.put("appKey",     HctApiClient.APP_KEY);
            val.put("deviceId",   deviceId);
            val.put("deviceType", DEVICE_TYPE);
            val.put("token",      token);
            val.put("userId",     userId);

            JSONObject req = new JSONObject();
            req.put("cmd",     0);
            req.put("control", ctrl);
            req.put("seq",     0);
            req.put("value",   val);

            sendFrame(CongaProtocol.MSG_LOGIN_REQ, req.toString());
            Log.d(TAG, "LOGIN_REQ sent");
        } catch (Exception e) {
            throw new IOException("Failed to build login: " + e.getMessage());
        }
    }

    // Send transitCmd to robot via cloud relay
    public void sendCmd(String transitCmd) {
        if (!running) return;
        new Thread(() -> {
            try {
                JSONObject ctrl = new JSONObject();
                JSONObject val  = new JSONObject();
                ctrl.put("authCode",   authCode);
                ctrl.put("deviceIp",   "");
                ctrl.put("devicePort", "8888");
                ctrl.put("targetId",   robotId);
                ctrl.put("targetType", "1");
                val.put("transitCmd", transitCmd);

                JSONObject req = new JSONObject();
                req.put("cmd",     0);
                req.put("control", ctrl);
                req.put("seq",     seqSend.get());
                req.put("value",   val);

                sendFrame(CongaProtocol.MSG_CMD_REQ, req.toString());
                Log.d(TAG, "CMD sent: " + transitCmd);
            } catch (Exception e) {
                listener.onError("CMD error: " + e.getMessage());
            }
        }).start();
    }

    private synchronized void sendFrame(int msgType, String json) throws IOException {
        int seq = seqSend.getAndIncrement();
        byte[] frame = CongaProtocol.buildFrame(msgType, json, seq, seqRecv);
        out.write(frame);
        out.flush();
    }

    private void readLoop() throws IOException {
        byte[] headerBuf = new byte[20];
        while (running) {
            // Read header
            readFully(headerBuf, 0, 20);
            int[] parsed = CongaProtocol.parseHeader(headerBuf, 0);
            if (parsed == null) {
                Log.w(TAG, "Bad header, skipping");
                continue;
            }
            int msgType    = parsed[0];
            int payloadLen = parsed[1];

            // Read payload (json + footer)
            byte[] payload = new byte[payloadLen];
            readFully(payload, 0, payloadLen);

            // Extract JSON (strip footer 28 bytes)
            int jsonLen = payloadLen - 28;
            if (jsonLen <= 0) continue;
            String json = new String(payload, 0, jsonLen, java.nio.charset.StandardCharsets.UTF_8);
            Log.d(TAG, "RX type=0x" + Integer.toHexString(msgType) + " json=" + json);

            handleMessage(msgType, json);
        }
    }

    private void handleMessage(int msgType, String json) {
        try {
            JSONObject obj = new JSONObject(json);
            switch (msgType) {
                case CongaProtocol.MSG_LOGIN_RSP: {
                    int result = obj.optInt("result", -1);
                    if (result == 0) {
                        listener.onLoginSuccess();
                        // Request status immediately
                        sendCmd(CongaProtocol.CMD_GET_STATUS);
                    } else {
                        listener.onLoginFailed(obj.optString("msg", "Login failed"));
                    }
                    break;
                }
                case CongaProtocol.MSG_STATUS_RSP:
                case CongaProtocol.MSG_CMD_RSP: {
                    JSONObject value = obj.optJSONObject("value");
                    if (value == null) break;
                    String noteCmd = value.optString("noteCmd", "");
                    // noteCmd 102 = status update
                    if ("102".equals(noteCmd) || msgType == CongaProtocol.MSG_STATUS_RSP) {
                        RobotStatus status = new RobotStatus();
                        status.battery    = parseInt(value.optString("battery", "0"));
                        status.workState  = parseInt(value.optString("workState", "0"));
                        status.workMode   = parseInt(value.optString("workMode", "0"));
                        status.fan        = parseInt(value.optString("fan", "0"));
                        status.brush      = parseInt(value.optString("brush", "0"));
                        status.error      = parseInt(value.optString("error", "0"));
                        status.version    = value.optString("version1", value.optString("version", ""));
                        status.deviceIp   = value.optString("deviceIp", "");
                        status.devicePort = parseInt(value.optString("devicePort", "8888"));

                        // Update authCode from control block if present
                        JSONObject ctrl = obj.optJSONObject("control");
                        if (ctrl != null) {
                            String ac = ctrl.optString("authCode", "");
                            if (!ac.isEmpty()) {
                                authCode = ac;
                                status.authCode = ac;
                            }
                            status.robotId = ctrl.optString("targetId", robotId);
                        }
                        listener.onStatusUpdate(status);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage());
        }
    }

    private void readFully(byte[] buf, int offset, int length) throws IOException {
        int read = 0;
        while (read < length) {
            int n = in.read(buf, offset + read, length - read);
            if (n < 0) throw new IOException("Connection closed");
            read += n;
        }
    }

    public void disconnect() {
        running = false;
        close();
    }

    private void close() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
