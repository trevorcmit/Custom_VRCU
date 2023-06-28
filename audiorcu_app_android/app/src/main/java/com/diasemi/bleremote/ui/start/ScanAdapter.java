/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.start;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.diasemi.bleremote.R;

import java.util.ArrayList;

/**
 * ScanItem adapter for the device list on the main activity
 */
public class ScanAdapter extends ArrayAdapter<ScanItem> {

    private Context mContext;
    private int layoutResourceId;
    private ArrayList<ScanItem> data = null;

    public ScanAdapter(Context mContext, int layoutResourceId, ArrayList<ScanItem> data) {
        super(mContext, layoutResourceId, data);
        this.layoutResourceId = layoutResourceId;
        this.mContext = mContext;
        this.data = data;
    }

    private static class Views {
        ImageView icon;
        TextView name;
        TextView description;
        TextView rssi;
        SignalBar signalBar;
        ImageView pairedImage;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Create view
        Views views;
        if (convertView == null) {
            convertView = ((Activity) mContext).getLayoutInflater().inflate(layoutResourceId, parent, false);
            views = new Views();
            views.icon = (ImageView) convertView.findViewById(R.id.scanItemIcon);
            views.name = (TextView) convertView.findViewById(R.id.scanItemName);
            views.description = (TextView) convertView.findViewById(R.id.scanItemDescription);
            views.rssi = (TextView) convertView.findViewById(R.id.scanItemRssi);
            views.signalBar = (SignalBar) convertView.findViewById(R.id.signalBar);
            views.pairedImage = (ImageView) convertView.findViewById(R.id.pairedIcon);
            convertView.setTag(views);
        } else {
            views = (Views) convertView.getTag();
        }

        // Update view
        ScanItem scanitem = data.get(position);
        views.icon.setImageResource(scanitem.scanIcon);
        views.name.setText(scanitem.scanName);
        views.description.setText(scanitem.scanDescription);
        views.rssi.setVisibility(scanitem.scanSignal == 0 ? View.INVISIBLE : View.VISIBLE);
        views.signalBar.setVisibility(scanitem.scanSignal == 0 ? View.INVISIBLE : View.VISIBLE);
        views.rssi.setText(String.valueOf(scanitem.scanSignal) + "dB");
        views.signalBar.setRssi(scanitem.scanSignal);
        views.pairedImage.setVisibility(scanitem.paired ? View.VISIBLE : View.GONE);
        return convertView;
    }

}