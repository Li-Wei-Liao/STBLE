package com.example.stble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothService extends Service {
    public static final String DEVICE_LIST_UPDATED = "DEVICE_LIST_UPDATED";
    public static final String CHARACTERISTICS_DISCOVERED = "CHARACTERISTICS_DISCOVERED";
    public static final String BLE_AVAILABLE = "BLE_AVAILABLE";
    public static final String INITIALIZED = "INITIALIZED";
    private static final String TAG = "BLUETOOTH_SERVICES";
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private final IBinder mBinder = new MyBinder();
    private UUID SERVICE_UUID, CHARACTERISTIC_UUID;
    private boolean mInitialized;
    private BluetoothGatt mGatt;
    private BluetoothAdapter mBluetoothAdapter;
    private ArrayList<BluetoothDevice> mScanResults;
    private BluetoothService.BtleScanCallback mScanCallback;
    private BluetoothLeScanner mBluetoothLeScanner;

    public static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 3];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = (byte) HEX_ARRAY[v >>> 4];
            hexChars[j * 3 + 1] = (byte) HEX_ARRAY[v & 0x0F];
            if (j != bytes.length - 1)
                hexChars[j * 3 + 2] = '-';
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }

    public BluetoothGatt getGatt() {
        return mGatt;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return mBluetoothAdapter;
    }

    public ArrayList<BluetoothDevice> getScanResults() {
        return mScanResults;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (mBluetoothAdapter == null) {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    public void startScan() {
        List<ScanFilter> filters = new ArrayList<>();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();
        mScanResults = new ArrayList<>();
        mScanCallback = new BtleScanCallback();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
    }

    public void stopScan() {
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback);
            mScanCallback = null;
        }
    }

    public void connectDevice(BluetoothDevice device) {
        BluetoothService.GattClientCallback gattClientCallback = new BluetoothService.GattClientCallback();
        mGatt = device.connectGatt(this, false, gattClientCallback);
        mGatt.requestMtu(20);
    }

    public void disconnectGattServer() {
        if (mGatt != null) {
            mGatt.disconnect();
            mGatt.close();
        }
    }

    public void setUUIDs(UUID SERVICE_UUID, UUID CHARACTERISTIC_UUID) {
        this.SERVICE_UUID = SERVICE_UUID;
        this.CHARACTERISTIC_UUID = CHARACTERISTIC_UUID;

        BluetoothGattService service = mGatt.getService(SERVICE_UUID);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

        mInitialized = mGatt.setCharacteristicNotification(characteristic, true);
        if (mInitialized)
            sendBroadcast(new Intent(BluetoothService.INITIALIZED));
    }

    public boolean sendMessage(byte[] message) {
        if (!mInitialized) {
            return false;
        }
        BluetoothGattService service = mGatt.getService(SERVICE_UUID);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
        characteristic.setValue(message);

        boolean success = mGatt.writeCharacteristic(characteristic);
        Log.d(TAG, "Sending message " + (success ? "success \"" : "not success \"") + bytesToHex(message) + "\"");
        return success;
    }

    public class MyBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
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
                    Log.d(TAG, "Device " + device.getAddress() + " " + device.getName());
                    sendBroadcast(new Intent(BluetoothService.DEVICE_LIST_UPDATED));
                }
            }

        }
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
            sendBroadcast(new Intent(BluetoothService.CHARACTERISTICS_DISCOVERED));
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            switch (status) {
                case BluetoothGatt.GATT_SUCCESS: {
                    Log.i(TAG, "Wrote to characteristic " + characteristic.getUuid() + " | value: " + bytesToHex(characteristic.getValue()));
                    sendBroadcast(new Intent(BluetoothService.BLE_AVAILABLE));
                    break;
                }
                case BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH: {
                    Log.e(TAG, "Write exceeded connection ATT MTU!");
                    break;
                }
                case BluetoothGatt.GATT_WRITE_NOT_PERMITTED: {
                    Log.e(TAG, "Write not permitted!");
                    break;
                }
                default: {
                    Log.e(TAG, "Characteristic write failed, error: " + status);
                    break;
                }
            }
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

    }
}
