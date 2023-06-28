/*
 *******************************************************************************
 *
 * Copyright (C) 2017-2018 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.misc;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;

import com.diasemi.bleremote.R;
import com.diasemi.bleremote.Utils;
import com.diasemi.bleremote.ui.main.MainActivity;

public class InfoFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.app_info);
        PreferenceManager preferenceManager = getPreferenceManager();

        String version = "N/A";
        try {
            version = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {}
        preferenceManager.findPreference("AppVersion").setSummary(version);

        String hidVersion = Utils.getHidDriverVersion();
        Preference hidVersionEntry = preferenceManager.findPreference("HidVersion");
        if (hidVersionEntry != null) {
            if (hidVersion.isEmpty())
                ((PreferenceGroup) preferenceManager.findPreference("Information")).removePreference(hidVersionEntry);
            else
                hidVersionEntry.setSummary(hidVersion);
        }

        String firmwareVersion = ((MainActivity)getActivity()).getBleService().getFirmwareVersion();
        if (firmwareVersion != null)
            preferenceManager.findPreference("FirmwareVersion").setSummary(firmwareVersion);

        preferenceManager.findPreference("InfoSendMail").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference pref) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setData(Uri.parse("mailto:bluetooth.support@diasemi.com?subject=Voice RCU application question"));
                getActivity().startActivity(intent);
                return true;
            }
        });
    }
}
