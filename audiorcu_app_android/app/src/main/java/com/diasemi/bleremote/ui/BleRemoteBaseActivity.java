/*
 *******************************************************************************
 *
 * Copyright (C) 2017-2018 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.diasemi.bleremote.Constants;
import com.diasemi.bleremote.R;
import com.diasemi.bleremote.Utils;
import com.diasemi.bleremote.service.BleRemoteService;

public abstract class BleRemoteBaseActivity extends AppCompatActivity implements ServiceConnection {

    private static final String TAG = BleRemoteBaseActivity.class.getSimpleName();

    /**
     * Intent filter. If you add a action to be received by the BroadcastReceiver, then make sure
     * you add entry here, as action will otherwise be filtered out.
     *
     * @return IntentFilter IntentFilter
     */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.ACTION_NO_BLUETOOTH);
        intentFilter.addAction(Constants.ACTION_GATT_CONNECTED);
        intentFilter.addAction(Constants.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(Constants.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(Constants.ACTION_GATT_DEVICE_READY);
        intentFilter.addAction(Constants.ACTION_HID_CONNECTION_TIMEOUT);
        intentFilter.addAction(Constants.ACTION_SCAN_DEVICE);
        intentFilter.addAction(Constants.ACTION_BITRATE_REPORTED);
        intentFilter.addAction(Constants.ACTION_CONTROL_INPUT);
        intentFilter.addAction(Constants.ACTION_CONFIG_UPDATE);
        intentFilter.addAction(Constants.ACTION_SPEECHREC_RESULT);
        intentFilter.addAction(Constants.ACTION_PLAYBACK_STATE);
        return intentFilter;
    }

    /**
     * BroadCastReceiver: handle various events fired by the BleRemoteService. Handles various
     * events fired by the Service. ACTION_GATT_CONNECTED: connected to a GATT server.
     * ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
     * ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
     * ACTION_DATA_AVAILABLE: received data from the device. This can be a result of read or
     * notification operations.
     * NOTE: if you add an event here, also update the makeGattUpdateIntentFilter.
     */
    private final BroadcastReceiver mGATTUpdateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (mBleRemoteService == null)
                return;
            final String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(Constants.EXTRA_DEVICE);
            switch (action) {
                case Constants.ACTION_NO_BLUETOOTH:
                    Utils.showSettingsAlert(BleRemoteBaseActivity.this);
                    break;
                case Constants.ACTION_GATT_CONNECTED:
                    mConnected = true;
                    onDeviceConnected(device);
                    break;
                case Constants.ACTION_GATT_DISCONNECTED:
                    mConnected = false;
                    onDeviceDisconnected(device);
                    break;
                case Constants.ACTION_GATT_SERVICES_DISCOVERED:
                    onServicesDiscovered(device);
                    break;
                case Constants.ACTION_GATT_DEVICE_READY:
                    onDeviceReady(device);
                    break;
                case Constants.ACTION_HID_CONNECTION_TIMEOUT:
                    onHidConnectionTimeout(device);
                    break;
                case Constants.ACTION_SCAN_DEVICE:
                    int rssi = intent.getIntExtra(Constants.EXTRA_RSSI, 0);
                    onDeviceFound(device, rssi);
                    break;
                case Constants.ACTION_BITRATE_REPORTED:
                    int bitrate = intent.getIntExtra(Constants.EXTRA_DATA, 0);
                    onBitrateReported(bitrate);
                    break;
                case Constants.ACTION_CONTROL_INPUT:
                    byte[] packet = intent.getByteArrayExtra(Constants.EXTRA_DATA);
                    onControlInput(packet);
                    break;
                case Constants.ACTION_CONFIG_UPDATE:
                    int mtu = intent.getIntExtra(Constants.EXTRA_MTU, -1);
                    int packetSize = intent.getIntExtra(Constants.EXTRA_PACKET_SIZE, -1);
                    int connectionInterval = intent.getIntExtra(Constants.EXTRA_CONN_INTERVAL, -1);
                    int slaveLatency = intent.getIntExtra(Constants.EXTRA_SLAVE_LATENCY, -1);
                    int supervisionTimeout = intent.getIntExtra(Constants.EXTRA_CONN_TIMEOUT, -1);
                    onConfigurationUpdate(mtu, packetSize, connectionInterval, slaveLatency, supervisionTimeout);
                    break;
                case Constants.ACTION_SPEECHREC_RESULT:
                    String transcript = intent.getStringExtra(Constants.EXTRA_DATA);
                    String confidence = intent.getStringExtra(Constants.EXTRA_VALUE);
                    boolean partial = intent.getBooleanExtra(Constants.EXTRA_PARTIAL, false);
                    onSpeechRecognitionResult(transcript, confidence, partial);
                    break;
                case Constants.ACTION_PLAYBACK_STATE:
                    onPlaybackState();
                    break;
            }
        }
    };

    protected BleRemoteService mBleRemoteService;
    protected String mDevice;
    protected boolean mConnected = false;
    protected boolean visible;

    // ServiceConnection METHODS(S)

    @Override
    public void onServiceConnected(final ComponentName componentName, final IBinder service) {
        Log.d(TAG, "onServiceConnected");
        mBleRemoteService = ((BleRemoteService.LocalBinder) service).getService();
        // Connect to the remote control, if it is disconnected.
        if (mBleRemoteService.isDisconnected() && !mConnected && mDevice != null && mDevice.equals(mBleRemoteService.getBluetoothDeviceAddress())) {
            Log.d(TAG, "Connecting to " + mDevice);
            mBleRemoteService.connect(mDevice);
        }
    }

    @Override
    public void onServiceDisconnected(final ComponentName componentName) {
        Log.d(TAG, "onServiceDisconnected");
        mBleRemoteService = null;
    }

    // LIFE CYCLE METHOD(S)

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21)
            getWindow().setNavigationBarColor(getResources().getColor(R.color.navigation_bar_background));
        LocalBroadcastManager.getInstance(this).registerReceiver(mGATTUpdateReceiver, makeGattUpdateIntentFilter());
        Intent gattServiceIntent = new Intent(this, BleRemoteService.class);
        bindService(gattServiceIntent, this, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mGATTUpdateReceiver);
        if (mBleRemoteService != null && mDevice != null && mDevice.equals(mBleRemoteService.getBluetoothDeviceAddress())) {
            mBleRemoteService.disconnect();
            mBleRemoteService.close();
        }
        unbindService(this);
        mBleRemoteService = null;
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
    }

    @Override
    protected void onStop() {
        visible = false;
        super.onStop();
    }

    public BleRemoteService getBleService() {
        return mBleRemoteService;
    }

    public boolean isVisible() {
        return visible;
    }

    protected void onDeviceConnected(BluetoothDevice device) {
    }

    protected void onDeviceDisconnected(BluetoothDevice device) {
    }

    protected void onHidConnectionTimeout(BluetoothDevice device) {
    }

    protected void onServicesDiscovered(BluetoothDevice device) {
    }

    protected void onDeviceReady(BluetoothDevice device) {
    }

    protected void onControlInput(byte[] packet) {
    }

    protected void onSpeechRecognitionResult(String transcript, String confidence, boolean partial) {
    }

    protected void onDeviceFound(BluetoothDevice device, int rssi) {
    }

    protected void onConfigurationUpdate(int mtu, int packetSize, int connectionInterval, int  slaveLatency, int supervisionTimeout) {
    }

    protected void onBitrateReported(int bitrate) {
    }

    protected void onPlaybackState() {
    }
}
