/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.main.logs;

import android.app.Fragment;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.diasemi.bleremote.BusProvider;
import com.diasemi.bleremote.Constants;
import com.diasemi.bleremote.R;
import com.diasemi.bleremote.manager.CacheManager;
import com.diasemi.bleremote.ui.LogEvent;
import com.squareup.otto.Subscribe;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class LogsFragment extends Fragment {

    private static final String TAG = "LogsFragment";
    @InjectView(R.id.log_scroll)
    ScrollView mLogScroll;
    @InjectView(R.id.text_logs)
    TextView mLogTextView;
    private int mLogNum = 0;

    // LIFE CYCLE METHOD(S)

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_logs, container, false);
        ButterKnife.inject(this, rootView);
        return rootView;
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mLogTextView.setMovementMethod(ScrollingMovementMethod.getInstance());
        if (savedInstanceState != null) {
            Log.d(TAG, "onViewCreated: Restoring from saved instance");
            mLogTextView.setText(savedInstanceState.getCharSequence(TAG));
        }
        BusProvider.getInstance().register(this);
    }

    @Override
    public void onDestroyView() {
        BusProvider.getInstance().unregister(this);
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
        outState.putCharSequence(TAG, mLogTextView.getText());
    }

    // SUBSCRIBED METHOD(S)

    @Subscribe
    public void onLogEvent(final LogEvent event) {
        refresh();
    }

    private void refresh() {
        if (CacheManager.hasCached(getActivity(), Constants.CACHED_MESSAGES) && isAdded()) {
            List<String> strings = (List<String>) CacheManager.readList(getActivity(), Constants.CACHED_MESSAGES);
            if (mLogNum < strings.size()) {
                StringBuilder stringBuffer = new StringBuilder();
                if (mLogNum == 0)
                    mLogTextView.setText("");
                else
                    stringBuffer.append('\n');

                for (int i = mLogNum; i < strings.size(); ++i) {
                    stringBuffer.append(strings.get(i));
                    stringBuffer.append('\n');
                }
                if (stringBuffer.charAt(stringBuffer.length() - 1) == '\n')
                    stringBuffer.setLength(stringBuffer.length() - 1);
                mLogTextView.append(stringBuffer.toString());
                mLogScroll.post(new Runnable() {
                    @Override
                    public void run() {
                        mLogScroll.smoothScrollTo(0, mLogTextView.getBottom());
                    }
                });
                mLogNum = strings.size();
            }
        }
    }
}
