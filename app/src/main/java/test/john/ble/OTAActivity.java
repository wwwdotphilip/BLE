package test.john.ble;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuServiceController;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;

public class OTAActivity extends Activity {
    private static final String TAG = "OTAActivity";
    private static final int FIND_FILE = 1234;
    private DfuServiceInitiator mStarter;
    private DfuServiceController mController;
    private DfuProgressListener mDfuProgressListener;
    private String filePath;
    private TextView mMessage, mSource;
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ota);

        mSource = findViewById(R.id.tvSource);
        mMessage = findViewById(R.id.tvMessage);
        mProgressBar = findViewById(R.id.pbUpload);
        mProgressBar.setMax(100);

        String deviceAddress = getIntent().getStringExtra("device_address");
        String deviceName = getIntent().getStringExtra("device_name");

        if (deviceAddress != null) {
            mStarter = new DfuServiceInitiator(deviceAddress)
                    .setDeviceName(deviceName)
                    .setKeepBond(true);
            mDfuProgressListener = new DfuProgressListener() {
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
                    mMessage.setText("Uploading firmware");
                }

                @Override
                public void onDfuProcessStarted(String deviceAddress) {

                }

                @Override
                public void onEnablingDfuMode(String deviceAddress) {

                }

                @Override
                public void onProgressChanged(String deviceAddress, int percent, float speed, float avgSpeed, int currentPart, int partsTotal) {
                    mProgressBar.setProgress(percent);
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
                    Log.v(TAG, "DFU completed " + deviceAddress);
                    mMessage.setText("Firmware upload complete");
                }

                @Override
                public void onDfuAborted(String deviceAddress) {
                    Log.v(TAG, "DFU aborted " + deviceAddress);
                    mMessage.setText("Firmware upload aborted");
                }

                @Override
                public void onError(String deviceAddress, int error, int errorType, String message) {
                    Log.v(TAG, "DFU error " + deviceAddress + "\nMessage: " + message);
                    mMessage.setText("Firmware upload error " + message);
                }
            };
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        DfuServiceListenerHelper.registerProgressListener(this, mDfuProgressListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        DfuServiceListenerHelper.unregisterProgressListener(this, mDfuProgressListener);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_CANCELED) {
            if (requestCode == FIND_FILE && data != null && data.getData() != null) {
                filePath = Utils.getActualPath(getApplicationContext(), data.getData());
                mSource.setText(String.format("Firmware source: %s", filePath));
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // Upload selected firmware file
    public void upload(View view) {
        if (filePath != null) {
            mStarter.setZip(filePath);
            mController = mStarter.start(this, DfuService.class);
        } else {
            Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show();
        }
    }

    // Launch file explorer to select firmware by starting an intent.
    public void findFile(View view) {
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.setType("*/*");
        chooseFile = Intent.createChooser(chooseFile, "Choose a file");
        startActivityForResult(chooseFile, FIND_FILE);
    }
}
