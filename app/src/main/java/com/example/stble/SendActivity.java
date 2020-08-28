package com.example.stble;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Class to send data to the device
 */
public class SendActivity extends AppCompatActivity implements ServiceConnection {


    private static final int PACKET_SIZE = 20;
    private static final String TAG = "SEND_ACTIVITY";
    private UUID SERVICE_UUID;
    private UUID CHARACTERISTIC_UUID;

    private Button mButtonSendText, mButtonSendImage, mButtonImportImage, mButtonSendOne;
    private ImageView mImageView;
    private EditText mDataToSend;
    private ProgressBar mProgressBar;
    private TextView mStatus, mNextCharset, mPreviousCharset, mNext, mPrevious;
    private byte[] mImage;
    private byte[] mMessageSent;
    private int mI;

    private volatile boolean mBLEAvailable = true;

    private BluetoothService mBluetoothService;
    private SendActivity.DataUpdateReceiver dataUpdateReceiver;

    /**
     * Send Activity onCreate
     *
     * @param savedInstanceState instance
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);

        TextView deviceText = findViewById(R.id.DeviceNameSend);

        mStatus = findViewById(R.id.ProgressStatus);
        mNextCharset = findViewById(R.id.charsetNext);
        mNext = findViewById(R.id.Next);
        mPreviousCharset = findViewById(R.id.CharsetPrevious);
        mPrevious = findViewById(R.id.Previous);

        mNext.setVisibility(View.INVISIBLE);
        mPrevious.setVisibility(View.INVISIBLE);

        mDataToSend = findViewById(R.id.dataToSend);
        mButtonSendText = findViewById(R.id.buttonSendText);

        mButtonSendImage = findViewById(R.id.buttonSendImage);
        mButtonImportImage = findViewById(R.id.buttonImportImage);
        mButtonSendOne = findViewById(R.id.SendOnePacket);
        mImageView = findViewById(R.id.imageView);

        mProgressBar = findViewById(R.id.progressBar);
        mProgressBar.setVisibility(View.INVISIBLE);

        BluetoothDevice selectedDevice = getIntent().getParcelableExtra("Device");
        assert selectedDevice != null;
        Log.d(TAG, "Device " + selectedDevice.getName());

        deviceText.setText(selectedDevice.getName());

        mButtonImportImage.setOnClickListener(view -> {
            Intent mRequestFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
            mRequestFileIntent.setType("image/jpeg");
            startActivityForResult(mRequestFileIntent, 0);
        });

        mButtonSendText.setOnClickListener(view -> {
            boolean wasEnabled = false;

            if (mButtonSendImage.isEnabled())
                wasEnabled = true;

            mButtonSendText.setEnabled(false);
            mButtonImportImage.setEnabled(false);
            mButtonSendImage.setEnabled(false);

            String message = mDataToSend.getText().toString();
            byte[] header = new byte[]{(byte) 0xAA, (byte) 0xAA};
            byte[] messageBytes;
            messageBytes = message.getBytes(StandardCharsets.UTF_8);
            byte[] finalMessage = new byte[header.length + messageBytes.length];
            System.arraycopy(header, 0, finalMessage, 0, header.length);
            System.arraycopy(messageBytes, 0, finalMessage, header.length, messageBytes.length);

            mBluetoothService.sendMessage(finalMessage);

            boolean finalWasEnabled = wasEnabled;
            runOnUiThread(() -> {
                if (finalWasEnabled)
                    mButtonSendImage.setEnabled(true);
                mButtonSendText.setEnabled(true);
                mButtonImportImage.setEnabled(true);
            });
        });

        mButtonSendOne.setOnClickListener(view -> {
            if (mMessageSent == null) {
                byte[] header = new byte[]{(byte) 0x55, (byte) 0x55};
                byte[] sizeInBytes = ByteBuffer.allocate(4).putInt(mImage.length).array();

                byte[] finalHeader = new byte[PACKET_SIZE];

                System.arraycopy(header, 0, finalHeader, 0, header.length);
                System.arraycopy(sizeInBytes, 0, finalHeader, header.length, sizeInBytes.length);

                mMessageSent = finalHeader;
                byte[] finalMessagePart2 = mMessageSent;
                runOnUiThread(() -> {
                    String text = "0/" + ((mImage.length / PACKET_SIZE) + 1);
                    mStatus.setText(text);
                    mPreviousCharset.setText(mNextCharset.getText());
                    mNextCharset.setText(BluetoothService.bytesToHex(finalMessagePart2));
                    mProgressBar.setVisibility(View.VISIBLE);
                    mProgressBar.setMax(mImage.length / PACKET_SIZE);
                    mButtonSendImage.setEnabled(false);
                    mButtonImportImage.setEnabled(false);
                    mButtonSendText.setEnabled(false);
                    mDataToSend.setEnabled(false);
                });
                mBluetoothService.sendMessage(mMessageSent);
                mBLEAvailable = false;
            } else {
                mMessageSent = Arrays.copyOfRange(mImage, mI * PACKET_SIZE, ((mI + 1) * PACKET_SIZE));
                int finalI = mI;
                byte[] finalMessagePart2 = mMessageSent;
                runOnUiThread(() -> {
                    String text = finalI + 1 + "/" + ((mImage.length / PACKET_SIZE) + 1);
                    mPreviousCharset.setText(mNextCharset.getText());
                    mNextCharset.setText(BluetoothService.bytesToHex(finalMessagePart2));
                    mStatus.setText(text);
                    mProgressBar.setProgress(mI);
                });

                if (mBLEAvailable) {
                    mBluetoothService.sendMessage(mMessageSent);
                    mI++;
                    mBLEAvailable = false;
                }
            }
        });

        mButtonSendImage.setOnClickListener(view -> {
            runOnUiThread(() -> {
                mButtonSendText.setEnabled(false);
                mButtonSendImage.setEnabled(false);
                mButtonImportImage.setEnabled(false);
                mButtonSendOne.setEnabled(false);
            });


            Thread thread = new Thread() {
                @Override
                public void run() {
                    byte[] header = new byte[]{(byte) 0x55, (byte) 0x55};
                    byte[] sizeInBytes = ByteBuffer.allocate(4).putInt(mImage.length).array();
                    byte[] finalHeader = new byte[PACKET_SIZE];

                    System.arraycopy(header, 0, finalHeader, 0, header.length);
                    System.arraycopy(sizeInBytes, 0, finalHeader, header.length, sizeInBytes.length);

                    mProgressBar.setMax(mImage.length / PACKET_SIZE);

                    mMessageSent = finalHeader;
                    byte[] finalMessagePart = mMessageSent;
                    runOnUiThread(() -> {
                        String text = "0/" + ((mImage.length / PACKET_SIZE) + 1);
                        mStatus.setText(text);
                        mPreviousCharset.setText(mNextCharset.getText());
                        mNextCharset.setText(BluetoothService.bytesToHex(finalMessagePart));
                        mProgressBar.setVisibility(View.VISIBLE);
                        mNext.setVisibility(View.VISIBLE);
                        mPrevious.setVisibility(View.VISIBLE);
                        mButtonSendImage.setEnabled(false);
                        mButtonImportImage.setEnabled(false);
                        mButtonSendOne.setEnabled(false);
                        mButtonSendText.setEnabled(false);
                        mDataToSend.setEnabled(false);
                    });

                    try {
                        mBluetoothService.sendMessage(mMessageSent);
                        mBLEAvailable = false;
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    for (mI = 0; mI != (mImage.length / PACKET_SIZE) + 1; mI++) {
                        while (!mBLEAvailable) {
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        mMessageSent = Arrays.copyOfRange(mImage, mI * PACKET_SIZE, ((mI + 1) * PACKET_SIZE));
                        int finalI = mI;
                        byte[] finalMessagePart2 = mMessageSent;
                        mProgressBar.setProgress(mI);
                        runOnUiThread(() -> {
                            String text = finalI + 1 + "/" + ((mImage.length / PACKET_SIZE) + 1);
                            mPreviousCharset.setText(mNextCharset.getText());
                            mNextCharset.setText(BluetoothService.bytesToHex(finalMessagePart2));
                            mStatus.setText(text);
                        });

                        if (!mBluetoothService.sendMessage(mMessageSent))
                            mI--;
                        else
                            mBLEAvailable = false;
                    }

                    mMessageSent = null;
                    mI = 0;

                    runOnUiThread(() -> {
                        mProgressBar.setVisibility(View.INVISIBLE);
                        mNext.setVisibility(View.INVISIBLE);
                        mPrevious.setVisibility(View.INVISIBLE);
                        mPreviousCharset.setText("");
                        mNextCharset.setText("");
                        mStatus.setText("");
                        mButtonSendText.setEnabled(true);
                        mButtonSendImage.setEnabled(true);
                        mButtonImportImage.setEnabled(true);
                        mButtonSendOne.setEnabled(true);
                        mDataToSend.setEnabled(true);
                    });

                }
            };

            thread.start();
        });

        mButtonSendImage.setEnabled(false);
        mButtonSendOne.setEnabled(false);

        SERVICE_UUID = java.util.UUID.fromString(getIntent().getStringExtra("Service"));
        CHARACTERISTIC_UUID = java.util.UUID.fromString(getIntent().getStringExtra("Chara"));

        Log.d(TAG, selectedDevice.getName() + " " + SERVICE_UUID + " " + CHARACTERISTIC_UUID);

        mButtonSendText.setEnabled(false);
        mButtonImportImage.setEnabled(false);
    }

    /**
     * Send Activity onResume
     */
    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent(this, BluetoothService.class);
        bindService(intent, this, Context.BIND_AUTO_CREATE);

        if (dataUpdateReceiver == null)
            dataUpdateReceiver = new SendActivity.DataUpdateReceiver();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothService.BLE_AVAILABLE);
        intentFilter.addAction(BluetoothService.INITIALIZED);
        registerReceiver(dataUpdateReceiver, intentFilter);
    }

    /**
     * Send Activity onDestroy
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dataUpdateReceiver != null) unregisterReceiver(dataUpdateReceiver);
        unbindService(this);
    }

    /**
     * Get bytes from input file
     *
     * @param inputStream input file
     * @return byte array
     * @throws IOException no file available
     */
    public byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    /**
     * returning result from get file
     *
     * @param requestCode  request code
     * @param resultCode   result code
     * @param returnIntent intent
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent returnIntent) {
        super.onActivityResult(requestCode, resultCode, returnIntent);
        if (resultCode == RESULT_OK) {
            Uri returnUri = returnIntent.getData();
            mImageView.setImageURI(returnUri);

            InputStream iStream;
            try {
                assert returnUri != null;
                iStream = getContentResolver().openInputStream(returnUri);
                assert iStream != null;
                mImage = getBytes(iStream);
            } catch (IOException e) {
                e.printStackTrace();
            }

            mButtonSendImage.setEnabled(true);
            mButtonSendOne.setEnabled(true);
        }
    }

    /**
     * on bluetooth service connected
     *
     * @param componentName component name
     * @param iBinder       binder
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        BluetoothService.MyBinder b = (BluetoothService.MyBinder) iBinder;
        mBluetoothService = b.getService();
        mBluetoothService.setUUIDs(SERVICE_UUID, CHARACTERISTIC_UUID);
    }

    /**
     * on bluetooth service disconnected
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
            if (Objects.equals(intent.getAction(), BluetoothService.BLE_AVAILABLE)) {
                mBLEAvailable = true;
            } else if (Objects.equals(intent.getAction(), BluetoothService.INITIALIZED)) {
                runOnUiThread(() -> {
                    mButtonSendText.setEnabled(true);
                    mButtonImportImage.setEnabled(true);
                });
            }
        }
    }
}