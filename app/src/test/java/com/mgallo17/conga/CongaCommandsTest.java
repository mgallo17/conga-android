package com.mgallo17.conga;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for CongaCommands constants.
 */
public class CongaCommandsTest {

    @Test
    public void testServerConfig() {
        // Real server extracted from pcap
        assertEquals("hc-s-eu.hctrobot.com", CongaCommands.SERVER_HOST);
        assertEquals(20008, CongaCommands.SERVER_PORT);
    }

    @Test
    public void testPrefsKeys() {
        assertNotNull(CongaCommands.PREFS_NAME);
        assertNotNull(CongaCommands.PREF_EMAIL);
        assertNotNull(CongaCommands.PREF_PASSWORD);
        assertNotNull(CongaCommands.PREF_SESSION_ID);
        assertNotNull(CongaCommands.PREF_USER_ID);
    }

    @Test
    public void testActionConstants() {
        assertNotNull(CongaCommands.ACTION_STATUS_UPDATE);
        assertNotNull(CongaCommands.ACTION_CONNECTED);
        assertNotNull(CongaCommands.ACTION_DISCONNECTED);
        assertNotNull(CongaCommands.ACTION_LOGIN_SUCCESS);
        assertNotNull(CongaCommands.ACTION_LOGIN_FAILED);
    }

    @Test
    public void testWorkStateValues() {
        // workState values from pcap
        assertEquals(0,  CongaProtocol.STATE_STANDBY);
        assertEquals(1,  CongaProtocol.STATE_CLEANING);
        assertEquals(9,  CongaProtocol.STATE_RETURNING);
        assertEquals(10, CongaProtocol.STATE_CHARGING);
    }
}
