/*
 *******************************************************************************
 *
 * Copyright (C) 2017-2018 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.main.config;

import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.diasemi.bleremote.BusProvider;
import com.diasemi.bleremote.Constants;
import com.diasemi.bleremote.R;
import com.diasemi.bleremote.service.BleRemoteService;
import com.diasemi.bleremote.ui.main.MainActivity;
import com.squareup.otto.Subscribe;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;

public class ConfigFragment extends Fragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    // Views
    @InjectView(R.id.min_interval)
    EditText minIntervalText;
    @InjectView(R.id.max_interval)
    EditText maxIntervalText;
    @InjectView(R.id.curr_interval)
    TextView currIntervalText;
    @InjectView(R.id.slave_latency)
    EditText slaveLatencyText;
    @InjectView(R.id.curr_slave_latency)
    TextView currSlaveLatencyText;
    @InjectView(R.id.conn_timeout)
    EditText connectionTimeoutText;
    @InjectView(R.id.curr_conn_timeout)
    TextView currConnectionTimeoutText;
    @InjectView(R.id.update_params_button)
    TextView updateConnParamsButton;
    @InjectView(R.id.packet_size)
    TextView packetSizeText;
    @InjectView(R.id.curr_packet_size)
    TextView currPacketSizeText;
    @InjectView(R.id.set_max_button)
    TextView setMaxButton;
    @InjectView(R.id.set_fixed_button)
    TextView setFixedButton;
    @InjectView(R.id.voice_rec_partial)
    Switch voiceRecPartialSwitch;
    @InjectView(R.id.auto_save_audio)
    Switch autoSaveAudioSwitch;
    @InjectView(R.id.auto_pairing)
    Switch autoPairingSwitch;
    @InjectView(R.id.auto_init_system_hid)
    Switch autoInitSystemHidSwitch;
    private boolean viewCreated;
    private SharedPreferences preferences;

    // Current values
    private int connectionInterval = -1;
    private int slaveLatency = -1;
    private int connTimeout = -1;
    private int mtu = -1;
    private int packetSize = -1;

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_configuration, container, false);
        ButterKnife.inject(this, rootView);
        return rootView;
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewCreated = true;
        preferences = getActivity().getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE);
        voiceRecPartialSwitch.setChecked(preferences.getBoolean(Constants.PREF_VOICE_REC_PARTIAL, Constants.DEFAULT_VOICE_REC_PARTIAL));
        autoSaveAudioSwitch.setChecked(preferences.getBoolean(Constants.PREF_AUTO_SAVE_AUDIO, Constants.DEFAULT_AUTO_SAVE_AUDIO));
        autoPairingSwitch.setChecked(preferences.getBoolean(Constants.PREF_AUTO_PAIRING, Constants.DEFAULT_AUTO_PAIRING));
        autoInitSystemHidSwitch.setChecked(preferences.getBoolean(Constants.PREF_AUTO_INIT_SYSTEM_HID, Constants.DEFAULT_AUTO_INIT_SYSTEM_HID));
        BusProvider.getInstance().register(this);
        BleRemoteService bleService = ((MainActivity) getActivity()).getBleService();
        boolean connected = bleService.isConnected();
        boolean commandSupport = (bleService.getFeatures() & Constants.CONTROL_FEATURES_COMMAND_SUPPORT) != 0;
        updateConnParamsButton.setEnabled(connected && commandSupport);
        setMaxButton.setEnabled(connected && commandSupport);
        setFixedButton.setEnabled(connected && commandSupport);
        if (commandSupport) {
            connectionInterval = bleService.getConnectionInterval();
            slaveLatency = bleService.getSlaveLatency();
            connTimeout = bleService.getSupervisionTimeout();
            packetSize = bleService.getPacketSize();
            mtu = bleService.getMtu();
            initValues();
        }
    }

    @Override
    public void onDestroyView() {
        viewCreated = false;
        BusProvider.getInstance().unregister(this);
        super.onDestroyView();
    }

    public void onDeviceReady() {
        if (!viewCreated)
            return;
        boolean commandSupport = (((MainActivity) getActivity()).getBleService().getFeatures() & Constants.CONTROL_FEATURES_COMMAND_SUPPORT) != 0;
        updateConnParamsButton.setEnabled(commandSupport);
        setMaxButton.setEnabled(commandSupport);
        setFixedButton.setEnabled(commandSupport);
    }

    public void onDeviceDisconnected() {
        if (!viewCreated)
            return;
        updateConnParamsButton.setEnabled(false);
        setMaxButton.setEnabled(false);
        setFixedButton.setEnabled(false);
    }

    private void initValues() {
        if (connectionInterval != -1) {
            String interval = String.valueOf(connectionInterval);
            minIntervalText.setText(interval);
            maxIntervalText.setText(interval);
        }
        if (slaveLatency != -1)
            slaveLatencyText.setText(String.valueOf(slaveLatency));
        if (connTimeout != -1)
            connectionTimeoutText.setText(String.valueOf(connTimeout));
        if (packetSize != -1)
            packetSizeText.setText(String.valueOf(packetSize - 3));
        updateCurrentValues();
    }

    private void updateCurrentValues() {
        if (connectionInterval != -1)
            currIntervalText.setText(String.format(getString(R.string.curr_interval), connectionInterval * 1.25));
        if (slaveLatency != -1)
            currSlaveLatencyText.setText(String.format(getString(R.string.curr_slave_latency), slaveLatency));
        if (connTimeout != -1)
            currConnectionTimeoutText.setText(String.format(getString(R.string.curr_conn_timeout), connTimeout * 10));
        if (packetSize != -1 && mtu != -1)
            currPacketSizeText.setText(String.format(getString(R.string.curr_packet_size), packetSize - 3, mtu - 3));
    }

    @Subscribe
    public void onConfigUpdateEvent(ConfigUpdateEvent event) {
        if (event.getConnectionInterval() != -1)
            connectionInterval = event.getConnectionInterval();
        if (event.getSlaveLatency() != -1)
            slaveLatency = event.getSlaveLatency();
        if (event.getSupervisionTimeout() != -1)
            connTimeout = event.getSupervisionTimeout();
        if (event.getPacketSize() != -1)
            packetSize = event.getPacketSize();
        if (event.getMtu() != -1)
            mtu = event.getMtu();
        updateCurrentValues();
    }

    @OnClick({R.id.update_params_button, R.id.set_fixed_button, R.id.set_max_button})
    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.update_params_button:
                try {
                    int minInterval = Integer.parseInt(minIntervalText.getText().toString());
                    int maxInterval = Integer.parseInt(maxIntervalText.getText().toString());
                    int latency = Integer.parseInt(slaveLatencyText.getText().toString());
                    int timeout = Integer.parseInt(connectionTimeoutText.getText().toString());
                    if (minInterval > maxInterval) {
                        minInterval = maxInterval;
                        minIntervalText.setText(String.valueOf(minInterval));
                    }
                    if (validateValue(minInterval, 6, 3200, R.string.invalid_interval_msg)
                        && validateValue(maxInterval, 6, 3200, R.string.invalid_interval_msg)
                        && validateValue(latency, 0, 499, R.string.invalid_slave_latency_msg)
                        && validateValue(timeout, 10, 3200, R.string.invalid_conn_timeout_msg)) {
                        BusProvider.getInstance().post(new ConnParamsButtonEvent(minInterval, maxInterval, latency, timeout));
                    }
                } catch (NumberFormatException nfe) {
                    showInvalidValueMsg(R.string.invalid_value_msg);
                }
                break;

            case R.id.set_fixed_button:
            case R.id.set_max_button:
                try {
                    int size = 3 + Integer.parseInt(packetSizeText.getText().toString());
                    if (validateValue(size, 23, mtu != -1 ? mtu : 512, R.string.invalid_packet_size_msg)) {
                        BusProvider.getInstance().post(new PacketSizeButtonEvent(size, v.getId() == R.id.set_fixed_button ? size : 0));
                    }
                } catch (NumberFormatException nfe) {
                    showInvalidValueMsg(R.string.invalid_value_msg);
                }
                break;
        }
    }

    private boolean validateValue(int value, int min, int max, int msgRes) {
        if (value >= min && value <= max)
            return true;
        showInvalidValueMsg(msgRes);
        return false;
    }

    private void showInvalidValueMsg(int msgRes) {
        Toast.makeText(getActivity(), msgRes, Toast.LENGTH_LONG).show();
    }

    @OnCheckedChanged({R.id.voice_rec_partial, R.id.auto_save_audio, R.id.auto_pairing, R.id.auto_init_system_hid})
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.voice_rec_partial:
                preferences.edit().putBoolean(Constants.PREF_VOICE_REC_PARTIAL, isChecked).apply();
                break;
            case R.id.auto_save_audio:
                preferences.edit().putBoolean(Constants.PREF_AUTO_SAVE_AUDIO, isChecked).apply();
                break;
            case R.id.auto_pairing:
                preferences.edit().putBoolean(Constants.PREF_AUTO_PAIRING, isChecked).apply();
                break;
            case R.id.auto_init_system_hid:
                preferences.edit().putBoolean(Constants.PREF_AUTO_INIT_SYSTEM_HID, isChecked).apply();
                break;
        }
    }
}
