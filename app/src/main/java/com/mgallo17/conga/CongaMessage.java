package com.mgallo17.conga;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Manual protobuf-compatible serialisation for Conga protocol messages.
 * Avoids the protobuf gradle plugin complexity while keeping wire format compatible.
 *
 * Protobuf wire types used:
 *   0 = varint, 2 = length-delimited (string/bytes)
 * Field tag = (field_number << 3) | wire_type
 */
public class CongaMessage {

    // ---------------------------------------------------------------
    // Login Request  (fields: 1=username, 2=password, 3=device_id, 4=app_version)
    // ---------------------------------------------------------------
    public static byte[] loginRequest(String username, String passwordMd5, String deviceId) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeString(out, 1, username);
            writeString(out, 2, passwordMd5);
            writeString(out, 3, deviceId);
            writeVarint(out, 4, 1); // app_version = 1
            return out.toByteArray();
        } catch (IOException e) { return new byte[0]; }
    }

    // ---------------------------------------------------------------
    // Command Request  (fields: 1=command, 2=param1, 3=param2)
    // ---------------------------------------------------------------
    public static byte[] commandRequest(int command) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeVarint(out, 1, command);
            return out.toByteArray();
        } catch (IOException e) { return new byte[0]; }
    }

    // ---------------------------------------------------------------
    // Schedule Request  (fields: 1=days_mask, 2=hour, 3=minute, 4=enabled)
    // ---------------------------------------------------------------
    public static byte[] scheduleRequest(int daysMask, int hour, int minute) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // Wrap in repeated ScheduleEntry (field 1, length-delimited)
            ByteArrayOutputStream entry = new ByteArrayOutputStream();
            writeVarint(entry, 1, daysMask);
            writeVarint(entry, 2, hour);
            writeVarint(entry, 3, minute);
            writeVarint(entry, 4, 1); // enabled = true
            byte[] entryBytes = entry.toByteArray();
            // field 1, wire type 2
            out.write(0x0A);
            writeRawVarint(out, entryBytes.length);
            out.write(entryBytes);
            return out.toByteArray();
        } catch (IOException e) { return new byte[0]; }
    }

    // ---------------------------------------------------------------
    // Parse Login Response  (fields: 1=result, 2=user_id, 3=token, 4=msg)
    // ---------------------------------------------------------------
    public static class LoginResponse {
        public int    result;
        public int    userId;
        public String token = "";
        public String msg   = "";
    }

    public static LoginResponse parseLoginResponse(byte[] data) {
        LoginResponse resp = new LoginResponse();
        if (data == null) return resp;
        int pos = 0;
        while (pos < data.length) {
            int tag      = data[pos++] & 0xFF;
            int field    = tag >> 3;
            int wireType = tag & 0x07;
            switch (wireType) {
                case 0: { // varint
                    long val = readVarint(data, pos);
                    pos += varintSize(data, pos);
                    if (field == 1) resp.result = (int) val;
                    if (field == 2) resp.userId = (int) val;
                    break;
                }
                case 2: { // length-delimited
                    int len = (int) readVarint(data, pos);
                    pos += varintSize(data, pos);
                    String s = new String(data, pos, len, java.nio.charset.StandardCharsets.UTF_8);
                    pos += len;
                    if (field == 3) resp.token = s;
                    if (field == 4) resp.msg   = s;
                    break;
                }
                default: return resp; // unknown wire type — stop
            }
        }
        return resp;
    }

    // ---------------------------------------------------------------
    // Parse Status Response  (fields: 1=state, 2=battery, 3=pos_x f, 4=pos_y f, 5=clean_time, 6=clean_area)
    // ---------------------------------------------------------------
    public static class StatusResponse {
        public int   state;
        public int   battery;
        public float posX;
        public float posY;
        public int   cleanTime;
        public int   cleanArea;
        public int   errorCode;
    }

    public static StatusResponse parseStatusResponse(byte[] data) {
        StatusResponse resp = new StatusResponse();
        if (data == null) return resp;
        int pos = 0;
        while (pos < data.length) {
            if (pos >= data.length) break;
            int tag      = data[pos++] & 0xFF;
            int field    = tag >> 3;
            int wireType = tag & 0x07;
            switch (wireType) {
                case 0: {
                    long val = readVarint(data, pos);
                    pos += varintSize(data, pos);
                    if (field == 1) resp.state     = (int) val;
                    if (field == 2) resp.battery   = (int) val;
                    if (field == 5) resp.cleanTime = (int) val;
                    if (field == 6) resp.cleanArea = (int) val;
                    if (field == 7) resp.errorCode = (int) val;
                    break;
                }
                case 5: { // 32-bit fixed (float)
                    if (pos + 4 > data.length) return resp;
                    int bits = (data[pos] & 0xFF) | ((data[pos+1] & 0xFF) << 8)
                             | ((data[pos+2] & 0xFF) << 16) | ((data[pos+3] & 0xFF) << 24);
                    float val = Float.intBitsToFloat(bits);
                    pos += 4;
                    if (field == 3) resp.posX = val;
                    if (field == 4) resp.posY = val;
                    break;
                }
                default: return resp;
            }
        }
        return resp;
    }

    // ---------------------------------------------------------------
    // Protobuf encoding helpers
    // ---------------------------------------------------------------

    private static void writeString(ByteArrayOutputStream out, int field, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.write((field << 3) | 2); // wire type 2
        writeRawVarint(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarint(ByteArrayOutputStream out, int field, long value) throws IOException {
        out.write((field << 3) | 0); // wire type 0
        writeRawVarint(out, value);
    }

    private static void writeRawVarint(ByteArrayOutputStream out, long value) throws IOException {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }

    private static long readVarint(byte[] data, int pos) {
        long result = 0;
        int shift = 0;
        while (pos < data.length) {
            byte b = data[pos++];
            result |= (long)(b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return result;
    }

    private static int varintSize(byte[] data, int pos) {
        int size = 0;
        while (pos + size < data.length) {
            byte b = data[pos + size++];
            if ((b & 0x80) == 0) break;
        }
        return size;
    }

    private CongaMessage() {}
}
