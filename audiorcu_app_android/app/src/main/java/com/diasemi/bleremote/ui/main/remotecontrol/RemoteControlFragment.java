/*
 *******************************************************************************
 *
 * Copyright (C) 2017-2018 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.main.remotecontrol;

import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.diasemi.bleremote.BusProvider;
import com.diasemi.bleremote.Constants;
import com.diasemi.bleremote.R;
import com.diasemi.bleremote.Utils;
import com.diasemi.bleremote.manager.XMLManager;
import com.diasemi.bleremote.model.KeyButton;
import com.diasemi.bleremote.ui.main.MainActivity;
import com.diasemi.bleremote.ui.main.input.StreamControlEvent;
import com.squareup.otto.Subscribe;

import java.util.Map;

public class RemoteControlFragment extends Fragment {

    private static String TAG = "RemoteControlFragment";

    private Map<Integer, XMLManager.KeyLayout> keyLayouts;
    private int currKeyLayout = -1;
    private ViewGroup keyLayoutParentView;
    private KeyButton[] keys = null;
    private Button pttButton;

    public void setKeyLayout(final int code) {
        if (keyLayouts == null) {
            currKeyLayout = code;
            return; // will be initialized when view is created
        }
        XMLManager.KeyLayout layout = keyLayouts.get(code);
        if (layout == null) {
            Log.e(TAG, "Unknown key layout: " + code);
            return;
        }
        // Setup key layout view
        currKeyLayout = code;
        if (layout.gridLayout == null && keyLayoutParentView.getWidth() == 0) {
            Log.d(TAG, "Delay setup until parent view is created");
            keyLayoutParentView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (currKeyLayout == code)
                        setKeyLayout(code);
                }
            }, 100);
            return;
        }
        Log.d(TAG, "Switch key layout: " + layout.name + " [" + code + "]");
        XMLManager.setupRemote(getActivity(), layout, keyLayoutParentView);
        keys = layout.keys;
        // Find audio button
        pttButton = null;
        for (KeyButton key : keys) {
            if (key.getName().equals(layout.ptt)) {
                pttButton = key.getButton();
                pttButton.setPressed(((MainActivity)getActivity()).isPttPressed());
                break;
            }
        }
    }

    public void resetKeyState() {
        if (keys != null) {
            for (KeyButton key : keys) {
                key.setState(false);
                key.getButton().setPressed(false);
            }
        }
        if (pttButton != null)
            pttButton.setPressed(((MainActivity)getActivity()).isPttPressed());
    }

    // LIFE CYCLE METHOD(S)

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BusProvider.getInstance().register(this);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_remote_control, container, false);
        keyLayoutParentView = (ViewGroup) rootView;
        return rootView;
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        keyLayouts = XMLManager.readKeyLayouts(getActivity());
        if (currKeyLayout != -1)
            setKeyLayout(currKeyLayout);
    }

    @Override
    public void onDestroyView() {
        keyLayoutParentView.removeAllViews();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        BusProvider.getInstance().unregister(this);
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (keys != null)
            for (KeyButton key : keys)
                key.getButton().setPressed(key.getState());
        if (pttButton != null)
            pttButton.setPressed(((MainActivity)getActivity()).isPttPressed());
    }

    // SUBSCRIBED METHOD(S)

    @Subscribe
    public void onStreamControlEvent(final StreamControlEvent event) {
        if (pttButton != null)
            pttButton.setPressed(event.getPacket()[Constants.CONTROL_STREAM_ENABLE_OFFSET] != 0);
    }

    @Subscribe
    public void onKeyPressed(final KeyPressEvent event) {
        if (keys == null)
            return;
        byte[] report = event.getPacket();

        // Check for key layout
        if (report.length > Constants.KEY_REPORT_LAYOUT_OFFSET) {
            int code = report[Constants.KEY_REPORT_LAYOUT_OFFSET];
            if (code != currKeyLayout)
                setKeyLayout(code);
        }

        // Report type/size
        int size = report[Constants.KEY_REPORT_SIZE_OFFSET];
        boolean extended = size != 8;
        int start = Constants.KEY_REPORT_SIZE_OFFSET + 1;
        int modifier = 0;
        if (!extended) {
            // First two bytes are modifiers
            modifier = report[start];
            start += 2;
            size -= 2;
        }

        // Parse report
        resetKeyState();
        StringBuilder message = new StringBuilder("Key report: ");
        boolean pressed = false;
        for (int i = 0; i < size && start + i < report.length; i++) {
            int code = report[start + i] & 0xFF;
            if (code == 0)
                continue;

            boolean found = false;
            for (KeyButton key : keys) {
                if (!extended && key.getCode() == code && (key.getModifier() == 0 || key.getModifier() == modifier)
                        || extended && (key.getCode() >> 8) == (i + 1) && (key.getCode() & code) != 0) {
                    if (key.getButton() != null && key.getButton().isEnabled()) {
                        key.setState(true);
                        key.getButton().setPressed(true);
                    }
                    found = true;
                    if (pressed)
                        message.append(", ");
                    pressed = true;
                    message.append(String.format("'%s' [%#04x]", key.getName(), key.getCode()));
                    if (!extended)
                        break;
                }
            }

            if (!found) {
                String msg = String.format("Unknown key code: %#06x", !extended ? code : code + ((i + 1) << 8));
                Log.d(TAG, msg);
                Utils.logMessage(getActivity(), msg);
            }
        }

        if (pressed) {
            String logMessage = message.toString();
            Log.d(TAG, logMessage);
            Utils.logMessage(getActivity(), logMessage);
        } else {
            Log.d(TAG, "Key report: NO KEYS");
        }
    }
}
