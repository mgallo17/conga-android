package com.mgallo17.conga;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for CongaProtocol — frame building and parsing.
 */
public class CongaProtocolTest {

    private final CongaProtocol protocol = new CongaProtocol();

    @Test
    public void testPingFrameLength() {
        byte[] frame = protocol.buildPing(0, 0);
        // Ping has no payload → frame must be exactly 24 bytes (header only)
        assertEquals(24, frame.length);
    }

    @Test
    public void testFrameLengthFieldIsLittleEndian() {
        byte[] frame = protocol.buildPing(0, 0);
        // First 4 bytes = length in little-endian = 24 = 0x18 0x00 0x00 0x00
        assertEquals(0x18, frame[0] & 0xFF);
        assertEquals(0x00, frame[1] & 0xFF);
        assertEquals(0x00, frame[2] & 0xFF);
        assertEquals(0x00, frame[3] & 0xFF);
    }

    @Test
    public void testPingOpcode() {
        byte[] frame = protocol.buildPing(0, 0);
        // Opcode at bytes 22-23 (little-endian) = 2005 = 0xD5 0x07
        assertEquals(0xD5, frame[22] & 0xFF);
        assertEquals(0x07, frame[23] & 0xFF);
    }

    @Test
    public void testParseRoundTrip() {
        byte[] payload = {0x01, 0x02, 0x03};
        byte[] frame   = protocol.buildFrame(CongaCommands.OPCODE_COMMAND, 42, 99, payload);

        CongaProtocol.ParsedFrame parsed = protocol.parseFrame(frame);

        assertNotNull(parsed);
        assertEquals(CongaCommands.OPCODE_COMMAND, parsed.opcode);
        assertEquals(42, parsed.userId);
        assertEquals(99, parsed.deviceId);
        assertArrayEquals(payload, parsed.payload);
    }

    @Test
    public void testReadFrameLength() {
        byte[] header = {0x18, 0x00, 0x00, 0x00}; // 24 in little-endian
        assertEquals(24, CongaProtocol.readFrameLength(header));
    }

    @Test
    public void testMd5() {
        // MD5("password") = "5f4dcc3b5aa765d61d8327deb882cf99"
        String hash = CongaProtocol.md5("password");
        assertEquals("5f4dcc3b5aa765d61d8327deb882cf99", hash);
    }

    @Test
    public void testSequenceIncrements() {
        byte[] f1 = protocol.buildPing(0, 0);
        byte[] f2 = protocol.buildPing(0, 0);
        // Sequence field is at bytes 16-21; first 4 bytes are the counter
        int seq1 = (f1[16] & 0xFF) | ((f1[17] & 0xFF) << 8)
                 | ((f1[18] & 0xFF) << 16) | ((f1[19] & 0xFF) << 24);
        int seq2 = (f2[16] & 0xFF) | ((f2[17] & 0xFF) << 8)
                 | ((f2[18] & 0xFF) << 16) | ((f2[19] & 0xFF) << 24);
        assertEquals(1, seq2 - seq1);
    }
}
