package com.example.stble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import java.util.UUID;

public class SendActivity extends AppCompatActivity {


    private static final int PACKET_SIZE = 20;
    private static final String TAG = "SEND_ACTIVITY";
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private UUID SERVICE_UUID;
    private UUID CHARACTERISTIC_UUID;
    private BluetoothGatt mGatt;
    private Button mButtonSendText, mButtonSendImage, mButtonImportImage, mButtonSendOne;
    private ImageView mImageView;
    private EditText mDataToSend;
    private ProgressBar mProgressBar;
    private TextView mStatus, mNextCharset, mPreviousCharset, mNext, mPrevious;
    private byte[] mImage;
    private byte[] mMessageSent;
    private int mI;
    private volatile boolean mBLEAvailable = true;
    private boolean mInitialized;

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

            sendMessage(finalMessage);

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
                    mNextCharset.setText(bytesToHex(finalMessagePart2));
                    mProgressBar.setVisibility(View.VISIBLE);
                    mProgressBar.setMax(mImage.length / PACKET_SIZE);
                    mButtonSendImage.setEnabled(false);
                    mButtonImportImage.setEnabled(false);
                    mButtonSendText.setEnabled(false);
                });
                sendMessage(mMessageSent);
                mBLEAvailable = false;
            } else {
                mMessageSent = Arrays.copyOfRange(mImage, mI * PACKET_SIZE, ((mI + 1) * PACKET_SIZE));
                int finalI = mI;
                byte[] finalMessagePart2 = mMessageSent;
                runOnUiThread(() -> {
                    String text = finalI + 1 + "/" + ((mImage.length / PACKET_SIZE) + 1);
                    mPreviousCharset.setText(mNextCharset.getText());
                    mNextCharset.setText(bytesToHex(finalMessagePart2));
                    mStatus.setText(text);
                    mProgressBar.setProgress(mI);
                });

                if (mBLEAvailable) {
                    sendMessage(mMessageSent);
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
                        mNextCharset.setText(bytesToHex(finalMessagePart));
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
                        sendMessage(mMessageSent);
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
                            mNextCharset.setText(bytesToHex(finalMessagePart2));
                            mStatus.setText(text);
                        });

                        if (!sendMessage(mMessageSent))
                            mI--;
                        else
                            mBLEAvailable = false;
                    }

                    mMessageSent = null;

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
        connectDevice(selectedDevice);

        mButtonSendText.setEnabled(false);
        mButtonImportImage.setEnabled(false);
    }

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

    private boolean sendMessage(byte[] message) {
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

    private void connectDevice(BluetoothDevice device) {
        SendActivity.GattClientCallback gattClientCallback = new SendActivity.GattClientCallback();
        mGatt = device.connectGatt(this, false, gattClientCallback);
    }

    private void disconnectGattServer() {
        if (mGatt != null) {
            mGatt.disconnect();
            mGatt.close();
        }
    }

    private class GattClientCallback extends BluetoothGattCallback {
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            switch (status) {
                case BluetoothGatt.GATT_SUCCESS: {
                    Log.i(TAG, "Wrote to characteristic " + characteristic.getUuid() + " | value: " + bytesToHex(characteristic.getValue()));
                    mBLEAvailable = true;
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
                BluetoothGattService service = mGatt.getService(SERVICE_UUID);
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                mInitialized = mGatt.setCharacteristicNotification(characteristic, true);

                runOnUiThread(() -> {
                    mButtonSendText.setEnabled(true);
                    mButtonImportImage.setEnabled(true);
                });

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer();
            }
        }
    }
}