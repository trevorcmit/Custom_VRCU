/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;
import android.view.View;
import android.view.ViewPropertyAnimator;

import com.diasemi.bleremote.manager.CacheManager;
import com.diasemi.bleremote.ui.LogEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Pattern;

public class Utils {

    // LOAD SPINNER

    public static void showProgress(final Context context, final View fragmentContainer,
            final View progressView, final boolean show) {
        showView(fragmentContainer, !show);
        animate(context, fragmentContainer, !show);
        showView(progressView, show);
        animate(context, progressView, show);
    }

    static void showView(final View view, final boolean show) {
        if (show) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private static void animate(final Context context, final View view, final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in the
        // progress spinner.
        int shortAnimTime = context.getResources().getInteger(android.R.integer.config_shortAnimTime);
        ViewPropertyAnimator animator = view.animate();
        animator.setDuration(shortAnimTime);
        if (show) {
            animator.alpha(1);
        } else {
            animator.alpha(0);
        }
        animator.setListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                showView(view, show);
            }
        });
    }

    public static void showSettingsAlert(final Context context) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
        alertDialog.setTitle("Bluetooth Settings");
        alertDialog.setMessage("Bluetooth is not enabled! Want to go to settings menu?");
        alertDialog.setPositiveButton("Settings", new OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                Intent intent;
                intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                context.startActivity(intent);
            }
        });
        alertDialog.setNegativeButton(android.R.string.cancel, new OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                dialog.dismiss();
            }
        });
        alertDialog.show();
    }

    // INTERNET-RELATED

    public static boolean isConnected(final Context context) {
        boolean connectedToWifi = false;
        boolean connectedToMobile = false;
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] networkInfos = connectivityManager.getAllNetworkInfo();
        for (NetworkInfo networkInfo : networkInfos) {
            if (networkInfo.getTypeName().equalsIgnoreCase("WIFI")) {
                if (networkInfo.isConnected()) {
                    connectedToWifi = true;
                }
            }
            if (networkInfo.getTypeName().equalsIgnoreCase("MOBILE")) {
                if (networkInfo.isConnected()) {
                    connectedToMobile = true;
                }
            }
        }
        if (connectedToWifi || connectedToMobile) {
            return true;
        }
        return false;
    }

    public static void showSettingsAlert(final Context context, final String provider) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
        alertDialog.setTitle(provider + " Settings");
        alertDialog.setMessage(provider + " is not enabled! Want to go to settings menu?");
        alertDialog.setPositiveButton("Settings", new OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                Intent intent = null;
                if (provider.equals(Constants.WIFI_PROVIDER)) {
                    intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                }
                context.startActivity(intent);
            }
        });
        alertDialog.setNegativeButton("Cancel", new OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                dialog.cancel();
            }
        });
        alertDialog.show();
    }

    public static void replaceFragment(final Activity activity, final Fragment fragment,
            final int layoutResourceId, final boolean addToBackstack) {
        FragmentTransaction fragmentTransaction = activity.getFragmentManager().beginTransaction();
        fragmentTransaction.replace(layoutResourceId, fragment);
        if (addToBackstack) {
            fragmentTransaction.addToBackStack(fragment.getClass().getName());
        }
        fragmentTransaction.commit();
    }

    public static void logMessage(Context context, String msg) {
        CacheManager.addString(context, Constants.CACHED_MESSAGES, msg);
        BusProvider.getInstance().post(new LogEvent());
    }

    public static boolean isHidDriverAvailable() {
        return new File("/sys/module/" + Constants.HID_DRIVER_MODULE_NAME).exists();
    }

    public static boolean isHidDriverSoundCardAvailable() {
        boolean found = false;
        try {
            for (int i = 0; ; i++) {
                File card = new File("/proc/asound/card" + i + "/id");
                if (!card.exists())
                    break;
                BufferedReader cardId = new BufferedReader(new FileReader(card));
                String id = cardId.readLine();
                cardId.close();
                if (Constants.HID_DRIVER_AUDIO_HAL_ID.equals(id) || Constants.HID_DRIVER_AUDIO_HAL_ID_OLD.equals(id)) {
                    found = true;
                    break;
                }
            }
        } catch (IOException e) {}
        return found;
    }

    public static boolean isHidDriverHalAvailable() {
        // Check HAL library
        String lib = "/system/lib/hw/audio." + Constants.HID_DRIVER_AUDIO_HAL_NAME + ".default.so";
        String lib64 = "/system/lib64/hw/audio." + Constants.HID_DRIVER_AUDIO_HAL_NAME + ".default.so";
        boolean found = new File(lib).exists() || new File(lib64).exists();
        if (!found)
            return false;
        // Check audio policy
        found = false;
        try {
            // Audio policy configuration file
            File audioPolicyConf = new File("/system/etc/audio_policy.conf");
            Pattern pattern;
            if (audioPolicyConf.exists()) {
                pattern = Pattern.compile("^\\s*" + Constants.HID_DRIVER_AUDIO_HAL_NAME + "\\s*\\{\\s*$");
            } else {
                // If the configuration file is not available, check for the new format (added on Android 7)
                audioPolicyConf = new File("/system/etc/audio_policy_configuration.xml");
                String rcuPolicyConf = Constants.HID_DRIVER_AUDIO_HAL_NAME + "_audio_policy_configuration.xml";
                if (!new File("/system/etc/" + rcuPolicyConf).exists())
                    return false;
                pattern = Pattern.compile("^\\s*<xi:include href=\"" + rcuPolicyConf + "\"/>\\s*$");
            }
            BufferedReader audioPolicy = new BufferedReader(new FileReader(audioPolicyConf));
            String line;
            while ((line = audioPolicy.readLine()) != null) {
                if (pattern.matcher(line).matches()) {
                    found = true;
                    break;
                }
            }
            audioPolicy.close();
        } catch (IOException e) {}
        return found;
    }

    public static String getHidDriverVersion() {
        String version = "";
        try {
            BufferedReader procVersion = new BufferedReader(new FileReader(new File(Constants.HID_DRIVER_PROC_VERSION)));
            version = procVersion.readLine();
            procVersion.close();
        } catch (IOException e) {}
        return version;
    }

    public static boolean isConnectedToHidDriver(String address) {
        boolean found = false;
        try {
            BufferedReader rcuAddr = new BufferedReader(new FileReader(new File(Constants.HID_DRIVER_PROC_RCU_ADDDR)));
            String line;
            while ((line = rcuAddr.readLine()) != null) {
                if (line.contains(address)) {
                    found = true;
                    break;
                }
            }
            rcuAddr.close();
        } catch (IOException e) {}
        return found;
    }
}
