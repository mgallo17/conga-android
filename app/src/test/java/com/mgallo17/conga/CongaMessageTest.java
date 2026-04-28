package com.mgallo17.conga;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for CongaMessage — keeping stub tests that compile cleanly.
 * The real protocol now uses JSON frames (see CongaProtocol/CongaClient).
 */
public class CongaMessageTest {

    @Test
    public void testMd5KnownValue() {
        // Verify MD5 used for password hashing
        assertEquals("5f4dcc3b5aa765d61d8327deb882cf99", CongaProtocol.md5("password"));
    }

    @Test
    public void testMd5EmptyString() {
        // MD5("") = d41d8cd98f00b204e9800998ecf8427e
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", CongaProtocol.md5(""));
    }

    @Test
    public void testTransitCmdConstants() {
        // Verify transitCmd constants match real pcap values
        assertEquals("100", CongaProtocol.CMD_START_CLEAN);
        assertEquals("102", CongaProtocol.CMD_GET_STATUS);
        assertEquals("104", CongaProtocol.CMD_GO_HOME);
        assertEquals("131", CongaProtocol.CMD_GET_MAP);
        assertEquals("400", CongaProtocol.CMD_GET_SCHEDULE);
    }

    @Test
    public void testMsgTypeConstants() {
        assertEquals(0x0010, CongaProtocol.MSG_LOGIN_REQ);
        assertEquals(0x0011, CongaProtocol.MSG_LOGIN_RSP);
        assertEquals(0x00fa, CongaProtocol.MSG_CMD_REQ);
        assertEquals(0x00fb, CongaProtocol.MSG_STATUS_RSP);
    }
}
