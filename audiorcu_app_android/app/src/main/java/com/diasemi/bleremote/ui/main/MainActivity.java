/*
 *******************************************************************************
 *
 * Copyright (C) 2017-2018 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.main;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.LayoutRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.diasemi.bleremote.BusProvider;
import com.diasemi.bleremote.Constants;
import com.diasemi.bleremote.R;
import com.diasemi.bleremote.RuntimePermissionChecker;
import com.diasemi.bleremote.Utils;
import com.diasemi.bleremote.audio.AudioManager;
import com.diasemi.bleremote.ui.BleRemoteBaseActivity;
import com.diasemi.bleremote.ui.main.config.ConfigFragment;
import com.diasemi.bleremote.ui.main.config.ConfigUpdateEvent;
import com.diasemi.bleremote.ui.main.config.ConnParamsButtonEvent;
import com.diasemi.bleremote.ui.main.config.PacketSizeButtonEvent;
import com.diasemi.bleremote.ui.main.input.BitRateEvent;
import com.diasemi.bleremote.ui.main.input.InputFragment;
import com.diasemi.bleremote.ui.main.input.PlayBackStateEvent;
import com.diasemi.bleremote.ui.main.input.StreamButtonEvent;
import com.diasemi.bleremote.ui.main.input.StreamControlEvent;
import com.diasemi.bleremote.ui.main.logs.ErrorEvent;
import com.diasemi.bleremote.ui.main.logs.LogsFragment;
import com.diasemi.bleremote.ui.main.remotecontrol.KeyPressEvent;
import com.diasemi.bleremote.ui.main.remotecontrol.RemoteControlFragment;
import com.diasemi.bleremote.ui.misc.DisclaimerFragment;
import com.diasemi.bleremote.ui.misc.InfoFragment;
import com.diasemi.bleremote.ui.searchlist.SearchListActivity;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.squareup.otto.Subscribe;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import butterknife.ButterKnife;
import butterknife.InjectView;

@SuppressWarnings("deprecation")
public class MainActivity extends BleRemoteBaseActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int DISPLAY_AVAILABLE_KEYWORDS_DELAY = 60000;

    private AudioManager audioManager;
    private boolean streamActive;
    private boolean pttPressed;
    private int decodeMode;
    private long lastKeywordsDisplay;
    private RemoteControlFragment remoteControlFragment;
    private InputFragment inputFragment;
    private LogsFragment logsFragment;
    private ConfigFragment configFragment;
    private InfoFragment infoFragment;
    private DisclaimerFragment disclaimerFragment;
    private Toolbar toolbar;
    private Drawer drawer;
    private int menuRemoteControlFragment, menuInputFragment, menuLogsFragment, menuConfigFragment, menuInfo, menuDisclaimer;
    private SecondaryDrawerItem disconnectButton;
    @InjectView(R.id.disconnected_overlay) View disconnectedOverlay;
    @InjectView(R.id.captured_image) ImageView capturedImage;
    private RuntimePermissionChecker permissionChecker;

    // LIFE CYCLE METHOD(S)

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);
        BusProvider.getInstance().register(this);
        permissionChecker = new RuntimePermissionChecker(this, savedInstanceState);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitleTextColor(Color.WHITE);
            toolbar.setTitle(R.string.app_name);
            toolbar.setSubtitle(R.string.title_remote);
            setSupportActionBar(toolbar);
        }

        int menuItemsTotal = 7;
        IDrawerItem[] drawerItems = new IDrawerItem[menuItemsTotal];
        int i = 0;
        drawerItems[i++] = new PrimaryDrawerItem().withName(R.string.title_remote).withIcon(GoogleMaterial.Icon.gmd_settings_remote);
        menuRemoteControlFragment = i;
        drawerItems[i++] = new PrimaryDrawerItem().withName(R.string.title_input).withIcon(GoogleMaterial.Icon.gmd_mic);
        menuInputFragment = i;
        drawerItems[i++] = new PrimaryDrawerItem().withName(R.string.title_logs).withIcon(GoogleMaterial.Icon.gmd_subject);
        menuLogsFragment = i;
        drawerItems[i++] = new PrimaryDrawerItem().withName(R.string.title_config).withIcon(GoogleMaterial.Icon.gmd_settings);
        menuConfigFragment = i;
        drawerItems[i++] = new DividerDrawerItem();
        drawerItems[i++] = new PrimaryDrawerItem().withName(R.string.title_information).withIcon(GoogleMaterial.Icon.gmd_info);
        menuInfo = i;
        drawerItems[i++] = new PrimaryDrawerItem().withName(R.string.title_disclaimer).withIcon(GoogleMaterial.Icon.gmd_info_outline);
        menuDisclaimer = i;

        disconnectButton = new SecondaryDrawerItem() {
            @Override
            public void onPostBindView(IDrawerItem drawerItem, View view) {
                super.onPostBindView(drawerItem, view);
                view.setBackgroundColor(getResources().getColor(R.color.button_color));
            }

            @Override
            @LayoutRes
            public int getLayoutRes() {
                return R.layout.disconnect_drawer_button;
            }
        };
        disconnectButton
                .withTextColor(ContextCompat.getColor(this, android.R.color.white))
                .withName(R.string.title_disconnect)
                .withIdentifier(300)
                .withEnabled(true);

        drawer = createNavDrawer(drawerItems);
        remoteControlFragment = new RemoteControlFragment();

        capturedImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                capturedImage.setVisibility(View.GONE);
            }
        });

        String mode = getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE).getString(Constants.PREF_LIST_BITRATE, getString(R.string.text_bitrate_default_value));
        String[] values = getResources().getStringArray(R.array.bitrate_values);
        decodeMode = Arrays.asList(values).indexOf(mode);

        audioManager = new AudioManager(this);
        Handler audioHandler = audioManager.getHandler();
        audioHandler.sendMessage(audioHandler.obtainMessage(Constants.AUDIOMANAGER_SET_MODE, decodeMode, 0));

        mDevice = getIntent().getStringExtra("device");
    }

    @Override
    protected void onDestroy() {
        BusProvider.getInstance().unregister(this);
        if (streamActive)
            mBleRemoteService.sendStreamEnable(false);
        mBleRemoteService.setAudioHandler(null);
        audioManager.getHandler().sendEmptyMessage(Constants.AUDIOMANAGER_RELEASE);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        permissionChecker.saveState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        permissionChecker.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            Bitmap bitmap = (Bitmap) data.getExtras().get("data");
            capturedImage.setImageBitmap(bitmap);
            capturedImage.setVisibility(View.VISIBLE);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen()) {
            drawer.closeDrawer();
        } else if (capturedImage.getVisibility() == View.VISIBLE) {
            capturedImage.setVisibility(View.GONE);
        } else {
            finish();
        }
    }

    public boolean checkPermission(String permission) {
        return permissionChecker.checkPermission(permission, null, null);
    }

    public AudioManager getAudioManager() {
        return audioManager;
    }

    public void setDecodeMode(int decodeMode) {
        if (!streamActive) {
            this.decodeMode = decodeMode;
            Handler audioHandler = audioManager.getHandler();
            audioHandler.sendMessage(audioHandler.obtainMessage(Constants.AUDIOMANAGER_SET_MODE, decodeMode, 0));
            mBleRemoteService.sendEncodeMode(decodeMode);
        }
    }

    public boolean isStreamActive() {
        return streamActive;
    }

    public boolean isPttPressed() {
        return pttPressed;
    }

    @Override
    public void onServiceConnected(final ComponentName componentName, final IBinder service) {
        super.onServiceConnected(componentName, service);
        mBleRemoteService.setAudioHandler(audioManager.getHandler());
        mBleRemoteService.sendEncodeMode(decodeMode);
        remoteControlFragment.setKeyLayout(mBleRemoteService.getInitialKeyLayout());
        changeFragment(getFragmentItem(1), 1);
    }

    @Override
    protected void onDeviceReady(BluetoothDevice device) {
        disconnectedOverlay.setVisibility(View.GONE);
        mBleRemoteService.sendEncodeMode(decodeMode);
        if (inputFragment != null)
            inputFragment.onDeviceReady();
        if (configFragment != null)
            configFragment.onDeviceReady();
    }

    @Override
    protected void onDeviceDisconnected(BluetoothDevice device) {
        disconnectedOverlay.setVisibility(View.VISIBLE);
        pttPressed = false;
        streamActive = false;
        if (remoteControlFragment != null)
            remoteControlFragment.resetKeyState();
        if (inputFragment != null)
            inputFragment.onDeviceDisconnected();
        if (configFragment != null)
            configFragment.onDeviceDisconnected();
    }

    @Override
    protected void onControlInput(final byte[] packet) {
        switch (packet[Constants.CONTROL_TYPE_OFFSET]) {
            case Constants.CONTROL_TYPE_STREAM:
                if (packet[Constants.CONTROL_STREAM_ENABLE_OFFSET] == 2)
                    break;
                boolean prevStreamActive = streamActive;
                streamActive = pttPressed = packet[Constants.CONTROL_STREAM_ENABLE_OFFSET] != 0;
                if (streamActive != prevStreamActive)
                    Utils.logMessage(this, streamActive ? "Audio stream ON" : "Audio stream OFF");
                BusProvider.getInstance().post(new StreamControlEvent(packet));
                break;

            case Constants.CONTROL_TYPE_KEY:
                BusProvider.getInstance().post(new KeyPressEvent(packet));
                break;

            case Constants.CONTROL_TYPE_STREAM_ERROR:
                BusProvider.getInstance().post(new ErrorEvent(packet));
                String msg = "RCU FIFO Errors: " + Arrays.toString(Arrays.copyOfRange(packet, 4, 10));
                Utils.logMessage(this, msg);
                Log.d(TAG, msg);
                break;

            default:
                break;
        }
    }

    @Override
    @SuppressLint("DefaultLocale")
    protected void onConfigurationUpdate(int mtu, int packetSize, int connectionInterval, int  slaveLatency, int supervisionTimeout) {
        Log.d(TAG, "onConfigurationUpdate");
        if (connectionInterval != -1 && slaveLatency != -1 && supervisionTimeout != -1) {
            String msg = String.format("Connection Parameters: interval = %.2fms, latency = %d events, timeout = %dms",
                    connectionInterval * 1.25, slaveLatency, supervisionTimeout * 10);
            Utils.logMessage(this, msg);
            Log.d(TAG, msg);
        }
        if (packetSize != -1 && mtu != -1) {
            String msg = String.format("Packet size: %d (MTU = %d)", packetSize, mtu);
            Utils.logMessage(this, msg);
            Log.d(TAG, msg);
        }
        BusProvider.getInstance().post(new ConfigUpdateEvent(mtu, packetSize, connectionInterval, slaveLatency, supervisionTimeout));
    }

    @Override
    protected void onBitrateReported(int bitrate) {
        BusProvider.getInstance().post(new BitRateEvent(bitrate));
    }

    @Override
    protected void onPlaybackState() {
        BusProvider.getInstance().post(new PlayBackStateEvent());
    }

    @Subscribe
    public void onStreamButtonEvent(final StreamButtonEvent event) {
        Log.d(TAG, "onStreamButtonEvent");
        streamActive = event.isChecked();
        Utils.logMessage(this, streamActive ? "Audio stream ON" : "Audio stream OFF");
        mBleRemoteService.sendStreamEnable(streamActive);
    }

    @Subscribe
    public void onPacketSizeButtonEvent(final PacketSizeButtonEvent event) {
        Log.d(TAG, String.format("Set packet size: max=%d, fixed=%d", event.getMax(), event.getFixed()));
        mBleRemoteService.updatePacketSize(event.getMax(), event.getFixed());
    }

    @Subscribe
    public void onConnParamsButtonEvent(final ConnParamsButtonEvent event) {
        Log.d(TAG, String.format("Update connection parameters: min=%d, max=%d, latency=%d, timeout=%d",
                event.getMinInterval(), event.getMaxInterval(), event.getSlaveLatency(), event.getSupervisionTimeout()));
        mBleRemoteService.updateConnectionParameters(event.getMinInterval(), event.getMaxInterval(), event.getSlaveLatency(), event.getSupervisionTimeout());
    }

    // Map keywords to voice commands
    private static class VoiceCommand {
        Pattern keyword;
        VoiceCommandAction action;

        VoiceCommand(String keyword, VoiceCommandAction action) {
            this.keyword = Pattern.compile("(?:"+keyword+")(\\s+.*)?", Pattern.CASE_INSENSITIVE);
            this.action = action;
        }
    }

    private interface VoiceCommandAction {
        boolean needsText(Matcher m);
        void execute(String text, Matcher m);
    }

    private class SearchAction implements VoiceCommandAction {
        String engine;

        SearchAction(String engine) {
            this.engine = engine;
        }

        @Override
        public boolean needsText(Matcher m) {
            return true;
        }

        @Override
        public void execute(String text, Matcher m) {
            search(engine, text);
        }
    }

    private VoiceCommandAction flashlightAction = new VoiceCommandAction() {
        @Override
        public boolean needsText(Matcher m) {
            return false;
        }

        @Override
        public void execute(String text, Matcher m) {
            boolean on = "on ".equalsIgnoreCase(m.group(1)) || " on".equalsIgnoreCase(m.group(2));
            boolean off = "off ".equalsIgnoreCase(m.group(1)) || " off".equalsIgnoreCase(m.group(2));
            if (!on && !off) {
                Toast.makeText(MainActivity.this, R.string.flashlight_incomplete, Toast.LENGTH_SHORT).show();
                return;
            }
            if (Build.VERSION.SDK_INT >= 23) {
                Log.d(TAG, "Flashlight " + (on ? "ON" : "OFF"));
                CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
                try {
                    if (cameraManager.getCameraIdList().length != 0 && getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH))
                        cameraManager.setTorchMode(cameraManager.getCameraIdList()[0], on);
                    else
                        Toast.makeText(MainActivity.this, R.string.flashlight_unavailable, Toast.LENGTH_SHORT).show();
                } catch (CameraAccessException | IllegalArgumentException e) {
                    Log.e(TAG, "Flashlight action error", e);
                    Toast.makeText(MainActivity.this, R.string.flashlight_error, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, R.string.flashlight_unsupported, Toast.LENGTH_SHORT).show();
            }
        }
    };

    private VoiceCommandAction cameraAction = new VoiceCommandAction() {
        @Override
        public boolean needsText(Matcher m) {
            return false;
        }

        @Override
        public void execute(String text, Matcher m) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) != null)
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        }
    };

    private VoiceCommand[] voiceCommands = new VoiceCommand[] {
        // Search engines
        new VoiceCommand("search(?: for)?|google",  new SearchAction("Google")),
        new VoiceCommand("imdb|movies?",            new SearchAction("IMDB")),
        new VoiceCommand("youtube|videos?",         new SearchAction("YouTube")),
        new VoiceCommand("rotten tomatoes",         new SearchAction("RottenTomatoes")),
        new VoiceCommand("chrome|web",              new SearchAction(null)),
        // Other actions
        new VoiceCommand("(?:turn )?(on |off )?(?:the )?(?:flashlight|flash|light)( on| off)?", flashlightAction),
        new VoiceCommand("(?:open )?(?:the )?camera|take (?:a )?(?:pic(?:ture)?|photo)", cameraAction),
    };

    @Override
    protected void onSpeechRecognitionResult(final String transcript, final String confidence, boolean partial) {
        SharedPreferences preferences = getSharedPreferences(Constants.PREFERENCES_NAME, MODE_PRIVATE);
        String searchEngine = preferences.getString(Constants.PREF_LIST_SEARCH_ENGINE, "Keyword");
        String searchQuery = "";
        if (!TextUtils.isEmpty(transcript)) {
            searchQuery = transcript;
        }
        Utils.logMessage(this, "Voice Recognition: " + (searchQuery.isEmpty() ? "EMPTY" : "\"" + searchQuery + "\""));
        if (inputFragment != null) {
            inputFragment.setVoiceRecText(searchQuery);
            if (inputFragment.isLiveMode())
                return;
        }
        if (!visible || partial || TextUtils.isEmpty(searchQuery)) {
            return; // We do not need to do any speech recognition.
        }
        if (searchEngine.equals("Keyword")) {
            // Check known keywords
            for (VoiceCommand voiceCommand: voiceCommands) {
                Matcher m = voiceCommand.keyword.matcher(searchQuery);
                if (m.matches()) {
                    String text = m.group(m.groupCount());
                    text = text != null && !text.isEmpty() ? text.substring(1) : ""; // remove leading space
                    if (text.isEmpty() && voiceCommand.action.needsText(m))
                        Toast.makeText(this, R.string.missing_keyword_text, Toast.LENGTH_SHORT).show();
                    else
                        voiceCommand.action.execute(text, m);
                    return;
                }
            }
            // Unknown keyword
            long now = new Date().getTime();
            if (now - lastKeywordsDisplay > DISPLAY_AVAILABLE_KEYWORDS_DELAY) {
                lastKeywordsDisplay = now;
                Toast.makeText(this, R.string.unknown_keyword, Toast.LENGTH_LONG).show();
            }
        } else {
            search(searchEngine, searchQuery);
        }
    }

    private void search(final String searchEngine, final String searchQuery) {
        Intent intent;
        if (!TextUtils.isEmpty(searchEngine)) {
            if (searchQuery.isEmpty())
                return;
            SharedPreferences preferences = getSharedPreferences(Constants.PREFERENCES_NAME, MODE_PRIVATE);
            boolean firstMatch = preferences.getBoolean(Constants.PREF_CHECKBOX_FIRST_MATCH, false);
            Bundle extras = new Bundle();
            extras.putString(Constants.EXTRA_MESSAGE, searchQuery);
            extras.putString(Constants.EXTRA_ENGINE, searchEngine);
            extras.putBoolean(Constants.EXTRA_FIRST_MATCH, firstMatch);
            intent = new Intent(this, SearchListActivity.class);
            intent.putExtras(extras);
            startActivity(intent);
            this.overridePendingTransition(0, 0);
        } else {
            if (searchQuery.isEmpty())
                return;
            try {
                String url = "https://www.google.com/search?ie=utf-8&oe=utf-8&q=";
                String query = URLEncoder.encode(searchQuery, "UTF-8");
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url + query));
                startActivity(intent);
            } catch (UnsupportedEncodingException e) {
                Toast.makeText(this, R.string.request_url_encoding_error, Toast.LENGTH_LONG).show();
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.no_activity_for_url_view, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "search()", e);
            }
        }
    }

    private Drawer createNavDrawer(IDrawerItem[] drawerItems) {
        AccountHeader accountHeader = new AccountHeaderBuilder()
                .withActivity(this)
                .withHeaderBackground(R.color.navigation_bar_background)
                .addProfiles(
                        new ProfileDrawerItem().withName(getString(R.string.app_name)).withEmail(getString(R.string.dialog_semiconductor))
                )
                .withProfileImagesClickable(false)
                .withProfileImagesVisible(false)
                .withSelectionListEnabledForSingleProfile(false)
                .withTextColor(getResources().getColor(android.R.color.white))
                .build();

        Drawer drawer = new DrawerBuilder().withActivity(this)
                .withAccountHeader(accountHeader)
                .withToolbar(toolbar)
                .addDrawerItems(drawerItems)
                .addStickyDrawerItems(disconnectButton)
                .withStickyFooterShadow(false)
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        Log.d(TAG, "Menu position: " + String.valueOf(position));
                        if (position == menuRemoteControlFragment) {
                            toolbar.setSubtitle(R.string.title_remote);
                        } else if (position == menuInputFragment) {
                            toolbar.setSubtitle(R.string.title_input);
                        } else if (position == menuLogsFragment) {
                            toolbar.setSubtitle(R.string.title_logs);
                        } else if (position == menuConfigFragment) {
                            toolbar.setSubtitle(R.string.title_config);
                        } else if (position == menuInfo) {
                            toolbar.setSubtitle(R.string.title_information);
                        } else if (position == menuDisclaimer) {
                            toolbar.setSubtitle(R.string.title_disclaimer);
                        }
                        if (position == -1) {
                            destroyFragments();
                            finish();
                        } else {
                            changeFragment(getFragmentItem(position), position);
                        }
                        return false;
                    }
                })
                .build();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            drawer.getActionBarDrawerToggle().setDrawerIndicatorEnabled(true);
        }

        return drawer;
    }

    public Fragment getFragmentItem(final int position) {
        if (position == menuRemoteControlFragment) {
            if (remoteControlFragment == null) {
                remoteControlFragment = new RemoteControlFragment();
            }
            return remoteControlFragment;
        } else if (position == menuInputFragment) {
            if (inputFragment == null) {
                inputFragment = new InputFragment();
            }
            return inputFragment;
        } else if (position == menuLogsFragment) {
            if (logsFragment == null) {
                logsFragment = new LogsFragment();
            }
            return logsFragment;
        } else if (position == menuConfigFragment) {
            if (configFragment == null) {
                configFragment = new ConfigFragment();
            }
            return configFragment;
        } else if (position == menuInfo) {
            if (infoFragment == null) {
                infoFragment = new InfoFragment();
            }
            return infoFragment;
        } else if (position == menuDisclaimer) {
            if (disclaimerFragment == null) {
                disclaimerFragment = new DisclaimerFragment();
            }
            return disclaimerFragment;
        } else {
            return new Fragment();
        }
    }

    public void changeFragment(Fragment newFragment, int position) {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        String name = makeFragmentName(R.id.fragment_container, position);
        Fragment previousFragment = fragmentManager.findFragmentById(R.id.fragment_container);

        if (previousFragment != null) {
            fragmentTransaction.detach(previousFragment);
        }

        Fragment fragment = fragmentManager.findFragmentByTag(name);
        if (fragment != null) {
            Log.v(TAG, "Attaching item #" + position + ": f=" + fragment);
            fragmentTransaction.attach(fragment);
        } else {

            Log.v(TAG, "Adding item #" + position + ": f=" + newFragment);
            fragmentTransaction.add(R.id.fragment_container, newFragment,
                    makeFragmentName(R.id.fragment_container, position));
        }
        fragmentTransaction.commit();
    }

    private static String makeFragmentName(int viewId, long id) {
        return "diasemi:switcher:" + viewId + ":" + id;
    }

    private void destroyFragments() {
        remoteControlFragment = null;
        inputFragment = null;
        logsFragment = null;
        configFragment = null;
    }
}
