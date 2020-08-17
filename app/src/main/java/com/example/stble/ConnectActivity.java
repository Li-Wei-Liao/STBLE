package com.example.stble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class ConnectActivity extends AppCompatActivity {

    private static final String TAG = "CONNECT_ACTIVITY";

    ListView mListView;

    ArrayList<String> mListItems = new ArrayList<>();
    ArrayList<BluetoothGattCharacteristic> mListCharacteristics = new ArrayList<>();
    ArrayAdapter<String> mAdapter;

    private BluetoothGatt mGatt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        TextView deviceText = findViewById(R.id.DeviceNameConnect);

        BluetoothDevice selectedDevice = getIntent().getParcelableExtra("Device");
        assert selectedDevice != null;
        Log.d(TAG, "Device " + selectedDevice.getName());

        deviceText.setText(selectedDevice.getName());

        mListView = findViewById(R.id.listView2);

        mListView.setOnItemClickListener((adapterView, view, i, l) -> {
            if (l != 0 && mListCharacteristics.get((int) l) != null) {
                BluetoothGattCharacteristic characteristic = mListCharacteristics.get((int) l);
                Intent intent = new Intent(ConnectActivity.this, SendActivity.class);
                intent.putExtra("Device", selectedDevice);
                intent.putExtra("Chara", characteristic.getUuid().toString());
                intent.putExtra("Service", characteristic.getService().getUuid().toString());
                startActivity(intent);
            }
        });

        connectDevice(selectedDevice);
    }

    private void connectDevice(BluetoothDevice device) {
        GattClientCallback gattClientCallback = new GattClientCallback();
        mGatt = device.connectGatt(this, false, gattClientCallback);
        mGatt.requestMtu(20);
    }

    private void disconnectGattServer() {
        if (mGatt != null) {
            mGatt.disconnect();
            mGatt.close();
        }
    }

    @Override
    protected void onDestroy() {
        disconnectGattServer();
        super.onDestroy();
    }

    private class GattClientCallback extends BluetoothGattCallback {
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.d(TAG, "ATT MTU changed to " + mtu + (status == BluetoothGatt.GATT_SUCCESS ? " Success" : " Failure"));
            super.onMtuChanged(gatt, mtu, status);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == BluetoothGatt.GATT_FAILURE) {
                disconnectGattServer();
                return;
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnectGattServer();
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer();
            }
        }


        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                return;
            }
            mAdapter = new ArrayAdapter<>(ConnectActivity.this,
                    android.R.layout.simple_list_item_1,
                    mListItems);
            runOnUiThread(() -> mListView.setAdapter(mAdapter));
            mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            for (BluetoothGattService service : gatt.getServices()) {
                Log.d(TAG, gatt.getDevice().getName() + ": " + service.getUuid().toString());
                mListCharacteristics.add(null);
                mListItems.add(service.getUuid().toString());
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    mListCharacteristics.add(characteristic);
                    mListItems.add("    " + characteristic.getUuid().toString());
                    Log.d(TAG, "          ->" + characteristic.getUuid().toString());
                }
            }
            runOnUiThread(() -> mAdapter.notifyDataSetChanged());
            disconnectGattServer();
        }

    }
}
