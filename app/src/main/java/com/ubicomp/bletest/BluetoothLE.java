package com.ubicomp.bletest;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressLint("NewApi")
public class BluetoothLE {
	private static final String TAG = "BluetoothLE";

    private static final UUID SERVICE3_UUID = UUID.fromString("713D0000-503E-4C75-BA94-3148F18D941E");
    public static final UUID SERVICE3_WRITE_CHAR_UUID = UUID.fromString("713D0003-503E-4C75-BA94-3148F18D941E");
    private static final UUID SERVICE3_NOTIFICATION_CHAR_UUID = UUID.fromString("713D0002-503E-4C75-BA94-3148F18D941E");

    public final static byte BLE_REQUEST_DEVICE_ID = (byte)0x00;
    public final static byte BLE_CHANGE_DEVICE_ID = (byte)0x01;
    public final static byte BLE_REQUEST_SALIVA_VOLTAGE = (byte)0x02;
    public final static byte BLE_REQUEST_IMAGE_INFO = (byte)0x03;
    public final static byte BLE_REQUEST_IMAGE_BY_INDEX = (byte)0x04;
    public final static byte BLE_END_IMAGE_TRANSFER = (byte)0x05;
    public final static byte BLE_DEREQUEST_SALIVA_VOLTAGE = (byte)0x06;
    public final static byte BLE_SHUTDOWN = (byte)0x07;


    public final static byte BLE_REPLY_IMAGE_INFO = (byte)0x00;
    public final static byte BLE_REPLY_SALIVA_VOLTAGE = (byte)0x01;
    public final static byte BLE_REPLY_DEVICE_ID = (byte)0x02;
    public final static byte BLE_REPLY_IMAGE_PACKET = (byte)0x03;

    public enum AppStateTypeDef {
        APP_FETCH_INFO,
        APP_IMAGE_GET_HEADER,
        APP_IMAGE_RECEIVING,
        APP_IMAGE_RECEIVED,
    };

	// Intent request codes
    private static final int REQUEST_ENABLE_BT = 2;
	
	private Activity activity = null;
    private String mDeviceName = null;
    private String mDeviceAddress = null;

	private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeService mBluetoothLeService = null;
    private BluetoothDataParserService mBluetoothDataParserService = null;
    private boolean mConnected = false;
    private boolean mDeviceScanned = false;

    private boolean mManualDisconnect = false;
    private boolean mReconnection = false;

    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattCharacteristic mWriteCharacteristic;

    private Handler mHandler;
    private Runnable mRunnable;
    private boolean mScanning = false;

    // Stops scanning after 15 seconds.
    private static final long SCAN_PERIOD = 15000;
    public AppStateTypeDef mAppStateTypeDef = AppStateTypeDef.APP_FETCH_INFO;

    public BluetoothLE(Activity activity, String mDeviceName) {
        mHandler = new Handler();

        this.activity = activity;
        this.mDeviceName = mDeviceName;

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            ((BluetoothListener) activity).bleNotSupported();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            ((BluetoothListener) activity).bleNotSupported();
            return;
        }
    }
     
	// Code to manage Service lifecycle.
	private final ServiceConnection mBluetoothLeServiceConnection = new ServiceConnection() {
        
	    @Override
	    public void onServiceConnected(ComponentName componentName, IBinder service) {
	        mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();

	        if (!mBluetoothLeService.initialize()) {
	            Log.e(TAG, "Unable to initialize Bluetooth Service.");
//	            activity.finish();
	        }
	        // Automatically connects to the device upon successful start-up initialization.
	        mBluetoothLeService.connect(mDeviceAddress);
	    }
	
	    @Override
	    public void onServiceDisconnected(ComponentName componentName) {
            unbindBLEService();
            mBluetoothLeService = null;
	    }
	};

    // Code to manage Service lifecycle.
    private final ServiceConnection mDataParserServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mBluetoothDataParserService = ((BluetoothDataParserService.LocalBinder) iBinder).getService();
            Log.d(TAG, "Successfully bind BluetoothDataParser Service.");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            // onServiceDisconnected is called when service was terminated by stopSelf() or stopService() and service is still bound.

            activity.unbindService(mDataParserServiceConnection);
            mBluetoothDataParserService = null;
            Log.d(TAG, "Unbind BluetoothDataParser Service.");
        }
    };


    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                mReconnection = false;
                //Terminate the BLE connection timeout (10sec)
                mHandler.removeCallbacks(mRunnable);
                mScanning = false;
                if(mBluetoothDataParserService != null)
                    mBluetoothDataParserService.setCheckTimeoutPause(false);
                ((BluetoothListener) activity).bleConnected();

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                unbindBLEService();
                if(mManualDisconnect){
                    ((BluetoothListener) activity).bleDisconnected();
                }
                else{
                    Log.d(TAG, "Reconnection started.");
                    if(mBluetoothDataParserService != null)
                        mBluetoothDataParserService.setCheckTimeoutPause(true);

                    mReconnection = true;
                    bleConnect();
                }

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
            	List<BluetoothGattService> gattServices = mBluetoothLeService.getSupportedGattServices();
            	mNotifyCharacteristic = gattServices.get(2).getCharacteristic(SERVICE3_NOTIFICATION_CHAR_UUID);
            	mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, true);

                /**** IMPORTANT added by Larry ****/
                // 0x2902 org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
                UUID uuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
                BluetoothGattDescriptor descriptor = mNotifyCharacteristic.getDescriptor(uuid);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mBluetoothLeService.writeDescriptor(descriptor);
                /****/
                
                mWriteCharacteristic = gattServices.get(2).getCharacteristic(SERVICE3_WRITE_CHAR_UUID);
                Log.d(TAG, "BLE ACTION_GATT_SERVICES_DISCOVERED");

            } else if (BluetoothLeService.ACTION_DATA_WRITE_SUCCESS.equals(action)) {
                ((BluetoothListener) activity).bleWriteCharacteristic1Success();

            } else if (BluetoothLeService.ACTION_DATA_WRITE_FAIL.equals(action)) {
                ((BluetoothListener) activity).bleWriteStateFail();

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
            	byte[] data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);

                // DEBUG
//            	StringBuffer stringBuffer = new StringBuffer("");
//            	for(int ii=0;ii<data.length;ii++){
//                    String s1 = String.format("%2s", Integer.toHexString(data[ii] & 0xFF)).replace(' ', '0');
//                    if( ii != data.length-1)
//                        stringBuffer.append(s1 + ":");
//                    else
//                        stringBuffer.append(s1);
//            	}
//            	Log.i(TAG, stringBuffer.toString());

                switch (mAppStateTypeDef){
                    case APP_IMAGE_RECEIVING:
                        if(data[0] == BLE_REPLY_IMAGE_PACKET){
                            if (mBluetoothDataParserService != null){
                                mBluetoothDataParserService.parseDataPacket(data);
                            }
                        }
                        break;
                    case APP_IMAGE_GET_HEADER:
                        terminatePacketParser();
                        if(data[0] == BLE_REPLY_IMAGE_INFO) {
                            Intent intentToParserService = new Intent(activity.getBaseContext(), BluetoothDataParserService.class);
                            intentToParserService.putExtra(BluetoothDataParserService.EXTRA_HEADER_DATA, data);
                            activity.startService(intentToParserService);
                        }
                        break;
                    case APP_IMAGE_RECEIVED:
                    case APP_FETCH_INFO:
                    default:
                    {
                        switch(data[0]) { // Handling notification depending on types
                            case BLE_REPLY_DEVICE_ID:
                                int cassetteId = ((data[1] & 0xFF) << 8) + (data[2] & 0xFF);
                                int battVolt = ((data[3] & 0xFF) << 8) + (data[4] & 0xFF);

                                if(cassetteId == 0xFFFF){
                                    ((BluetoothListener) activity).bleNoPlugDetected();
                                }
                                else{
                                    ((BluetoothListener) activity).blePlugInserted(cassetteId);
                                }
                                ((BluetoothListener) activity).bleUpdateBattLevel(battVolt);
                                break;
                            case BLE_REPLY_SALIVA_VOLTAGE:
                                int value = ((data[1] & 0xFF) << 8) + (data[2] & 0xFF);
                                ((BluetoothListener) activity).bleUpdateSalivaVolt(value);
                                break;
                        }
                        break;
                    }
                }
            }
            else if (BluetoothDataParserService.ACTION_IMAGE_RECEIVED_SUCCESS.equals(action)){
                mAppStateTypeDef = AppStateTypeDef.APP_IMAGE_RECEIVED;
                ((MainActivity) activity).updateTextViewInfo(String.format("%.1f", 100.0) + "%");
                terminatePacketParser();

                byte[] data = intent.getByteArrayExtra(BluetoothDataParserService.EXTRA_IMAGE_DATA);
                new updateUIAsyncTask().execute(data);
            }
            else if (BluetoothDataParserService.ACTION_IMAGE_HEADER_CHECKED.equals(action)){
                mAppStateTypeDef = AppStateTypeDef.APP_IMAGE_RECEIVING;
                Intent _Intent = new Intent(activity, BluetoothDataParserService.class);
                activity.bindService(_Intent, mDataParserServiceConnection, Context.BIND_AUTO_CREATE);

                byte [] command = new byte[] {BluetoothLE.BLE_REQUEST_IMAGE_BY_INDEX, (byte)0x00};
                bleWriteCharacteristic1(command);
                Log.d(TAG, "Received data header information.");
            }
            else if (BluetoothDataParserService.ACTION_UPDATA_TRANSFER_PROGRESS.equals(action)){
                float progress = intent.getFloatExtra(BluetoothDataParserService.EXTRA_TRANSFER_INFO_DATA, -1);
                ((MainActivity) activity).updateTextViewInfo(String.format("%.1f", 100*progress) + "%" );
            }
            else if (BluetoothDataParserService.ACTION_ACK_LOST_PACKETS.equals(action)){
                byte[] command = intent.getByteArrayExtra(BluetoothDataParserService.EXTRA_TRANSFER_INFO_DATA);
                bleWriteCharacteristic1(command);
            }
            else if (BluetoothDataParserService.ACTION_IMAGE_RECEIVED_FAILED.equals(action)){
                terminatePacketParser();
                float dropoutRate = intent.getFloatExtra(BluetoothDataParserService.EXTRA_TRANSFER_INFO_DATA, -1);
                // Print dropout rate
                ((MainActivity) activity).updateTextViewInfo("Dropout:" + String.format("%.1f", 100*(1-dropoutRate)) + "%" );
                mAppStateTypeDef = AppStateTypeDef.APP_FETCH_INFO;
                Log.d(TAG, "Can not retrieve data.");
            }
            else{
                Log.d(TAG, "----BLE Can't handle data----");
            }

        }
    };

    private void unbindBLEService() {
        activity.unbindService(mBluetoothLeServiceConnection);
        activity.unregisterReceiver(mGattUpdateReceiver);
        mDeviceScanned = false;
    }

	public void bleConnect() {
		
		// Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        else {
            if(mScanning){
//                Log.d(TAG, "Stop previous handler");
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                mHandler.removeCallbacks(mRunnable);
            }
            bleScan();
        }
	}

    public void terminatePacketParser() {
        if(mBluetoothDataParserService != null){
            Log.d(TAG, "Stopping BluetoothDataParserService.");
            activity.unbindService(mDataParserServiceConnection);
            mBluetoothDataParserService = null;
            activity.stopService(new Intent(activity.getBaseContext(), BluetoothDataParserService.class));
        }
    }

    public void bleDisconnect() {
        if(mBluetoothLeService != null) {
            mBluetoothLeService.disconnect();
        }
        terminatePacketParser();
        if(!mConnected) {
            //Terminate the BLE connection timeout (10sec)
            mHandler.removeCallbacks(mRunnable);
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    public void bleWriteCharacteristic1(byte [] data) {

        if((mBluetoothLeService != null) && (mWriteCharacteristic != null)) {
            mWriteCharacteristic.setValue(data);
            mBluetoothLeService.writeCharacteristic(mWriteCharacteristic);
        }
        return;
    }

    /* Timeout implementation */
    private void bleScan() {
        mHandler.postDelayed(mRunnable = new Runnable() {

            @Override
            public void run() {
                mScanning = false;
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                if(!mReconnection){
                    ((BluetoothListener) activity).bleConnectionTimeout();
                }
                else{
                    mReconnection = false;
                    ((BluetoothListener) activity).bleDisconnected();
                    Log.d(TAG, "Still cannot reconnect.");
                }
//                    Log.i("BLE", "thread run");
            }
        }, SCAN_PERIOD);
        mScanning = true;

        mBluetoothAdapter.startLeScan(mLeScanCallback);
    }
	
	private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        /* BluetoothLeService */
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_WRITE_SUCCESS);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_WRITE_FAIL);

        /* BluetoothLeService */
        intentFilter.addAction(BluetoothDataParserService.ACTION_IMAGE_RECEIVED_SUCCESS);
        intentFilter.addAction(BluetoothDataParserService.ACTION_IMAGE_HEADER_CHECKED);
        intentFilter.addAction(BluetoothDataParserService.ACTION_ACK_LOST_PACKETS);
        intentFilter.addAction(BluetoothDataParserService.ACTION_IMAGE_RECEIVED_FAILED);
        intentFilter.addAction(BluetoothDataParserService.ACTION_UPDATA_TRANSFER_PROGRESS);

        return intentFilter;
    }

	public void onBLEActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
            case REQUEST_ENABLE_BT:{
        	    // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, enable BLE scan
                    bleScan();
                } else{
                    // User did not enable Bluetooth or an error occurred
                    Toast.makeText(activity, "Bluetooth did not enable!", Toast.LENGTH_SHORT).show();
//                activity.finish();
                }
        	    break;
            }
		}
	}

    public void setManualDisconnectFlag(boolean flags){
        mManualDisconnect = flags;
    }

    public boolean isScanning(){return mScanning;}

    public void setDeviceName(String name){mDeviceName = name;}

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    // Do nothing if target device is scanned
                    if(mDeviceScanned)
                        return;

                    String scannedName = device.getName();
                    if(device.getName() == null){
                        BLEAdvertisedData badData = new BLEUtil().parseAdvertisedData(scanRecord);
                        scannedName = badData.getName();
                    }

                    Log.d(TAG, "Device = " + scannedName + ", Address = " + device.getAddress());

                    if(mDeviceName.equals(scannedName)){
                        mDeviceAddress = device.getAddress();
                        mDeviceScanned = true;

                        Intent gattServiceIntent = new Intent(activity, BluetoothLeService.class);

                        activity.bindService(gattServiceIntent, mBluetoothLeServiceConnection, Context.BIND_AUTO_CREATE);
                        activity.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    }
                }
            };

    final public class BLEUtil {

        public BLEAdvertisedData parseAdvertisedData(byte[] advertisedData) {
            List<UUID> Uuids = new ArrayList<UUID>();
            String name = null;
            if( advertisedData == null ){
                return new BLEAdvertisedData(Uuids, name);
            }

            ByteBuffer buffer = ByteBuffer.wrap(advertisedData).order(ByteOrder.LITTLE_ENDIAN);
            while (buffer.remaining() > 2) {
                byte length = buffer.get();
                if (length == 0)
                    break;

                byte type = buffer.get();
                switch (type) {
                    case 0x02: // Partial list of 16-bit UUIDs
                    case 0x03: // Complete list of 16-bit UUIDs
                        while (length >= 2) {
                            Uuids.add(UUID.fromString(String.format(
                                    "%08x-0000-1000-8000-00805f9b34fb", buffer.getShort())));
                            length -= 2;
                        }
                        break;
                    case 0x06: // Partial list of 128-bit UUIDs
                    case 0x07: // Complete list of 128-bit UUIDs
                        while (length >= 16) {
                            long lsb = buffer.getLong();
                            long msb = buffer.getLong();
                            Uuids.add(new UUID(msb, lsb));
                            length -= 16;
                        }
                        break;
                    case 0x09:
                        byte[] nameBytes = new byte[length-1];
                        buffer.get(nameBytes);
                        try {
                            name = new String(nameBytes, "utf-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        break;
                    default:
                        buffer.position(buffer.position() + length - 1);
                        break;
                }
            }
            return new BLEAdvertisedData(Uuids, name);
        }
    }

    public class BLEAdvertisedData {
        private List<UUID> mUuids;
        private String mName;
        public BLEAdvertisedData(List<UUID> uuids, String name){
            mUuids = uuids;
            mName = name;
        }

        public List<UUID> getUuids(){
            return mUuids;
        }
        public String getName(){
            return mName;
        }
    }

    public class updateUIAsyncTask extends AsyncTask<byte[], Void, byte[]>{

        @Override
        protected byte[] doInBackground(byte[]... bytes) {
            ImageDetection imageDetection = new ImageDetection(activity, bytes[0]);
            imageDetection.detectImageResult();
            return bytes[0];
        }

        @Override
        protected void onPostExecute(byte[] result)
        {
            super.onPostExecute(result);
            //  Process returned byte[] from doInBackground into Bitmap
            Bitmap bitmap = BitmapFactory.decodeByteArray(result, 0, result.length);
            // Refresh ImageView
            if(bitmap == null){
                Log.d(TAG, "Image corrupted during dat transmission!");
            }
            else{
                Log.d(TAG, "Received image successfully.");
                ((MainActivity) activity).updateImageViewPreview(bitmap);
            }
        }
    }
}

