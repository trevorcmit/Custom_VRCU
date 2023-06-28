/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.main.logs;

public class ErrorEvent {

    private byte[] mPacket;

    public ErrorEvent(final byte[] packet) {
        mPacket = packet;
    }

    public byte[] getPacket() {
        return mPacket;
    }
}