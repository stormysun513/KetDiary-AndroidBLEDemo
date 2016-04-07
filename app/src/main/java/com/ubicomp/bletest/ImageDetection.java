package com.ubicomp.bletest;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Environment;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by larry on 2/29/16.
 */
public class ImageDetection {
    private static final String TAG = "ImageDetection";

    private static final int SEARCH_REGION_X_MIN = 165;
    private static final int SEARCH_REGION_X_MAX = 220;
    private static final int SEARCH_REGION_Y_MIN = 60;
    private static final int SEARCH_REGION_Y_MAX = 200;

    private static final int DEFAULT_X_MIN = 175;
    private static final int DEFAULT_X_MAX = 205;   //30
    private static final int DEFAULT_Y_MIN = 70;
    private static final int DEFAULT_Y_MAX = 197;   //127

    private static final int ROI_LENGTH = 30;
    private static final int ROI_WIDTH = 127;

    private static final int WHITE_THRESHOLD = 220;

    private int xmin = DEFAULT_X_MIN;
    private int xmax = DEFAULT_X_MAX;
    private int ymin = DEFAULT_Y_MIN;
    private int ymax = DEFAULT_Y_MAX;

    private static double COMPACT_RATIO = 0.25;

    private static int CANNY_THRES1 = 10;
    private static int CANNY_THRES2 = 120;

    Activity activity = null;
    byte[] data = null;
    modified_svm_scale svm_scale = null;
    modified_svm_predict svm_predict = null;

    File model_directory = null;
    File result_directory = null;
    Long timestamp = null;

    private static final String model_directory_name = "DetectionParameters";
    private static final String result_directory_name = "DetectionResult";

    private static final String svm_model_name = "model.out";
    private static final String scale_param_name = "scale_param.out";
    private static final String debug_feature_name = "input_raw_debug.libsvm";
    private static final String predict_out_name = "predict.out";

    public ImageDetection(Activity activity, byte[] data) {

        this.activity = activity;
        this.data = data;

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            model_directory = new File(Environment.getExternalStorageDirectory(), model_directory_name);
        else
            model_directory = new File(activity.getApplicationContext().getFilesDir(), model_directory_name);

        if (!model_directory.exists())
            model_directory.mkdirs();

        File mainStorage;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            mainStorage = new File(Environment.getExternalStorageDirectory(), result_directory_name);
        else
            mainStorage = new File(activity.getApplicationContext().getFilesDir(), result_directory_name);

        if (!mainStorage.exists())
            mainStorage.mkdirs();

        timestamp = System.currentTimeMillis()/1000;
        result_directory = new File(mainStorage, timestamp.toString());

        if (!result_directory.exists())
            result_directory.mkdirs();

        svm_scale = new modified_svm_scale();
        svm_predict = new modified_svm_predict();
    }

    public void setDataBuffer(byte[] data){
        this.data = data;
    }

    public boolean checkSVMModel() {
        boolean result = false;

        File check = new File(model_directory, scale_param_name);
        if (!check.exists()){
            // TODO : Syncronize model files from the server
        }
        else {
            Date dateLastModified = new Date(check.lastModified());
            Date dateOnServer = null;
            // TODO : Compare the date, if the local model is not latest, download the new one.
        }

        return result;
    }

    public double detectImageResult() throws IOException{

        double result = Double.MIN_VALUE;
        if(this.data == null)
            return result;

        String name = "PIC_".concat(String.valueOf(timestamp.toString())).concat("_0.jpg");
        File file_save_path = new File(result_directory, name);
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(file_save_path, true);
            fos.write(data);
            fos.close();
        } catch (IOException e) {
            Log.d(TAG, "FAIL TO WRITE FILE : " + file_save_path.getAbsolutePath());
        }


        Bitmap bitmap_orig = BitmapFactory.decodeByteArray(data, 0, data.length);
        Mat mat_cropped = getROIRegionMat(bitmap_orig);
        File scale_param_path = new File(model_directory, scale_param_name);
        String image_svm_feat = imageToSvmFeat(mat_cropped);          // Close for DEBUG

        // DEBUG : fake data used for svm input to make sure the match of feature dimension
//        File debug_feature_path = new File(model_directory, debug_feature_name);
//        BufferedReader fp = new BufferedReader(new FileReader(debug_feature_path));
//        String line = fp.readLine();
//		fp.close();
//        String output = svm_scale.svm_scale(scale_param_path.toString(), line);
        // End of DEBUG

        // SVM detection
        String output = svm_scale.svm_scale(scale_param_path.toString(), image_svm_feat); // Close for DEBUG
        File predict_out_path = new File(result_directory, predict_out_name);
        File svm_model_path = new File(model_directory, svm_model_name);
        DataOutputStream data_out_stream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(predict_out_path)));

        if(!svm_predict.isModelLoaded())
            svm_predict.loadModel(svm_model_path.toString());

        result = svm_predict.predict(output, data_out_stream, 1);
        data_out_stream.close();

        return result;
    }

    private Mat getROIRegionMat(Bitmap bitmap){
        int xSum = 0;
        int ySum = 0;
        int count = 0;

        for (int i = SEARCH_REGION_Y_MIN; i < SEARCH_REGION_Y_MAX; i++) {
            for (int j = SEARCH_REGION_X_MIN; j < SEARCH_REGION_X_MAX; j++) {
                int pixel = bitmap.getPixel(j, i);
                int greenValue = Color.green(pixel);
                if (greenValue > WHITE_THRESHOLD) {
                    xSum += j*greenValue;
                    ySum += i*greenValue;
                    count += greenValue;
                }
            }
        }

        if(count != 0){
            int xCenter = xSum / count;
            int yCenter = ySum / count;

            xmin = xCenter - (ROI_LENGTH/2);
            xmax = xCenter + (ROI_LENGTH/2);
            ymin = yCenter - (ROI_WIDTH/2);
            ymax = yCenter + (ROI_WIDTH/2);
        }

        if(xmin < 0 || ymin < 0 || xmax >= 320 || ymax >= 240){
            xmin = DEFAULT_X_MIN;
            xmax = DEFAULT_X_MAX;
            ymin = DEFAULT_Y_MIN;
            ymax = DEFAULT_Y_MAX;
        }

        Log.i(TAG, "xmin: " + xmin + ", xmax: " + xmax + ", ymin: " + ymin + ", ymax: " + ymax);

        Mat mat_orig = new Mat();
        Utils.bitmapToMat(bitmap, mat_orig);
        Rect rect_roi = new Rect(xmin, ymin, (xmax-xmin), (ymax-ymin));
        Mat mat_cropped = new Mat(mat_orig, rect_roi);

        Point p1 = new Point(xmin, ymin);
        Point p2 = new Point(xmin, ymax);
        Point p3 = new Point(xmax, ymin);
        Point p4 = new Point(xmax, ymax);

        Imgproc.line(mat_orig, p1, p2, new Scalar(0, 0, 0), 3);
        Imgproc.line(mat_orig, p2, p4, new Scalar(0, 0, 0), 3);
        Imgproc.line(mat_orig, p4, p3, new Scalar(0, 0, 0), 3);
        Imgproc.line(mat_orig, p3, p1, new Scalar(0, 0, 0), 3);

        String name = "PIC_".concat(String.valueOf(timestamp.toString())).concat("_1.jpg");
        File file = new File(result_directory, name);
        try {
            Imgcodecs.imwrite(file.toString(), mat_orig);
        }
        catch(CvException e){
            Log.d(TAG, "Fail writing image to external storage");
        }
        mat_orig.release();

        Mat mat_rotated = new Mat();
        Core.flip(mat_cropped.t(), mat_rotated, 0);
        mat_cropped.release();

        name = "PIC_".concat(String.valueOf(timestamp.toString())).concat("_2.jpg");
        file = new File(result_directory, name);
        try {
            Imgcodecs.imwrite(file.toString(), mat_rotated);
        }
        catch(CvException e){
            Log.d(TAG, "Fail writing image to external storage");
        }

        return mat_rotated;
    }

    private String imageToSvmFeat(Mat mat_cropped){

        int beginCol = mat_cropped.cols()/15;
        int halfCol = mat_cropped.cols()/4 + 1;
        Rect rect_roi = new Rect(beginCol, 0, halfCol, mat_cropped.rows());
        Mat mat_test = new Mat(mat_cropped, rect_roi);
        mat_cropped.release();

        List<Mat> vmat_resized = new ArrayList<Mat>();
        Core.split(mat_test, vmat_resized); 			// B, G, R

        Mat mat_canny0 = new Mat();
        Mat mat_canny2 = new Mat();

        Imgproc.Canny(vmat_resized.get(0), mat_canny0, CANNY_THRES1, CANNY_THRES2);
        Imgproc.Canny(vmat_resized.get(2), mat_canny2, CANNY_THRES1, CANNY_THRES2);

        Mat mat_feat_0 = new Mat();
        Mat mat_feat_2 = new Mat();
        Imgproc.Sobel(mat_canny0, mat_feat_0, Imgcodecs.IMREAD_GRAYSCALE, 1, 0);
        Imgproc.Sobel(mat_canny2, mat_feat_2, Imgcodecs.IMREAD_GRAYSCALE, 1, 0);
        mat_canny0.release();
        mat_canny2.release();

        int count0 = 0, count2 = 0;

        byte [] bytes_feat_0 = new byte[mat_feat_0.cols()*mat_feat_0.rows()*mat_feat_0.channels()];
        mat_feat_0.get(0, 0, bytes_feat_0);
        mat_feat_0.release();
        for(byte b: bytes_feat_0){
            if(b != 0)
                count0++;
        }


        byte [] bytes_feat_2 = new byte[mat_feat_2.cols()*mat_feat_2.rows()*mat_feat_2.channels()];
        mat_feat_2.get(0, 0, bytes_feat_2);
        mat_feat_2.release();
        for(byte b:bytes_feat_2){
            if(b != 0)
                count2++;
        }

        String image_svm_feat = -1 + " 1:" + count0 + " " + "2:" + count2 ;
        return image_svm_feat;
    }
}
