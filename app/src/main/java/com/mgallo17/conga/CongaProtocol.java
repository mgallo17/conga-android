package com.mgallo17.conga;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protocol framing — extracted from real pcap capture.
 *
 * Frame layout:
 *   [HEADER 20 bytes][JSON payload N bytes][FOOTER 28 bytes]
 *
 * Header (little-endian):
 *   [0:4]  payload_len = json_len + 28
 *   [4:6]  msg_type
 *   [6:8]  0x00c8 (fixed)
 *   [8:12] flags (0)
 *   [12:16] seq_send
 *   [16:20] seq_recv
 *
 * Footer (28 bytes, fixed):
 *   01 07 20 21 00 00 28 f5 "Conga 1490 1590" 00 00 00 00 00
 */
public class CongaProtocol {

    // Message types
    public static final int MSG_LOGIN_REQ  = 0x0010;
    public static final int MSG_LOGIN_RSP  = 0x0011;
    public static final int MSG_CMD_REQ    = 0x00fa;
    public static final int MSG_STATUS_RSP = 0x00fb;
    public static final int MSG_CMD_RSP    = 0x0093;

    // transitCmd values
    public static final String CMD_START_CLEAN = "100";
    public static final String CMD_GET_STATUS  = "102";
    public static final String CMD_GO_HOME     = "104";
    public static final String CMD_GET_MAP     = "131";
    public static final String CMD_GET_SCHEDULE = "400";

    // workState values
    public static final int STATE_STANDBY  = 0;
    public static final int STATE_CLEANING = 1;
    public static final int STATE_CLEANING2 = 2;
    public static final int STATE_RETURNING = 9;
    public static final int STATE_CHARGING  = 10;

    private static final byte[] FOOTER = new byte[]{
        0x01, 0x07, 0x20, 0x21, 0x00, 0x00, 0x28, (byte)0xf5,
        'C','o','n','g','a',' ','1','4','9','0',' ','1','5','9','0',
        0x00, 0x00, 0x00, 0x00, 0x00
    };

    // Build a complete frame
    public static byte[] buildFrame(int msgType, String json, int seqSend, int seqRecv) {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int payloadLen = jsonBytes.length + FOOTER.length;

        ByteBuffer buf = ByteBuffer.allocate(20 + jsonBytes.length + FOOTER.length);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(payloadLen);
        buf.putShort((short) msgType);
        buf.putShort((short) 0x00c8);
        buf.putInt(0); // flags
        buf.putInt(seqSend);
        buf.putInt(seqRecv);
        buf.put(jsonBytes);
        buf.put(FOOTER);
        return buf.array();
    }

    // Parse frame header — returns json start offset and msg type, or null if incomplete
    public static int[] parseHeader(byte[] data, int offset) {
        if (data.length - offset < 20) return null;
        ByteBuffer buf = ByteBuffer.wrap(data, offset, data.length - offset);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int payloadLen = buf.getInt();   // json + footer
        int msgType    = buf.getShort() & 0xFFFF;
        // full frame = 20 (header) + payloadLen
        return new int[]{msgType, payloadLen, 20 + payloadLen};
    }

    // Extract JSON from a complete frame (after header)
    public static String extractJson(byte[] frame) {
        if (frame.length < 20 + FOOTER.length) return null;
        int jsonLen = frame.length - 20 - FOOTER.length;
        if (jsonLen <= 0) return null;
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
