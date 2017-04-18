package org.opencv.samples.colorblobdetect;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOError;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static android.os.SystemClock.sleep;
// bluetooth code taken from http://www.egr.msu.edu/classes/ece480/capstone/spring14/group01/docs/appnote/Wirsing-SendingAndReceivingDataViaBluetoothWithAnAndroidDevice.pdf
public class ColorBlobDetectionActivity extends Activity implements OnTouchListener, CvCameraViewListener2 {
    private static final String TAG = "OCVSample::Activity";

    private boolean mIsColorSelected = false;
    private boolean ppIsColorSelected = false;
    private boolean ffIsColorSelected = false;
    private Mat mRgba;
    private Scalar mBlobColorRgba;
    private Scalar mBlobColorHsv;
    private Scalar mBlobColorRgbaPP;
    private Scalar mBlobColorHsvPP;
    private Scalar mBlobColorRgbaFF;
    private Scalar mBlobColorHsvFF;
    private TextToSpeech tts;

//    String envpath = Environment.getDataDirectory().getPath() + File.separator + "Tactile Reader";
     final String envpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Tactile Reader";



    // private Scalar               mBlackColorHsv;

    private ColorBlobDetector mDetector;
    private ColorBlobDetector ppDetector;
    private ColorBlobDetector ffDetector;

    // private ColorBlobDetector    mBlackDetector;
    private Utility mUtility;

    private Mat mSpectrum;
    private Size SPECTRUM_SIZE;
    private Scalar CONTOUR_COLOR;
    private Scalar CONTOUR_COLOR_PP;
    private Scalar CONTOUR_COLOR_FF;

    private List<Integer> blackCentroidsX;
    private List<Integer> blackCentroidsY;
    private int fingerCentroidX;
    private int fingerCentroidY;

    private int previousState = -1;
    String filename;

    private CameraBridgeViewBase mOpenCvCameraView;

    SharedPreferences sp;

    public static boolean calibrated = true;
    public static int calibrationTagsNotVisible = 0;
    public static int calibrationCount = 0;
    public static int noCalibrationCount = 0;
    public static int pulsedPolygon = -1;
    public static int pulseState = -1;
    public static int pulseDuration = 0;
    public static int pulseDurationLimit = 5;
    public static int calibrationFrameRate = 100;
    public static int continousFrameBehaivior = 0;

    public static int allowedFingerMovement = 50;
    public static int fingerStaticCount = 0;
    public static int fingerStaticLimit = 3;
    public static Point previousFingerPosition = null;
    public static int fingerApprovalCount = 0;

    public static int fastForwardText = 2;
    public static int fastForwardAudio = 5000;  // in milliseconds

    //play/pause variables
    public static int ppState = -1;
    public static int ffState = -1;
    public static int ffPrevState = -1;
    private int currSpeak;
    private int currSpeakAudio;
    private boolean inPolygon = false;

    // local bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // member object for service
    private BluetoothService mBluetoothService = null;
    // to be connected Bluetooth device address
    private String mBluetoothDeviceAddress = null;
    private BluetoothDevice mBluetoothDevice = null;

    // to distinguish b/w SCAN_AND_READ and SCAN_AND_SEND modes
    private boolean isBluetooth;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(ColorBlobDetectionActivity.this);
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public ColorBlobDetectionActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.color_blob_detection_surface_view);
        //android.graphics.Point sSize = getScreenDimensions();
        //getWindow().setLayout(sSize.x, sSize.y);
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);

        sp = PreferenceManager.getDefaultSharedPreferences(this);
        filename = sp.getString("context_name", null);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        Log.i(TAG, "about to parse " + filename);
        mUtility = new Utility(getApplicationContext());
        mUtility.parseFile(filename);

        pulseState = -1;

        isBluetooth = getIntent().getExtras().getBoolean("is_bluetooth");
        Log.i(TAG, "boolean value: " + isBluetooth);

        if(isBluetooth) {
            mBluetoothDeviceAddress = getIntent().getExtras().getString("bluetoothAddress");
            Log.i(TAG, "bluetoothAddress: " + mBluetoothDeviceAddress);
            // bluetooth initialisation
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null) {
                Log.i(TAG, "Bluetooth not available on this device");
            }
            else if (!mBluetoothAdapter.isEnabled()) {
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, Constants.REQUEST_ENABLE_BT_SENDER);
            }
            else if (mBluetoothService == null) {
                mBluetoothService = new BluetoothService(this, mHandler);
                if (mBluetoothService.getState() == Constants.STATE_NONE) {
                    Log.i(TAG, "State is none");
                    mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mBluetoothDeviceAddress);
                    mBluetoothService.connect(mBluetoothDevice);
                    Log.i(TAG, "Connected to device");
                }
            }
        }

        final String speakStr = "Scanning " + filename;
        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.ENGLISH);
                    tts.speak(speakStr, TextToSpeech.QUEUE_FLUSH, null);
                }
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            int speakingId = -1;
            public int getId() {
                return speakingId;
            }
            @Override
            public void onStart(String s) {
                speakingId = Integer.parseInt(s);
                Log.i("UtteranceProgList", "Starting UtteranceId = " + s);
                currSpeak = speakingId;
            }

            @Override
            public void onDone(String s) {
                Log.i("UtteranceProgList", "Done UtteranceId = " + s);
            }

            @Override
            public void onError(String s) {

            }
        });
        tts.speak(speakStr, TextToSpeech.QUEUE_FLUSH, null, ""+-1);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Constants.REQUEST_ENABLE_BT_SENDER:
                // TODO: start bluetooth service and connect to the device
                if (resultCode == Activity.RESULT_OK) {
                    mBluetoothService = new BluetoothService(this, mHandler);
                    if (mBluetoothService.getState() == Constants.STATE_NONE) {
                        Log.i(TAG, "State is none");
                        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mBluetoothDeviceAddress);
                        mBluetoothService.connect(mBluetoothDevice);
                        Log.i(TAG, "Connected to device");
                    }
                } else {
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Bluetooth was not enabled", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        if(isBluetooth) {
            if (mBluetoothService != null) {
                Log.i(TAG, "Attempting to Connect");
                // only if STATE_NONE we know that the service was not started already
                if (mBluetoothService.getState() == Constants.STATE_NONE) {
                    Log.i(TAG, "State is none");
                    mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mBluetoothDeviceAddress);
                    mBluetoothService.connect(mBluetoothDevice);
                    Log.i(TAG, "Connected to device");
                }
            }
        }
    }

    public void onDestroy() {
        super.onDestroy();
        tts.stop();
        mUtility.stopAudio();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        if(mBluetoothService != null)
            mBluetoothService.stop();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mDetector = new ColorBlobDetector();
        ppDetector = new ColorBlobDetector();
        ffDetector = new ColorBlobDetector();
        // mBlackDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        mBlobColorRgbaPP = new Scalar(255);
        mBlobColorHsvPP = new Scalar(255);
        mBlobColorRgbaFF = new Scalar(255);
        mBlobColorHsvFF = new Scalar(255);
        // mBlackColorHsv = new Scalar(0,0,0,255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(0, 255, 0, 255);
        CONTOUR_COLOR_PP = new Scalar(0, 255, 0, 255);
        CONTOUR_COLOR_FF = new Scalar(0, 255, 0, 255);

        displayColor();
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public void displayColor() {
        JSONArray savedColor = new JSONArray();
        try {
            savedColor = new JSONArray(sp.getString("touched_color_hsv", "[]"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (savedColor == null || savedColor.length() == 0) {
            for (int i = 0; i < mBlobColorHsv.val.length; i++) {
                mBlobColorHsv.val[i] = 0;

            }
        } else {
            for (int i = 0; i < mBlobColorHsv.val.length; i++) {
                try {
                    mBlobColorHsv.val[i] = savedColor.getDouble(i);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        }
        mBlobColorRgba = mUtility.convertScalarHsv2Rgba(mBlobColorHsv);
        Log.i(TAG, "Saved rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        JSONArray savedColorPP = new JSONArray();
        try {
            savedColorPP = new JSONArray(sp.getString("touched_color_hsv_pp", "[]"));
        } catch(JSONException e) {
            e.printStackTrace();
        }
        if(savedColorPP == null || savedColorPP.length() == 0) {
            for(int i=0; i< mBlobColorHsvPP.val.length; i++) {
                mBlobColorHsvPP.val[i] = 0;
            }
        } else {
            for(int i=0; i<mBlobColorHsvPP.val.length; i++) {
                try {
                    mBlobColorHsvPP.val[i] = savedColorPP.getDouble(i);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        mBlobColorRgbaPP = mUtility.convertScalarHsv2Rgba(mBlobColorHsvPP);
        Log.i(TAG, "Saved rgba color PP: (" + mBlobColorRgbaPP.val[0] + ", " + mBlobColorRgbaPP.val[1] +
                ", " + mBlobColorRgbaPP.val[2] + ", " + mBlobColorRgbaPP.val[3] + ")");

        JSONArray savedColorFF = new JSONArray();
        try {
            savedColorFF = new JSONArray(sp.getString("touched_color_hsv_ff", "[]"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if(savedColorFF == null || savedColorFF.length() == 0) {
            for(int i=0; i<mBlobColorHsvFF.val.length; i++) {
                mBlobColorHsvFF.val[i] = 0;
            }
        } else {
            for(int i=0; i<mBlobColorHsvFF.val.length; i++) {
                try {
                    mBlobColorHsvFF.val[i] = savedColorFF.getDouble(i);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        mBlobColorRgbaFF = mUtility.convertScalarHsv2Rgba(mBlobColorHsvFF);
        Log.i(TAG, "Saved rgba color FF: (" + mBlobColorRgbaFF.val[0] + ", " + mBlobColorRgbaFF.val[1] +
                ", " + mBlobColorRgbaFF.val[2] + ", " + mBlobColorRgbaFF.val[3] + ")");

        mDetector.setHsvColor(mBlobColorHsv);
        ppDetector.setHsvColor(mBlobColorHsvPP);
        ffDetector.setHsvColor(mBlobColorHsvFF);

        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);
        Imgproc.resize(ppDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);
        Imgproc.resize(ffDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);
        mIsColorSelected = true;
        ppIsColorSelected = true;
        ffIsColorSelected = true;
    }

    public boolean onTouch(View v, MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

        int x = (int) event.getX() - xOffset;
        int y = (int) event.getY() - yOffset;

        Log.i("TOUCH", "Touch image coordinates + Offset: (" + x + xOffset + ", " + y + yOffset + ")");
        Log.i("TOUCH", "Offset: (" + xOffset + ", " + yOffset + ")");
        Log.i("TOUCH", "Touch image coordinates: (" + x + ", " + y + ")");
        Log.i("TOUCH", "Cols + Rows: (" + cols + ", " + rows + ")");
        Log.i("TOUCH", "Width + Height: (" + mOpenCvCameraView.getWidth() + ", " + mOpenCvCameraView.getHeight() + ")");

        return false; // don't need subsequent touch events
    }

    private void sendMessage(String message) {
        // check that we are actually connected before trying anything
        if (mBluetoothService.getState() != Constants.STATE_CONNECTED) {
//            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
//            Toast.makeText(this, "Bluetooth Not Connected", Toast.LENGTH_SHORT).show();
            Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
            Bundle bundle = new Bundle();
            bundle.putString(Constants.TOAST, "Bluetooth Not Connected");
            msg.setData(bundle);
            mHandler.sendMessage(msg);
            return;
        }
        // get the message bytes and tell teh BluetoothService to write
        byte[] send = message.getBytes();
        mBluetoothService.write(send);

    }

    public Mat onCameraFrame1(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
//        String centroidStrings = "anda \nmurgi \nchickentandoori \nchickenkorma \nkadhaichicken";
        String centroidStrings = "{1,0}+{2,0}+{3,0}+1\n{4,5}+{6,7}+{8,9}+1\n" +
                "{1,0}+{2,0}+{3,0}+1\n" +
                "{4,5}+{6,7}+{8,9}+1\n{1,0}+{2,0}+{3,0}+1\n" +
                "{4,5}+{6,7}+{8,9}+1\n{1,0}+{2,0}+{3,0}+1\n" +
                "{4,5}+{6,7}+{8,9}+1\n{1,0}+{2,0}+{3,0}+1\n" +
                "{4,5}+{6,7}+{8,9}+1\n";
        sendMessage(centroidStrings);
        return mRgba;
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();

        if (mIsColorSelected) {
            mDetector.process(mRgba);
            List<MatOfPoint> contours = mDetector.getContours();
            Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR);
            // play/pause
            List<MatOfPoint> ppContours = new ArrayList<>();
            List<MatOfPoint> ffContours = new ArrayList<>();
            if(ppIsColorSelected) {
                ppDetector.process(mRgba);
                ppContours = ppDetector.getContours();
                Imgproc.drawContours(mRgba, ppContours, -1, CONTOUR_COLOR_PP);
            }
            if(ffIsColorSelected) {
                ffDetector.process(mRgba);
                ffContours = ffDetector.getContours();
                Imgproc.drawContours(mRgba, ffContours, -1, CONTOUR_COLOR_FF);
            }

            changeCalibrationState(contours, getApplicationContext());
            if (contours.size() == 2 && calibrated && (pulseState == 0)) {
                pulseDuration++;
                Log.i("PULSE", "PulseDuration: " + pulseDuration);
                if (pulseDuration > pulseDurationLimit) {
                    pulseState = 1;
                    pulseDuration = 0;
                }
            }
            // change play/pause state
//            ppState = 1;
            if(ppContours.size() > 0) {
                ppState = 1;
            } else {
                ppState = 0;
            }

            Log.i("PULSE", "PulseState: " + pulseState);
            Log.i("PULSE", "ContourSize: " + contours.size());
            Log.i("PLAY_PAUSE", "State: " + ppState);
            Log.i("PLAY_PAUSE", "ContourSize: " + ppContours.size());

            if(isBluetooth) Log.i(TAG, "Checking if bluetooth active. State = " + (mBluetoothService.getState() == 3));
            Log.i(TAG, "Checking contours size = " + contours.size());
            Log.i(TAG, "Checking calibration = " + calibrated);
            Log.i(TAG, "Checking TTS = " + !tts.isSpeaking());
            Log.i(TAG, "Checking mUtility = " + !mUtility.mp.isPlaying());
            // Logic to call state name
            if (contours.size() == 3 && calibrated){// && !mUtility.mp.isPlaying()){// && !tts.isSpeaking()) {// && mBluetoothService.getState() == 3) {
                pulseDuration = 0;
                Point[] centroids;
                if (mUtility.getOrientation() % 2 == 0) {
                    centroids = mUtility.getCentroid(contours, mUtility.compX);
                } else {
                    centroids = mUtility.getCentroid(contours, mUtility.compY);
                }
                fingerCentroidX = (int) centroids[1].x;
                fingerCentroidY = (int) centroids[1].y;

                if(isBluetooth) {
                    // sending via bluetooth
                    String centroidStringsNew = "";
                    for (Point centroid : centroids) {
                        centroidStringsNew += centroid.toString();
                        centroidStringsNew += "+";
                    }
                    centroidStringsNew += "pulseState:"+pulseState+"+";
                    centroidStringsNew += "ppState:"+ppState+"+";
                    centroidStringsNew += "ffState:"+ffState+"+";
                    centroidStringsNew += "\n";
                    sendMessage(centroidStringsNew);
                    //                String fingerCentroidStr = fingerCentroidX + "," + fingerCentroidY;
                    //                sendMessage(fingerCentroidStr);
                    Log.i(TAG, "Sent centroids = " + centroidStringsNew);
                }

                blackCentroidsX = new ArrayList<Integer>();
                blackCentroidsY = new ArrayList<Integer>();
                blackCentroidsX.add((int) centroids[0].x);
                blackCentroidsX.add((int) centroids[2].x);
                blackCentroidsY.add((int) centroids[0].y);
                blackCentroidsY.add((int) centroids[2].y);

                Log.i("CENTROIDS", "Finger: " + fingerCentroidX + " " + fingerCentroidY);
                Log.i("CENTROIDS", "Blob1: " + blackCentroidsX.get(0) + " " + blackCentroidsY.get(0));
                Log.i("CENTROIDS", "Blob2: " + blackCentroidsX.get(1) + " " + blackCentroidsY.get(1));
                android.graphics.Point pt = getScreenDimensions();
                Log.i("CENTROIDS", "Size: " + pt.x + " " + pt.y);

                Point nP = normalizePoint(new Point(fingerCentroidX, fingerCentroidY));
                Log.i(TAG, "Normalized Finger: " + nP.x + " " + nP.y + " " + mUtility.regionPoints.size());
                if (Utility.isFingerStatic(nP)) {
                    inPolygon = false;
                    for (int i = 0; i < mUtility.regionPoints.size(); i++) {                    // for each region
                        Log.i(TAG, "For polygonTest: " + mUtility.titles.get(i) + " " + i);
                        //if (Imgproc.pointPolygonTest(mUtility.statesContours.get(i), nP, false) > 0*/
                        if (mUtility.polygonTest(nP, mUtility.regionPoints.get(i))) {            // if finger is in region i
                            inPolygon = true;
                            Log.i(TAG, "polygontestpassed");
                            Log.i(TAG, "PulsedPolygon: " + pulsedPolygon + " " + Utility.titles.get(i) + " " + i);
                            // state machine for fastforward
                            ffPrevState = ffState;
                            Log.i("Fast_Forward","ffPrevState: " + ffPrevState);
                            Log.i("Fast_forward", "ffContourSize: " + ffContours.size());
                            if(ffContours.size() == 0) {
                                ffState = 1;
                            }
                            else {
                                if(ffPrevState == 1)
                                    ffState = 2;
                                else
                                    ffState = 0;
                            }
                            if(ffState == 2) {
                                ffState = 0;
                                if(isBluetooth) {
                                    String centroidStringsNew = "";
                                    for (Point centroid : centroids) {
                                        centroidStringsNew += centroid.toString();
                                        centroidStringsNew += "+";
                                    }
                                    centroidStringsNew += "pulseState:"+pulseState+"+";
                                    centroidStringsNew += "ppState:"+ppState+"+";
                                    centroidStringsNew += "ffState:2+";
                                    centroidStringsNew += "\n";
                                    sendMessage(centroidStringsNew);
                                    Log.i(TAG, "Sent centroids = " + centroidStringsNew);
                                } else {
                                    fastForward(pulsedPolygon, fastForwardText);
                                }
                            }
                            Log.i("Fast_Forward","ffState: " + ffState);
                            // state machine for pulse
                            if (pulseState == -1) {
                                pulseState = 0;
                                pulsedPolygon = i;
                            } else if (pulseState == 1 && pulsedPolygon == i) {
                                pulseState = 2;
                            } else if (pulseState == 1 && pulsedPolygon != i) {
                                pulsedPolygon = i;
                                pulseState = 0;
                            } else if (pulseState == 0) {
                                pulsedPolygon = i;
                            }

                            if ((previousState != i) && pulseState != 2) {
                                previousState = i;
                                Log.i(TAG, "isBluetooth = " + isBluetooth);
                                if(!isBluetooth) {
                                    String speakStr = mUtility.titles.get(i);
                                    Log.i("PULSE", "toSpeak: " + speakStr);
                                    //speakOut(speakStr, getApplicationContext());
                                    tts.speak(speakStr, TextToSpeech.QUEUE_FLUSH, null, ""+-1);
                                }
                            }
                            // pause
                            if(ppState != 1 && pulseState == 0) {   // pause
                                Log.i("Play_Pause", "Pausing pulsedPolygon = " + pulsedPolygon + " with currSpeak = " + currSpeak +" " +
                                        "and currSpeakAudio = " + currSpeakAudio);
                                if(isBluetooth) {
                                    String centroidStringsNew = "";
                                    for (Point centroid : centroids) {
                                        centroidStringsNew += centroid.toString();
                                        centroidStringsNew += "+";
                                    }
                                    centroidStringsNew += "pulseState:"+pulseState+"+";
                                    centroidStringsNew += "ppState:"+ppState+"+";
                                    centroidStringsNew += "ffState:"+ffState+"+";
                                    centroidStringsNew += "\n";
                                    sendMessage(centroidStringsNew);
                                    Log.i(TAG, "Sent centroids = " + centroidStringsNew);
                                } else {
                                    if (mUtility.isSpeaking()) {
                                        Log.i("Play_Pause", "Pausing MediaPlayer");
                                        pauseAudio(pulsedPolygon);
                                    }
                                    if (tts.isSpeaking()) {
                                        Log.i("Play_Pause", "Pausing TTS");
                                        pauseTTS(pulsedPolygon, currSpeak);
                                    }
                                }
                            }
                            if (pulseState == 2 && previousState == i) {
                                Log.i(TAG, "PulseState: " + pulseState);
                                pulseState = 0;
                                if(isBluetooth) {
                                    String centroidStringsNew = "";
                                    for (Point centroid : centroids) {
                                        centroidStringsNew += centroid.toString();
                                        centroidStringsNew += "+";
                                    }
                                    centroidStringsNew += "pulseState:2+";
                                    centroidStringsNew += "ppState:"+ppState+"+";
                                    centroidStringsNew += "ffState:"+ffState+"+";
                                    centroidStringsNew += "\n";
                                    sendMessage(centroidStringsNew);
                                } else {
//                                    final String toDescribe = mUtility.descriptions.get(pulsedPolygon);
                                    final List<String> toDescribeList = mUtility.descriptionStatements.get(pulsedPolygon);
                                    Log.i(TAG, "Starting to Speak. ppState: " + ppState + " toDescribeList.size = " + toDescribeList.size());
                                    currSpeak = mUtility.lastLocation.get(pulsedPolygon);
                                    currSpeakAudio = mUtility.lastLocationAudio.get(pulsedPolygon);
                                    Log.i("PLAY_PAUSE", "Speaking with pulsedPolygon = " + pulsedPolygon + " currSpeak = " + currSpeak);
                                    //TextToSpeech tts = new TextToSpeech(this, this);
                                    if(currSpeak >= toDescribeList.size()-1)
                                        currSpeak = 0;
                                    if(ppState != 1) {
                                        if(tts.isSpeaking()) {
                                            Log.i(TAG, "About to pause speech at pulsedPolygon = " + pulsedPolygon + " and currSpeak = " + currSpeak);
                                            pauseTTS(pulsedPolygon, currSpeak);
                                        }
                                        if(mUtility.isSpeaking()) {
                                            pauseAudio(pulsedPolygon);
                                        }
                                    }
                                    for(currSpeak = mUtility.lastLocation.get(pulsedPolygon); currSpeak < toDescribeList.size(); currSpeak++) {
                                        if(ppState == 1) {
                                            String toDescribe = toDescribeList.get(currSpeak);
                                            if (toDescribe.startsWith("$AUDIO$")) {
                                                Log.i(TAG, "Speaking from currSpeakAudio = " + currSpeakAudio);
                                                mUtility.playAudio(envpath + File.separator + filename, toDescribe, currSpeakAudio);
                                                Log.wtf("MTP", "parsing: " + envpath + "/" + toDescribe);
                                            } else {
                                                Log.i(TAG, "Speaking from currSpeak = " + currSpeak);
                                                Log.i(TAG, "toDescribe: " + toDescribe);
                                                mUtility.changeLastLocation(pulsedPolygon, currSpeak);
                                                //change
                                                if (!mUtility.mp.isPlaying()) {
                                                    final String speakStr = toDescribe;
                                                    tts.speak(speakStr, TextToSpeech.QUEUE_ADD, null, ""+currSpeak);
                                                    //tts.speak(speakStr, TextToSpeech.QUEUE_ADD, null);
                                                }
                                                //change
                                            }
                                        }
                                    }
                                    Log.i(TAG, "speech over. currSpeak = " + currSpeak);
                                    if (currSpeak >= toDescribeList.size() - 1) {
                                        currSpeak = 0;
                                        mUtility.changeLastLocation(pulsedPolygon, currSpeak);
                                    }
                                }
                            }
                            break;
                        }
                    }
                    if(!inPolygon) {
                        Log.i("Play_Pause", "Outside any polygon. Speech paused. pulseState = " + pulseState);
                        if(tts.isSpeaking())
                            tts.stop();
                        if(mUtility.isSpeaking()) {
                            mUtility.stopAudio();
                        }
                    }
                }
            }
            Mat colorLabel = mRgba.submat(4, 68, 4, 68);
            colorLabel.setTo(mBlobColorRgba);

            //Mat spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70, 70 + mSpectrum.cols());
            //mSpectrum.copyTo(spectrumLabel);
        }
        return mRgba;
    }

    private void pauseTTS(int pulsedPolygon, int currSpeak) {
        Log.i("Play_Pause", "Pausing pulsedPolygon = " + pulsedPolygon + " with currSpeak = " + currSpeak);
        tts.stop();
        if(pulsedPolygon != -1)
            mUtility.changeLastLocation(pulsedPolygon, currSpeak);
    }
    private void pauseAudio(int pulsedPolygon) {
        Log.i("Play_Pause", "Pausing Audio. PulsedPolygon = " + pulsedPolygon);
        mUtility.pauseAudio(pulsedPolygon);
    }
    private void fastForward(int pulsedPolygon, int numStatements) {
        List<String> toDescribeList = mUtility.descriptionStatements.get(pulsedPolygon);
        int size = toDescribeList.size();
        if(tts.isSpeaking()) {
            Log.i("Fast Forwarding", "From " + currSpeak);
            if(currSpeak + numStatements <= size-1) {
                currSpeak = currSpeak + numStatements;
            }
            else {
                currSpeak = size;
            }
            Log.i("Fast Forwarding", "To " + currSpeak);
            tts.stop();
            for(; currSpeak < toDescribeList.size(); currSpeak++) {
                if(ppState == 1) {
                    Log.i(TAG, "Speaking from currSpeak = " + currSpeak);
                    String toDescribe = toDescribeList.get(currSpeak);
                    if (toDescribe.startsWith("$AUDIO$")) {
                        //envpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + "Tactile Reader";
                        mUtility.playAudio(envpath + File.separator + filename, toDescribe, currSpeakAudio);
                        Log.wtf("MTP", "parsing: " + envpath + "/" + toDescribe);
                    } else {
                        Log.i(TAG, "toDescribe: " + toDescribe);
                        //while (tts.isSpeaking()){}
                        //speakOut(toDescribe, getApplicationContext());
                        mUtility.changeLastLocation(pulsedPolygon, currSpeak);
                        //change
                        if (!mUtility.mp.isPlaying()) {
                            final String speakStr = toDescribe;
                            tts.speak(speakStr, TextToSpeech.QUEUE_ADD, null, ""+currSpeak);
                            //tts.speak(speakStr, TextToSpeech.QUEUE_ADD, null);
                        }
                        //change
                    }
                }
            }
        }
        if(mUtility.isSpeaking()) {
            mUtility.fastForwardAudio(fastForwardAudio);
        }

    }

    public void speakOut(String toSpeak, Context applicationContext) {
        Log.i("TTS", "speakOut: toSpeak: " + toSpeak);
        if (!mUtility.mp.isPlaying() && !tts.isSpeaking()) {
            final String speakStr = toSpeak;
            tts = new TextToSpeech(applicationContext, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    Log.i("TTS", "Status = " + status);
                    //TODO: read addSpeech
                    if (status != TextToSpeech.ERROR) {
                        Log.i("TTS", "speakOut: speaking: " + speakStr);
                        tts.setLanguage(Locale.ENGLISH);
                        tts.speak(speakStr, TextToSpeech.QUEUE_ADD, null);
                    }
                }
            });
        }
    }
    /*
        Screen coordinate axis:
         Y -------------
                       |
                       |
                       |
                       |
                       | X
     */

    private Point normalizePoint(Point P) {
        double scalingFactor;

        // Log.i(TAG, "Corner1: " + blackCentroidsX.get(l) + " " + blackCentroidsY.get(l));
        // Log.i(TAG, "Corner2: " + blackCentroidsX.get(h) + " " + blackCentroidsY.get(h));

        // Find screen dist
        double ySQR = Math.pow((double) blackCentroidsY.get(1).intValue() - (double) blackCentroidsY.get(0).intValue(), 2);
        double xSQR = Math.pow((double) blackCentroidsX.get(1).intValue() - (double) blackCentroidsX.get(0).intValue(), 2);
        double screenDist = Math.pow(xSQR + ySQR, 0.5);

        double y2 = Math.pow(mUtility.Corners[1].y - mUtility.Corners[0].y, 2);
        double x2 = Math.pow(mUtility.Corners[1].x - mUtility.Corners[0].x, 2);
        double tagDist = Math.pow(x2 + y2, 0.5);
        scalingFactor = tagDist / screenDist;
//        scalingFactor = ((double) Math.abs(mUtility.Corners[1].x - mUtility.Corners[0].x)) / screenDist;

        double xDash = P.x * scalingFactor;
        double yDash = P.y * scalingFactor;

        double theta = Math.atan(((double) blackCentroidsY.get(1).intValue() - (double) blackCentroidsY.get(0).intValue()) / ((double) blackCentroidsX.get(1).intValue() - (double) blackCentroidsX.get(0).intValue()));

        if (mUtility.getOrientation() > 2) {
            theta = theta - Math.toRadians(180);
        }

        double x = xDash * Math.cos(theta) - yDash * Math.sin(theta);
        double y = xDash * Math.sin(theta) + yDash * Math.cos(theta);
        Log.i("ROTATION", "Theta: " + Math.toDegrees(theta));
        Log.i("ROTATION", "xDash, yDash: " + xDash + ", " + yDash);
        Log.i("ROTATION", "x, y: " + x + ", " + y);

        Point normalizedP = new Point(x, y);
        return normalizedP;
    }

    // Returns size of screen in pixels
    public android.graphics.Point getScreenDimensions() {
        Display display = getWindowManager().getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point();
        display.getSize(size);

        return size;
    }

    public boolean isInView(List<MatOfPoint> contours) {
        Point[] tempC;
        if (mUtility.getOrientation() % 2 == 0) {
            tempC = mUtility.getCentroid(contours, mUtility.compY);
        } else {
            tempC = mUtility.getCentroid(contours, mUtility.compX);
        }
        Point lowestPoint = new Point();
        double toAdd = 0;
        if (mUtility.getOrientation() == 1) {
            lowestPoint = tempC[1];
            toAdd = lowestPoint.x;
        } else if (mUtility.getOrientation() == 2) {
            lowestPoint = tempC[1];
            toAdd = lowestPoint.y;
        } else if (mUtility.getOrientation() == 3 || (mUtility.getOrientation() == 4)) {
            if (contours.size() == 2) {
                lowestPoint = tempC[0];
            } else if (contours.size() == 3) {
                lowestPoint = tempC[1];
            } else {
                Log.i(TAG, "This Orientation is not Possible");
            }
            if (mUtility.getOrientation() == 3) {
                toAdd = lowestPoint.x;
            } else {
                toAdd = lowestPoint.y;
            }
        } else {
            Log.i(TAG, "This Orientation is not Possible");
        }
        double ySQR = Math.pow((double) tempC[1].y - (double) tempC[0].y, 2);
        double xSQR = Math.pow((double) tempC[1].x - (double) tempC[0].x, 2);
        double screenDistX = Math.pow(xSQR + ySQR, 0.5);

        double scalingFactor = ((double) Math.abs(mUtility.Corners[1].x - mUtility.Corners[0].x)) / screenDistX;

        double cornerDist_1_2 = Math.abs(mUtility.Corners[1].y - mUtility.Corners[2].y);
        double cornerDist_0_3 = Math.abs(mUtility.Corners[0].y - mUtility.Corners[3].y);
        double cornerDistY = (cornerDist_1_2 < cornerDist_0_3) ? cornerDist_0_3 : cornerDist_1_2;

        // Actual Y would be lower than this as the image would be slightly tilted but to be on the safe side
        double extreme = 0;
        if (mUtility.getOrientation() > 2) {
            extreme = toAdd - (cornerDistY / scalingFactor);
        } else {
            extreme = toAdd + (cornerDistY / scalingFactor);
        }

        if (mUtility.getOrientation() % 2 == 1) {
            int cols = mRgba.cols();
            int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
            extreme += xOffset;

            Log.i("CALIBRATION", "extreme+Offset" + "\t" + "lX" + "\t" + "lY");
            Log.i("CALIBRATION", extreme + " " + lowestPoint.x + " " + lowestPoint.y);

            if ((extreme > mOpenCvCameraView.getWidth()) || (extreme < 0)) {
                return false;
            } else {
                return true;
            }
        } else {
            int rows = mRgba.rows();
            int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;
            extreme += yOffset;

            if ((extreme > mOpenCvCameraView.getHeight()) || (extreme < 0)) {
                return false;
            } else {
                return true;
            }
        }
    }

    /*
        given: contours has size = 2
        return true if both are corner tags
        return false if one of them is finger tag
     */
    public boolean isCornerInView(List<MatOfPoint> contours) {
        Point[] centroids;
        if (mUtility.getOrientation() % 2 == 0) {
            centroids = mUtility.getCentroid(contours, mUtility.compX);
        } else {
            centroids = mUtility.getCentroid(contours, mUtility.compY);
        }
        Log.wtf("CALIBRATE", "count of centroids: " + centroids.length);
        Log.wtf("CALIBRATE", "centroid 1: " + centroids[0].x + ", " + centroids[0].y);
        Log.wtf("CALIBRATE", "centroid 2: " + centroids[1].x + ", " + centroids[1].y);
        double x1 = centroids[0].x;
        double y1 = centroids[0].y;
        double x2 = centroids[1].x;
        double y2 = centroids[1].y;
        if (mUtility.getOrientation() % 2 == 0) {
            double del_y = (y1 > y2) ? (y1-y2) : (y2-y1);
            return (del_y < 100);
        } else {
            double del_x = (x1 > x2) ? (x1-x2) : (x2-x1);
            return (del_x < 100);
        }
    }

    public String dir2Move(List<MatOfPoint> contours) {
        Point[] centroids;
        if (mUtility.getOrientation() % 2 == 0) {
            centroids = mUtility.getCentroid(contours, mUtility.compX);
        } else {
            centroids = mUtility.getCentroid(contours, mUtility.compY);
        }
        int cols = mRgba.cols();
        int rows = mRgba.rows();
        Log.wtf("CALIBRATE", "count of centroids: " + centroids.length);
        Log.wtf("CALIBRATE", "screen dimensions: " + cols + ", " + rows);

        double x, y;
        if (contours.size() == 1) {
            x = centroids[0].x;
            y = centroids[0].y;
            Log.wtf("CALIBRATE", "only selected centroid: " + centroids[0].x + ", " + centroids[0].y);
        } else {
            int ind;
            if (mUtility.getOrientation() == 1) {
                ind = (centroids[0].x < centroids[1].x) ? 0 : 1;
            } else if (mUtility.getOrientation() == 2) {
                ind = (centroids[0].y < centroids[1].y) ? 0 : 1;
            } else if (mUtility.getOrientation() == 3) {
                ind = (centroids[0].x < centroids[1].x) ? 1 : 0;
            } else {
                ind = (centroids[0].y < centroids[1].y) ? 1 : 0;
            }
            x = centroids[ind].x;
            y = centroids[ind].y;
            Log.wtf("CALIBRATE", "centroid 1: " + centroids[0].x + ", " + centroids[0].y);
            Log.wtf("CALIBRATE", "centroid 2: " + centroids[1].x + ", " + centroids[1].y);
            Log.wtf("CALIBRATE", "selected centroid: " + x + ", " + y);
        }

        if (mUtility.getOrientation() == 1) {
            if (2*y < rows){
                return "one tag missing, move to right";
            } else {
                return "one tag missing, move to left";
            }
        } else if (mUtility.getOrientation() == 2) {
            if (2*x < cols){
                return "one tag missing, move to left";
            } else {
                return "one tag missing, move to right";
            }
        } else if (mUtility.getOrientation() == 3) {
            if (2*y < rows){
                return "one tag missing, move to left";
            } else {
                return "one tag missing, move to right";
            }
        } else {
            if (2*x < cols){
                return "one tag missing, move to right";
            } else {
                return "one tag missing, move to left";
            }
        }
    }

    public void changeCalibrationState(List<MatOfPoint> contours, Context applicationContext) {
        boolean prevCalibrationState = calibrated;
        if (contours.size() == 0 || contours.size() == 1) {
            calibrationTagsNotVisible++;
            calibrationCount = 0;
            noCalibrationCount = 0;
            continousFrameBehaivior++;
            if (continousFrameBehaivior > 10) {
                calibrated = false;
                continousFrameBehaivior = 0;
            }
            Log.i("CalCheck", "NotCalibrated2");
            if ((prevCalibrationState != calibrated) || (calibrationTagsNotVisible > calibrationFrameRate)) {
                if(contours.size() == 0) {
                    String toSpeak = "None of the tags in view";
                    speakOut(toSpeak, applicationContext);
                    calibrationTagsNotVisible = 0;
                } else {
                    String toSpeak = dir2Move(contours);
                    speakOut(toSpeak, applicationContext);
                    calibrationTagsNotVisible = 0;
                }
            }
        } else if (contours.size() == 2 || contours.size() == 3) {
            continousFrameBehaivior = 0;
            calibrationTagsNotVisible = 0;
            calibrated = (contours.size()==3) || isCornerInView(contours);
            Log.i("CALIBRATION", "IsCalibrated: " + calibrated);
            if (calibrated) {
                calibrationCount++;
                calibrationCount = calibrationCount % calibrationFrameRate;
                noCalibrationCount = 0;
                if (prevCalibrationState != calibrated) {
                    calibrationCount = 0;
                    final String toSpeak = "Both tags now in view";
                    Log.i("CalCheck", "Calibrated");
                    // Add in queue even if tts isSpeaking(), speakOut doesn't add if isSpeaking()
                    speakOut(toSpeak, applicationContext);
                }
            } else {
                noCalibrationCount++;
                calibrationCount = 0;
                if ((prevCalibrationState != calibrated) || (noCalibrationCount > calibrationFrameRate)) {
                    noCalibrationCount = 0;
                    Log.i("CalCheck", "NotCalibrated");
                    speakOut(dir2Move(contours), applicationContext);
                }
            }
        } else {
            String toSpeak = "Conditions not suitable, many tags visible";
            // speakOut(toSpeak, applicationContext);
        }
    }



    // the Handler that gets information back from the BLuetoothService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch(message.what) {
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    String mConnectedDeviceName = message.getData().getString(Constants.DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_TOAST:
                    String msg = message.getData().getString(Constants.TOAST);
                    Toast.makeText(ColorBlobDetectionActivity.this, msg, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
}
