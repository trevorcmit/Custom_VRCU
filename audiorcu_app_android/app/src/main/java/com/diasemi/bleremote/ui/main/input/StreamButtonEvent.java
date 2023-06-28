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

public class StreamButtonEvent {

    private boolean mIsChecked;

    public StreamButtonEvent(final boolean isChecked) {
        mIsChecked = isChecked;
    }

    public boolean isChecked() {
        return mIsChecked;
    }
}