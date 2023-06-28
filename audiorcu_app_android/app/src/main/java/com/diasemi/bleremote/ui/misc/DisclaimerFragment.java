/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.misc;

import android.app.Fragment;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.diasemi.bleremote.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class DisclaimerFragment extends Fragment {

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        View fragmentView = inflater.inflate(R.layout.fragment_info, container, false);
        AssetManager assetManager = getActivity().getAssets();
        WebView webView = (WebView) fragmentView.findViewById(R.id.webView);

        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(assetManager.open("info.html")));
            String html = readerToString(r);
            html = html.replace("[version]", "");
            webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fragmentView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    /**
     * @param r Reader
     * @return String
     */
    public String readerToString(BufferedReader r) {
        String line;
        StringBuilder sb = new StringBuilder();
        try {
            while ((line = r.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

}
