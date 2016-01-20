package com.ubicomp.bletest;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by larry on 1/13/16.
 */
public class BluetoothDataParserService extends Service {

    private final static String TAG = "BluetoothLE";
    private static final int TIMEOUT_LIMIT = 2000;

    public final static String ACTION_IMAGE_HEADER_CHECKED =
            "com.ubicomp.bletest.dataparser.ACTION_IMAGE_HEADER_CHECKED";
    public final static String ACTION_ACK_LOST_PACKETS =
            "com.ubicomp.bletest.dataparser.ACTION_ACK_LOST_PACKETS";
    public final static String ACTION_IMAGE_RECEIVED_SUCCESS =
            "com.ubicomp.bletest.dataparser.ACTION_IMAGE_RECEIVED_SUCCESS";
    public final static String ACTION_IMAGE_RECEIVED_FAILED =
            "com.ubicomp.bletest.dataparser.ACTION_IMAGE_RECEIVED_FAILED";
    public final static String EXTRA_HEADER_DATA =
            "com.ubicomp.bletest.dataparser.EXTRA_DATA";
    public final static String EXTRA_IMAGE_DATA =
            "com.ubicomp.bletest.dataparser.EXTRA_IMAGE_DATA";

    /* Constants for data transmission. */
    private final static int PACKET_SIZE = 111;
    private final static int MAXIMUM_PACKET_NUM = 500;
    private final static int BLE_PACKET_SIZE = 20;
    private final static String dirName = "TempPicDir";

    /* Variables for handling transfer protocol. */
    private int recvNum = 0;
    private int packetNum = 0;
    private int lastPacketSize = 0;
    private byte [][] dataBuf = null;
    private byte [] tempBuf = null;
    private int currentPacketId = 0;
    private boolean isLastPackets = false;
    private int targetPacketSize = 0;
    private int bufOffset = 0;
    private Set<Integer> recvPacketIdTable;

    /* Service variables */
    private Handler mHandler = new Handler();
    private final IBinder mBinder = new LocalBinder();
    private static int counter = 0;

    private static int totalRetransPkts = 0;

    public void parseDataPacket(byte [] data){

        if (bufOffset == 0){
            int seqNum = ((data[1] & 0xFF) << 8) + (data[2] & 0xFF);
            currentPacketId = seqNum;

            if(currentPacketId == packetNum-1){
                isLastPackets = true;
            }

            if( !isLastPackets ){
                targetPacketSize = PACKET_SIZE;
            }
            else {
                targetPacketSize = lastPacketSize;
            }

            System.arraycopy(data, 3, tempBuf, 0, data.length-3);
            bufOffset += (data.length-3);
        }
        else{
            if (targetPacketSize - bufOffset <= BLE_PACKET_SIZE){
                System.arraycopy(data, 1, tempBuf, bufOffset, data.length-2);
                bufOffset += (data.length-2);
                int check = data[data.length-1] & 0xFF;
                int checksum = 0;

                for(int i = 0; i < tempBuf.length; i++){
                    checksum += (tempBuf[i] & 0xFF);
                    checksum = checksum & 0xFF;
                }

//                Log.i(TAG, "Packet length : " + String.valueOf(bufOffset));
//                Log.i(TAG, "Checksum : " + String.valueOf(checksum) + ", Check :" + String.valueOf(check));
                if (checksum == check){
                    dataBuf[currentPacketId] = new byte[bufOffset];
                    System.arraycopy(tempBuf, 0, dataBuf[currentPacketId], 0, bufOffset);
                    recvPacketIdTable.add(currentPacketId);
                    recvNum++;
                    bufOffset = 0;
                    Log.i(TAG, "Receive packet index: " + String.valueOf(currentPacketId) + "/ " + String.valueOf(packetNum-1));
                }
                else{
                    bufOffset = 0;
                    Log.d(TAG, "Checksum error on ble packets ".concat(String.valueOf(currentPacketId)));
                }

                if (recvNum == packetNum || isLastPackets == true){
                    checkDataBuf();
                }

            }
            else {
                System.arraycopy(data, 1, tempBuf, bufOffset, data.length-1);
                bufOffset += (data.length-1);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        byte [] data = intent.getByteArrayExtra(BluetoothDataParserService.EXTRA_HEADER_DATA);

        if(data[0] == BluetoothLE.BLE_REPLY_IMAGE_INFO && data.length >= 3){
            int picTotalLen = ((data[1] & 0xFF) << 8) + (data[2] & 0xFF);
            packetNum = picTotalLen / PACKET_SIZE;
            if (picTotalLen % PACKET_SIZE != 0) {
                packetNum++;
                lastPacketSize = picTotalLen % PACKET_SIZE;
            }
            Log.d(TAG, "Total picture length:".concat(String.valueOf(picTotalLen)));
            Log.d(TAG, "Total packets:".concat(String.valueOf(packetNum)));
            Log.d(TAG, "Last packet size:".concat(String.valueOf(lastPacketSize)));

            dataBuf = new byte [MAXIMUM_PACKET_NUM][];
            tempBuf = new byte [PACKET_SIZE];
            recvPacketIdTable = new HashSet();

            final Intent _intent = new Intent();
            _intent.setAction(ACTION_IMAGE_HEADER_CHECKED);
            sendBroadcast(_intent);

//            mHandler.postDelayed(showTime, TIMEOUT_LIMIT);        Timeout timer
        }
        Log.i(TAG, "BluetoothDataParserService starts.");
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
//        mHandler.removeCallbacks(showTime);
        Log.i(TAG, "BluetoothDataParserService has been terminated.");
        super.onDestroy();
    }

    private Runnable showTime = new Runnable() {
        public void run() {
            //log目前時間
            Log.i(TAG, "time:" + new Date().toString());

            final Intent _intent = new Intent();
            _intent.setAction(ACTION_IMAGE_RECEIVED_SUCCESS);
            counter++;
            _intent.putExtra("counter", counter);
            sendBroadcast(_intent);

            mHandler.postDelayed(this, TIMEOUT_LIMIT);
            Log.i(TAG, "Timeout : " + String.valueOf(counter));
        }
    };

    private void checkDataBuf() {
        Log.i(TAG, "Dropout rate: " + (float)(packetNum-recvNum)*100/packetNum + "%");
        if(recvNum == packetNum){

            int currentIdx = 0;
            byte [] pictureBytes;
            if(lastPacketSize > 0)
                pictureBytes = new byte [(packetNum-1)*PACKET_SIZE+lastPacketSize];
            else
                pictureBytes = new byte [packetNum*PACKET_SIZE];

            for(int i = 0; i < packetNum; i++) {
                System.arraycopy(dataBuf[i], 0, pictureBytes, currentIdx, dataBuf[i].length);
//                fos.write(picBuf[i]);
                currentIdx += dataBuf[i].length;
            }


            // Passing constructed jpeg files back to BluetoothLE
            final Intent _intent = new Intent();
            _intent.setAction(ACTION_IMAGE_RECEIVED_SUCCESS);
            _intent.putExtra(EXTRA_IMAGE_DATA, pictureBytes);
            sendBroadcast(_intent);
            this.stopSelf();
        }
        else{
            int remainPktNum = packetNum - recvNum;
            if(remainPktNum > 18)
                remainPktNum = 18;

            Log.i(TAG, "Request " + remainPktNum + " packets.");
            byte [] bytes = new byte [20];
            bytes[0] = (byte)0xA3;
            bytes[1] = (byte)(remainPktNum & 0xFF);

            for(int i = 0; i < remainPktNum; i++){
                for(int j = 0; j < packetNum; j++){
                    if(!recvPacketIdTable.contains(j)){
                        bytes[i+2] = (byte)(j & 0xFF);
                        j++;
                        break;
                    }
                }
            }

            /* TODO */
            /* Have to use intent to ask for retransmission */
//            ble.bleWriteData(bytes);
            totalRetransPkts += remainPktNum;
            bufOffset = 0;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        BluetoothDataParserService getService() {

            return BluetoothDataParserService.this;
        }
    }
}
