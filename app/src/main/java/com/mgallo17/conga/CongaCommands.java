package com.mgallo17.conga;

public final class CongaCommands {

    // Server — real values from pcap capture
    public static final String SERVER_HOST = "hc-s-eu.hctrobot.com";
    public static final int    SERVER_PORT  = 20008;

    // OpCodes (little-endian on wire)
    public static final int OPCODE_PING         = 2005;
    public static final int OPCODE_PING_RESP    = 2006;
    public static final int OPCODE_LOGIN        = 3001;
    public static final int OPCODE_LOGIN_RESP   = 3002;
    public static final int OPCODE_LOGIN_ACK    = 3003;
    public static final int OPCODE_COMMAND      = 3104;
    public static final int OPCODE_STATUS       = 3105;
    public static final int OPCODE_SCHEDULE_SET = 3200;
    public static final int OPCODE_SCHEDULE_GET = 3201;

    // Cleaning commands
    public static final int CMD_START    = 100;
    public static final int CMD_AUTO     = 101;
    public static final int CMD_SPIRAL   = 102;
    public static final int CMD_STOP     = 103;
    public static final int CMD_HOME     = 104;
    public static final int CMD_EDGE     = 105;
    public static final int CMD_SPOT     = 106;
    public static final int CMD_PAUSE    = 110;

    // Robot states (StatusResponse.state)
    public static final int STATE_IDLE      = 0;
    public static final int STATE_CLEANING  = 1;
    public static final int STATE_RETURNING = 2;
    public static final int STATE_CHARGING  = 3;
    public static final int STATE_ERROR     = 4;

    // Intent actions for LocalBroadcast
    public static final String ACTION_STATUS_UPDATE    = "com.mgallo17.conga.STATUS_UPDATE";
    public static final String ACTION_CONNECTED        = "com.mgallo17.conga.CONNECTED";
    public static final String ACTION_DISCONNECTED     = "com.mgallo17.conga.DISCONNECTED";
    public static final String ACTION_LOGIN_SUCCESS    = "com.mgallo17.conga.LOGIN_SUCCESS";
    public static final String ACTION_LOGIN_FAILED     = "com.mgallo17.conga.LOGIN_FAILED";
    public static final String EXTRA_STATE             = "state";
    public static final String EXTRA_BATTERY           = "battery";
    public static final String EXTRA_POS_X             = "pos_x";
    public static final String EXTRA_POS_Y             = "pos_y";
    public static final String EXTRA_CLEAN_TIME        = "clean_time";
    public static final String EXTRA_CLEAN_AREA        = "clean_area";
    public static final String EXTRA_LOGIN_MSG         = "login_msg";

    // SharedPreferences keys
    public static final String PREFS_NAME      = "conga_prefs";
    public static final String PREF_EMAIL      = "email";
    public static final String PREF_PASSWORD   = "password";
    public static final String PREF_SESSION_ID = "session_id";
    public static final String PREF_USER_ID    = "user_id";
    public static final String PREF_DEVICE_ID  = "device_id";
    public static final String PREF_LOCAL_MODE = "local_mode";
    public static final String PREF_ROBOT_IP   = "robot_ip";
    public static final String PREF_ROBOT_PORT = "robot_port";

    private CongaCommands() {}
}
