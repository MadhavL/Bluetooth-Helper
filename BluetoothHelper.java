package com.appName.NAME;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BluetoothHelper {
    private static BluetoothHelper instance;
    private BluetoothAdapter bluetoothAdapter;
    static final int REQUEST_ENABLE_BT = 19;
    private InputStream inputStream;
    private OutputStream outputStream;
    private String TAG = "btConn";
    private boolean mScanning = false;
    private Handler handler = new Handler();
    private Activity activity;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    public static synchronized BluetoothHelper getInstance() {
        if (instance == null) {
            instance = new BluetoothHelper();
        }
        return instance;
    }

    void enableBluetooth(Activity activity) {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else checkForSupportedBtTech(activity);
        } else Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_SHORT).show();
    }

    private Object[] showPairedDevices() {
        if (bluetoothAdapter.isEnabled()) {
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

            if (bondedDevices.size() > 0) {
                return bondedDevices.toArray();
            } else return null;
        } else return null;
    }

    void showPsiredDeviceList(final Activity activity) {
        if (showPairedDevices() != null) {
            final AlertDialog.Builder builderSingle = new AlertDialog.Builder(activity);
            builderSingle.setTitle("Select Your Device");

            final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(activity, android.R.layout.select_dialog_singlechoice);
            for (int i = 0; i < showPairedDevices().length; i++) {
                BluetoothDevice device = (BluetoothDevice) showPairedDevices()[i];
                arrayAdapter.add(device.getName());
            }

            builderSingle.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    setUpConnectionWithDevice((BluetoothDevice) showPairedDevices()[which]);
                    dialog.dismiss();
                }
            });
            builderSingle.show();
        }
    }

    private void setUpConnectionWithDevice(BluetoothDevice device) {
        ParcelUuid[] uuids = device.getUuids();
        BluetoothSocket socket;
        try {
            socket = device.createRfcommSocketToServiceRecord(uuids[0].getUuid());
            socket.connect();
            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();
            Log.d(TAG, "successfully connected");
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "error " + e.getMessage());
        }
    }

    void write(String s) throws IOException {
        if (outputStream != null) {
            outputStream.write(s.getBytes());
            outputStream.flush();
        }
    }

    void read() throws IOException {
        inputStream.read();
    }

    void checkForSupportedBtTech(Activity context) {
        this.activity = context;
//        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
//            Toast.makeText(context, "BLE not supported", Toast.LENGTH_SHORT).show();
//            showPsiredDeviceList(context);
//        } else scanLeDevice();
        showPsiredDeviceList(context);
    }
