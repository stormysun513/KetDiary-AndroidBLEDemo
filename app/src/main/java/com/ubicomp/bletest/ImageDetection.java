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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Created by larry on 2/29/16.
 */
public class ImageDetection {
    private static final String TAG = "ImageDetection";

    private static final int SEARCH_REGION_X_MIN = 110;
    private static final int SEARCH_REGION_X_MAX = 190;
    private static final int SEARCH_REGION_Y_MIN = 0;
    private static final int SEARCH_REGION_Y_MAX = 240;

    private static final int DEFAULT_X_MIN = 127;
    private static final int DEFAULT_X_MAX = 187;
    private static final int DEFAULT_Y_MIN = 25;
    private static final int DEFAULT_Y_MAX = 195;

    private static final int ROI_LENGTH = 60;
    private static final int ROI_WIDTH = 170;

    private static final int WHITE_THRESHOLD = 160;
    private static final int VALID_THRESHOLD = -15;
    private static final int MINIMAL_EFFECTIVE_RANGE = 20;

    private int xmin = DEFAULT_X_MIN;
    private int xmax = DEFAULT_X_MAX;
    private int ymin = DEFAULT_Y_MIN;
    private int ymax = DEFAULT_Y_MAX;

    private static double COMPACT_RATIO = 0.25;

    private static int CANNY_THRES1 = 10;
    private static int CANNY_THRES2 = 120;

    Activity activity = null;
    byte[] data = null;

    File model_directory = null;
    File result_directory = null;
    Long timestamp = null;

    private final static String model_directory_name = "DetectionParameters";
    private final static String result_directory_name = "DetectionResult";

    private final static String model_name = "model.out";
    private final static String scale_param_name = "scale_param.out";

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
    }

    public boolean checkSVMModel() {
        File check = new File(model_directory, scale_param_name);
        if (!check.exists()){
            // TODO: Syncronize model files from the server
        }
        else {
            Date lastModDate = new Date(check.lastModified());
        }

        return true;
    }

    public boolean detectImageResult() {

        if(this.data == null)
            return false;

        String name = "PIC_".concat(String.valueOf(timestamp.toString())).concat("_0.jpg");
        File file = new File(result_directory, name);
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(file, true);
            fos.write(data);
            fos.close();
        } catch (IOException e) {
            Log.d(TAG, "FAIL TO WRITE FILE : " + file.getAbsolutePath());
        }

        Bitmap bitmap_orig = BitmapFactory.decodeByteArray(data, 0, data.length);
        Mat mat_cropped = getROIRegionMat(bitmap_orig);

        String image_svm_feat = imageToSvmFeat(mat_cropped);
        return true;
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

        Mat mat_resized = new Mat();
        Size sz = new Size(mat_cropped.cols()*COMPACT_RATIO, mat_cropped.rows()*COMPACT_RATIO);
        Imgproc.resize(mat_cropped, mat_resized, sz);
        mat_cropped.release();

        Mat mat_canny = new Mat();
        Imgproc.Canny(mat_resized, mat_canny, CANNY_THRES1, CANNY_THRES2);

        int halfCol = mat_resized.cols()/2 + 1;
        Rect rect_roi = new Rect(0, 0, halfCol, mat_resized.rows());
        Mat mat_feat_1 = new Mat(mat_resized, rect_roi);
        Mat mat_feat_2 = new Mat(mat_canny, rect_roi);

        byte [] bytes_feat_1 = new byte[mat_feat_1.cols()*mat_feat_1.rows()*mat_feat_1.channels()];
        mat_feat_1.get(0, 0, bytes_feat_1);

        byte [] bytes_feat_2 = new byte[mat_feat_2.cols()*mat_feat_2.rows()];
        mat_feat_2.get(0, 0, bytes_feat_2);

        String image_svm_feat = String.valueOf(-1) + " ";

        int index = 1;
        for(int i = 0; i < bytes_feat_1.length; i++){
            int value = 0xFF & bytes_feat_1[i];
            image_svm_feat += (String.valueOf(index++) + ":" + String.valueOf(value) + " ");
        }

        for(int i = 0; i < bytes_feat_2.length; i++){
            int value = 0xFF & bytes_feat_2[i];
            image_svm_feat += (String.valueOf(index++) + ":" + String.valueOf(value) + " ");
        }

        return image_svm_feat;
    }

    public float getDetectionResults(){
        return -1f;
    }
}
