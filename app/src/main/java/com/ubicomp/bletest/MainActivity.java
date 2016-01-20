package com.ubicomp.bletest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
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
    private Button buttonStart;
    private Button buttonClose;
    private Button buttonSendData;
    private Button buttonSnapshot;
    private Button buttonVoltage;
    private Button buttonDeviceInfo;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        editTextBlock = (EditText)findViewById(R.id.editTextBlock);
        textViewInfo = (TextView)findViewById(R.id.textViewInfo);
        imageViewPreview = (ImageView)findViewById(R.id.imageViewPreview);
        buttonStart = (Button)findViewById(R.id.buttonStart);
        buttonClose = (Button)findViewById(R.id.buttonClose);
        buttonSendData = (Button)findViewById(R.id.buttonSendData);
        buttonSnapshot = (Button)findViewById(R.id.buttonSnapshot);
        buttonVoltage = (Button)findViewById(R.id.buttonVoltage);
        buttonDeviceInfo = (Button)findViewById(R.id.buttonDeviceInfo);

        buttonStart.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if(ble != null)
                    return;
                String deviceName = editTextBlock.getText().toString();
                if(deviceName == "")
                    return;
                ble = new BluetoothLE(mainActivity, deviceName);
                ble.bleConnect();
            }

        });

        buttonClose.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (ble != null) {
                    ble.bleDisconnect();
                    ble = null;
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
                    Log.i(TAG, "Contain illegal characters.");
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
                ble.mAppState = BluetoothLE.AppState.APP_IMAGE_GET_HEADER;
                ble.bleWriteCharacteristic1(command);
            }
        });

        buttonVoltage.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                if(ble == null)
                    return;

                byte [] command = new byte[] {BluetoothLE.BLE_REQUEST_SALIVA_VOLTAGE};
                ble.mAppState = BluetoothLE.AppState.APP_FETCH_INFO;
                ble.bleWriteCharacteristic1(command);
            }
        });

        buttonDeviceInfo.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                if(ble == null)
                    return;

                byte [] command = new byte[] {BluetoothLE.BLE_REQUEST_DEVICE_ID};
                ble.mAppState = BluetoothLE.AppState.APP_FETCH_INFO;
                ble.bleWriteCharacteristic1(command);
            }
        });

        Log.i(TAG, "On create");
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
        if(ble != null) {
            ble = null;
        }
    }

    @Override
    public void bleConnected() {
    	Toast.makeText(this, "BLE connected", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "BLE connected");
        clearTextViewInfo();
        clearImageViewPreview();
    }

    @Override
    public void bleDisconnected() {
    	Toast.makeText(this, "BLE disconnected", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "BLE disconnected");
        if(ble != null) {
            ble = null;
        }
    }

    @Override
    public void bleWriteCharacteristic1Success() {
    	Toast.makeText(this, "BLE ACTION_DATA_WRITE_SUCCESS", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "BLE ACTION_DATA_WRITE_SUCCESS");
    }

    @Override
    public void bleWriteStateFail() {
    	Toast.makeText(this, "BLE ACTION_DATA_WRITE_FAIL", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "BLE ACTION_DATA_WRITE_FAIL");
    }

    @Override
    public void bleNoPlug() {
    	Toast.makeText(this, "No test plug", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "No test plug");
    }

    @Override
    public void blePlugInserted(byte[] plugId) {
    	//Toast.makeText(this, "Test plug is inserted", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Test plug is inserted");
    }


	@Override
	public void bleElectrodeAdcReading(byte state, byte[] adcReading) {
		// TODO Auto-generated method stub
		
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
}
