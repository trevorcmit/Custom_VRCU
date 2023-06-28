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

public class ConnParamsButtonEvent {

    private int minInterval;
    private int maxInterval;
    private int slaveLatency;
    private int supervisionTimeout;

    public ConnParamsButtonEvent(int minInterval, int maxInterval, int slaveLatency, int supervisionTimeout) {
        this.minInterval = minInterval;
        this.maxInterval = maxInterval;
        this.slaveLatency = slaveLatency;
        this.supervisionTimeout = supervisionTimeout;
    }

    public int getMinInterval() {
        return minInterval;
    }

    public int getMaxInterval() {
        return maxInterval;
    }

    public int getSlaveLatency() {
        return slaveLatency;
    }

    public int getSupervisionTimeout() {
        return supervisionTimeout;
    }
}
