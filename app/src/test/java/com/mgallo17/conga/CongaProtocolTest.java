package com.mgallo17.conga;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Unit tests for CongaProtocol — real frame format from pcap.
 */
public class CongaProtocolTest {

    @Test
    public void testFrameHasCorrectTotalLength() {
        String json = "{\"test\":1}";
        byte[] frame = CongaProtocol.buildFrame(CongaProtocol.MSG_LOGIN_REQ, json, 0, 0);
        // total = 20 (header) + json.length + 28 (footer)
        int expected = 20 + json.getBytes().length + 28;
        assertEquals(expected, frame.length);
    }

    @Test
    public void testHeaderPayloadLenField() {
        String json = "{\"test\":1}";
        byte[] frame = CongaProtocol.buildFrame(CongaProtocol.MSG_CMD_REQ, json, 0, 0);
        // bytes [0:4] = json.length + 28 (footer length)
        ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        int payloadLen = buf.getInt();
        assertEquals(json.getBytes().length + 28, payloadLen);
    }

    @Test
    public void testHeaderMsgType() {
        String json = "{}";
        byte[] frame = CongaProtocol.buildFrame(CongaProtocol.MSG_LOGIN_REQ, json, 0, 0);
        ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        buf.getInt(); // skip payloadLen
        int msgType = buf.getShort() & 0xFFFF;
        assertEquals(CongaProtocol.MSG_LOGIN_REQ, msgType);
    }

    @Test
    public void testFooterContent() {
        String json = "{}";
        byte[] frame = CongaProtocol.buildFrame(CongaProtocol.MSG_CMD_REQ, json, 0, 0);
        // Footer starts at offset 20 + json.length
        int footerOffset = 20 + json.getBytes().length;
        // First byte of footer = 0x01
        assertEquals(0x01, frame[footerOffset] & 0xFF);
        // "Conga 1490 1590" starts at footerOffset + 8
        String model = new String(frame, footerOffset + 8, 15);
        assertEquals("Conga 1490 1590", model);
    }

    @Test
    public void testExtractJson() {
        String json = "{\"cmd\":0,\"value\":\"test\"}";
        byte[] frame = CongaProtocol.buildFrame(CongaProtocol.MSG_CMD_REQ, json, 5, 3);
        String extracted = CongaProtocol.extractJson(frame);
        assertEquals(json, extracted);
    }

    @Test
    public void testSeqFieldsInHeader() {
        byte[] frame = CongaProtocol.buildFrame(CongaProtocol.MSG_CMD_REQ, "{}", 42, 99);
        ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        buf.getInt();            // payloadLen
        buf.getShort();          // msgType
        buf.getShort();          // flags 0xc8
        buf.getInt();            // field8
        int seqSend = buf.getInt();
        int seqRecv = buf.getInt();
        assertEquals(42, seqSend);
        assertEquals(99, seqRecv);
    }

    @Test
    public void testMd5() {
        // MD5("password") = "5f4dcc3b5aa765d61d8327deb882cf99"
        assertEquals("5f4dcc3b5aa765d61d8327deb882cf99", CongaProtocol.md5("password"));
    }

    @Test
    public void testParseHeader() {
        String json = "{\"test\":true}";
        byte[] frame = CongaProtocol.buildFrame(CongaProtocol.MSG_STATUS_RSP, json, 1, 0);
        int[] parsed = CongaProtocol.parseHeader(frame, 0);
        assertNotNull(parsed);
        assertEquals(CongaProtocol.MSG_STATUS_RSP, parsed[0]); // msgType
        assertEquals(json.getBytes().length + 28, parsed[1]);  // payloadLen
        assertEquals(frame.length, parsed[2]);                  // total frame length
    }
}
