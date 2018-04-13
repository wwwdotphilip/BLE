package test.john.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.scan.ScanResult;
import com.polidea.rxandroidble.scan.ScanSettings;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    public static final String TARGET_SERVICE = "fd00";
    private RxBleClient rxBleClient;
    private RxBleDevice bleDevice;

    private TextView message;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        message = findViewById(R.id.tvMessage);
    }

    @Override
    protected void onStart() {
        super.onStart();
        rxBleClient = RxBleClient.create(this);
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        int REQUEST_ENABLE_BT = 1;
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public void scan(View view){
        final String[] temp = {message.getText().toString()};
        temp[0] = temp[0] + "Scanning ble devices.\n";
        message.setText(temp[0]);
        rxBleClient.scanBleDevices(
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC) // change if needed
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // change if needed
                        .build())
                .subscribe(
                        scanResult -> {
                            if (scanResult.getBleDevice() != null && scanResult.getBleDevice().getName() != null) {
                                runOnUiThread(() -> {
                                    if (scanResult.getBleDevice().getName().equals("RC900") && bleDevice == null) {
                                        temp[0] = message.getText().toString();
                                        temp[0] = temp[0] + "Found " + scanResult.getBleDevice().getName() + "\n";
                                        message.setText(temp[0]);
                                        connect(scanResult);
                                    }
                                });
                            }
                        },
                        throwable -> Log.e(this.getClass().getName(), throwable.toString())
                );
    }

    public void disconnect(View view){

    }

    private void connect(ScanResult scanResult) {
        ParcelUuid[] uuids = scanResult.getBleDevice().getBluetoothDevice().getUuids();
        bleDevice = rxBleClient.getBleDevice(scanResult.getBleDevice().getMacAddress());

        Log.v(this.getClass().getSimpleName(), "Connection state: " + String.valueOf(bleDevice.getConnectionState()));

        bleDevice.establishConnection(true)
                .flatMap(RxBleConnection::discoverServices)
                .subscribe(
                        rxBleConnection -> {
                            runOnUiThread(() -> {
                                Log.v(this.getClass().getSimpleName(), bleDevice.getName());

                                Log.v(this.getClass().getSimpleName(), "Connection state: " + String.valueOf(bleDevice.getConnectionState()));
                                BluetoothDevice device = bleDevice.getBluetoothDevice();
                                if (device != null){
                                    Log.v(this.getClass().getSimpleName(), "Device is not null");
                                    List<BluetoothGattService> serviceList = rxBleConnection.getBluetoothGattServices();
                                    String temp = message.getText().toString();
                                    temp = temp + "Services found: \n";
                                    for (BluetoothGattService item : serviceList){
                                        if (item.getUuid().toString().contains(TARGET_SERVICE)){
//                                            readCharacters(item.getUuid());
                                            temp = temp + "Found target UUID: " +item.getUuid()+ "\n";
                                            message.setText(temp);
                                        }
                                    }
                                } else {
                                    Log.e(this.getClass().getSimpleName(), "Device is null");
                                }
                            });
                        },
                        throwable -> Log.v(this.getClass().getSimpleName(), throwable.toString())
                );
    }

    private void readCharacters(UUID uuid) {
        bleDevice.establishConnection(true)
                .flatMap(rxBleConnection -> rxBleConnection.readCharacteristic(uuid))
                .subscribe(characteristicValue -> runOnUiThread(() -> {
                    String temp = message.getText().toString();
                    temp = temp + Arrays.toString(characteristicValue) + "\n";
                    message.setText(temp);
                }));
    }
}
