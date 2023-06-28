/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.main.config;

public class PacketSizeButtonEvent {

    private int max;
    private int fixed;

    public PacketSizeButtonEvent(int max, int fixed) {
        this.max = max;
        this.fixed = fixed;
    }

    public int getMax() {
        return max;
    }

    public int getFixed() {
        return fixed;
    }
}
