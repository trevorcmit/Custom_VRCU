/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.main.input;

public class BitRateEvent {

    private int bitRate;

    public BitRateEvent(int bitRate) {
        this.bitRate = bitRate;
    }

    public int getBitRate() {
        return bitRate;
    }
}
