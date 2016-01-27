package com.ubicomp.bletest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity implements BluetoothListener {

    private static final String TAG = "BluetoothLE";

	private BluetoothLE ble = null;
    MainActivity mainActivity = this;               // Context of this activity could be inferred.
    private EditText editTextBlock;
    private TextView textViewInfo;
    private ImageView imageViewPreview;
    private Button buttonSwitch;
    private Button buttonSendData;
    private Button buttonSnapshot;
    private Button buttonGetSalivaVoltage;
    private Button buttonDeMonitor;
    private Button buttonNotification;
    private Button buttonGetId;
    private Button buttonWriteId;
    private Button buttonBattery;

    public enum DisplayTypeDef{
        DISPLAY_NONE,
        DISPLAY_VOLTAGE,
        DISPLAY_BATTERY,
        DISPLAY_DEVICE_ID
    }

    private int casecatteId = 0xFFFF;
    private float salivaVoltage = 0xFFFF;
    private float batteryLevel = 0xFFFF;

    public static DisplayTypeDef mDisplayTypeDef = DisplayTypeDef.DISPLAY_NONE;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        editTextBlock = (EditText)findViewById(R.id.editTextBlock);
        textViewInfo = (TextView)findViewById(R.id.textViewInfo);
        imageViewPreview = (ImageView)findViewById(R.id.imageViewPreview);
        buttonSwitch = (Button)findViewById(R.id.buttonSwitch);
        buttonSendData = (Button)findViewById(R.id.buttonSendData);
        buttonSnapshot = (Button)findViewById(R.id.buttonSnapshot);
        buttonGetSalivaVoltage = (Button)findViewById(R.id.buttonGetSalivaVoltage);
        buttonDeMonitor = (Button)findViewById(R.id.buttonDeMonitor);
        buttonNotification = (Button)findViewById(R.id.buttonNotification);
        buttonGetId = (Button)findViewById(R.id.buttonGetId);
        buttonWriteId = (Button)findViewById(R.id.buttonWriteId);
        buttonBattery = (Button)findViewById(R.id.buttonBattery);

        buttonSwitch.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (ble != null && buttonSwitch.getText().toString().equals(getString(R.string.button_close))){
                    ble.setManualDisconnectFlag(true);
                    ble.bleDisconnect();
                    ble = null;
                }
                else if(ble == null && buttonSwitch.getText().toString().equals(getString(R.string.button_start))){
                    String deviceName = editTextBlock.getText().toString();
                    if (deviceName.equals(""))
                        return;
                    ble = new BluetoothLE(mainActivity, deviceName);
                    ble.bleConnect();
                }
                else if(ble != null){
                    if(ble.isScanning()){
                        String deviceName = editTextBlock.getText().toString();
                        if (deviceName.equals(""))
                            return;
                        ble.setDeviceName(deviceName);
                        ble.bleConnect();
                    }
                }
            }
        });

        buttonSendData.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if(ble == null)
                    return;

                String inputStr = editTextBlock.getText().toString().replaceAll("\\s+", "").toLowerCase();
                String regPatternStr = "[^0-9a-f]";
                Pattern pattern = Pattern.compile(regPatternStr);
                Matcher matcher = pattern.matcher(inputStr);
                if (matcher.find()){
                    Log.d(TAG, "Contain illegal characters.");
                    return;
                }

                if(inputStr.length() % 2 != 0){
                    inputStr += "0";
                }
                int size = inputStr.length()/2;
                if(size > 20)
                    size = 20;
                byte [] data = new byte[size];

                for(int i = 0; i < inputStr.length()/2; i++){
                    char ch1 = inputStr.charAt(2*i);
                    char ch2 = inputStr.charAt(2*i+1);
                    int tmp1, tmp2;
                    if(ch1 > '9')
                        tmp1 = (int)ch1 - (int)'a' + 10;
                    else
                        tmp1 = (int)ch1 - (int)'0';
                    if(ch2 > '9')
                        tmp2 = (int)ch2 - (int)'a' + 10;
                    else
                        tmp2 = (int)ch2 - (int)'0';

                    byte inputByte = (byte)(((tmp1 & 0xFF) << 4) | tmp2 & 0xFF );
                    data[i] = inputByte;
                }
                ble.bleWriteCharacteristic1(data);
            }

        });

        buttonSnapshot.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                if(ble == null)
                    return;

                byte [] command = new byte[] {BluetoothLE.BLE_REQUEST_IMAGE_INFO};
                ble.mAppStateTypeDef = BluetoothLE.AppStateTypeDef.APP_IMAGE_GET_HEADER;
                ble.bleWriteCharacteristic1(command);
            }
        });

        buttonGetSalivaVoltage.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (ble == null)
                    return;

                mDisplayTypeDef = DisplayTypeDef.DISPLAY_VOLTAGE;
                if(salivaVoltage != 0xFFFF){
                    updateTextViewInfo("Voltage:"+String.valueOf(salivaVoltage));
                }

                byte[] command = new byte[]{BluetoothLE.BLE_REQUEST_SALIVA_VOLTAGE};
                ble.mAppStateTypeDef = BluetoothLE.AppStateTypeDef.APP_FETCH_INFO;
                ble.bleWriteCharacteristic1(command);
            }
        });

        buttonDeMonitor.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                if (ble == null)
                    return;

                mDisplayTypeDef = DisplayTypeDef.DISPLAY_NONE;
                updateTextViewInfo("");


                byte[] command = new byte[]{BluetoothLE.BLE_DEREQUEST_SALIVA_VOLTAGE};
                ble.mAppStateTypeDef = BluetoothLE.AppStateTypeDef.APP_FETCH_INFO;
                ble.bleWriteCharacteristic1(command);
            }
        });

        buttonNotification.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (ble == null)
                    return;

                byte[] command = new byte[]{BluetoothLE.BLE_REQUEST_DEVICE_ID};
                ble.mAppStateTypeDef = BluetoothLE.AppStateTypeDef.APP_FETCH_INFO;
                ble.bleWriteCharacteristic1(command);
            }
        });

        buttonGetId.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                mDisplayTypeDef = DisplayTypeDef.DISPLAY_DEVICE_ID;
                if(casecatteId != 0xFFFF){
                    updateTextViewInfo("ket_"+String.valueOf(casecatteId));
                }
            }
        });

        buttonWriteId.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                if (ble == null)
                    return;

                String inputStr = editTextBlock.getText().toString().replaceAll("\\s+", "").toLowerCase();
                String regPatternStr = "[^0-9]";
                Pattern pattern = Pattern.compile(regPatternStr);
                Matcher matcher = pattern.matcher(inputStr);
                if (matcher.find()) {
                    Log.d(TAG, "Contain illegal characters.");
                    return;
                }

                int sum = 0;
                for (int i = 0; i < inputStr.length(); i++) {
                    char ch = inputStr.charAt(i);
                    sum = sum * 10 + ((int) ch - (int) '0');
                }

                if (sum > 0xFFFF) {
                    Log.d(TAG, "Cannot surpass 65535");
                    return;
                }


                byte dataByte1 = (byte) ((sum >> 8) & 0xFF);
                byte dataByte2 = (byte) (sum & 0xFF);

                byte[] command = new byte[]{BluetoothLE.BLE_CHANGE_DEVICE_ID, dataByte1, dataByte2};
                ble.mAppStateTypeDef = BluetoothLE.AppStateTypeDef.APP_FETCH_INFO;
                ble.bleWriteCharacteristic1(command);
            }
        });

        buttonBattery.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                mDisplayTypeDef = DisplayTypeDef.DISPLAY_BATTERY;
                if(batteryLevel != 0xFFFF){
                    updateTextViewInfo("Temp:" + String.format("%.2f", batteryLevel));
                }
            }
        });

        Log.d(TAG, "On create");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {

        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0)
        {
            this.moveTaskToBack(true);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(ble != null) {
            ble.bleDisconnect();
        }
    }

    @Override
    public Context getContext() {
        return MainActivity.this;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ble.onBLEActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void bleNotSupported() {
        Toast.makeText(activity, "BLE not supported!", Toast.LENGTH_SHORT).show();
//        this.finish();
    }

    @Override
    public void bleConnectionTimeout() {
        Toast.makeText(this, "BLE connection timeout", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "BLE connection timeout");
        if(ble != null) {
            ble = null;
        }
    }

    @Override
    public void bleConnected() {
    	Toast.makeText(this, "BLE connected", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "BLE connected");
        editTextBlock.setText("");
        buttonSwitch.setText(getString(R.string.button_close));
        clearTextViewInfo();
        clearImageViewPreview();
    }

    @Override
    public void bleDisconnected() {
    	Toast.makeText(this, "BLE disconnected", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "BLE disconnected");
        buttonSwitch.setText(getString(R.string.button_start));
        if(ble != null) {
            ble = null;
        }
        casecatteId = 0xFFFF;
        salivaVoltage = 0xFFFF;
        batteryLevel = 0xFFFF;
    }

    @Override
    public void bleWriteCharacteristic1Success() {
    	Toast.makeText(this, "BLE ACTION_DATA_WRITE_SUCCESS", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "BLE ACTION_DATA_WRITE_SUCCESS");
    }

    @Override
    public void bleWriteStateFail() {
    	Toast.makeText(this, "BLE ACTION_DATA_WRITE_FAIL", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "BLE ACTION_DATA_WRITE_FAIL");
    }

    @Override
    public void bleNoPlug() {
    	Toast.makeText(this, "No test plug", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "No test plug");
    }

    @Override
    public void blePlugInserted(byte[] plugId) {
    	//Toast.makeText(this, "Test plug is inserted", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Test plug is inserted");
    }

    public void updateTextViewInfo(String string){
        textViewInfo.setText(string);
    }

    public void clearTextViewInfo(){
        textViewInfo.setText("");
    }

    public void updateImageViewPreview(Bitmap bitmap) {
        imageViewPreview.setImageBitmap(bitmap);
    }

    public void clearImageViewPreview(){imageViewPreview.setImageDrawable(null);}

    public void setCasecatteId(int id) { casecatteId = id;}

    public void setBatteryLevel(float voltage) {batteryLevel = voltage;}

    public void setSalivaVoltage(float voltage) {salivaVoltage = voltage;}
}
