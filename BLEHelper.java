package com.google.audio;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_MATCH_LOST;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

@SuppressLint("MissingPermission")
class BLEHelper {

    /*
     * Singleton
     * */
    private static BLEHelper instance;
    private static Activity activity;

    private BLEHelper() {
        scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {

                BluetoothDevice device = result.getDevice();
                ArrayList<ScanResult> localScanResults = scanResults.getValue();

                /*try {
                    Log.d("LocalScanResults", result.getDevice().getName());
                }
                catch (Exception e) {

                }*/

                int indexQuery = -1;
                if (localScanResults != null && !localScanResults.isEmpty()) {
                    for (int i = 0; i < localScanResults.size(); i++) {
                        if (localScanResults.get(i).getDevice().getAddress().equals(device.getAddress())) {
                            indexQuery = i;
                            break;
                        }
                    }
                } else {
                    localScanResults = new ArrayList<>();
                }
                boolean needToUpdate = false;
                if (callbackType == CALLBACK_TYPE_ALL_MATCHES) {
                    if (indexQuery != -1) { // A scan result already exists with the same address
                        localScanResults.set(indexQuery, result);
                    } else {
                        if (result.getDevice().getName() != null){
                            localScanResults.add(result);
                            Log.d("Add", "Name: " + result.getDevice().getName());
                            needToUpdate = true;
                        }
                    }
                } else if (callbackType == CALLBACK_TYPE_MATCH_LOST) {
                    if (indexQuery != -1) { // A scan result already exists with the same address
                        localScanResults.remove(indexQuery);
                        Log.d("Fire", "2");
                        needToUpdate = true;
                    }
                }

                scanResults.setValue(localScanResults);
                if (!localScanResults.isEmpty() && needToUpdate) {
                    showDeviceList(localScanResults);
                }
            }
        };
    }

    public static BLEHelper getInstance(AppCompatActivity activity) {
        BLEHelper.activity = activity;
        if (instance == null) {
            instance = new BLEHelper();
            instance.initBLE();
        }
        return instance;
    }


    /*
     * Constants
     * */
    private static final int ENABLE_GPS_REQUEST_CODE = 546;
    private static final int ENABLE_BLUETOOTH_REQUEST_CODE = 985;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 682;
    private static final int BLE_SCAN_PERMISSION_REQUEST_CODE = 488;
    private static final int BLE_CONNECT_PERMISSION_REQUEST_CODE = 485;

    /*
     * Variables
     * */

    private AlertDialog alertDialog;

    private long startScanningAt = 0L;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private ScanSettings scanSettings;
    private ScanCallback scanCallback;
    private BluetoothGatt bluetoothGatt;

    private MutableLiveData<ArrayList<ScanResult>> scanResults = new MutableLiveData<>();
    private MutableLiveData<String> readData = new MutableLiveData<>();
    private MutableLiveData<Boolean> isConnected = new MutableLiveData<>();

    /*
     * Returning livedata of scanned device list
     * */
    public LiveData<ArrayList<ScanResult>> getScanResults() {
        return scanResults;
    }

    /*
     * Return livedata of string to get read data.
     * */
    public LiveData<String> getReadData() {
        return readData;
    }

    /*
     * Return livedata of device connection status
     * */
    public MutableLiveData<Boolean> getIsConnected() {
        return isConnected;
    }

    private boolean isScanning = false;

    public boolean getScanning() {
        return isScanning;
    }

    public void setScanning(boolean scanning) {
        startScanningAt = System.currentTimeMillis();
        isScanning = scanning;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter.isEnabled();
    }

    private boolean isGPSEnabled() {
        LocationManager manager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    /*
     * Connectivity Callback
     * */
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    bluetoothGatt = gatt;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (bluetoothGatt != null) bluetoothGatt.discoverServices();
                    });
                    isConnected.postValue(true);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close();
                    bluetoothGatt = null;
                    isConnected.postValue(false);
                }
            } else {
                gatt.close();
                bluetoothGatt = null;
                isConnected.postValue(false);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            //Log.d("BluetoothCharacter", characteristic.getStringValue(0));
            readData.postValue(characteristic.getStringValue(0));
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (gatt!= null) {
                if (gatt.getService(UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))!= null) {
                    BluetoothGattCharacteristic characteristic = gatt.getService(UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")).getCharacteristic(UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"));
                    gatt.setCharacteristicNotification(characteristic, true);
                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                    Log.d("Services", "Successfully set Notification");
                }
            }

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            switch (status) {
                case BluetoothGatt.GATT_SUCCESS: {
                    String oldStr = readData.getValue();
                    if (oldStr == null) oldStr = "";
                    readData.setValue(oldStr + toHexString(characteristic.getValue()));
                    Log.i("BluetoothGattCallback", "Read characteristic " + characteristic.getUuid() + ":\n" + toHexString(characteristic.getValue()));
                }
                case BluetoothGatt.GATT_READ_NOT_PERMITTED: {
                    Log.e("BluetoothGattCallback", "Read not permitted for $uuid!");
                }
                default: {
                    Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status");
                }
            }
        }
    };

    /*
     * Utility for converting byte data to string
     * */
    private String toHexString(byte[] byteArray) {
        StringBuilder result = new StringBuilder("0x");
        for (int i = 0; i <= byteArray.length; i++) {
            result.append(String.format("%02X", byteArray[i]));
            if (i + 1 != byteArray.length)
                result.append(" ");
        }

        return result.toString();
    }

    /*
     * Utility to check characteristic property
     * */
    private boolean containsProperty(BluetoothGattCharacteristic characteristic, int property) {
        return (characteristic.getProperties() & property) != 0;
    }

    private boolean isReadable(BluetoothGattCharacteristic characteristic) {
        return containsProperty(characteristic, BluetoothGattCharacteristic.PROPERTY_READ);
    }

    private boolean isWritable(BluetoothGattCharacteristic characteristic) {
        return containsProperty(characteristic, BluetoothGattCharacteristic.PROPERTY_WRITE);
    }

    private boolean isWritableWithoutResponse(BluetoothGattCharacteristic characteristic) {
        return containsProperty(characteristic, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE);
    }

    /*
     * Method to write string to connected BLE Device.
     * */
    public void write(String data) {
        if (hasAllPermissions() && bluetoothGatt != null) {
            List<BluetoothGattService> services = bluetoothGatt.getServices();
            Integer writeType = null;
            for (int i = 0; i < services.size(); i++) {
                BluetoothGattService service = services.get(i);
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                for (int j = 0; j < characteristics.size(); j++) {
                    BluetoothGattCharacteristic characteristic = characteristics.get(j);
                    /*if (isWritable(characteristic)) {
                        writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
                    } else */
                    if (isWritableWithoutResponse(characteristic)) {
                        writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
                    }

                    if (writeType != null) {
                        characteristic.setWriteType(writeType);
                        characteristic.setValue(data);
                        bluetoothGatt.writeCharacteristic(characteristic);
                        break;
                    }
                }
                if (writeType != null) break;
            }
        }
    }

    /*
     * Initialize BLE Helper
     * */
    public void initBLE() {
        scanResults.setValue(new ArrayList<>());
        BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    /*
     * Method to start scanning
     * */
    public void startBleScan() {
        scanResults.setValue(new ArrayList<>());
        if (!isLocationPermissionGranted()) {
            requestLocationPermission();
        } else if (!isBluetoothScanPermissionGranted()) {
            requestBLEScanPermission();
        } else if (!isBluetoothConnectPermissionGranted()) {
            requestBLEConnectPermission();
        } else if (!isGPSEnabled()) {
            promptEnableGPS();
        } else if (!isBluetoothEnabled()) {
            promptEnableBluetooth();
        } else {
            if (bleScanner == null) initBLE();
            bleScanner.startScan(null, scanSettings, scanCallback);
            setScanning(true);
        }
    }

    /*
     * Method to stop scanning
     * */
    public void stopBleScan() {
        try {
            bleScanner.stopScan(scanCallback);
        } catch (Exception e) {
            e.printStackTrace();
        }
        setScanning(false);
    }

    /*
     * Method to connect a device
     * */
    public void connect(BluetoothDevice device) {
        if (getScanning()) {
            stopBleScan();
        }
        device.connectGatt(activity, false, gattCallback);
    }

    /*
     * Prompt a dialog to request location permission
     * */
    private void requestLocationPermission() {
        if (isLocationPermissionGranted()) {
            return;
        }
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("Location permission required")
                    .setMessage("Starting from Android M (6.0), the system requires apps to be granted location access in order to scan for BLE devices.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            //set what would happen when positive button is clicked
                            requestPermission(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    LOCATION_PERMISSION_REQUEST_CODE
                            );
                        }
                    })
                    .show();
        });
    }

    /*
     * Showing Dialog to to request BLE scan permission
     * Uncomment the code if targeting api level 31
     * */
    private void requestBLEScanPermission() {
        if (isBluetoothScanPermissionGranted()) {
            return;
        }
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("Bluetooth Scan permission required")
                    .setMessage("Starting from Android R (12.0), the system requires apps to be granted in order to scan for BLE devices.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            //set what would happen when positive button is clicked
                            requestPermission(
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    BLE_SCAN_PERMISSION_REQUEST_CODE
                            );
                        }
                    })
                    .show();
        });
    }

    /*
     * Showing Dialog to to request BLE connect permission
     * Uncomment the code if targeting api level 31
     * */
    private void requestBLEConnectPermission() {
        if (isBluetoothConnectPermissionGranted()) {
            return;
        }
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("Bluetooth Connect permission required")
                    .setMessage("Starting from Android R (12.0), the system requires apps to be granted in order to connect to BLE devices.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            //set what would happen when positive button is clicked
                            requestPermission(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    BLE_CONNECT_PERMISSION_REQUEST_CODE
                            );
                        }
                    })
                    .show();
        });
    }

    /*
     * Generic method to request any runtime permission
     * */
    public void requestPermission(String permission, int requestCode) {
        ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
    }

    /*
     * Generic method to check runtime permission granted or not
     * */
    public boolean hasPermission(String permissionType) {
        return ContextCompat.checkSelfPermission(activity, permissionType) == PackageManager.PERMISSION_GRANTED;
    }

    /*
     * Check if location permission granted or not
     * */
    public boolean isLocationPermissionGranted() {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    /*
     * Check BLE scan permission
     * Uncomment the code if targeting api level 31
     * */
    public boolean isBluetoothScanPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return hasPermission(Manifest.permission.BLUETOOTH_SCAN);
        } else {
        return true;
        }
    }

    /*
     * Check BLE connect permission
     * Uncomment the code if targeting api level 31
     * */
    public boolean isBluetoothConnectPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return hasPermission(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
        return true;
        }
    }

    /*
     * Method to log all characteristics of given device
     * */
    public void printGattTable(BluetoothGatt gatt) {
        if (gatt.getServices().isEmpty()) {
            return;
        }
        List<BluetoothGattService> services = gatt.getServices();
        StringBuilder characteristicsTable = new StringBuilder("|--");

        for (int i = 0; i < services.size(); i++) {
            BluetoothGattService service = services.get(i);

            //StringBuilder characteristicsTable = new StringBuilder("|--");

            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
            for (int j = 0; j < characteristics.size(); j++) {
                BluetoothGattCharacteristic characteristic = characteristics.get(j);
                characteristicsTable.append(characteristic.getUuid().toString());
                if (j + 1 != characteristics.size()) {
                    characteristicsTable.append("\n|--");
                }
            }
        }
        Log.d("GattServices", services.toString() + " | " + characteristicsTable.toString());

    }

    /*
     * Method to prompt a dialog to enable bluetooth
     * */
    public void promptEnableBluetooth() {
        if (hasAllPermissions()) {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                activity.startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE);
            }
        }
    }

    /*
     * Method to prompt a dialog to enable bluetooth
     * */
    public void promptEnableGPS() {
        if (hasAllPermissions()) {
            if (!isGPSEnabled()) {
                LocationRequest request = LocationRequest.create();
                request.setInterval(10000);
                request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

                LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(request);
                SettingsClient client = LocationServices.getSettingsClient(activity);
                Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());
                task.addOnFailureListener(command -> {
                    if (command instanceof ResolvableApiException) {
                        try {
                            ((ResolvableApiException) command).startResolutionForResult(activity, ENABLE_GPS_REQUEST_CODE);
                        } catch (IntentSender.SendIntentException sendEx) {
                            sendEx.printStackTrace();
                        }
                    }
                }).addOnSuccessListener(command -> startBleScan());
            }
        }
    }

    /*
     * Method to show a dialog of all scanned devices
     * Also will connect the selected device
     * */
    void showDeviceList(ArrayList<ScanResult> scanResults) {

        ArrayList<BluetoothDevice> devices = new ArrayList<>();

        for (int i = 0; scanResults != null && i < scanResults.size(); i++) {
            devices.add(scanResults.get(i).getDevice());
        }

        if (!devices.isEmpty()) {
            if (alertDialog != null) alertDialog.dismiss();
            AlertDialog.Builder builderSingle = new AlertDialog.Builder(activity);
            builderSingle.setTitle("Select Your Device");

            final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(activity, android.R.layout.select_dialog_singlechoice);
            for (int i = 0; i < devices.size(); i++) {
                BluetoothDevice device = (BluetoothDevice) devices.get(i);
                String name = device.getName();
                if (TextUtils.isEmpty(name)) name = "NoName";
                arrayAdapter.add(name); //removed so empty names will not show up in list
                //if (!TextUtils.isEmpty(name)) arrayAdapter.add(name);
            }
            
            builderSingle.setCancelable(false);

            builderSingle.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    stopBleScan();
                }
            });

            builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    connect((BluetoothDevice) devices.get(which));
                    dialog.dismiss();
                }
            });
            alertDialog = builderSingle.show();
            alertDialog.setCanceledOnTouchOutside(false);
        }
    }

    /*
     * Wrapper method to handle on activity result
     * */
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == ENABLE_GPS_REQUEST_CODE || requestCode == ENABLE_BLUETOOTH_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                startBleScan();
            }
        }
    }

    /*
     * Wrapper method to manage request permission callback
     * */
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE || requestCode == BLE_SCAN_PERMISSION_REQUEST_CODE || requestCode == BLE_CONNECT_PERMISSION_REQUEST_CODE) {
            startBleScan();
        }
    }

    /*
     * Method to check all required method are granted or not
     * */
    private boolean hasAllPermissions() {
        return isLocationPermissionGranted() && isBluetoothScanPermissionGranted() && isBluetoothConnectPermissionGranted();
    }
}
