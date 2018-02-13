/**
 * SCAVIActivity.java - gets the point cloud data and processes it
 * Compares two {@code int} values numerically.
 * Author: Santhanakrishnan Narayanan (n.santhanakrishnan@gmail.com)
 * Creation date: 14/02/2018
 */

package com.n_san.scavi;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.tango.support.TangoSupport;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.content.SharedPreferences;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import android.graphics.Color;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import java.util.Locale;



/**
 * Main activity class for the Depth Perception sample. Handles the connection to the {@link Tango}
 * service and propagation of Tango Point Cloud data to Layout view.
 */
public class SCAVIActivity extends Activity implements OnInitListener {

    private TextToSpeech tts;
    private static final String TAG = SCAVIActivity.class.getSimpleName();
    private TangoCameraIntrinsics mIntrinsics;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsConnected = false;
    private int firstTimeAudio = 0;
    double timerDifference, startTime = System.currentTimeMillis(), stopTime;
    private double cx, cy, fx, fy, distortion0, distortion1, distortion4;
    private int previousWarning = 0; //for storing the obstacle detection state of previous run
    SCAVIView SCAVIView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scavi);
        SCAVIView = new SCAVIView(this);
        SCAVIView.setBackgroundColor(Color.BLACK);
        tts = new TextToSpeech(this,this);
        setContentView(SCAVIView);

    }

    @Override
    protected void onResume() {
        super.onResume();

        // Initialize Tango Service as a normal Android Service. Since we call mTango.disconnect()
        // in onPause, this will unbind Tango Service, so every time onResume gets called we
        // should create a new Tango object.
        mTango = new Tango(SCAVIActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready; this Runnable
            // will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only when there are no
            // UI thread changes involved.
            @Override
            public void run() {
                synchronized (SCAVIActivity.this) {
                    try {
                        mConfig = setupTangoConfig(mTango);
                        mTango.connect(mConfig);
                        startupTango();
                        TangoSupport.initialize(mTango);
                        mIsConnected = true;
                        mIntrinsics = mTango.getCameraIntrinsics(TangoCameraIntrinsics.TANGO_CAMERA_DEPTH);
                        cx = mIntrinsics.cx;
                        cy = mIntrinsics.cy;
                        fx = mIntrinsics.fx;
                        fy = mIntrinsics.fy;
                        distortion0 = mIntrinsics.distortion[0];
                        distortion1 = mIntrinsics.distortion[1];
                        distortion4 = mIntrinsics.distortion[4];

                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_out_of_date), e);
                        showsToastAndFinishOnUiThread(R.string.exception_out_of_date);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                        showsToastAndFinishOnUiThread(R.string.exception_tango_error);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, getString(R.string.exception_tango_invalid), e);
                        showsToastAndFinishOnUiThread(R.string.exception_tango_invalid);
                    }
                }
            }
        });
    }
    public void onInit(int initStatus) {
        if (initStatus == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
        }
    }
    private void firstTimeAudio(){
        String text = "Hello! Thanks for installing me! I am SCAVI. Smart Camera App to help you navigate." +
                "Your front view is divided into three segments namely straight, left and right." +
                "I try to detect presence of obstacle in the straight segment every second." +
                "If obstacle is found in straight segment, I will check the left and right" +
                "segment for presence of obstacle and inform you how to proceed." +
                "If I inform left, please move couple of steps to your left and proceed." +
                "If I inform right, please move couple of steps to your right and proceed." +
                "If I inform left or right, you can move couple of steps either to your" +
                "left or right and proceed." +
                "If I inform obstacle, it means that there is an obstacle in all the three segments." +
                "Based on previous feedback, I will suggest to move left or right and you need to move for a meter." +
                "If the previous feedback is also an obstacle warning, I will just say obstacle!" +
                "and will not give any suggestion." +
                "You need to move left or right for a metre and I will recheck obstacle presence." +
                "Hope you find me useful. Move with extra confidence and have a safe trip!";
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        boolean speakingEnd = tts.isSpeaking();
        do {
            speakingEnd = tts.isSpeaking();
            System.out.print("Do something or nothing while speaking..");
        } while (speakingEnd);
    }

    /***
     * Checks that application runs first time and write flag at SharedPreferences
     * @return true if 1st time
     */
    private boolean isFirstTime()
    {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        boolean ranBefore = preferences.getBoolean("RanBefore", false);
        if (!ranBefore) {
            // first time
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("RanBefore", true);
            editor.commit();
        }
        return !ranBefore;
    }

    @Override
    protected void onPause() {
        super.onPause();
        synchronized (this) {
            try {
                mTango.disconnect();
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }
    }

    /**
     * Sets up the Tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Create a new Tango configuration and enable the Depth Sensing API.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
        return config;
    }

    /**
     * Set up the callback listeners for the Tango Service and obtain other parameters required
     * after Tango connection.
     * Listen to new Point Cloud data.
     */
    private void startupTango() {

        // Lock configuration and connect to Tango.
        // Select coordinate frame pair.
        final ArrayList<TangoCoordinateFramePair> framePairs =
                new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));


        // Listen for new Tango data.
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                // We are not using TangoPoseData for this application.
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onPointCloudAvailable(final TangoPointCloudData pointCloudData) {
                if (isFirstTime()) {
                    firstTimeAudio();
                }
                logPointCloud(pointCloudData);
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                // Ignoring TangoEvents.
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // We are not using onFrameAvailable for this application.
            }
        });
    }

    /*
     * Log the point count and the average depth of the given PointCloud data
     * in the Logcat as information.
     */
    private void logPointCloud(TangoPointCloudData pointCloudData) {
        processingPointCloud(pointCloudData.points, pointCloudData.numPoints);
    }


    private void processingPointCloud(FloatBuffer pointCloudBuffer, int numPoints) {

        float[][] cloudDataXY = new float[9][3]; // Matrix for storing no.of points in the rectangles segregated based on depth
        float [] depthInRectangle = new float [9]; // Number of points in the rectangles based on nearest depth
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 3; j++) {
                cloudDataXY[i][j] = 0;
            }
        }
        //Calculating number of points in each rectangle segregated based on three depth ranges
        for (int i = 0; i < 4*numPoints; i+=4) {
            if(pointCloudBuffer.get(i)<=0.5 && pointCloudBuffer.get(i)>-0.1 && pointCloudBuffer.get(i+1)>-0.69  && pointCloudBuffer.get(i+1)<=-0.23) {
                if(pointCloudBuffer.get(i+2)<=1.5){
                    cloudDataXY[0][0] = cloudDataXY[0][0]+1;
                }
                else if (pointCloudBuffer.get(i+2)>1.5 && pointCloudBuffer.get(i+2)<=3){
                    cloudDataXY[0][1] = cloudDataXY[0][1]+1;
                }
                else if (pointCloudBuffer.get(i+2)>3){
                    cloudDataXY[0][2] = cloudDataXY[0][2]+1;
                }

            }
            else if(pointCloudBuffer.get(i)<=0.5 && pointCloudBuffer.get(i)>-0.1 && pointCloudBuffer.get(i+1)>-0.23  && pointCloudBuffer.get(i+1)<=0.23) {
                if(pointCloudBuffer.get(i+2)<=1.5){
                    cloudDataXY[1][0] = cloudDataXY[1][0]+1;
                }
                else if (pointCloudBuffer.get(i+2)>1.5 && pointCloudBuffer.get(i+2)<=3){
                    cloudDataXY[1][1] = cloudDataXY[1][1]+1;
                }
                else if (pointCloudBuffer.get(i+2)>3){
                    cloudDataXY[1][2] = cloudDataXY[1][2]+1;
                }
            }
            else if(pointCloudBuffer.get(i)<=0.5 && pointCloudBuffer.get(i)>-0.1 && pointCloudBuffer.get(i+1)>0.23  && pointCloudBuffer.get(i+1)<=0.69) {
                if(pointCloudBuffer.get(i+2)<=1.5){
                    cloudDataXY[2][0] = cloudDataXY[2][0]+1;
                }
                else if (pointCloudBuffer.get(i+2)>1.5 && pointCloudBuffer.get(i+2)<=3){
                    cloudDataXY[2][1] = cloudDataXY[2][1]+1;
                }
                else if (pointCloudBuffer.get(i+2)>3){
                    cloudDataXY[2][2] = cloudDataXY[2][2]+1;
                }
            }
            else if(pointCloudBuffer.get(i)<=-0.1 && pointCloudBuffer.get(i)>-0.7 && pointCloudBuffer.get(i+1)>-0.69  && pointCloudBuffer.get(i+1)<=-0.23) {
                if(pointCloudBuffer.get(i+2)<=1.5){
                    cloudDataXY[3][0] = cloudDataXY[3][0]+1;
                }
                else if (pointCloudBuffer.get(i+2)>1.5 && pointCloudBuffer.get(i+2)<=3){
                    cloudDataXY[3][1] = cloudDataXY[3][1]+1;
                }
                else if (pointCloudBuffer.get(i+2)>3){
                    cloudDataXY[3][2] = cloudDataXY[3][2]+1;
                }
            }
            else if(pointCloudBuffer.get(i)<=-0.1 && pointCloudBuffer.get(i)>-0.7 && pointCloudBuffer.get(i+1)>-0.23  && pointCloudBuffer.get(i+1)<=0.23) {
                if(pointCloudBuffer.get(i+2)<=1.5){
                    cloudDataXY[4][0] = cloudDataXY[4][0]+1;
                }
                else if (pointCloudBuffer.get(i+2)>1.5 && pointCloudBuffer.get(i+2)<=3){
                    cloudDataXY[4][1] = cloudDataXY[4][1]+1;
                }
                else if (pointCloudBuffer.get(i+2)>3){
                    cloudDataXY[4][2] = cloudDataXY[4][2]+1;
                }
            }
            else if(pointCloudBuffer.get(i)<=-0.1 && pointCloudBuffer.get(i)>-0.7 && pointCloudBuffer.get(i+1)>0.23  && pointCloudBuffer.get(i+1)<=0.69) {
                if(pointCloudBuffer.get(i+2)<=1.5){
                    cloudDataXY[5][0] = cloudDataXY[5][0]+1;
                }
                else if (pointCloudBuffer.get(i+2)>1.5 && pointCloudBuffer.get(i+2)<=3){
                    cloudDataXY[5][1] = cloudDataXY[5][1]+1;
                }
                else if (pointCloudBuffer.get(i+2)>3){
                    cloudDataXY[5][2] = cloudDataXY[5][2]+1;
                }
            }
            else if(pointCloudBuffer.get(i)<=-0.7 && pointCloudBuffer.get(i)>-1.3 && pointCloudBuffer.get(i+1)>-0.69  && pointCloudBuffer.get(i+1)<=-0.23) {
                if(pointCloudBuffer.get(i+2)<=1.5){
                    cloudDataXY[6][0] = cloudDataXY[6][0]+1;
                }
                else if (pointCloudBuffer.get(i+2)>1.5 && pointCloudBuffer.get(i+2)<=3){
                    cloudDataXY[6][1] = cloudDataXY[6][1]+1;
                }
                else if (pointCloudBuffer.get(i+2)>3){
                    cloudDataXY[6][2] = cloudDataXY[6][2]+1;
                }
            }
            else if(pointCloudBuffer.get(i)<=-0.7 && pointCloudBuffer.get(i)>-1.3 && pointCloudBuffer.get(i+1)>-0.23  && pointCloudBuffer.get(i+1)<=0.23) {
                if(pointCloudBuffer.get(i+2)<=1.5){
                    cloudDataXY[7][0] = cloudDataXY[7][0]+1;
                }
                else if (pointCloudBuffer.get(i+2)>1.5 && pointCloudBuffer.get(i+2)<=3){
                    cloudDataXY[7][1] = cloudDataXY[7][1]+1;
                }
                else if (pointCloudBuffer.get(i+2)>3){
                    cloudDataXY[7][2] = cloudDataXY[7][2]+1;
                }
            }
            else if(pointCloudBuffer.get(i)<=-0.7 && pointCloudBuffer.get(i)>-1.3 && pointCloudBuffer.get(i+1)>0.23  && pointCloudBuffer.get(i+1)<=0.69) {
                if(pointCloudBuffer.get(i+2)<=1.5){
                    cloudDataXY[8][0] = cloudDataXY[8][0]+1;
                }
                else if (pointCloudBuffer.get(i+2)>1.5 && pointCloudBuffer.get(i+2)<=3){
                    cloudDataXY[8][1] = cloudDataXY[8][1]+1;
                }
                else if (pointCloudBuffer.get(i+2)>3){
                    cloudDataXY[8][2] = cloudDataXY[8][2]+1;
                }
            }
        }
        // assigning colour to the rectangles based on depth
        for (int i=0;i<9;i++){
            if(cloudDataXY[i][0]>100) depthInRectangle[i]=255;
            else if(cloudDataXY[i][1]>100) depthInRectangle[i]=150;
                //else if(cloudDataXY[i][2]>100) depthInRectangle[i]=50;
            else depthInRectangle[i]=0;
        }

        //conditions for audio output
        if(depthInRectangle[1]==255 || depthInRectangle[4]==255 || depthInRectangle[7]==255 ){
            stopTime = System.currentTimeMillis();
            timerDifference = stopTime - startTime;
            if (firstTimeAudio == 0 || ((int)timerDifference)>=2000) {
                if (depthInRectangle[0]==0 && depthInRectangle[3]==0 && depthInRectangle[6]==0 && depthInRectangle[2]==0 && depthInRectangle[5]==0 && depthInRectangle[8]==0){
                    String text = "Left or right!";
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);

                }
                else if (depthInRectangle[0]==0 && depthInRectangle[3]==0 && depthInRectangle[6]==0){
                    String text = "Left!";
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                }
                else if (depthInRectangle[2]==0 && depthInRectangle[5]==0 && depthInRectangle[8]==0){
                    String text = "Right!";
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                }
                else if (depthInRectangle[0]<=150 && depthInRectangle[3]<=150 && depthInRectangle[6]<=150 && (depthInRectangle[2]==255 || depthInRectangle[5]==255 || depthInRectangle[8]==255)){
                    String text = "Left!";
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                }
                else if ((depthInRectangle[0]==255 || depthInRectangle[3]==255 || depthInRectangle[6]==255) && depthInRectangle[2]<=150 && depthInRectangle[5]<=150 && depthInRectangle[8]<=150){
                    String text = "Right!";
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                }
                else if (depthInRectangle[0]<=150 && depthInRectangle[3]<=150 && depthInRectangle[6]<=150 && depthInRectangle[2]<=150 && depthInRectangle[5]<=150 && depthInRectangle[8]<=150){
                    String text = "Left or right!";
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                }
                else{
                    if(previousWarning == 1){
                        String text = "Obstacle. Prefer Left or Right!";
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                    }
                    else if(previousWarning == 2){
                        String text = "Obstacle. Prefer Left!";
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                    }
                    else if(previousWarning == 3){
                        String text = "Obstacle. Prefer Right!";
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                    }
                    else{
                        String text = "Obstacle!";
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                    }
                }
                startTime = System.currentTimeMillis();
                firstTimeAudio = 1;
            }
        }
        if (depthInRectangle[0]==0 && depthInRectangle[3]==0 && depthInRectangle[6]==0 && depthInRectangle[2]==0 && depthInRectangle[5]==0 && depthInRectangle[8]==0){
            previousWarning = 1;
        }
        else if (depthInRectangle[0]==0 && depthInRectangle[3]==0 && depthInRectangle[6]==0){
            previousWarning = 2;
        }
        else if (depthInRectangle[2]==0 && depthInRectangle[5]==0 && depthInRectangle[8]==0){
            previousWarning = 3;
        }
        else if (depthInRectangle[0]<=150 && depthInRectangle[3]<=150 && depthInRectangle[6]<=150 && (depthInRectangle[2]==255 || depthInRectangle[5]==255 || depthInRectangle[8]==255)){
            previousWarning = 2;
        }
        else if ((depthInRectangle[0]==255 || depthInRectangle[3]==255 || depthInRectangle[6]==255) && depthInRectangle[2]<=150 && depthInRectangle[5]<=150 && depthInRectangle[8]<=150){
            previousWarning = 3;
        }
        else if (depthInRectangle[0]<=150 && depthInRectangle[3]<=150 && depthInRectangle[6]<=150 && depthInRectangle[2]<=150 && depthInRectangle[5]<=150 && depthInRectangle[8]<=150){
            previousWarning = 1;
        }
        SCAVIView.setCurrentPoint(depthInRectangle); //passing data to View class for drawing rectangles
    }


    /**
     * Display toast on UI thread.
     *
     * @param resId The resource id of the string resource to use. Can be formatted text.
     */
    private void showsToastAndFinishOnUiThread(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SCAVIActivity.this,
                        getString(resId), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
}
