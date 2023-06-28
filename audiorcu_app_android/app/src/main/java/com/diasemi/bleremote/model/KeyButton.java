/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.model;

import android.widget.Button;

public class KeyButton {

    private Button button;
    private String name;
    private int code;
    private int modifier;
    private boolean state;

    public KeyButton(final Button button, final String name, final int code, final int modifier) {
        this.button = button;
        this.name = name;
        this.code = code;
        this.modifier = modifier;
    }

    public Button getButton() {
        return button;
    }

    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getModifier() {
        return modifier;
    }

    public boolean getState() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }
}
