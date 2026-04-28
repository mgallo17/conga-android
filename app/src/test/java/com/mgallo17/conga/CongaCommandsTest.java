package com.mgallo17.conga;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for CongaCommands constants.
 */
public class CongaCommandsTest {

    @Test
    public void testServerConfig() {
        assertEquals("eu.inteli.cecotec.com", CongaCommands.SERVER_HOST);
        assertEquals(4006, CongaCommands.SERVER_PORT);
    }

    @Test
    public void testOpcodeValues() {
        assertEquals(2005, CongaCommands.OPCODE_PING);
        assertEquals(2006, CongaCommands.OPCODE_PING_RESP);
        assertEquals(3001, CongaCommands.OPCODE_LOGIN);
        assertEquals(3002, CongaCommands.OPCODE_LOGIN_RESP);
        assertEquals(3104, CongaCommands.OPCODE_COMMAND);
    }

    @Test
    public void testCommandValues() {
        assertEquals(100, CongaCommands.CMD_START);
        assertEquals(101, CongaCommands.CMD_AUTO);
        assertEquals(102, CongaCommands.CMD_SPIRAL);
        assertEquals(103, CongaCommands.CMD_STOP);
        assertEquals(104, CongaCommands.CMD_HOME);
        assertEquals(105, CongaCommands.CMD_EDGE);
        assertEquals(106, CongaCommands.CMD_SPOT);
    }

    @Test
    public void testStateValues() {
        assertEquals(0, CongaCommands.STATE_IDLE);
        assertEquals(1, CongaCommands.STATE_CLEANING);
        assertEquals(2, CongaCommands.STATE_RETURNING);
        assertEquals(3, CongaCommands.STATE_CHARGING);
        assertEquals(4, CongaCommands.STATE_ERROR);
    }
}
