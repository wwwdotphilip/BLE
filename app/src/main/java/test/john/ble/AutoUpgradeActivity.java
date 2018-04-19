package test.john.ble;

import android.app.Activity;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.io.File;

public class AutoUpgradeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auto_upgrade);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String path = Environment.getExternalStorageDirectory() + "/F45" + "/" + "ota.zip";
        Log.v("FilePath", path);
        AutomateOTAFirmwareUpload.startAutoUpgrade(this, path);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AutomateOTAFirmwareUpload.stopAutoUpgrade();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AutomateOTAFirmwareUpload.destroy(this);
    }
}
