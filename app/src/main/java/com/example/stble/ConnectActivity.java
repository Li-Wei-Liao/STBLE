package com.example.stble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Class to handel connection to a device
 */
public class ConnectActivity extends AppCompatActivity implements ServiceConnection {

    private static final String TAG = "CONNECT_ACTIVITY";

    ListView mListView;

    ArrayList<String> mListItems = new ArrayList<>();
    ArrayList<BluetoothGattCharacteristic> mListCharacteristics = new ArrayList<>();
    ArrayAdapter<String> mAdapter;

    private BluetoothDevice mSelectedDevice;

    private BluetoothService mBluetoothService;
    private ConnectActivity.DataUpdateReceiver dataUpdateReceiver;

    private ProgressBar mSearchWheel;

    /**
     * Connect Activity onCreate
     *
     * @param savedInstanceState instance
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        TextView deviceText = findViewById(R.id.DeviceNameConnect);

        mSelectedDevice = getIntent().getParcelableExtra("Device");
        assert mSelectedDevice != null;
        Log.d(TAG, "Device " + mSelectedDevice.getName());

        deviceText.setText(mSelectedDevice.getName());

        mListView = findViewById(R.id.listView2);

        mListView.setOnItemClickListener((adapterView, view, i, l) -> {
            if (l != 0 && mListCharacteristics.get((int) l) != null) {
                BluetoothGattCharacteristic characteristic = mListCharacteristics.get((int) l);
                Intent intent = new Intent(ConnectActivity.this, SendActivity.class);
                intent.putExtra("Device", mSelectedDevice);
                intent.putExtra("Chara", characteristic.getUuid().toString());
                intent.putExtra("Service", characteristic.getService().getUuid().toString());
                startActivity(intent);
            }
        });

        mSearchWheel = findViewById(R.id.progressBar2);
    }

    /**
     * Connect Activity onResume
     */
    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent(this, BluetoothService.class);
        bindService(intent, this, Context.BIND_AUTO_CREATE);

        if (dataUpdateReceiver == null)
            dataUpdateReceiver = new ConnectActivity.DataUpdateReceiver();

        IntentFilter intentFilter = new IntentFilter(BluetoothService.CHARACTERISTICS_DISCOVERED);
        registerReceiver(dataUpdateReceiver, intentFilter);
    }

    /**
     * Send Activity onPause
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (dataUpdateReceiver != null) unregisterReceiver(dataUpdateReceiver);
    }

    /**
     * Send Activity onDestroy
     */
    @Override
    protected void onDestroy() {
        unbindService(this);

        mBluetoothService.disconnectGattServer();
        super.onDestroy();
    }

    /**
     * When bluetooth service is connected
     *
     * @param componentName component name
     * @param iBinder       binder
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        BluetoothService.MyBinder b = (BluetoothService.MyBinder) iBinder;
        mBluetoothService = b.getService();
        mBluetoothService.connectDevice(mSelectedDevice);
    }

    /**
     * when bluetooth service is disconnected
     *
     * @param componentName component name
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
         * On data receive from bluetooth service
         *
         * @param context context
         * @param intent  intent
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), BluetoothService.CHARACTERISTICS_DISCOVERED)) {

                BluetoothGatt gatt = mBluetoothService.getGatt();

                mAdapter = new ArrayAdapter<>(ConnectActivity.this,
                        android.R.layout.simple_list_item_1,
                        mListItems);
                runOnUiThread(() -> mListView.setAdapter(mAdapter));
                mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

                for (BluetoothGattService service : gatt.getServices()) {
                    Log.d(TAG, service.getUuid().toString());
                    mListCharacteristics.add(null);
                    mListItems.add(service.getUuid().toString());
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        mListCharacteristics.add(characteristic);
                        mListItems.add("  └─> " + characteristic.getUuid().toString());
                        Log.d(TAG, "  └─> " + characteristic.getUuid().toString());
                    }
                }
                runOnUiThread(() -> {
                    mAdapter.notifyDataSetChanged();
                    mSearchWheel.setVisibility(View.INVISIBLE);
                });
            }
        }
    }
}
