/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.main.remotecontrol;

public class KeyPressEvent {

    private byte[] mPacket;

    public KeyPressEvent(final byte[] packet) {
        mPacket = packet;
    }

    public byte[] getPacket() {
        return mPacket;
    }
}
