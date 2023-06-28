/*
 *******************************************************************************
 *
 * Copyright (C) 2017-2018 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.service;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.diasemi.bleremote.Constants;
import com.diasemi.bleremote.Utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Service for managing connection and data communication with a GATT server hosted on a BLE Remote device.
 */
public class BleRemoteService extends Service {
    private static final String TAG = BleRemoteService.class.getSimpleName();

    // Time to wait before trying to reconnect
    private static final int RECONNECTION_DELAY_MS = 3000;
    // Time to wait before starting service discovery after pending system HID connection
    // is complete. This is needed in order not to interfere with the connector service.
    private static final int SYSTEM_HID_CONNECTION_DELAY_MS = 1000;
    // System HID service connection timeout
    private static final int HID_CONNECTION_TIMEOUT = 15000;

    // Writing to the client characteristic configuration descriptor of HID Report characteristics
    // may not be necessary, because the Android BLE stack should already have done this.
    private static final boolean WRITE_REPORT_CLIENT_CONFIGURATION = false;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    // HID (Human Interface Device) Profile
    private static final UUID HID_SERVICE = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb"); // HID Service
    private static final UUID HID_REPORT_CHAR = UUID.fromString("00002a4d-0000-1000-8000-00805f9b34fb");  // HID Report Characteristic
    private static final UUID HID_REPORT_REFERENCE = UUID.fromString("00002908-0000-1000-8000-00805f9b34fb"); // HID Report Reference Descriptor
    private static final int HID_REPORT_TYPE_INPUT = 1;
    private static final int HID_REPORT_TYPE_OUTPUT = 2;
    // Device information service
    private static final UUID SERVICE_DEVICE_INFO = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    private static final UUID DEVICE_INFO_SW_REVISION = UUID.fromString("00002A28-0000-1000-8000-00805f9b34fb");
    private static final UUID DEVICE_INFO_PNP_ID = UUID.fromString("00002A50-0000-1000-8000-00805f9b34fb");
    // Client Configuration Descriptor
    private static final UUID CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Custom profile
    private static final UUID CUSTOM_SERVICE = UUID.fromString("bc1d108b-5094-4c03-b6b0-3d912d9903d8");
    private static final UUID CUSTOM_STREAM_CONFIG = UUID.fromString("fdf9289f-9c21-4dd7-bfc6-0e3d87ac9546");
    private static final UUID CUSTOM_STREAM_CONTROL = UUID.fromString("0c0541ae-efe8-4771-b2c2-dc0b0f6fbc6a");
    private static final UUID CUSTOM_STREAM_DATA = UUID.fromString("8d2c0991-0d20-4ce4-8e87-613224073dd1");

    // Ble Remote HID predefined Report IDs (Audio)
    private static final int HID_STREAM_CONTROL_OUTPUT_REPORT = 4;
    private static final int HID_STREAM_CONTROL_INPUT_REPORT = 5;
    private static final int HID_STREAM_DATA_REPORT_1 = 6;
    private static final int HID_STREAM_DATA_REPORT_2 = 7;
    private static final int HID_STREAM_DATA_REPORT_3 = 8;
    private static final int HID_STREAM_DATA_MIN_REPORT = 6;
    private static final int HID_STREAM_DATA_MAX_REPORT = 8;
    private static final int HID_STREAM_DATA_REPORT_CUSTOM = 0x100;

    // Data
    private SharedPreferences preferences;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothDevice mBluetoothDevice;
    private int mConnectionState = STATE_DISCONNECTED;
    private boolean connectionEncrypted;
    private boolean requireEncryption;
    private Map<Integer, Integer> instanceToReportID = new HashMap<>();
    private int audioReportID = -1;
    private BluetoothGattCharacteristic readConfigCharacteristic = null;
    private BluetoothGattCharacteristic controlWriteCharacteristic = null;
    private BluetoothGattCharacteristic audioReportCharacteristic = null;
    private ArrayDeque<BluetoothGattDescriptor> clientConfigDescriptors;
    private int expectedReport = 0; // for checking audio data notifications sequence
    private int features;
    private boolean setAudioMode;
    private int audioMode = Constants.AUDIO_MODE_AUTOMATIC;
    private int initialKeyLayout;
    private int mtu = -1;
    private int packetSize = -1;
    private int connectionInterval = -1;
    private int slaveLatency = -1;
    private int supervisionTimeout = -1;
    private String firmwareVersion;
    private int pnpVendor;
    private int pnpProduct;
    private int pnpVersion;
    private Handler mHandler = null;
    private Handler mReconnectHandler = null;
    private Handler mBitrateHandler = null;
    private Handler mAudioHandler = null;
    private int numBytesReceived = 0;
    private int prevBytesReceived = 0;
    private long timeBitrateReported = 0;
    private boolean mScanIsRunning = false;
    private boolean deviceReady = false;
    private boolean configRead = false;
    private boolean customProfile = false;
    private boolean isHidDevice = false;
    private boolean pendingHidConnection = false;
    private boolean noBluetoothPrivileged = false;
    private BluetoothPrivilegedCallback bluetoothPrivilegedCallback;

    // Hidden API
    private Method removeBondMethod; // BluetoothDevice.removeBond()
    private Method isDeviceEncryptedMethod; // BluetoothDevice.isEncrypted() (API >= 22)
    private int HID_PROFILE_ID; // BluetoothProfile.INPUT_DEVICE
    private Class bluetoothInputDeviceClass; // android.bluetooth.BluetoothInputDevice
    private String ACTION_HID_CONNECTION_STATE_CHANGED; // BluetoothInputDevice.ACTION_CONNECTION_STATE_CHANGED
    private Method hidConnectDeviceMethod; // BluetoothInputDevice.connect()
    private BluetoothProfile hidProfile; // Actual object will be BluetoothInputDevice

    // GATT read/write queues (needed because there can't be more than one pending gatt operations)
    // NOTE: These queues must also be synced with each other. Apart from the control write queue,
    // the others are only used during initialization, where they are synced. Control writes are
    // disabled during initialization and synced through the queue afterwards.
    private ConcurrentLinkedQueue<BluetoothGattCharacteristic> mReadQueue = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<BluetoothGattDescriptor> mDescReadQueue = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<BluetoothGattDescriptor> mDescWriteQueue = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<byte[]> mControlWriteQueue = new ConcurrentLinkedQueue<>();

    /**
     * Binder stuff to connect BleRemoteService to BleActivity
     */
    public class LocalBinder extends Binder {

        public BleRemoteService getService() {
            return BleRemoteService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(final Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        // After using a given device, you should make sure that
        // BluetoothGatt.close() is called such that resources are cleaned up
        // properly. In this particular example, close() is invoked when the UI
        // is disconnected from the Service.
        Log.i(TAG, "UI unbind from ble service, calling close");
        close();
        mBitrateHandler.removeCallbacksAndMessages(null);
        return super.onUnbind(intent);
    }

    /**
     * Service lifecycle callbacks
     */
    @Override
    public void onCreate() {
        preferences = getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE);
        mHandler = new Handler();
        mBitrateHandler = new Handler();
        mReconnectHandler = new Handler();
        initBluetooth();
        initHiddenApi();

        if (HID_PROFILE_ID != 0 && mBluetoothAdapter != null) {
            Log.d(TAG, "Get system HID service");
            mBluetoothAdapter.getProfileProxy(this, hidServiceListener, HID_PROFILE_ID);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (ACTION_HID_CONNECTION_STATE_CHANGED != null)
            filter.addAction(ACTION_HID_CONNECTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        mBitrateHandler.removeCallbacksAndMessages(null);
        mReconnectHandler.removeCallbacksAndMessages(null);
        unregisterReceiver(mReceiver);
        if (hidProfile != null)
            mBluetoothAdapter.closeProfileProxy(HID_PROFILE_ID, hidProfile);
    }

    private void initHiddenApi() {
        try {
            removeBondMethod = BluetoothDevice.class.getMethod("removeBond", (Class[]) null);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "BluetoothDevice.removeBond method not found", e);
        }
        if (Build.VERSION.SDK_INT >= 22) {
            try {
                isDeviceEncryptedMethod = BluetoothDevice.class.getMethod("isEncrypted", (Class[]) null);
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "BluetoothDevice.isEncrypted method not found", e);
            }
        }
        try {
            HID_PROFILE_ID = BluetoothProfile.class.getField(Build.VERSION.SDK_INT < 28 ? "INPUT_DEVICE" : "HID_HOST").getInt(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            Log.e(TAG, "BluetoothProfile.INPUT_DEVICE field not found", e);
        }
        try {
            bluetoothInputDeviceClass = Class.forName(Build.VERSION.SDK_INT < 28 ? "android.bluetooth.BluetoothInputDevice" : "android.bluetooth.BluetoothHidHost");
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "BluetoothInputDevice class not found", e);
        }
        if (bluetoothInputDeviceClass != null) {
            try {
                ACTION_HID_CONNECTION_STATE_CHANGED = (String) bluetoothInputDeviceClass.getField("ACTION_CONNECTION_STATE_CHANGED").get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                Log.e(TAG, "BluetoothInputDevice.ACTION_CONNECTION_STATE_CHANGED field not found", e);
            }
            try {
                hidConnectDeviceMethod = bluetoothInputDeviceClass.getMethod("connect", BluetoothDevice.class);
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "BluetoothInputDevice.connect method not found", e);
            }
        }
    }

    private BluetoothProfile.ServiceListener hidServiceListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            Log.d(TAG, "Connected to system HID service");
            hidProfile = proxy;
        }

        @Override
        public void onServiceDisconnected(int profile) {
            Log.d(TAG, "Disconnected from system HID service");
            hidProfile = null;
        }
    };

    private void reset() {
        clearQueues();
        deviceReady = false;
        configRead = false;
        customProfile = false;
        isHidDevice = false;
        pendingHidConnection = false;
        initialKeyLayout = 0;
        features = 0;
        setAudioMode = false;
        audioMode = Constants.AUDIO_MODE_AUTOMATIC;
        packetSize = -1;
        mtu = -1;
        connectionInterval = -1;
        slaveLatency = -1;
        supervisionTimeout = -1;
        instanceToReportID.clear();
        controlWriteCharacteristic = null;
        audioReportID = 0;
        audioReportCharacteristic = null;
        clientConfigDescriptors = null;
        firmwareVersion = null;
        pnpVendor = pnpProduct = pnpVersion = 0;
        noBluetoothPrivileged = false;
    }

    private void clearQueues() {
        mHandler.removeCallbacks(delayedInitOp);
        mReadQueue.clear();
        mDescReadQueue.clear();
        mDescWriteQueue.clear();
        mControlWriteQueue.clear();
    }

    private Runnable delayedInitOp = new Runnable() {
        @Override
        public void run() {
            startNextInitOp(mBluetoothGatt);
        }
    };

    private void startNextInitOp(BluetoothGatt gatt) {
        // Check for connection encryption
        if (requireEncryption && !connectionEncrypted) {
            int delay = 1000;
            connectionEncrypted = true; // assume encryption after delay, if API missing or failed
            if (isDeviceEncryptedMethod != null) {
                try {
                    connectionEncrypted = (Boolean) isDeviceEncryptedMethod.invoke(gatt.getDevice(), (Object[]) null);
                    delay = connectionEncrypted ? 0 : 200;
                } catch (IllegalAccessException | InvocationTargetException e) {
                    Log.e(TAG, "BluetoothDevice.isEncrypted call failed", e);
                }
            }
            if (delay > 0) {
                Log.d(TAG, "No encryption yet. Delay init operations.");
                mHandler.postDelayed(delayedInitOp, delay);
                return;
            }
        }

        boolean success = true;
        // Read HID report reference
        if (!mDescReadQueue.isEmpty())
            success = gatt.readDescriptor(mDescReadQueue.poll());
        // Read Config / Device info
        else if (!mReadQueue.isEmpty())
            success = gatt.readCharacteristic(mReadQueue.poll());
        // Write client configuration
        else if (!mDescWriteQueue.isEmpty())
            success = gatt.writeDescriptor(mDescWriteQueue.poll());
        // No init operation, device is ready
        else {
            deviceReady = true;
            Log.d(TAG, "Device ready");
            log("Device ready");
            broadcastUpdate(Constants.ACTION_GATT_DEVICE_READY, gatt.getDevice());
        }
        if (!success)
            Log.e(TAG, "Init operation failed to start");
    }

    private Runnable hidConnectionTimeout = new Runnable() {
        @Override
        public void run() {
            Log.e(TAG, "HID connection timeout");
            broadcastUpdate(Constants.ACTION_HID_CONNECTION_TIMEOUT, mBluetoothDevice);
        }
    };

    private Runnable hidConnectionDiscoveryDelay = new Runnable() {
        @Override
        public void run() {
            pendingHidConnection = false;
            mBluetoothGatt.discoverServices();
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null)
                return;
            final String action = intent.getAction();
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            switch (action) {
                case BluetoothDevice.ACTION_ACL_CONNECTED:
                    if (device.equals(mBluetoothDevice)) {
                        mReconnectHandler.removeCallbacksAndMessages(null);
                        mReconnectHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                synchronized (mGattCallback) {
                                    if (mConnectionState == STATE_DISCONNECTED) {
                                        Log.d(TAG, "Reconnecting to device");
                                        reset();
                                        if (mBluetoothGatt != null)
                                            mBluetoothGatt.close();
                                        mBluetoothGatt = device.connectGatt(BleRemoteService.this, false, mGattCallback);
                                    }
                                }
                            }
                        }, RECONNECTION_DELAY_MS);
                    }
                    break;

                case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                    if (device.equals(mBluetoothDevice))
                        mReconnectHandler.removeCallbacksAndMessages(null);
                    break;

                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, 0);
                    String status = state == BluetoothDevice.BOND_BONDED ? "Paired with " : state == BluetoothDevice.BOND_NONE ? "Unpaired from " : null;
                    if (status != null) {
                        String msg = status + device.getName() + " [" + device.getAddress() + "]";
                        Log.d(TAG, msg);
                        log(msg);
                    }
                    broadcastUpdate(Constants.ACTION_SCAN_DEVICE, device, 0);

                    // Check for pending pairing procedure or system HID connection
                    boolean autoPair = preferences.getBoolean(Constants.PREF_AUTO_PAIRING, Constants.DEFAULT_AUTO_PAIRING);
                    boolean autoHid = preferences.getBoolean(Constants.PREF_AUTO_INIT_SYSTEM_HID, Constants.DEFAULT_AUTO_INIT_SYSTEM_HID);
                    if (autoPair || autoHid) {
                        String prefix = autoHid ? "HID auto connection" : "Auto pairing";
                        synchronized (mGattCallback) {
                            if (mConnectionState == STATE_CONNECTING && device.equals(mBluetoothDevice)) {
                                if (state == BluetoothDevice.BOND_BONDED) {
                                    Log.d(TAG, prefix + ": Device bonded. Connecting...");
                                    mBluetoothGatt = mBluetoothDevice.connectGatt(BleRemoteService.this, false, mGattCallback);
                                } else if (state == BluetoothDevice.BOND_NONE) {
                                    Log.e(TAG, prefix + ": Pairing failed.");
                                    broadcastUpdate(Constants.ACTION_GATT_DISCONNECTED, device);
                                }
                            }
                        }
                    }
                    break;

                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                    if (state == BluetoothAdapter.STATE_ON) {
                        log("Bluetooth ON");
                    } else if (state == BluetoothAdapter.STATE_OFF) {
                        log("Bluetooth OFF");
                    } else if (state == BluetoothAdapter.STATE_TURNING_OFF) {
                        if (!isDisconnected())
                            broadcastUpdate(Constants.ACTION_GATT_DISCONNECTED, mBluetoothDevice);
                        BluetoothDevice keepCurrentDevice = mBluetoothDevice; // keep for reconnection
                        close();
                        mBluetoothDevice = keepCurrentDevice;
                    }
                    break;

                default:
                    if (ACTION_HID_CONNECTION_STATE_CHANGED != null && ACTION_HID_CONNECTION_STATE_CHANGED.equals(action)) {
                        state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0);
                        status = state == BluetoothProfile.STATE_CONNECTED ? "connected" : state == BluetoothProfile.STATE_DISCONNECTED ? "disconnected" : null;
                        if (status != null)
                            Log.d(TAG, "HID device " + device.getName() + " [" + device.getAddress() + "] " + status);

                        // Check for pending system HID connection
                        if (preferences.getBoolean(Constants.PREF_AUTO_INIT_SYSTEM_HID, Constants.DEFAULT_AUTO_INIT_SYSTEM_HID)) {
                            if (state == BluetoothProfile.STATE_CONNECTED && pendingHidConnection && device.equals(mBluetoothDevice)) {
                                mHandler.removeCallbacks(hidConnectionTimeout);
                                Log.d(TAG, "HID auto connection: Pending connection complete");
                                mHandler.postDelayed(hidConnectionDiscoveryDelay, SYSTEM_HID_CONNECTION_DELAY_MS);
                            }
                        }
                    }
                    break;
            }
        }
    };

    public interface BluetoothPrivilegedCallback {
        void noBluetoothPrivileged();
    }

    public void checkBluetoothPrivileged(BluetoothPrivilegedCallback callback) {
        if (noBluetoothPrivileged)
            callback.noBluetoothPrivileged();
        else
            bluetoothPrivilegedCallback = callback;
    }

    /**
     * ********* Scanning for devices
     */

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            Log.i(TAG, "New LE Device: " + device.getName() + " @ " + rssi);
            broadcastUpdate(Constants.ACTION_SCAN_DEVICE, device, rssi);
        }
    };

    // Access function to start scan
    @SuppressWarnings("deprecation")
    public void startScan() {
        mBluetoothAdapter.startLeScan(new UUID[]{HID_SERVICE}, mLeScanCallback);
        mScanIsRunning = true;
    }

    // Access function to stop scan
    @SuppressWarnings("deprecation")
    public void stopScan() {
        if (!mScanIsRunning)
            return;
        mBluetoothAdapter.stopLeScan(mLeScanCallback);
        mScanIsRunning = false;
    }

    private static final int ALL_CONNECTION_STATES[] = new int[] {
            BluetoothProfile.STATE_CONNECTING,
            BluetoothProfile.STATE_CONNECTED,
            BluetoothProfile.STATE_DISCONNECTING,
            BluetoothProfile.STATE_DISCONNECTED,
    };

    public List<BluetoothDevice> getHidDevices() {
        // If we are not connected to the HID service, return bonded devices,
        // else return all known HID devices that are currently bonded.
        List<BluetoothDevice> bondedDevices = new ArrayList<>(mBluetoothAdapter.getBondedDevices());
        if (hidProfile == null)
            return bondedDevices;
        List<BluetoothDevice> hidDevices = hidProfile.getDevicesMatchingConnectionStates(ALL_CONNECTION_STATES);
        Iterator<BluetoothDevice> i = hidDevices.iterator();
        while (i.hasNext()) {
            BluetoothDevice device = i.next();
            if (device.getBondState() == BluetoothDevice.BOND_NONE)
                i.remove();
        }
        return hidDevices;
    }

    /**
     * ***********************************************************************************
     * BluetoothGattCallback Implements callback methods for GATT events that the app cares about.
     * For example, connection change and services discovered.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            int reportID = -1;
            if (!customProfile) {
                reportID = instanceToReportID.get(characteristic.getInstanceId());
                if (reportID == audioReportID)
                    reportID = HID_STREAM_DATA_REPORT_CUSTOM;
            } else {
                UUID uuid = characteristic.getUuid();
                if (CUSTOM_STREAM_CONTROL.equals(uuid))
                    reportID = HID_STREAM_CONTROL_INPUT_REPORT;
                else if (CUSTOM_STREAM_DATA.equals(uuid))
                    reportID = HID_STREAM_DATA_REPORT_CUSTOM;
            }

            switch (reportID) {
                case HID_STREAM_CONTROL_INPUT_REPORT:
                    byte control[] = characteristic.getValue();
                    Log.d(TAG, "Control input: " + Arrays.toString(control));
                    processAudioControlInput(gatt, control);
                    broadcastUpdate(Constants.ACTION_CONTROL_INPUT, control);
                    return;

                case HID_STREAM_DATA_REPORT_1:
                case HID_STREAM_DATA_REPORT_2:
                case HID_STREAM_DATA_REPORT_3:
                    // Check audio report sequence
                    if (expectedReport == 0) {
                        expectedReport = reportID;
                    } else {
                        ++expectedReport;
                    }
                    if (expectedReport > HID_STREAM_DATA_MAX_REPORT) {
                        expectedReport = HID_STREAM_DATA_MIN_REPORT;
                    }
                    if (expectedReport != reportID) {
                        Log.w(TAG, String.format("Packet sequence interruption: expected %d, received %d", expectedReport, reportID));
                        expectedReport = reportID;
                    }
                    // FALL THROUGH
                case HID_STREAM_DATA_REPORT_CUSTOM:
                    byte packet[] = characteristic.getValue();
                    sendAudioData(packet);

                    // Enable bitrate reports
                    if (numBytesReceived == 0)
                        startBitrateReporter();
                    numBytesReceived += packet.length;
                    return;

                default:
                    Log.w(TAG, "Warning, unknown HID report received: ID=" + reportID);
                    break;
            }
        }

        @Override
        public void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            Log.i(TAG, "OnRead " + characteristic.getUuid().toString() + ", id=" + characteristic.getInstanceId() + ", status=" + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                UUID uuid = characteristic.getUuid();
                if (HID_REPORT_CHAR.equals(uuid) && instanceToReportID.get(characteristic.getInstanceId()) == HID_STREAM_CONTROL_INPUT_REPORT
                        || CUSTOM_STREAM_CONFIG.equals(uuid)) {
                    Log.d(TAG, "Control input read: " + Arrays.toString(characteristic.getValue()));
                    processAudioControlInput(gatt, characteristic.getValue());
                    configRead = true;
                } else if (DEVICE_INFO_PNP_ID.equals(uuid)) {
                    ByteBuffer pnp = ByteBuffer.allocate(characteristic.getValue().length).put(characteristic.getValue()).order(ByteOrder.LITTLE_ENDIAN);
                    pnpVendor = pnp.getShort(1) & 0xffff;
                    pnpProduct = pnp.getShort(3) & 0xffff;
                    pnpVersion = pnp.getShort(5) & 0xffff;
                    Log.d(TAG, String.format("RCU PNP ID: %04x %04x %04x", pnpVendor, pnpProduct, pnpVersion));
                } else if (DEVICE_INFO_SW_REVISION.equals(uuid)) {
                    firmwareVersion = characteristic.getStringValue(0).replace("v_", "");
                    Log.d(TAG, "RCU software revision: " + firmwareVersion);
                }
            }

            startNextInitOp(gatt);
        }

        @Override
        public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            Log.i(TAG, "OnWrite " + characteristic.getUuid().toString() + ", id=" + characteristic.getInstanceId() + ", status=" + status);

            // Initiate next operation
            synchronized (mControlWriteQueue) {
                mControlWriteQueue.poll();
                while (!mControlWriteQueue.isEmpty()) {
                    byte[] control = mControlWriteQueue.peek();
                    Log.d(TAG, "Write control: " + Arrays.toString(control));
                    controlWriteCharacteristic.setValue(control);
                    if (mBluetoothGatt.writeCharacteristic(controlWriteCharacteristic))
                        break;
                    Log.e(TAG, "Write control failed to start");
                    mControlWriteQueue.poll();
                }
            }
        }

        @Override
        public synchronized void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            Log.i(TAG, "Connection state change: " + (newState == BluetoothGatt.STATE_CONNECTED ? "connected" : "disconnected") + ", status=" + status);

            try {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to GATT server.");
                    mReconnectHandler.removeCallbacksAndMessages(null);
                    boolean wasReady = deviceReady && isHidDevice;
                    deviceReady = false; // reset temporarily to read config
                    requireEncryption = isHidDevice || preferences.getBoolean(Constants.PREF_AUTO_PAIRING, Constants.DEFAULT_AUTO_PAIRING);
                    connectionEncrypted = false;
                    mConnectionState = STATE_CONNECTED;
                    logConnectionState();
                    broadcastUpdate(Constants.ACTION_GATT_CONNECTED, gatt.getDevice());
                    if (!wasReady) {
                        Log.d(TAG, "Starting service discovery");
                        gatt.discoverServices();
                    } else {
                        mReadQueue.add(readConfigCharacteristic);
                        startNextInitOp(gatt);
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from GATT server.");
                    clearQueues();
                    mConnectionState = STATE_DISCONNECTED;
                    logConnectionState();
                    broadcastUpdate(Constants.ACTION_GATT_DISCONNECTED, gatt.getDevice());
                } else {
                    Log.i(TAG, "Unhandled OnConnectionStateChange " + newState + " " + status);
                }
            } catch (Exception e) {
                // Can't actually catch the DeadObjectException itself for some reason...*shrug*.
                // if( e instanceof DeadObjectException )
                Log.e(TAG, "Caught Exception on bluetooth onConnectionStateChange");
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.i(TAG, "onDescriptorRead " + descriptor.getUuid()
                    + ", char " + descriptor.getCharacteristic().getUuid().toString() + ", id=" + descriptor.getCharacteristic().getInstanceId()
                    + ", status=" + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
                int id = descriptor.getValue()[0] & 0xff;
                int type = descriptor.getValue()[1] & 0xff;
                int instance = descriptor.getCharacteristic().getInstanceId();
                Log.d(TAG, "HID report ["  + instance +"]: ID=" + id + ", type=" + type);
                instanceToReportID.put(instance, id);

                // Check for known report IDs
                boolean setNotify = false;
                switch (id) {
                    case HID_STREAM_CONTROL_OUTPUT_REPORT:
                        Log.d(TAG, "Found stream control output report");
                        controlWriteCharacteristic = characteristic;
                        break;
                    case HID_STREAM_CONTROL_INPUT_REPORT:
                        Log.d(TAG, "Found stream control input report");
                        readConfigCharacteristic = characteristic;
                        mReadQueue.add(characteristic); // Read configuration
                        setNotify = true;
                        break;
                    case HID_STREAM_DATA_REPORT_1:
                        Log.d(TAG, "Found stream data audio report 1");
                        setNotify = true;
                        break;
                    case HID_STREAM_DATA_REPORT_2:
                        Log.d(TAG, "Found stream data audio report 2");
                        setNotify = true;
                        break;
                    case HID_STREAM_DATA_REPORT_3:
                        Log.d(TAG, "Found stream data audio report 3");
                        setNotify = true;
                        break;
                }

                // Enable notifications for input reports
                if (type == HID_REPORT_TYPE_INPUT) {
                    // Workaround for Android 4 descriptor discovery issue
                    if (Build.VERSION.SDK_INT <= 19 && clientConfigDescriptors != null) {
                        if (clientConfigDescriptors.isEmpty())
                            Log.e(TAG, "Missing client configuration descriptor");
                        else
                            characteristic.addDescriptor(clientConfigDescriptors.poll());
                    }
                    // Enable local notifications
                    if (setNotify)
                        gatt.setCharacteristicNotification(characteristic, true);
                    // Enable remote notifications
                    if (WRITE_REPORT_CLIENT_CONFIGURATION) {
                        BluetoothGattDescriptor ccc = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR);
                        if (ccc != null) {
                            Log.i(TAG, "Found client configuration descriptor for report ID " + id);
                            ccc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            mDescWriteQueue.add(ccc);
                        }
                    }
                }
            }

            startNextInitOp(gatt);
        }

        @Override
        public void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            Log.i(TAG, "onDescriptorWrite " + descriptor.getUuid()
                    + ", char " + descriptor.getCharacteristic().getUuid().toString() + ", id=" + descriptor.getCharacteristic().getInstanceId()
                    + ", status=" + status);

            startNextInitOp(gatt);
        }

        @Override
        public void onReadRemoteRssi(final BluetoothGatt gatt, final int rssi, final int status) {
            Log.d(TAG, "Remote RSSI: " + rssi);
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered");
                initHidService(gatt);
                broadcastUpdate(Constants.ACTION_GATT_SERVICES_DISCOVERED, gatt.getDevice());
            } else {
                Log.w(TAG, "onServicesDiscovered received error: " + status);
            }
        }

        /**
         * Initialize HID service characteristics and enable notifications.
         *
         * @param gatt Gatt connection to use.
         */
        private void initHidService(final BluetoothGatt gatt) {
            reset();

            // Loop through available GATT Services.
            List<BluetoothGattService> serviceList = gatt.getServices();
            for (BluetoothGattService service : serviceList) {
                Log.d(TAG, "Service UUID " + service.getUuid().toString() + ", id=" + service.getInstanceId());
            }

            isHidDevice = gatt.getService(HID_SERVICE) != null;
            requireEncryption |= isHidDevice;
            if (ACTION_HID_CONNECTION_STATE_CHANGED != null && preferences.getBoolean(Constants.PREF_AUTO_INIT_SYSTEM_HID, Constants.DEFAULT_AUTO_INIT_SYSTEM_HID)) {
                // Check system HID connection
                if (isHidDevice && hidConnectDeviceMethod != null && hidProfile != null
                        && hidProfile.getConnectionState(gatt.getDevice()) != BluetoothProfile.STATE_CONNECTED) {
                    try {
                        Log.d(TAG, "HID auto connection: Enable HID profile");
                        pendingHidConnection = true;
                        mHandler.postDelayed(hidConnectionTimeout, HID_CONNECTION_TIMEOUT);
                        hidConnectDeviceMethod.invoke(hidProfile, gatt.getDevice());
                        return;
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        Log.e(TAG, "BluetoothInputDevice.connect call failed", e);
                        pendingHidConnection = false;
                    }
                }
            }

            // Find HID/Custom Service
            if (!checkCustomProfile(gatt)) {
                // Check for BLUETOOTH_PRIVILEGED permission
                if (Build.VERSION.SDK_INT >= 22 && ContextCompat.checkSelfPermission(BleRemoteService.this, Manifest.permission.BLUETOOTH_PRIVILEGED) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "BLUETOOTH_PRIVILEGED permission not granted.");
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            noBluetoothPrivileged = true;
                            if (bluetoothPrivilegedCallback != null) {
                                bluetoothPrivilegedCallback.noBluetoothPrivileged();
                                bluetoothPrivilegedCallback = null;
                            }
                        }
                    });
                    return;
                }

                BluetoothGattService hidService = gatt.getService(HID_SERVICE);
                if (hidService == null) {
                    Log.e(TAG, "HID service not found");
                    return;
                }

                Log.d(TAG, "Found HID service. Initializing...");
                if (Build.VERSION.SDK_INT <= 19)
                    kitkatReportReferenceDiscoveryWorkaround(hidService);
                for (BluetoothGattCharacteristic characteristic : hidService.getCharacteristics()) {
                    UUID uuid = characteristic.getUuid();
                    int instance = characteristic.getInstanceId();
                    Log.d(TAG, "Characteristic UUID " + uuid + ", id=" + instance + ", prop=" + characteristic.getProperties());
                    // Check for HID Report characteristics
                    if (HID_REPORT_CHAR.equals(uuid)) {
                        Log.d(TAG, "Found HID report characteristic");
                        // Read the report reference descriptor to get the report ID
                        BluetoothGattDescriptor reference = characteristic.getDescriptor(HID_REPORT_REFERENCE);
                        if (reference != null) {
                            Log.d(TAG, "Found report reference descriptor");
                            mDescReadQueue.add(reference);
                        }
                    }
                }
            }

            // Find device information
            BluetoothGattService disService = gatt.getService(SERVICE_DEVICE_INFO);
            if (disService != null) {
                BluetoothGattCharacteristic disChar = disService.getCharacteristic(DEVICE_INFO_PNP_ID);
                if (disChar != null)
                    mReadQueue.add(disChar);
                disChar = disService.getCharacteristic(DEVICE_INFO_SW_REVISION);
                if (disChar != null)
                    mReadQueue.add(disChar);
            }

            startNextInitOp(gatt);
        }

        private boolean checkCustomProfile(BluetoothGatt gatt) {
            BluetoothGattService customService = gatt.getService(CUSTOM_SERVICE);
            if (customService == null)
                return false;
            customProfile = true;
            Log.d(TAG, "Found custom profile. Initializing...");
            for (BluetoothGattCharacteristic characteristic : customService.getCharacteristics())
                Log.d(TAG, "Characteristic UUID " + characteristic.getUuid() + ", prop=" + characteristic.getProperties());

            // Config characteristic
            BluetoothGattCharacteristic config = customService.getCharacteristic(CUSTOM_STREAM_CONFIG);
            if (config != null) {
                readConfigCharacteristic = config;
                mReadQueue.add(config); // Read configuration
            } else {
                Log.e(TAG, "Missing custom profile config characteristic");
            }

            // Control input/output characteristic
            BluetoothGattCharacteristic control = customService.getCharacteristic(CUSTOM_STREAM_CONTROL);
            if (control != null) {
                // Input
                gatt.setCharacteristicNotification(control, true);
                BluetoothGattDescriptor ccc = control.getDescriptor(CLIENT_CONFIG_DESCRIPTOR);
                if (ccc != null) {
                    Log.d(TAG, "Found client configuration descriptor for control input");
                    ccc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    mDescWriteQueue.add(ccc);
                }
                // Output
                controlWriteCharacteristic = control;
            } else {
                Log.e(TAG, "Missing custom profile control characteristic");
            }

            // Audio characteristic
            audioReportCharacteristic = customService.getCharacteristic(CUSTOM_STREAM_DATA);
            if (audioReportCharacteristic != null) {
                gatt.setCharacteristicNotification(audioReportCharacteristic, true);
                BluetoothGattDescriptor ccc = audioReportCharacteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR);
                if (ccc != null) {
                    Log.d(TAG, "Found client configuration descriptor for stream data");
                    ccc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    mDescWriteQueue.add(ccc);
                }
            } else {
                Log.e(TAG, "Missing custom profile stream data characteristic");
            }

            return true;
        }

        private void kitkatReportReferenceDiscoveryWorkaround(BluetoothGattService hidService) {
            // There is a bug on the bluetooth API on Android 4 (was fixed on Android 5), that causes all
            // the report reference descriptors to be associated with the first report characteristic.
            // This breaks the report ID discovery procedure, so the app can't work properly.
            // The workaround is to use the BluetoothGattCharacteristic.addDescriptor
            // API to associate each descriptor with the correct characteristic.
            // It also breaks the client configuration write. Since only input reports have such
            // descriptors, we save them and associate each of them with each found input report.
            Log.w(TAG, "Workaround for Android 4 report reference descriptor issue");
            BluetoothGattCharacteristic firstReport = hidService.getCharacteristic(HID_REPORT_CHAR);
            List<BluetoothGattDescriptor> descriptors = firstReport.getDescriptors();
            if (descriptors.size() <= 2)
                return;
            ArrayDeque<BluetoothGattDescriptor> reportDescriptors = new ArrayDeque<>();
            clientConfigDescriptors = new ArrayDeque<>();
            for (BluetoothGattDescriptor descriptor : descriptors) {
                if (HID_REPORT_REFERENCE.equals(descriptor.getUuid()))
                    reportDescriptors.add(descriptor);
                if (CLIENT_CONFIG_DESCRIPTOR.equals(descriptor.getUuid()))
                    clientConfigDescriptors.add(descriptor);
            }
            Log.d(TAG, "Found " + reportDescriptors.size() + " report reference and " + clientConfigDescriptors.size() +
                    " client configuration descriptors (" + descriptors.size() + " total) on first report characteristic");
            // Distribute report reference descriptors to report characteristics
            for (BluetoothGattCharacteristic characteristic : hidService.getCharacteristics()) {
                if (reportDescriptors.isEmpty()) {
                    Log.e(TAG, "Missing report reference descriptors");
                    break;
                }
                if (HID_REPORT_CHAR.equals(characteristic.getUuid()))
                    characteristic.addDescriptor(reportDescriptors.poll());
            }
        }
    };

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result is
     * reported asynchronously through the {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt,
     * int, int)} callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        try {
            // Previously connected device. Try to reconnect.
            if (mBluetoothDevice != null && mBluetoothGatt != null && address.equals(mBluetoothDevice.getAddress()) ) {
                if (mConnectionState == STATE_CONNECTED) {
                    Log.d(TAG, "Already connected.");
                    mGattCallback.onConnectionStateChange(mBluetoothGatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED);
                    return true;
                }
                Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
                if (mBluetoothGatt.connect()) {
                    mConnectionState = STATE_CONNECTING;
                    logConnectionState();
                    return true;
                }
            }

            if (mBluetoothGatt != null)
                close();

            Log.d(TAG, "Trying to create a new connection.");
            synchronized (mGattCallback) {
                mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(address);
                mConnectionState = STATE_CONNECTING;
                logConnectionState();

                // Check bond state
                boolean autoPair = preferences.getBoolean(Constants.PREF_AUTO_PAIRING, Constants.DEFAULT_AUTO_PAIRING);
                boolean autoHid = preferences.getBoolean(Constants.PREF_AUTO_INIT_SYSTEM_HID, Constants.DEFAULT_AUTO_INIT_SYSTEM_HID);
                if (autoPair || autoHid) {
                    if (mBluetoothDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                        Log.d(TAG, (autoHid ? "HID auto connection" : "Auto pairing") + ": Device not paired. Bonding...");
                        mBluetoothDevice.createBond();
                        return false;
                    }
                }

                mBluetoothGatt = mBluetoothDevice.connectGatt(this, false, mGattCallback);
            }
        } catch (Exception e) {
            // Can't actually catch the DeadObjectException itself for some
            // reason...*shrug*.
            Log.e(TAG, "Caught Exception on bluetooth connect", e);
        }
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt,
     * int, int)} callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null) {
            Log.d(TAG, "BluetoothAdapter not initialized");
            return;
        }
        try {
            if (mBluetoothGatt != null) {
                mBluetoothGatt.disconnect();
            }
        } catch (Exception e) {
            // Can't actually catch the DeadObjectException itself for some
            // reason...*shrug*.
            Log.e(TAG, "Caught Exception on bluetooth disconnect", e);
        }
    }

    /**
     * Send control output report to the RCU to enable/disable audio stream.
     *
     * @param enable Stream enable/disable
     */
    public void sendStreamEnable(boolean enable) {
        // Inform audio manager
        if (mAudioHandler != null) {
            Message message = mAudioHandler.obtainMessage(enable ? Constants.AUDIOMANAGER_STREAM_ON : Constants.AUDIOMANAGER_STREAM_OFF);
            mAudioHandler.sendMessage(message);
        }
        // Send stream control command
        expectedReport = 0;
        byte control[] = new byte[enable ? 2 : 1];
        control[0] = (byte) (enable ? 1 : 0);
        if (enable && setAudioMode)
            control[1] = (byte) ((2 + audioMode) | Constants.AUDIO_MODE_PRIORITY_FLAG);
        writeControl(control);
        // Reset bitrate calculation
        mBitrateHandler.removeCallbacksAndMessages(null);
        if (enable) {
            numBytesReceived = 0;
            prevBytesReceived = 0;
        }
    }

    public void sendEncodeMode(int mode) {
        audioMode = mode;
        byte control[] = new byte[] { 0, (byte) (2 + mode) };
        writeControl(control);
    }

    public void sendReadConfigCommand() {
        byte control[] = new byte[] { 0, (byte) (Constants.CONTROL_COMMAND_FLAG | Constants.CONTROL_COMMAND_READ_CONFIG), 0 };
        writeControl(control);
    }

    public void updatePacketSize(int max, int fixed) {
        ByteBuffer control = ByteBuffer.allocate(Constants.CONTROL_COMMAND_DATA_OFFSET + Constants.CONTROL_COMMAND_SET_MTU_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN).put((byte) 0)
                .put((byte) (Constants.CONTROL_COMMAND_FLAG | Constants.CONTROL_COMMAND_SET_MTU))
                .put((byte) Constants.CONTROL_COMMAND_SET_MTU_SIZE)
                .putShort((short) max)
                .putShort((short) fixed);
        writeControl(control.array());
    }

    public void updateConnectionParameters(int minInterval, int maxInterval, int slaveLatency, int timeout) {
        ByteBuffer control = ByteBuffer.allocate(Constants.CONTROL_COMMAND_DATA_OFFSET + Constants.CONTROL_COMMAND_UPDATE_CONN_PARAMS_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN).put((byte) 0)
                .put((byte) (Constants.CONTROL_COMMAND_FLAG | Constants.CONTROL_COMMAND_UPDATE_CONN_PARAMS))
                .put((byte) Constants.CONTROL_COMMAND_UPDATE_CONN_PARAMS_SIZE)
                .putShort((short) minInterval)
                .putShort((short) maxInterval)
                .putShort((short) slaveLatency)
                .putShort((short) timeout);
        writeControl(control.array());
    }

    private void writeControl(byte[] control) {
        if (mConnectionState != STATE_CONNECTED || !deviceReady || mBluetoothGatt == null || controlWriteCharacteristic == null)
            return;
        synchronized (mControlWriteQueue) {
            mControlWriteQueue.add(control);
            if (mControlWriteQueue.size() == 1) {
                Log.d(TAG, "Write control: " + Arrays.toString(control));
                controlWriteCharacteristic.setValue(control);
                if (!mBluetoothGatt.writeCharacteristic(controlWriteCharacteristic)) {
                    Log.e(TAG, "Write control failed to start");
                    mControlWriteQueue.poll();
                }
            }
        }
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public boolean isCustomProfile() {
        return customProfile;
    }

    public int getFeatures() {
        return features;
    }

    public int getMtu() {
        return mtu;
    }

    public int getPacketSize() {
        return packetSize;
    }

    public int getConnectionInterval() {
        return connectionInterval;
    }

    public int getSlaveLatency() {
        return slaveLatency;
    }

    public int getSupervisionTimeout() {
        return supervisionTimeout;
    }

    public int getInitialKeyLayout() {
        return initialKeyLayout;
    }

    public void setAudioHandler(final Handler handler) {
        mAudioHandler = handler;
        if (mAudioHandler != null && configRead) {
            synchronized (mAudioHandler) {
                Message message = mAudioHandler.obtainMessage(Constants.AUDIOMANAGER_SET_FEATURES, features, 0);
                mAudioHandler.sendMessage(message);
            }
        }
    }

    private void sendAudioData(final byte data[]) {
        if (mAudioHandler == null)
            return;
        Message message = new Message();
        message.what = Constants.AUDIOMANAGER_STREAM_IMA;
        message.obj = data;
        mAudioHandler.sendMessage(message);
    }

    private void processAudioControlInput(BluetoothGatt gatt, byte[] control) {
        if (control.length <= Constants.CONTROL_TYPE_OFFSET)
            return;
        int type = control[Constants.CONTROL_TYPE_OFFSET] & 0xff;
        Intent configUpdate = null;
        switch (type) {
            case Constants.CONTROL_TYPE_CONFIG:
                initialKeyLayout = control.length > Constants.CONTROL_KEY_LAYOUT_OFFSET ? control[Constants.CONTROL_KEY_LAYOUT_OFFSET] & 0xff : 0;
                features = control.length > Constants.CONTROL_FEATURES_OFFSET ? control[Constants.CONTROL_FEATURES_OFFSET] & 0xff : 0;
                setAudioMode = (features & Constants.CONTROL_FEATURES_SETMODE) != 0;
                if (mAudioHandler != null) {
                    synchronized (mAudioHandler) {
                        Message message = mAudioHandler.obtainMessage(Constants.AUDIOMANAGER_SET_FEATURES, features, 0);
                        mAudioHandler.sendMessage(message);
                    }
                }
                if ((features & Constants.CONTROL_FEATURES_COMMAND_SUPPORT) != 0) {
                    readPacketConfig(control, Constants.CONTROL_CONFIG_MTU_OFFSET, Constants.CONTROL_CONFIG_MTU_SIZE);
                    readConnParamsConfig(control, Constants.CONTROL_CONFIG_CONN_PARAMS_OFFSET, Constants.CONTROL_CONFIG_CONN_PARAMS_SIZE);
                    configUpdate = new Intent(Constants.ACTION_CONFIG_UPDATE);
                    configUpdate.putExtra(Constants.EXTRA_MTU, mtu);
                    configUpdate.putExtra(Constants.EXTRA_PACKET_SIZE, packetSize);
                    configUpdate.putExtra(Constants.EXTRA_CONN_INTERVAL, connectionInterval);
                    configUpdate.putExtra(Constants.EXTRA_SLAVE_LATENCY, slaveLatency);
                    configUpdate.putExtra(Constants.EXTRA_CONN_TIMEOUT, supervisionTimeout);
                }
                int decodeModeConfig = control.length > Constants.CONTROL_AUDIO_MODE_OFFSET ? control[Constants.CONTROL_AUDIO_MODE_OFFSET] & 0xff : 0;
                if ((decodeModeConfig & Constants.CONTROL_AUDIO_MODE_MASK) == Constants.CONTROL_AUDIO_MODE_FIXED)
                    audioMode = decodeModeConfig & Constants.CONTROL_AUDIO_MODE_RATE_MASK;
                else
                    audioMode = Constants.AUDIO_MODE_AUTOMATIC;
                setAudioReportID(control.length > Constants.CONTROL_AUDIO_REPORT_OFFSET ? control[Constants.CONTROL_AUDIO_REPORT_OFFSET] & 0xff : 0, gatt);
                break;

            case Constants.CONTROL_TYPE_STREAM:
                setAudioReportID(control.length > Constants.CONTROL_AUDIO_REPORT_OFFSET ? control[Constants.CONTROL_AUDIO_REPORT_OFFSET] & 0xff : 0, gatt);
                if (control[Constants.CONTROL_STREAM_ENABLE_OFFSET] == 2)
                    break;
                boolean enable = control[Constants.CONTROL_STREAM_ENABLE_OFFSET] != 0;
                Log.d(TAG, "Stream " + (enable ? "ON" : "OFF"));
                sendStreamEnable(enable);
                break;

            case Constants.CONTROL_TYPE_KEY:
            case Constants.CONTROL_TYPE_STREAM_ERROR:
                // Processed by the activity
                break;

            case Constants.CONTROL_TYPE_AUDIO_MODE:
                if (control.length > Constants.AUDIO_MODE_REPORT_MODE_OFFSET) {
                    int mode = control[Constants.AUDIO_MODE_REPORT_MODE_OFFSET] & 0xff;
                    Log.d(TAG, "Received legacy set audio mode report, mode = " + mode);
                    // This functionality was never used in the legacy RCUs.
                    // It was added for completeness. For now, we ignore it.
                }
                break;

            case Constants.CONTROL_TYPE_CONN_PARAMS:
                readConnParamsConfig(control, Constants.CONN_PARAMS_REPORT_DATA_OFFSET, Constants.CONN_PARAMS_REPORT_DATA_SIZE);
                configUpdate = new Intent(Constants.ACTION_CONFIG_UPDATE);
                configUpdate.putExtra(Constants.EXTRA_CONN_INTERVAL, connectionInterval);
                configUpdate.putExtra(Constants.EXTRA_SLAVE_LATENCY, slaveLatency);
                configUpdate.putExtra(Constants.EXTRA_CONN_TIMEOUT, supervisionTimeout);
                break;

            case Constants.CONTROL_TYPE_MTU:
                readPacketConfig(control, Constants.MTU_REPORT_DATA_OFFSET, Constants.MTU_REPORT_DATA_SIZE);
                configUpdate = new Intent(Constants.ACTION_CONFIG_UPDATE);
                configUpdate.putExtra(Constants.EXTRA_MTU, mtu);
                configUpdate.putExtra(Constants.EXTRA_PACKET_SIZE, packetSize);
                break;

            default:
                break;
        }

        if (configUpdate != null)
            LocalBroadcastManager.getInstance(this).sendBroadcast(configUpdate);
    }

    private void setAudioReportID(int reportID, BluetoothGatt gatt) {
        if (customProfile)
            return;
        if (audioReportID == reportID)
            return;
        audioReportID = reportID;
        if (audioReportCharacteristic != null)
            gatt.setCharacteristicNotification(audioReportCharacteristic, false);
        audioReportCharacteristic = null;
        if (audioReportID != 0) {
            Log.d(TAG, "Received audio report ID: " + audioReportID);
            for (BluetoothGattCharacteristic hidChar : gatt.getService(HID_SERVICE).getCharacteristics()) {
                if (HID_REPORT_CHAR.equals(hidChar.getUuid())) {
                    Integer id = instanceToReportID.get(hidChar.getInstanceId());
                    if (id != null && id == audioReportID) {
                        Log.d(TAG, "Found audio report characteristic: " + hidChar.getInstanceId());
                        audioReportCharacteristic = hidChar;
                        gatt.setCharacteristicNotification(audioReportCharacteristic, true);
                        break;
                    }
                }
            }
            if (audioReportCharacteristic == null) {
                Log.e(TAG, "Audio report characteristic not found");
                audioReportID = 0;
            }
        } else {
            Log.d(TAG, "Use legacy audio reports");
        }
    }

    private void readConnParamsConfig(byte[] control, int offset, int size) {
        if (offset + size > control.length)
            return;
        ByteBuffer buffer = ByteBuffer.wrap(control, offset, size).order(ByteOrder.LITTLE_ENDIAN);
        connectionInterval = buffer.getShort();
        slaveLatency = buffer.getShort();
        supervisionTimeout = buffer.getShort();
    }

    private void readPacketConfig(byte[] control, int offset, int size) {
        if (offset + size > control.length)
            return;
        ByteBuffer buffer = ByteBuffer.wrap(control, offset, size).order(ByteOrder.LITTLE_ENDIAN);
        packetSize = buffer.getShort();
        mtu = buffer.getShort();
    }

    /**
     * ** broadcastUpdate functions to send data back to BleActivity
     */
    void broadcastUpdate(final String action, BluetoothDevice device) {
        Intent intent = new Intent(action);
        intent.putExtra(Constants.EXTRA_DEVICE, device);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    void broadcastUpdate(final String action, final byte[] data) {
        Intent intent = new Intent(action);
        intent.putExtra(Constants.EXTRA_DATA, data);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    void broadcastUpdate(final String action, final int data) {
        Intent intent = new Intent(action);
        intent.putExtra(Constants.EXTRA_DATA, data);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    void broadcastUpdate(final String action, final BluetoothDevice device, final int rssi) {
        Intent intent = new Intent(action);
        intent.putExtra(Constants.EXTRA_DEVICE, device);
        intent.putExtra(Constants.EXTRA_RSSI, rssi);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Initialises a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialisation is successful.
     */
    public boolean initBluetooth() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Log.e(TAG, "BluetoothAdapter is not enabled");
            return false;
        }
        return true;
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        Log.d(TAG, "close");
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        boolean wasConnected = mConnectionState == STATE_CONNECTED;
        mConnectionState = STATE_DISCONNECTED;
        if (wasConnected)
            logConnectionState();
        reset();
        mHandler.removeCallbacks(hidConnectionTimeout);
        mHandler.removeCallbacks(hidConnectionDiscoveryDelay);
        mReconnectHandler.removeCallbacksAndMessages(null);
        mBluetoothDevice = null;
        bluetoothPrivilegedCallback = null;
    }

    public boolean removeBond(BluetoothDevice device) {
        if (removeBondMethod == null)
            return false;
        Log.d(TAG, "Unpair from " + device.getName() + " [" + device.getAddress() + "]");
        try {
            return (boolean) removeBondMethod.invoke(device, (Object[]) null);
        } catch (InvocationTargetException | IllegalAccessException e) {
            Log.e(TAG, "BluetoothDevice.removeBond call failed", e);
            return false;
        }
    }

    public String getBluetoothDeviceAddress() {
        return mBluetoothDevice != null ? mBluetoothDevice.getAddress() : null;
    }

    public boolean isConnected() {
        return mConnectionState == STATE_CONNECTED;

    }

    public boolean isDisconnected() {
        return mConnectionState == STATE_DISCONNECTED;
    }

    private void startBitrateReporter() {
        mBitrateHandler.removeCallbacksAndMessages(null);
        timeBitrateReported = new Date().getTime();
        mBitrateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long now = new Date().getTime();
                int bytes = numBytesReceived - prevBytesReceived;
                if (bytes != 0) {
                    int bitRate = bytes * 8000 / (int) (now - timeBitrateReported);
                    prevBytesReceived = numBytesReceived;
                    timeBitrateReported = now;
                    broadcastUpdate(Constants.ACTION_BITRATE_REPORTED, bitRate);
                }
                mBitrateHandler.postDelayed(this, 500);
            }
        }, 500);
    }

    private void logConnectionState() {
        String msg;
        switch (mConnectionState) {
            case STATE_CONNECTING:
                msg = "Connecting...";
                break;
            case STATE_CONNECTED:
                msg = "Connected to " + mBluetoothDevice.getName() + " [" + mBluetoothDevice.getAddress() + "]";
                break;
            case STATE_DISCONNECTED:
                msg = "Disconnected";
                break;
            default:
                msg = null;
                break;
        }
        if (msg != null)
            log(msg);
    }

    private void log(final String msg) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Utils.logMessage(BleRemoteService.this, msg);
            }
        });
    }
}