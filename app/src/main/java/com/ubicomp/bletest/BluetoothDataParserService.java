package com.ubicomp.bletest;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by larry on 1/13/16.
 */
public class BluetoothDataParserService extends Service {

    private final static String TAG = "BluetoothLE";
    private static final int TIMEOUT_PERIOD = 300;
    private static final int MAX_TIMEOUT_TIME = 3;
    private static final int MAX_RETRANSMIT_TIME = 5;
    private int timerCounter = 0;
    private int retransmitTime = 0;

    /* Actions messages */
    public final static String ACTION_IMAGE_HEADER_CHECKED =
            "com.ubicomp.bletest.dataparser.ACTION_IMAGE_HEADER_CHECKED";
    public final static String ACTION_ACK_LOST_PACKETS =
            "com.ubicomp.bletest.dataparser.ACTION_ACK_LOST_PACKETS";
    public final static String ACTION_IMAGE_RECEIVED_SUCCESS =
            "com.ubicomp.bletest.dataparser.ACTION_IMAGE_RECEIVED_SUCCESS";
    public final static String ACTION_IMAGE_RECEIVED_FAILED =
            "com.ubicomp.bletest.dataparser.ACTION_IMAGE_RECEIVED_FAILED";
    public final static String ACTION_UPDATA_TRANSFER_PROGRESS =
            "com.ubicomp.bletest.dataparser.ACTION_UPDATA_TRANSFER_PROGRESS";
    public final static String EXTRA_HEADER_DATA =
            "com.ubicomp.bletest.dataparser.EXTRA_DATA";
    public final static String EXTRA_IMAGE_DATA =
            "com.ubicomp.bletest.dataparser.EXTRA_IMAGE_DATA";
    public final static String EXTRA_TRANSFER_INFO_DATA =
            "com.ubicomp.bletest.dataparser.EXTRA_TRANSFER_INFO_DATA";

    /* Constants for data transmission. */
    private final static int PACKET_SIZE = 111;
    private final static int MAXIMUM_PACKET_NUM = 500;
    private final static int BLE_PACKET_SIZE = 20;

    /* Variables for handling transfer protocol. */
    private int recvNum = 0;
    private int lastRecvNum = recvNum;
    private int packetNum = 0;
    private int lastPacketSize = 0;
    private byte [][] dataBuf = null;
    private byte [] tempBuf = null;
    private int currentPacketId = 0;
    private boolean isLastPackets = false;
    private int targetPacketSize = 0;
    private int bufOffset = 0;
    private boolean isVerifySeqNum = false;
    private Set<Integer> recvPacketIdTable;

    /* Service variables */
    private Handler mHandler = new Handler();
    private final IBinder mBinder = new LocalBinder();
    private boolean isPaused = false;

    /* Performance evaluation */
    private int totalRetransPkts = 0;

    public void parseDataPacket(byte [] data){

        if (bufOffset == 0){
            int seqNum = ((data[1] & 0xFF) << 8) + (data[2] & 0xFF);
            if(seqNum >= packetNum) {
                isVerifySeqNum = false;
                Log.d(TAG, "Drop this ble packet.");
                return;
            }
            else{
                isVerifySeqNum = true;
            }

            currentPacketId = seqNum;
            if(currentPacketId == packetNum-1) {
                isLastPackets = true;
            }
            else {
                isLastPackets = false;
            }

            if( !isLastPackets ){
                targetPacketSize = PACKET_SIZE;
            }
            else{
                targetPacketSize = lastPacketSize;
            }

            if(targetPacketSize > BLE_PACKET_SIZE-4){
                System.arraycopy(data, 3, tempBuf, 0, data.length-3);
                bufOffset += (data.length-3);
            }
            else{
                Log.d(TAG, "Debug:" + String.valueOf(targetPacketSize) + " " + String.valueOf(isLastPackets));
                System.arraycopy(data, 3, tempBuf, 0, data.length - 4);
                bufOffset += (data.length-4);
                int check = data[data.length-1] & 0xFF;
                verifyPacketChecksum(check);

                if (recvNum == packetNum)
                    checkDataBuf();

                bufOffset = 0;
            }
        }
        else if(isVerifySeqNum == true){
            if (targetPacketSize - bufOffset <= BLE_PACKET_SIZE - 2){       // Modified by Larry on 2/15
                System.arraycopy(data, 1, tempBuf, bufOffset, data.length-2);
                bufOffset += (data.length-2);
                int check = data[data.length-1] & 0xFF;
                verifyPacketChecksum(check);

                if (recvNum == packetNum || isLastPackets == true)
                    checkDataBuf();

                bufOffset = 0;
            }
            else {
                System.arraycopy(data, 1, tempBuf, bufOffset, data.length-1);
                bufOffset += (data.length-1);
            }
        }
        else{
            // Bypass those corrupted data
            bufOffset = 0;
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

            mHandler.postDelayed(checkTimeoutRunnable, TIMEOUT_PERIOD);
        }
        Log.i(TAG, "BluetoothDataParserService starts.");
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(checkTimeoutRunnable);
        Log.d(TAG, "BluetoothDataParserService has been terminated.");
        super.onDestroy();
    }

    public void setCheckTimeoutPause(boolean option){
        isPaused = option;
    }

    private Runnable checkTimeoutRunnable = new Runnable() {
        public void run() {
            if(!isPaused) {
                timerCounter++;
                if(lastRecvNum != recvNum) {
                    timerCounter = 0;
                    retransmitTime = 0;
                }

                // Current time log
                Log.d(TAG, "Time: " + new Date().toString() + ", Counter: " + String.valueOf(timerCounter));

                if(timerCounter >= MAX_TIMEOUT_TIME){
                    retransmitTime++;
                    if(retransmitTime >= MAX_RETRANSMIT_TIME){
                        final Intent _intent = new Intent();
                        _intent.setAction(ACTION_IMAGE_RECEIVED_FAILED);
                        _intent.putExtra(EXTRA_TRANSFER_INFO_DATA, (float) recvNum / packetNum);
                        sendBroadcast(_intent);
                        return;
                    }
                    else{
                        if(recvNum == 0){
                            final Intent _intent = new Intent();
                            _intent.setAction(ACTION_IMAGE_HEADER_CHECKED);
                            sendBroadcast(_intent);
                        }
                        else if(recvNum < packetNum){
//                            Log.d(TAG, "Time: " + new Date().toString());
                            byte [] retransmitData = getRetransmitIndices();
                            final Intent _intent = new Intent();
                            _intent.setAction(ACTION_ACK_LOST_PACKETS);
                            _intent.putExtra(EXTRA_TRANSFER_INFO_DATA, retransmitData);
                            sendBroadcast(_intent);

//                            bufOffset = 0;
                        }
                    }
                    timerCounter = 0;
                }
                else{
                    final Intent _intent = new Intent();
                    _intent.setAction(ACTION_UPDATA_TRANSFER_PROGRESS);
                    _intent.putExtra(EXTRA_TRANSFER_INFO_DATA, (float) recvNum/packetNum);
                    sendBroadcast(_intent);
                }
            }

            mHandler.postDelayed(this, TIMEOUT_PERIOD);
            lastRecvNum = recvNum;
        }
    };

    private void verifyPacketChecksum(int check){
        int checksum = 0;

        for(int i = 0; i < bufOffset; i++){
            checksum += (tempBuf[i] & 0xFF);
            checksum = checksum & 0xFF;
        }
//                Log.i(TAG, "Packet length : " + String.valueOf(bufOffset));
//                Log.i(TAG, "Checksum : " + String.valueOf(checksum) + ", Check :" + String.valueOf(check));
        if (checksum == check){
//            dataBuf[currentPacketId] = new byte[bufOffset];
//            System.arraycopy(tempBuf, 0, dataBuf[currentPacketId], 0, bufOffset);
            if(recvPacketIdTable.contains(currentPacketId)){
                Log.d(TAG, "Already received packet index: " + String.valueOf(currentPacketId) + "/ " + String.valueOf(packetNum - 1));
            }
            else{
                dataBuf[currentPacketId] = new byte[bufOffset];
                System.arraycopy(tempBuf, 0, dataBuf[currentPacketId], 0, bufOffset);
                recvPacketIdTable.add(currentPacketId);
                Log.d(TAG, "Receive packet index: " + String.valueOf(currentPacketId) + "/ " + String.valueOf(packetNum - 1));
                recvNum++;
            }
        }
        else{
            Log.d(TAG, "Checksum error on ble packets ".concat(String.valueOf(currentPacketId)));
            Log.i(TAG, "Checksum : " + String.format("%2s", Integer.toHexString(checksum).replace(' ', '0')) +
                    ", Check :" +String.format("%2s", Integer.toHexString(check).replace(' ', '0')));

//            StringBuffer stringBuffer = new StringBuffer("");
//            for(int ii=0; ii<tempBuf.length;ii++){
//                String s1 = String.valueOf(tempBuf[ii] & 0xFF);
//                if( ii != tempBuf.length-1)
//                    stringBuffer.append(s1 + ",");
//                else
//                    stringBuffer.append(s1);
//            }
//            Log.i(TAG, stringBuffer.toString());
        }
    }

    private void checkDataBuf() {
        Log.d(TAG, "Dropout rate: " + (float)(packetNum-recvNum)*100/packetNum + "%");
        if(recvNum >= packetNum){

            int currentIdx = 0;
            byte [] pictureBytes;


            if(lastPacketSize > 0)
                pictureBytes = new byte [(packetNum-1)*PACKET_SIZE+lastPacketSize];
            else
                pictureBytes = new byte [packetNum*PACKET_SIZE];

            int i = 0;
            try{
                for(i = 0; i < packetNum; i++) {
                    System.arraycopy(dataBuf[i], 0, pictureBytes, currentIdx, dataBuf[i].length);
                    currentIdx += dataBuf[i].length;
                }
            }
            catch (Exception e){
                Log.d(TAG, String.valueOf(i) + ", " + String.valueOf(dataBuf[i].length) + ", " + String.valueOf(pictureBytes.length));
                return;
            }

            // Passing constructed jpeg files back to BluetoothLE
            final Intent _intent = new Intent();
            _intent.setAction(ACTION_IMAGE_RECEIVED_SUCCESS);
            _intent.putExtra(EXTRA_IMAGE_DATA, pictureBytes);
            sendBroadcast(_intent);
            mHandler.removeCallbacks(checkTimeoutRunnable);
            this.stopSelf();     // Work only the bound activity is not at foreground
        }
        else{
            byte [] retransmitData = getRetransmitIndices();

            final Intent _intent = new Intent();
            _intent.setAction(ACTION_ACK_LOST_PACKETS);
            _intent.putExtra(EXTRA_TRANSFER_INFO_DATA, retransmitData);
            sendBroadcast(_intent);
            bufOffset = 0;
            timerCounter = 0;
        }
    }

    public byte[] getRetransmitIndices(){
        int remainPacketNum = packetNum - recvNum;
        if(remainPacketNum > 18)
            remainPacketNum = 18;

        totalRetransPkts += remainPacketNum;
        Log.d(TAG, "Request " + remainPacketNum + " packets.");

        byte [] bytes = new byte [remainPacketNum+2];
        bytes[0] = BluetoothLE.BLE_REQUEST_IMAGE_BY_INDEX;
        bytes[1] = (byte)(remainPacketNum & 0xFF);

        int i = 0;
        for(int j = 0; j < packetNum; j++){
            if(!recvPacketIdTable.contains(j)){
                if(i >= remainPacketNum)
                    break;
                bytes[i+2] = (byte)(j & 0xFF);
                i++;
            }
        }


        StringBuffer stringBuffer = new StringBuffer("");
        for(int ii = 2; ii < bytes.length; ii++){
            String s1 = String.format("%s", Integer.toString(bytes[ii] & 0xFF));
            if( ii != bytes.length-1)
                stringBuffer.append(s1 + ", ");
            else
                stringBuffer.append(s1);
        }
        Log.d(TAG, "Indices: " + stringBuffer.toString());
        return bytes;
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
