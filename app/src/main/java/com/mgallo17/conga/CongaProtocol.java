package com.mgallo17.conga;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds and parses the Cecotec Conga binary protocol frames.
 *
 * Frame structure (all fields little-endian):
 * [4B length][2B ctype][2B flow][4B userId][4B deviceId][6B sequence][2B opcode][N bytes payload]
 *
 * References:
 *  - https://github.com/adrigzr/badconga
 *  - https://xumeiquer.github.io/LibreConga/research/protocol/
 */
public class CongaProtocol {

    private static final int HEADER_SIZE = 24; // bytes before payload
    private static final int CTYPE       = 0x0002;
    private static final int FLOW        = 0x0000;

    private final AtomicInteger sequenceCounter = new AtomicInteger(0);

    // ---------------------------------------------------------------
    // Frame building
    // ---------------------------------------------------------------

    /**
     * Build a complete protocol frame.
     *
     * @param opcode   operation code (see CongaCommands.OPCODE_*)
     * @param userId   authenticated user id (0 before login)
     * @param deviceId robot device id (0 before pairing)
     * @param payload  serialised protobuf payload (may be empty)
     * @return complete binary frame ready to send over TCP
     */
    public byte[] buildFrame(int opcode, int userId, int deviceId, byte[] payload) {
        int totalLength = HEADER_SIZE + (payload != null ? payload.length : 0);
        ByteBuffer buf = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN);

        // Length (total frame size, little-endian)
        buf.putInt(totalLength);
        // ctype
        buf.putShort((short) CTYPE);
        // flow
        buf.putShort((short) FLOW);
        // userId
        buf.putInt(userId);
        // deviceId
        buf.putInt(deviceId);
        // sequence (6 bytes — use 4B counter + 2B zero pad)
        int seq = sequenceCounter.incrementAndGet();
        buf.putInt(seq);
        buf.putShort((short) 0);
        // opcode
        buf.putShort((short) opcode);
        // payload
        if (payload != null && payload.length > 0) {
            buf.put(payload);
        }

        return buf.array();
    }

    /**
     * Build a ping frame (opcode 2005, no payload).
     */
    public byte[] buildPing(int userId, int deviceId) {
        return buildFrame(CongaCommands.OPCODE_PING, userId, deviceId, null);
    }

    // ---------------------------------------------------------------
    // Frame parsing
    // ---------------------------------------------------------------

    public static class ParsedFrame {
        public final int    totalLength;
        public final int    ctype;
        public final int    flow;
        public final int    userId;
        public final int    deviceId;
        public final int    opcode;
        public final byte[] payload;

        ParsedFrame(int totalLength, int ctype, int flow,
                    int userId, int deviceId, int opcode, byte[] payload) {
            this.totalLength = totalLength;
            this.ctype       = ctype;
            this.flow        = flow;
            this.userId      = userId;
            this.deviceId    = deviceId;
            this.opcode      = opcode;
            this.payload     = payload;
        }
    }

    /**
     * Parse a received frame from a byte array.
     *
     * @param data raw bytes received from the server (must be at least HEADER_SIZE long)
     * @return parsed frame, or null if data is too short
     */
    public ParsedFrame parseFrame(byte[] data) {
        if (data == null || data.length < HEADER_SIZE) return null;

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        int totalLength = buf.getInt();
        int ctype       = buf.getShort() & 0xFFFF;
        int flow        = buf.getShort() & 0xFFFF;
        int userId      = buf.getInt();
        int deviceId    = buf.getInt();
        // skip 6-byte sequence
        buf.getInt();
        buf.getShort();
        int opcode = buf.getShort() & 0xFFFF;

        int payloadLen = totalLength - HEADER_SIZE;
        byte[] payload = new byte[Math.max(0, payloadLen)];
        if (payloadLen > 0 && buf.remaining() >= payloadLen) {
            buf.get(payload);
        }

        return new ParsedFrame(totalLength, ctype, flow, userId, deviceId, opcode, payload);
    }

    /**
     * Read the frame length from the first 4 bytes (little-endian).
     * Used to know how many bytes to read before parsing the full frame.
     */
    public static int readFrameLength(byte[] header4) {
        return ByteBuffer.wrap(header4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    // ---------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------

    /** Compute MD5 hex string of a password for login payload. */
    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 failed", e);
        }
    }
}
