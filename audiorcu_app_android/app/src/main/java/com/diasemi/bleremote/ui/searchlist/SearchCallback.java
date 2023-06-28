/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.searchlist;

import java.util.ArrayList;

public abstract interface SearchCallback {

    public abstract void onSearchCompleted(boolean success, ArrayList<SearchItem> searchItems,
            ArrayList<String> errorList);
}
