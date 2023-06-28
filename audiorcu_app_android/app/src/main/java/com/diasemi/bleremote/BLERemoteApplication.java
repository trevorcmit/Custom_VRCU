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

import android.app.Application;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.Iconics;

public class BLERemoteApplication extends Application {

    @Override
    public void onCreate() {
        Iconics.registerFont(new GoogleMaterial());
        super.onCreate();
    }
}
