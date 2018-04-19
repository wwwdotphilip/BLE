package test.john.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;

public class AutomateOTAFirmwareUpload {
    private static final String TAG = "OTA";
    private static final String NONE_OTA = "RC900";
    private static final String OTA = "RC900_OTA";
    private static final long SCAN_PERIOD = 100000;
    private static volatile AutomateOTAFirmwareUpload instance;
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    // Device scan callback.

    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;

    private BluetoothAdapter.LeScanCallback mLeScanCallback;
    private ServiceConnection mServiceConnection;


    private DfuServiceInitiator mStarter;
    private DfuProgressListener mDfuProgressListener;
    private String mFilePath;
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.v(TAG, "Connected to " + mDeviceName);
                mConnected = true;
                new Handler().postDelayed(() -> performAction(context), 5000);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                //displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

            }
        }
    };
    private boolean writing = false;

    private static AutomateOTAFirmwareUpload getInstance() {
        if (instance == null) {
            synchronized (AutomateOTAFirmwareUpload.class) {
                instance = new AutomateOTAFirmwareUpload();
                instance.mHandler = new Handler();
            }
        }
        return instance;
    }

    public static void startAutoUpgrade(Context context, String filePath) {
        getInstance().mFilePath = filePath;
        getInstance().mLeScanCallback =
                (device, rssi, scanRecord) -> {
                    if (device != null && device.getName() != null) {
                        Log.v(TAG, "Found device " + device.getName());
                        if (!getInstance().writing && device.getName().equals(NONE_OTA)) {
                            if (!getInstance().mConnected) {
                                initializeGattService(context, device);
                                scanLeDevice(false);
                            }
                        } else if (getInstance().writing && device.getName().equals(OTA)) {
                            Log.v(TAG, "Found device " + device.getName());
                            scanLeDevice(false);
                            getInstance().writing = false;
                            getInstance().mDeviceName = device.getName();
                            getInstance().mDeviceAddress = device.getAddress();
                            getInstance().mBluetoothLeService.connect(device.getAddress());
                        }
                    }
                };
        final BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            getInstance().mBluetoothAdapter = bluetoothManager.getAdapter();
        }

        // Checks if Bluetooth is supported on the device.
        if (getInstance().mBluetoothAdapter == null) {
            Toast.makeText(context, "Bluetooth not supported.", Toast.LENGTH_SHORT).show();
        }

        context.registerReceiver(getInstance().mGattUpdateReceiver, makeGattUpdateIntentFilter());
        scanLeDevice(true);
    }

    public static void destroy(Context context) {
        context.unregisterReceiver(getInstance().mGattUpdateReceiver);
        context.unbindService(getInstance().mServiceConnection);
        DfuServiceListenerHelper.unregisterProgressListener(context, getInstance().mDfuProgressListener);
        getInstance().mBluetoothLeService.disconnect();
        getInstance().mBluetoothLeService = null;
    }

    public static void stopAutoUpgrade() {
        scanLeDevice(false);
    }

    private static void scanLeDevice(final boolean enable) {
        Log.v(TAG, "Scan devices " + enable);
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            getInstance().mHandler.postDelayed(() -> {
                getInstance().mBluetoothAdapter.stopLeScan(getInstance().mLeScanCallback);
                Log.v(TAG, "Stop scan.");
            }, SCAN_PERIOD);

            getInstance().mBluetoothAdapter.startLeScan(getInstance().mLeScanCallback);
            Log.v(TAG, "Should scan.");
        } else {
            getInstance().mBluetoothAdapter.stopLeScan(getInstance().mLeScanCallback);
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private static void performAction(Context context) {
        if (getInstance().mDeviceName.equals(NONE_OTA)) {
            write();
        } else if (getInstance().mDeviceName.equals(OTA)) {
            upload(context);
        }
    }

    private static void write() {
        Log.v(TAG, "Performing action write OTA");

        if (getInstance().mBluetoothLeService != null) {
            List<BluetoothGattService> serviceList = getInstance().mBluetoothLeService.getSupportedGattServices();
            for (BluetoothGattService item : serviceList) {
                Log.v(TAG, "Service UUID: " + item.getUuid().toString());
                if (item.getUuid().toString().equalsIgnoreCase("0000fd00-0000-1000-8000-00805f9b34fb")) {
                    List<BluetoothGattCharacteristic> characteristics = item.getCharacteristics();
                    for (BluetoothGattCharacteristic ch : characteristics) {
                        Log.v(TAG, "Characteristics: " + ch.getUuid().toString());
                        if (ch.getUuid().toString().equalsIgnoreCase("0000fd0a-0000-1000-8000-00805f9b34fb")) {
                            int chProp = ch.getProperties();
                            Log.v(TAG, "CH properties " + chProp);
                            getInstance().writing = true;
                            getInstance().mBluetoothLeService.writeCharacteristics(new byte[]{(byte) 0xA2, 0x04, 0x01, (byte) 0xA7}, item.getUuid().toString(), ch.getUuid().toString());
                            new Handler().postDelayed(() -> scanLeDevice(true), 5000);
                            break;
                        }
                    }
                    break;
                }
            }
        } else {
            Log.v(TAG, "Bluetooth service is null.");
        }
    }

    private static void upload(Context context) {
        Log.v(TAG, "Performing action upload firmware.");
        getInstance().mStarter = new DfuServiceInitiator(getInstance().mDeviceAddress)
                .setDeviceName(getInstance().mDeviceName)
                .setKeepBond(true);

        if (getInstance().mFilePath != null) {
            getInstance().mStarter.setZip(getInstance().mFilePath);
            getInstance().mStarter.start(context, DfuService.class);
        }
    }

    private static void initializeGattService(Context context, BluetoothDevice device) {
        Log.v(TAG, "Initializing gatt");
        getInstance().mDeviceName = device.getName();
        getInstance().mDeviceAddress = device.getAddress();
        getInstance().mServiceConnection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName componentName, IBinder service) {
                getInstance().mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
                if (!getInstance().mBluetoothLeService.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth");
                }
                getInstance().mBluetoothLeService.connect(getInstance().mDeviceAddress);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                getInstance().mBluetoothLeService = null;
            }
        };

        Intent gattServiceIntent = new Intent(context, BluetoothLeService.class);
        context.bindService(gattServiceIntent, getInstance().mServiceConnection, Context.BIND_AUTO_CREATE);


        getInstance().mDfuProgressListener = new DfuProgressListener() {
            @Override
            public void onDeviceConnecting(String deviceAddress) {
                Log.v(TAG, "Connecting to " + deviceAddress);
            }

            @Override
            public void onDeviceConnected(String deviceAddress) {
                Log.v(TAG, "Connected to " + deviceAddress);
            }

            @Override
            public void onDfuProcessStarting(String deviceAddress) {
                Log.v(TAG, "DFU Process started " + deviceAddress);
            }

            @Override
            public void onDfuProcessStarted(String deviceAddress) {

            }

            @Override
            public void onEnablingDfuMode(String deviceAddress) {

            }

            @Override
            public void onProgressChanged(String deviceAddress, int percent, float speed, float avgSpeed, int currentPart, int partsTotal) {
                Log.v(TAG, "Current progress " + percent);
            }

            @Override
            public void onFirmwareValidating(String deviceAddress) {

            }

            @Override
            public void onDeviceDisconnecting(String deviceAddress) {
                Log.v(TAG, "Disconnecting to " + deviceAddress);
            }

            @Override
            public void onDeviceDisconnected(String deviceAddress) {
                Log.v(TAG, "Disconnected to " + deviceAddress);
            }

            @Override
            public void onDfuCompleted(String deviceAddress) {
                Log.v(TAG, "Firmware upload complete");
            }

            @Override
            public void onDfuAborted(String deviceAddress) {
                Log.v(TAG, "Firmware upload aborted");
            }

            @Override
            public void onError(String deviceAddress, int error, int errorType, String message) {
                Log.v(TAG, "DFU error " + deviceAddress + "\nMessage: " + message);
            }
        };

        DfuServiceListenerHelper.registerProgressListener(context, getInstance().mDfuProgressListener);
    }
}
