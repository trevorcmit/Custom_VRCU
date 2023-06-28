/*
 *******************************************************************************
 *
 * Copyright (C) 2017-2018 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.start;

public class ScanItem {
    public int scanIcon;
    public String scanName;
    public String scanDescription;
    public String btAddress;
    public String btName;
    public int scanSignal;
    public boolean paired;

    public ScanItem(int scanIcon, String scanName, String scanDescription, int scanSignal, String btAddress, String btName, boolean paired) {
        this.scanIcon = scanIcon;
        this.scanName = scanName;
        this.scanDescription = scanDescription;
        this.scanSignal = scanSignal;
        this.btAddress = btAddress;
        this.btName = btName;
        this.paired = paired;
    }
}
