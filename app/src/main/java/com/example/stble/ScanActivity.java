package com.example.stble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScanActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 42;
    private static final String TAG = "SCAN_ACTIVITY";
    private static final int REQUEST_FINE_LOCATION = 12;
    private static final long SCAN_PERIOD = 10000;

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning = false;
    private ArrayList<BluetoothDevice> mScanResults;
    private BtleScanCallback mScanCallback;
    private BluetoothLeScanner mBluetoothLeScanner;

    private BluetoothDevice mSelectedDevice;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private String[] mListElements = new String[]{""};

    private List<String> mListElementsArrayList;
    private ArrayAdapter<String> mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        mSwipeRefreshLayout = findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            finish();
            overridePendingTransition(0, 0);
            startActivity(getIntent());
            overridePendingTransition(0, 0);
        });
    }

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

        startScan();
    }

    private void startScan() {
        if (!hasPermissions() || mScanning) {
            return;
        }
        runOnUiThread(() -> mSwipeRefreshLayout.setEnabled(false));
        List<ScanFilter> filters = new ArrayList<>();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();
        mScanResults = new ArrayList<>();
        mScanCallback = new BtleScanCallback();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
        mScanning = true;
        new Handler().postDelayed(this::stopScan, SCAN_PERIOD);
    }

    private void stopScan() {
        if (mScanning && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback);
            scanComplete();
        }

        mScanCallback = null;
        mScanning = false;
    }

    private void scanComplete() {
        runOnUiThread(() -> mSwipeRefreshLayout.setEnabled(true));
        mListElementsArrayList.set(0, "Scan finished, found " + (mListElementsArrayList.size() - 1) + " element(s)" + ".");
        mAdapter.notifyDataSetChanged();
        Log.d(TAG, "Scan complete");
    }

    private boolean hasPermissions() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            requestBluetoothEnable();
            return false;
        } else if (!hasLocationPermissions()) {
            requestLocationPermission();
            return false;
        }
        return true;
    }

    private void requestBluetoothEnable() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        Log.d(TAG, "Requested user enables Bluetooth. Try starting the scan again.");
    }

    private boolean hasLocationPermissions() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
    }

    private class BtleScanCallback extends ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            addScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                addScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE Scan Failed with code " + errorCode);
        }

        private void addScanResult(ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device.getName() != null) {
                if (!mScanResults.contains(device)) {
                    mScanResults.add(device);
                    mListElementsArrayList.add("Device " + device.getAddress() + " " + device.getName());
                    Log.d(TAG, "Device " + device.getAddress() + " " + device.getName());
                    mAdapter.notifyDataSetChanged();
                }
            }

        }
    }


}