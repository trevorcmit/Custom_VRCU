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

public class ConfigUpdateEvent {

    private int mtu;
    private int packetSize;
    private int connectionInterval;
    private int slaveLatency;
    private int supervisionTimeout;

    public ConfigUpdateEvent(int mtu, int packetSize, int connectionInterval, int slaveLatency, int supervisionTimeout) {
        this.mtu = mtu;
        this.packetSize = packetSize;
        this.connectionInterval = connectionInterval;
        this.slaveLatency = slaveLatency;
        this.supervisionTimeout = supervisionTimeout;
    }

    public int getMtu() {
        return mtu;
    }

    public int getPacketSize() {
        return packetSize;
    }

    public int getConnectionInterval() {
        return connectionInterval;
    }

    public int getSlaveLatency() {
        return slaveLatency;
    }

    public int getSupervisionTimeout() {
        return supervisionTimeout;
    }
}
