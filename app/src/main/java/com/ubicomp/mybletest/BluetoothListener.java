package com.ubicomp.mybletest;

import android.content.Context;
import android.graphics.Bitmap;

public interface BluetoothListener {

    Context getContext();

//	public Activity activity = null;
	
//    void onActivityResult(int requestCode, int resultCode, Intent data);

    /* BLE is not supported in this device */
    void bleNotSupported();

    /* BLE connection establishment timeout (10sec) */
    void bleConnectionTimeout();

    /* BLE connection established successfully */
    void bleConnected();

    /* BLE disconnected */
    void bleDisconnected();

    /* BLE write state success */
    void bleWriteCharacteristic1Success();

    /* BLE write state fail */
    void bleWriteStateFail();

    /* Can not detect test plug */
    void bleNoPlugDetected();

    /* Test plug is detected with its ID */
    void blePlugInserted(int cassetteId);

    /* Update battery level */
    void bleUpdateBattLevel(int battVolt);

    /* Notify device information */
    void notifyDeviceVersion(int version);

    /* Update saliva voltage */
    void bleUpdateSalivaVolt(int salivaVolt);

    /* Image is retrieved successfully */
    void bleGetImageSuccess(Bitmap bitmap);

    /* Failed to retrieve image */
    void bleGetImageFailure(float dropoutRate);

    /* Notify detection result */
    void bleNotifyDetectionResult(double score);

    void bleReturnDeviceVersion(int version);

}
