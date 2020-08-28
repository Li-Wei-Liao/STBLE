package com.example.stble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Class handling the scan of the devices
 */
public class ScanActivity extends AppCompatActivity implements ServiceConnection {

    private static final int REQUEST_ENABLE_BT = 42;
    private static final String TAG = "SCAN_ACTIVITY";
    private static final int REQUEST_FINE_LOCATION = 12;
    private static final long SCAN_PERIOD = 10000;

    private boolean mScanning = false;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private String[] mListElements = new String[]{""};
    private List<String> mListElementsArrayList;
    private ArrayAdapter<String> mAdapter;

    private ProgressBar mSearchWheel;

    private ArrayList<BluetoothDevice> mScanResults;
    private BluetoothDevice mSelectedDevice;

    private BluetoothService mBluetoothService;

    private DataUpdateReceiver dataUpdateReceiver;

    /**
     * Scan Activity OnCreate function
     *
     * @param savedInstanceState instance
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        mSwipeRefreshLayout = findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            finish();
            overridePendingTransition(0, 0);
            startActivity(getIntent());
            overridePendingTransition(0, 0);
        });

        mSearchWheel = findViewById(R.id.progressBar);
    }

    /**
     * Scan Activity OnResume function
     */
    @Override
    protected void onResume() {
        super.onResume();

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            finish();

        ListView listView = findViewById(R.id.listView1);

        mListElementsArrayList = new ArrayList<>(Arrays.asList(mListElements));
        mAdapter = new ArrayAdapter<>
                (ScanActivity.this, android.R.layout.simple_list_item_1, mListElementsArrayList);
        listView.setAdapter(mAdapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        listView.setOnItemClickListener((adapterView, view, i, l) -> {
            if (l != 0) {
                mSelectedDevice = mScanResults.get((int) l - 1);
                Log.d(TAG, "Item clicked id " + l + " device " + mSelectedDevice.getName());
                Intent intent = new Intent(ScanActivity.this, ConnectActivity.class);
                intent.putExtra("Device", mSelectedDevice);
                startActivity(intent);
                stopScan();
            }
        });

        Intent intent = new Intent(this, BluetoothService.class);
        bindService(intent, this, Context.BIND_AUTO_CREATE);
        if (dataUpdateReceiver == null) dataUpdateReceiver = new DataUpdateReceiver();
        IntentFilter intentFilter = new IntentFilter(BluetoothService.DEVICE_LIST_UPDATED);
        registerReceiver(dataUpdateReceiver, intentFilter);
    }

    /**
     * Scan Activity onPause function
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (dataUpdateReceiver != null) unregisterReceiver(dataUpdateReceiver);
        unbindService(this);
    }

    /**
     * Start the scan of devices
     */
    private void startScan() {
        if (!hasPermissions() || mScanning || mBluetoothService == null) {
            return;
        }
        runOnUiThread(() -> {
            mSwipeRefreshLayout.setEnabled(false);
            mSearchWheel.setVisibility(View.VISIBLE);
        });
        mBluetoothService.startScan();
        mScanning = true;
        new Handler().postDelayed(this::stopScan, SCAN_PERIOD);
    }

    /**
     * Stop the scan of devices
     */
    private void stopScan() {
        if (mScanning)
            mBluetoothService.stopScan();
        scanComplete();
        runOnUiThread(() -> mSearchWheel.setVisibility(View.INVISIBLE));
        mScanning = false;
    }

    /**
     * Called when the scan is completed
     */
    private void scanComplete() {
        runOnUiThread(() -> mSwipeRefreshLayout.setEnabled(true));
        mListElementsArrayList.set(0, "Scan finished, found " + (mListElementsArrayList.size() - 1) + " element(s)" + ".");
        mAdapter.notifyDataSetChanged();
        Log.d(TAG, "Scan complete");
    }

    /**
     * Verify if the Android device has permission to use bluetooth services
     *
     * @return permission status
     */
    private boolean hasPermissions() {
        if (mBluetoothService.getBluetoothAdapter() == null || !mBluetoothService.getBluetoothAdapter().isEnabled()) {
            requestBluetoothEnable();
            return false;
        } else if (!hasLocationPermissions()) {
            requestLocationPermission();
            return false;
        }
        return true;
    }

    /**
     * Request to enable the bluetooth
     */
    private void requestBluetoothEnable() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        Log.d(TAG, "Requested user enables Bluetooth. Try starting the scan again.");
    }

    /**
     * Verify if the Android device allow to use the location service
     *
     * @return true if the Android device allows to use the location service
     */
    private boolean hasLocationPermissions() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request to use the location service
     */
    private void requestLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
    }

    /**
     * Is called when the bluetooth service is connected
     *
     * @param componentName name of the component
     * @param iBinder       binder to this component
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        BluetoothService.MyBinder b = (BluetoothService.MyBinder) iBinder;
        mBluetoothService = b.getService();
        startScan();
    }

    /**
     * Is called when the bluetooth service is disconnected
     *
     * @param componentName name of the component
     */
    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mBluetoothService = null;
    }

    /**
     * Class allowing data transmit between the services and the activity
     */
    private class DataUpdateReceiver extends BroadcastReceiver {
        /**
         * Function called when data is received from the service
         *
         * @param context context
         * @param intent  intent
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), BluetoothService.DEVICE_LIST_UPDATED)) {
                mScanResults = mBluetoothService.getScanResults();
                BluetoothDevice device = mScanResults.get(mScanResults.size() - 1);
                mListElementsArrayList.add(device.getAddress() + " " + device.getName());
                mAdapter.notifyDataSetChanged();
            }
        }
    }
}