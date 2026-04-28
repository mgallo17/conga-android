package com.mgallo17.conga;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protocol framing — confirmed from real pcap capture.
 *
 * Frame layout (NO footer — confirmed from live capture):
 *   [HEADER 20 bytes][JSON payload N bytes]
 *
 * Header (little-endian):
 *   [0:4]  total_frame_len = 20 + json_len
 *   [4:6]  msg_type
 *   [6:8]  0x00c8 (fixed flags)
 *   [8:12] 0 (reserved)
 *   [12:16] seq_send
 *   [16:20] seq_recv (0)
 */
public class CongaProtocol {

    // Message types
    public static final int MSG_LOGIN_REQ  = 0x0010;
    public static final int MSG_LOGIN_RSP  = 0x0011;
    public static final int MSG_CMD_REQ    = 0x00fa;
    public static final int MSG_STATUS_RSP = 0x00fb;
    public static final int MSG_CMD_RSP    = 0x0093;

    // transitCmd values
    public static final String CMD_START_CLEAN  = "100";
    public static final String CMD_GET_STATUS   = "102";
    public static final String CMD_GO_HOME      = "104";
    public static final String CMD_GET_MAP      = "131";
    public static final String CMD_GET_SCHEDULE = "400";

    // workState values
    public static final int STATE_STANDBY   = 0;
    public static final int STATE_CLEANING  = 1;
    public static final int STATE_CLEANING2 = 2;
    public static final int STATE_RETURNING = 9;
    public static final int STATE_CHARGING  = 10;

    // Build a complete frame: HEADER(20) + JSON
    public static byte[] buildFrame(int msgType, String json, int seqSend, int seqRecv) {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int totalLen = 20 + jsonBytes.length;

        ByteBuffer buf = ByteBuffer.allocate(totalLen);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(totalLen);           // [0:4]  total frame length
        buf.putShort((short) msgType);  // [4:6]  message type
        buf.putShort((short) 0x00c8);  // [6:8]  fixed flags
        buf.putInt(0);                  // [8:12] reserved
        buf.putInt(seqSend);            // [12:16] seq_send
        buf.putInt(seqRecv);            // [16:20] seq_recv
        buf.put(jsonBytes);
        return buf.array();
    }

    // Parse frame header — returns [msgType, jsonLen, totalLen] or null if incomplete
    public static int[] parseHeader(byte[] data, int offset) {
        if (data.length - offset < 20) return null;
        ByteBuffer buf = ByteBuffer.wrap(data, offset, data.length - offset);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int totalLen = buf.getInt();
        int msgType  = buf.getShort() & 0xFFFF;
        int jsonLen  = totalLen - 20;
        return new int[]{msgType, jsonLen, totalLen};
    }

    // Extract JSON from a complete frame
    public static String extractJson(byte[] frame) {
        if (frame.length <= 20) return null;
        int jsonLen = frame.length - 20;
        return new String(frame, 20, jsonLen, StandardCharsets.UTF_8);
    }

    // MD5 hex (for password hashing)
    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
