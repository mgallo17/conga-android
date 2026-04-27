package com.mgallo17.conga;

import android.util.Log;

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
 * the Conga binary protocol (login, ping, commands, status).
 */
public class CongaClient {

    private static final String TAG             = "CongaClient";
    private static final int    CONNECT_TIMEOUT = 10_000;
    private static final int    PING_INTERVAL   = 30;
    private static final int    SOCKET_TIMEOUT  = 60_000;

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onLoginSuccess(int userId);
        void onLoginFailed(String reason);
        void onStatus(CongaMessage.StatusResponse status);
        void onError(String message);
    }

    private final CongaProtocol            protocol  = new CongaProtocol();
    private final ExecutorService          ioThread  = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean            running   = new AtomicBoolean(false);

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

        try { deviceId = Integer.parseInt(savedDeviceId, 16); } catch (Exception ignored) {}

        ioThread.execute(() -> {
            try {
                Log.d(TAG, "Connecting to " + CongaCommands.SERVER_HOST + ":" + CongaCommands.SERVER_PORT);
                socket = new Socket();
                socket.connect(new InetSocketAddress(
                        CongaCommands.SERVER_HOST, CongaCommands.SERVER_PORT), CONNECT_TIMEOUT);
                socket.setSoTimeout(SOCKET_TIMEOUT);
                out = socket.getOutputStream();
                in  = socket.getInputStream();

                if (listener != null) listener.onConnected();

                // Initial ping
                sendRaw(protocol.buildPing(0, deviceId));

                // Periodic pings
                scheduler.scheduleAtFixedRate(this::ping, PING_INTERVAL, PING_INTERVAL, TimeUnit.SECONDS);

                // Login
                byte[] loginPayload = CongaMessage.loginRequest(
                        email, CongaProtocol.md5(password), String.valueOf(deviceId));
                sendRaw(protocol.buildFrame(CongaCommands.OPCODE_LOGIN, 0, deviceId, loginPayload));

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
    // Commands
    // ---------------------------------------------------------------

    public void sendCommand(int cmd) {
        ioThread.execute(() -> {
            try {
                byte[] payload = CongaMessage.commandRequest(cmd);
                byte[] frame   = protocol.buildFrame(CongaCommands.OPCODE_COMMAND, userId, deviceId, payload);
                sendRaw(frame);
            } catch (IOException e) {
                Log.e(TAG, "sendCommand error: " + e.getMessage());
            }
        });
    }

    public void sendSchedule(int daysMask, int hour, int minute) {
        ioThread.execute(() -> {
            try {
                byte[] payload = CongaMessage.scheduleRequest(daysMask, hour, minute);
                byte[] frame   = protocol.buildFrame(CongaCommands.OPCODE_SCHEDULE_SET, userId, deviceId, payload);
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
        byte[] header = new byte[4];
        while (running.get() && socket != null && !socket.isClosed()) {
            if (readFully(header, 0, 4) < 4) break;

            int frameLen = CongaProtocol.readFrameLength(header);
            if (frameLen < 24 || frameLen > 65536) { Log.w(TAG, "Bad frame len: " + frameLen); break; }

            byte[] frameBuf = new byte[frameLen];
            System.arraycopy(header, 0, frameBuf, 0, 4);
            if (readFully(frameBuf, 4, frameLen - 4) < frameLen - 4) break;

            handleFrame(frameBuf);
        }
        Log.d(TAG, "Read loop ended");
        if (listener != null) listener.onDisconnected();
        running.set(false);
    }

    private void handleFrame(byte[] data) {
        CongaProtocol.ParsedFrame frame = protocol.parseFrame(data);
        if (frame == null) return;

        switch (frame.opcode) {
            case CongaCommands.OPCODE_PING_RESP:
                Log.d(TAG, "Pong");
                break;

            case CongaCommands.OPCODE_LOGIN_RESP:
                CongaMessage.LoginResponse lr = CongaMessage.parseLoginResponse(frame.payload);
                if (lr.result == 0) {
                    userId = lr.userId;
                    Log.d(TAG, "Login OK userId=" + userId);
                    if (listener != null) listener.onLoginSuccess(userId);
                } else {
                    Log.w(TAG, "Login failed: " + lr.msg);
                    if (listener != null) listener.onLoginFailed(lr.msg);
                }
                break;

            case CongaCommands.OPCODE_STATUS:
                CongaMessage.StatusResponse status = CongaMessage.parseStatusResponse(frame.payload);
                if (listener != null) listener.onStatus(status);
                break;

            default:
                Log.d(TAG, "Opcode: " + frame.opcode);
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
        if (out != null) { out.write(data); out.flush(); }
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
