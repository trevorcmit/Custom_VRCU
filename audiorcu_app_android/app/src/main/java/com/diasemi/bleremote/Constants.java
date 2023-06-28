/*
 *******************************************************************************
 *
 * Copyright (C) 2017-2018 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote;

public class Constants {

    // Audio inband control
    public static final int AUDIO_CONTROL_ESCAPE = 0x7f;
    public static final int AUDIO_CONTROL_OP_MASK = 0xf0;
    public static final int AUDIO_CONTROL_OP_SHIFT = 4;
    public static final int AUDIO_CONTROL_OP_DATA_MASK = 0x0f;
    // Audio inband commands
    public static final int AUDIO_CONTROL_OP_RESET = 0;
    public static final int AUDIO_CONTROL_OP_SETMODE = 1;

    // Control input HID report
    // Type
    public static final int CONTROL_TYPE_OFFSET = 1;
    public static final int CONTROL_TYPE_STREAM = 0;
    public static final int CONTROL_TYPE_CONFIG = 1;
    public static final int CONTROL_TYPE_KEY = 2;
    public static final int CONTROL_TYPE_STREAM_ERROR = 3;
    public static final int CONTROL_TYPE_AUDIO_MODE = 4;
    public static final int CONTROL_TYPE_CONN_PARAMS = 5;
    public static final int CONTROL_TYPE_MTU = 6;
    // Stream/Config type
    public static final int CONTROL_STREAM_ENABLE_OFFSET = 0;
    public static final int CONTROL_AUDIO_REPORT_OFFSET = 3;
    public static final int CONTROL_FEATURES_OFFSET = 4;
    public static final int CONTROL_AUDIO_MODE_OFFSET = 5;
    public static final int CONTROL_KEY_LAYOUT_OFFSET = 6;
    public static final int CONTROL_CONFIG_MTU_OFFSET = 7;
    public static final int CONTROL_CONFIG_MTU_SIZE = 4;
    public static final int CONTROL_CONFIG_CONN_PARAMS_OFFSET = 11;
    public static final int CONTROL_CONFIG_CONN_PARAMS_SIZE = 6;
    // Features (stream/config)
    public static final int CONTROL_FEATURES_INBAND = 0x01;
    public static final int CONTROL_FEATURES_SETMODE = 0x02;
    public static final int CONTROL_FEATURES_NOT_PACKET_BASED = 0x04;
    public static final int CONTROL_FEATURES_COMMAND_SUPPORT = 0x08;
    // Audio mode (stream/config)
    public static final int CONTROL_AUDIO_MODE_MASK = 0xF0;
    public static final int CONTROL_AUDIO_MODE_RATE_MASK = 0x0F;
    public static final int CONTROL_AUDIO_MODE_LEGACY = 0x00;
    public static final int CONTROL_AUDIO_MODE_FIXED = 0x10;
    public static final int CONTROL_AUDIO_MODE_AUTO = 0x20;
    // Key report
    public static final int KEY_REPORT_SIZE_OFFSET = 2;
    public static final int KEY_REPORT_LAYOUT_OFFSET = 12;
    // Audio mode report
    public static final int AUDIO_MODE_REPORT_MODE_OFFSET = 2;
    // Connection parameters report
    public static final int CONN_PARAMS_REPORT_DATA_OFFSET = 3;
    public static final int CONN_PARAMS_REPORT_DATA_SIZE = 6;
    // MTU report
    public static final int MTU_REPORT_DATA_OFFSET = 3;
    public static final int MTU_REPORT_DATA_SIZE = 4;

    // Decoder modes
    public static final int AUDIO_MODE_64KBPS = 0;
    public static final int AUDIO_MODE_48KBPS = 1;
    public static final int AUDIO_MODE_32KBPS = 2;
    public static final int AUDIO_MODE_24KBPS = 3;
    public static final int AUDIO_MODE_AUTOMATIC = 4;
    public static final int AUDIO_MODE_PRIORITY_FLAG = 0x10;

    // Control output commands
    public static final int CONTROL_COMMAND_STREAM_OFFSET = 0; // always 0 for commands
    public static final int CONTROL_COMMAND_OFFSET = 1;
    public static final int CONTROL_COMMAND_SIZE_OFFSET = 2;
    public static final int CONTROL_COMMAND_DATA_OFFSET = 3;
    public static final int CONTROL_COMMAND_FLAG = 0x80;
    public static final int CONTROL_COMMAND_SET_MTU = 1;
    public static final int CONTROL_COMMAND_SET_MTU_SIZE= 4;
    public static final int CONTROL_COMMAND_UPDATE_CONN_PARAMS = 2;
    public static final int CONTROL_COMMAND_UPDATE_CONN_PARAMS_SIZE = 8;
    public static final int CONTROL_COMMAND_READ_CONFIG = 3;

    // Audio manager messages
    public static final int AUDIOMANAGER_STREAM_ON = 1;
    public static final int AUDIOMANAGER_STREAM_OFF = 2;
    public static final int AUDIOMANAGER_STREAM_IMA = 3;
    public static final int AUDIOMANAGER_TOGGLE_PLAYBACK = 4;
    public static final int AUDIOMANAGER_RELEASE = 5;
    public static final int AUDIOMANAGER_SET_MODE = 6;
    public static final int AUDIOMANAGER_SET_FEATURES = 7;
    public static final int AUDIOMANAGER_RECORDING_START = 8;
    public static final int AUDIOMANAGER_RECORDING_STOP = 9;
    public static final int AUDIOMANAGER_RECORDING_DATA = 10;

    // Intent actions
    public static final String ACTION_NO_BLUETOOTH = "com.diasemi.bleremote.ACTION_NO_BLUETOOTH";
    public static final String ACTION_GATT_CONNECTED = "com.diasemi.bleremote.ACTION_GATT_CONNECTED";
    public static final String ACTION_GATT_DISCONNECTED = "com.diasemi.bleremote.ACTION_GATT_DISCONNECTED";
    public static final String ACTION_GATT_SERVICES_DISCOVERED = "com.diasemi.bleremote.ACTION_GATT_SERVICES_DISCOVERED";
    public static final String ACTION_GATT_DEVICE_READY = "com.diasemi.bleremote.ACTION_GATT_DEVICE_READY";
    public static final String ACTION_HID_CONNECTION_TIMEOUT = "com.diasemi.bleremote.ACTION_HID_CONNECTION_TIMEOUT";
    public static final String ACTION_SCAN_DEVICE = "com.diasemi.bleremote.ACTION_SCAN_DEVICE";
    public static final String ACTION_BITRATE_REPORTED = "com.diasemi.bleremote.ACTION_BITRATE_REPORTED";
    public static final String ACTION_CONTROL_INPUT = "com.diasemi.bleremote.ACTION_CONTROL_INPUT";
    public static final String ACTION_PLAYBACK_STATE = "com.diasemi.bleremote.ACTION_PLAYBACK_STATE";
    public static final String ACTION_CONFIG_UPDATE = "com.diasemi.bleremote.ACTION_CONFIG_UPDATE";
    public static final String ACTION_KEY_REPORTED = "com.diasemi.bleremote.ACTION_KEY_REPORTED";
    public static final String ACTION_SPEECHREC_RESULT = "com.diasemi.bleremote.ACTION_SPEECHREC_RESULT";

    // Intent extras
    public static final String EXTRA_DEVICE = "com.diasemi.bleremote.EXTRA_DEVICE";
    public static final String EXTRA_DATA = "com.diasemi.bleremote.EXTRA_DATA";
    public static final String EXTRA_VALUE = "com.diasemi.bleremote.EXTRA_VALUE";
    public static final String EXTRA_RSSI = "com.diasemi.bleremote.EXTRA_RSSI";
    public static final String EXTRA_MESSAGE = "com.diasemi.bleremote.MESSAGE";
    public static final String EXTRA_ENGINE = "com.diasemi.bleremote.ENGINE";
    public static final String EXTRA_FIRST_MATCH = "com.diasemi.bleremote.FIRSTMATCH";
    public static final String EXTRA_PARTIAL = "com.diasemi.bleremote.EXTRA_PARTIAL";
    public static final String EXTRA_MTU = "mtu";
    public static final String EXTRA_PACKET_SIZE = "packetSize";
    public static final String EXTRA_CONN_INTERVAL = "connectionInterval";
    public static final String EXTRA_SLAVE_LATENCY = "slaveLatency";
    public static final String EXTRA_CONN_TIMEOUT = "connectionTimeout";

    // Preferences
    public static final String PREFERENCES_NAME = "com.diasemi.bleremote.PREFERENCES";
    public static final String PREF_LIST_BITRATE = "list_bit_rate";
    public static final String PREF_MODE_CUSTOM = "mode_custom";
    public static final String PREF_MODE_HID_AUDIO = "mode_hid_audio";
    public static final String PREF_LIST_SEARCH_ENGINE = "list_search_engine";
    public static final String PREF_CHECKBOX_VOICE_REC = "checkbox_voice_rec";
    public static final String PREF_CHECKBOX_FIRST_MATCH = "checkbox_first_match";
    public static final String PREF_VOICE_REC_PARTIAL = "voice_rec_partial";
    public static final String PREF_AUTO_SAVE_AUDIO = "auto_save_audio";
    public static final String PREF_AUTO_PAIRING = "auto_pairing";
    public static final String PREF_AUTO_INIT_SYSTEM_HID = "init_system_hid";
    public static final String PREF_VOICE_REC_LANG = "voice_rec_lang";
    public static final String PREF_USE_SPEECH_REC_DIALOG = "use_speech_rec_dialog";
    public static final String PREF_PROCESS_SPEECH_REC_RESULT = "process_speech_rec_result";
    public static final String PREF_AUDIO_RECORD_USE_PTT = "audio_record_use_ptt";
    // Default values
    public static final boolean DEFAULT_VOICE_REC_PARTIAL = true;
    public static final boolean DEFAULT_AUTO_SAVE_AUDIO = false;
    public static final boolean DEFAULT_AUTO_PAIRING = true;
    public static final boolean DEFAULT_AUTO_INIT_SYSTEM_HID = true;

    // Search ID/keys
    public static final String CLIENT_ID = "AIzaSyDaxD-JN8L42Qw0xmnRVm7CqGnhvEOfg9g";
    public static final String GOOGLE_KEY = "013036536707430787589:_pqjad5hr1a";
    public static final String IMDB_KEY = "002323337938959020373:u5o11hmhtwa";
    public static final String ROTTEN_TOMATOES_KEY = "002323337938959020373:1b8qcvpuq7c";
    public static final String YOUTUBE_KEY = "002323337938959020373:bdmi9djq0um";

    // Misc
    public static final String CACHED_MESSAGES = "com.diasemi.bleremote.CACHED_MESSAGE";
    public static final String WIFI_PROVIDER = "WiFi";

    // HID driver
    public static final String HID_DRIVER_MODULE_NAME = "hid_dia_remote";
    public static final String HID_DRIVER_AUDIO_HAL_ID = "DialogRCU";
    public static final String HID_DRIVER_AUDIO_HAL_ID_OLD = "DIAAudio";
    public static final String HID_DRIVER_AUDIO_HAL_NAME = "dia_remote";
    public static final String HID_DRIVER_PROC_VERSION = "/proc/diasemi/hid-driver-version";
    public static final String HID_DRIVER_PROC_RCU_ADDDR = "/proc/diasemi/rcu-addr";
}
