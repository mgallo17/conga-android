package com.mgallo17.conga;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for CongaMessage serialisation / deserialisation.
 */
public class CongaMessageTest {

    @Test
    public void testLoginRequestNotEmpty() {
        byte[] payload = CongaMessage.loginRequest("user@example.com", "abc123md5hash__", "12345");
        assertNotNull(payload);
        assertTrue(payload.length > 0);
    }

    @Test
    public void testCommandRequestNotEmpty() {
        byte[] payload = CongaMessage.commandRequest(CongaCommands.CMD_START);
        assertNotNull(payload);
        assertTrue(payload.length > 0);
    }

    @Test
    public void testScheduleRequestNotEmpty() {
        byte[] payload = CongaMessage.scheduleRequest(0b0011111, 8, 30); // Mon-Fri + Sat + Sun, 08:30
        assertNotNull(payload);
        assertTrue(payload.length > 0);
    }

    @Test
    public void testParseLoginResponseSuccess() {
        // Encode a simple login response: result=0 (field1), userId=42 (field2)
        // field1 varint: tag=0x08 (field1, wiretype0), value=0x00
        // field2 varint: tag=0x10 (field2, wiretype0), value=0x2A (42)
        byte[] data = {0x08, 0x00, 0x10, 0x2A};
        CongaMessage.LoginResponse resp = CongaMessage.parseLoginResponse(data);
        assertEquals(0, resp.result);
        assertEquals(42, resp.userId);
    }

    @Test
    public void testParseLoginResponseFailure() {
        // result=1 (field1), msg="bad" (field4)
        byte[] msgBytes = "bad".getBytes();
        byte[] data = new byte[2 + 2 + msgBytes.length];
        data[0] = 0x08; data[1] = 0x01; // field1 = 1
        data[2] = 0x22; data[3] = (byte) msgBytes.length; // field4, length
        System.arraycopy(msgBytes, 0, data, 4, msgBytes.length);
        CongaMessage.LoginResponse resp = CongaMessage.parseLoginResponse(data);
        assertEquals(1, resp.result);
        assertEquals("bad", resp.msg);
    }

    @Test
    public void testParseStatusResponse() {
        // state=1 (field1), battery=75 (field2)
        byte[] data = {0x08, 0x01, 0x10, 0x4B};
        CongaMessage.StatusResponse resp = CongaMessage.parseStatusResponse(data);
        assertEquals(1, resp.state);
        assertEquals(75, resp.battery);
    }

    @Test
    public void testParseNullSafe() {
        CongaMessage.LoginResponse lr = CongaMessage.parseLoginResponse(null);
        assertNotNull(lr);

        CongaMessage.StatusResponse sr = CongaMessage.parseStatusResponse(null);
        assertNotNull(sr);
    }

    @Test
    public void testParseEmptyPayload() {
        CongaMessage.LoginResponse lr = CongaMessage.parseLoginResponse(new byte[0]);
        assertEquals(0, lr.result);
        assertEquals(0, lr.userId);
    }
}
