package com.mgallo17.conga;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Unit tests for CongaProtocol — confirmed format from real pcap.
 *
 * Frame = HEADER(20) + JSON (NO footer)
 * Header[0:4] = total frame length (20 + json_len)
 */
public class CongaProtocolTest {

    @Test
    public void testFrameTotalLength() {
        String json = "{\"test\":1}";
        byte[] frame = CongaProtocol.buildFrame(CongaProtocol.MSG_LOGIN_REQ, json, 0, 0);
        // total = 20 (header) + json.length
        int expected = 20 + json.getBytes().length;
        assertEquals(expected, frame.length);
    }

    @Test
    public void testHeaderLengthField() {
        String json = "{\"test\":1}";
        byte[] frame = CongaProtocol.buildFrame(CongaProtocol.MSG_CMD_REQ, json, 0, 0);
        // bytes [0:4] = total frame length
        ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        int totalLen = buf.getInt();
        assertEquals(frame.length, totalLen);
    }

    @Test
    public void testHeaderMsgType() {
        String json = "{}";
        byte[] frame = CongaProtocol.buildFrame(CongaProtocol.MSG_LOGIN_REQ, json, 0, 0);
        ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        buf.getInt(); // skip totalLen
        int msgType = buf.getShort() & 0xFFFF;
        assertEquals(CongaProtocol.MSG_LOGIN_REQ, msgType);
    }

    @Test
    public void testHeaderFlags() {
        byte[] frame = CongaProtocol.buildFrame(CongaProtocol.MSG_CMD_REQ, "{}", 0, 0);
        ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        buf.getInt();           // totalLen
        buf.getShort();         // msgType
        int flags = buf.getShort() & 0xFFFF;
        assertEquals(0x00c8, flags);
    }

    @Test
    public void testSeqFields() {
        byte[] frame = CongaProtocol.buildFrame(CongaProtocol.MSG_CMD_REQ, "{}", 42, 99);
        ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        buf.getInt();   // totalLen
        buf.getShort(); // msgType
        buf.getShort(); // flags
        buf.getInt();   // reserved
        int seqSend = buf.getInt();
        int seqRecv = buf.getInt();
        assertEquals(42, seqSend);
        assertEquals(99, seqRecv);
    }

    @Test
    public void testExtractJson() {
        String json = "{\"cmd\":0,\"value\":\"test\"}";
        byte[] frame = CongaProtocol.buildFrame(CongaProtocol.MSG_CMD_REQ, json, 5, 3);
        String extracted = CongaProtocol.extractJson(frame);
        assertEquals(json, extracted);
    }

    @Test
    public void testParseHeader() {
        String json = "{\"test\":true}";
        byte[] frame = CongaProtocol.buildFrame(CongaProtocol.MSG_STATUS_RSP, json, 1, 0);
        int[] parsed = CongaProtocol.parseHeader(frame, 0);
        assertNotNull(parsed);
        assertEquals(CongaProtocol.MSG_STATUS_RSP, parsed[0]); // msgType
        assertEquals(json.getBytes().length,        parsed[1]); // jsonLen
        assertEquals(frame.length,                  parsed[2]); // totalLen
    }

    @Test
    public void testMd5() {
        assertEquals("5f4dcc3b5aa765d61d8327deb882cf99", CongaProtocol.md5("password"));
    }
}
