package com.mgallo17.conga;

import android.util.Log;

import com.mgallo17.conga.proto.CongaProto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the TCP connection to the Cecotec server and implements
 * the Conga protocol (login, ping, commands, status).
 */
public class CongaClient {

    private static final String TAG             = "CongaClient";
    private static final int    CONNECT_TIMEOUT = 10_000; // ms
    private static final int    PING_INTERVAL   = 30;     // seconds
    private static final int    RECONNECT_DELAY = 5;      // seconds

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onLoginSuccess(int userId);
        void onLoginFailed(String reason);
        void onStatus(CongaProto.StatusResponse status);
        void onError(String message);
    }

    private final CongaProtocol              protocol  = new CongaProtocol();
    private final ExecutorService            ioThread  = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService   scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean              running   = new AtomicBoolean(false);

    private Socket       socket;
    private OutputStream out;
    private InputStream  in;
    private Listener     listener;
    private int          userId   = 0;
    private int          deviceId = 0;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // ---------------------------------------------------------------
    // Connection
    // ---------------------------------------------------------------

    public void connect(String email, String password, String savedDeviceId) {
        if (running.getAndSet(true)) return;

        int devId = 0;
        try { devId = Integer.parseInt(savedDeviceId, 16); } catch (Exception ignored) {}
        this.deviceId = devId;

        final String pwd   = password;
        final String eml   = email;

        ioThread.execute(() -> {
            try {
                Log.d(TAG, "Connecting to " + CongaCommands.SERVER_HOST + ":" + CongaCommands.SERVER_PORT);
                socket = new Socket();
                socket.connect(new InetSocketAddress(
                        CongaCommands.SERVER_HOST, CongaCommands.SERVER_PORT), CONNECT_TIMEOUT);
                out = socket.getOutputStream();
                in  = socket.getInputStream();

                if (listener != null) listener.onConnected();

                // Send initial ping
                sendRaw(protocol.buildPing(0, deviceId));

                // Schedule periodic pings
                scheduler.scheduleAtFixedRate(this::ping, PING_INTERVAL, PING_INTERVAL, TimeUnit.SECONDS);

                // Login
                sendLogin(eml, pwd);

                // Read loop
                readLoop();

            } catch (Exception e) {
                Log.e(TAG, "Connection error: " + e.getMessage());
                if (listener != null) listener.onDisconnected();
                running.set(false);
            }
        });
    }

    public void disconnect() {
        running.set(false);
        scheduler.shutdownNow();
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        if (listener != null) listener.onDisconnected();
    }

    // ---------------------------------------------------------------
    // Login
    // ---------------------------------------------------------------

    private void sendLogin(String email, String password) throws IOException {
        CongaProto.LoginRequest req = CongaProto.LoginRequest.newBuilder()
                .setUsername(email)
                .setPassword(CongaProtocol.md5(password))
                .setDeviceId(String.valueOf(deviceId))
                .setAppVersion(1)
                .build();

        byte[] frame = protocol.buildFrame(
                CongaCommands.OPCODE_LOGIN, 0, deviceId, req.toByteArray());
        sendRaw(frame);
    }

    // ---------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------

    public void sendCommand(int cmd) {
        ioThread.execute(() -> {
            try {
                CongaProto.CommandRequest req = CongaProto.CommandRequest.newBuilder()
                        .setCommand(cmd)
                        .build();
                byte[] frame = protocol.buildFrame(
                        CongaCommands.OPCODE_COMMAND, userId, deviceId, req.toByteArray());
                sendRaw(frame);
            } catch (IOException e) {
                Log.e(TAG, "sendCommand error: " + e.getMessage());
            }
        });
    }

    public void sendSchedule(int daysMask, int hour, int minute) {
        ioThread.execute(() -> {
            try {
                CongaProto.ScheduleEntry entry = CongaProto.ScheduleEntry.newBuilder()
                        .setDaysMask(daysMask)
                        .setHour(hour)
                        .setMinute(minute)
                        .setEnabled(true)
                        .build();
                CongaProto.ScheduleRequest req = CongaProto.ScheduleRequest.newBuilder()
                        .addEntries(entry)
                        .build();
                byte[] frame = protocol.buildFrame(
                        CongaCommands.OPCODE_SCHEDULE_SET, userId, deviceId, req.toByteArray());
                sendRaw(frame);
            } catch (IOException e) {
                Log.e(TAG, "sendSchedule error: " + e.getMessage());
            }
        });
    }

    // ---------------------------------------------------------------
    // Read loop
    // ---------------------------------------------------------------

    private void readLoop() throws IOException {
        byte[] headerBuf = new byte[4];
        while (running.get() && !socket.isClosed()) {
            // Read 4-byte length prefix
            int read = readFully(headerBuf, 4);
            if (read < 4) break;

            int frameLen = CongaProtocol.readFrameLength(headerBuf);
            if (frameLen < 24 || frameLen > 65536) {
                Log.w(TAG, "Unexpected frame length: " + frameLen);
                break;
            }

            byte[] frameBuf = new byte[frameLen];
            System.arraycopy(headerBuf, 0, frameBuf, 0, 4);
            read = readFully(frameBuf, 4, frameLen - 4);
            if (read < frameLen - 4) break;

            handleFrame(frameBuf);
        }
    }

    private void handleFrame(byte[] data) {
        CongaProtocol.ParsedFrame frame = protocol.parseFrame(data);
        if (frame == null) return;

        try {
            switch (frame.opcode) {
                case CongaCommands.OPCODE_PING_RESP:
                    Log.d(TAG, "Pong received");
                    break;

                case CongaCommands.OPCODE_LOGIN_RESP:
                    CongaProto.LoginResponse loginResp =
                            CongaProto.LoginResponse.parseFrom(frame.payload);
                    if (loginResp.getResult() == 0) {
                        userId = loginResp.getUserId();
                        Log.d(TAG, "Login OK, userId=" + userId);
                        if (listener != null) listener.onLoginSuccess(userId);
                    } else {
                        Log.w(TAG, "Login failed: " + loginResp.getMsg());
                        if (listener != null) listener.onLoginFailed(loginResp.getMsg());
                    }
                    break;

                case CongaCommands.OPCODE_STATUS:
                    CongaProto.StatusResponse status =
                            CongaProto.StatusResponse.parseFrom(frame.payload);
                    if (listener != null) listener.onStatus(status);
                    break;

                default:
                    Log.d(TAG, "Unknown opcode: " + frame.opcode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Frame parse error: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void ping() {
        try { sendRaw(protocol.buildPing(userId, deviceId)); }
        catch (IOException e) { Log.w(TAG, "Ping failed: " + e.getMessage()); }
    }

    private synchronized void sendRaw(byte[] data) throws IOException {
        if (out != null) {
            out.write(data);
            out.flush();
        }
    }

    private int readFully(byte[] buf, int len) throws IOException {
        return readFully(buf, 0, len);
    }

    private int readFully(byte[] buf, int offset, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = in.read(buf, offset + total, len - total);
            if (n < 0) return total;
            total += n;
        }
        return total;
    }

    public boolean isConnected() {
        return running.get() && socket != null && socket.isConnected() && !socket.isClosed();
    }
}
