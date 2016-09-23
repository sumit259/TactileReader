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
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.opencv.core.Point;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShowCentroidsActivity extends Activity {

    private static final String TAG = "ShowCentroidsActivity";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // BLUETOOTH Variables
    // Message types sent from BluetoothService
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // local bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // member object for service
    private BluetoothService mBluetoothService = null;

    String filename;
    SharedPreferences sp;
    private TextToSpeech tts;

    private Utility mUtility;

    private List<Integer> blackCentroidsX;
    private List<Integer> blackCentroidsY;
    private int fingerCentroidX;
    private int fingerCentroidY;
    public static int pulsedPolygon = -1;
    private int previousState = -1;

    String envpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Tactile Reader";

    TextView corner1, corner2, finger;

    Button speak;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_centroids);

        sp = PreferenceManager.getDefaultSharedPreferences(this);
        filename = sp.getString("context_name", null);

        mUtility = new Utility(getApplicationContext());
        mUtility.parseFile(filename);

        corner1 = (TextView)findViewById(R.id.Corner1);
        corner2 = (TextView)findViewById(R.id.Corner2);
        finger  = (TextView)findViewById(R.id.fingerCentroid);

        speak = (Button)findViewById(R.id.button_tts);
        speak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String readMessage = "{0, 0}+{-306, 348}+{10, 666}+";
                Point[] points = centroids(readMessage);
                int pulseState = getPulseState(readMessage);
                show(points, pulseState);
            }
        });

        // bluetooth initialisation
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null){
            Log.i("Bluetooth service", "Bluetooth not available on this device");
        }
        if(!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }else {
            if (mBluetoothService == null) {
                if(mBluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE){
                    Log.i("MTP","CONNECTABLE DISCOVERABLE");
                }
                else if(mBluetoothAdapter.getScanMode()==BluetoothAdapter.SCAN_MODE_CONNECTABLE){
                    Log.i("MTP","CONNECTABLE");
                }
                else{
                    Log.i("MTP","NONE");
                }
                mBluetoothService = new BluetoothService(this, mHandler);
                Log.i("MTP", "starting to listen");
                mBluetoothService.start();
            }
        }

        // speak file name
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
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("BLUETOOTH", "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
//                if (resultCode == Activity.RESULT_OK) {
//                    // Get the device MAC address
//                    String address = data.getExtras()
//                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
//                    // Get the BluetoothDevice object
//                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
//                    // Attempt to connect to the device
//                    mChatService.connect(device);
//                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    mBluetoothService = new BluetoothService(this, mHandler);
                    Log.i("MTP", "starting to listen");
                    mBluetoothService.start();
                } else {
                    // User did not enable Bluetooth or an error occured
                    Log.d("BLUETOOTH", "BT not enabled");
                    finish();
                }
        }
    }

    // Returns size of screen in pixels
    public android.graphics.Point getScreenDimensions() {
        Display display = getWindowManager().getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point();
        display.getSize(size);
        return size;
    }

    void show(Point[] centroids, int pulseState){
        fingerCentroidX = (int) centroids[1].x;
        fingerCentroidY = (int) centroids[1].y;

        blackCentroidsX = new ArrayList<Integer>();
        blackCentroidsY = new ArrayList<Integer>();
        blackCentroidsX.add((int) centroids[0].x);
        blackCentroidsX.add((int) centroids[2].x);
        blackCentroidsY.add((int) centroids[0].y);
        blackCentroidsY.add((int) centroids[2].y);

        CharSequence chars = (int)centroids[0].x + ", " + (int) centroids[0].y;
        corner1.setText(chars);

        chars = (int)centroids[2].x + ", " + (int) centroids[2].y;
        corner2.setText(chars);

        chars = fingerCentroidX + ", " + fingerCentroidY;
        finger.setText(chars);

        Log.i("CENTROIDS", "Finger: " + fingerCentroidX + " " + fingerCentroidY);
        Log.i("CENTROIDS", "Blob1: " + blackCentroidsX.get(0) + " " + blackCentroidsY.get(0));
        Log.i("CENTROIDS", "Blob2: " + blackCentroidsX.get(1) + " " + blackCentroidsY.get(1));
        android.graphics.Point pt = getScreenDimensions();
        Log.i("CENTROIDS", "Size: " + pt.x + " " + pt.y);

//        // tts from centroids
        Point nP = normalizePoint(new Point(fingerCentroidX, fingerCentroidY));
        Log.i(TAG, "Normalized Finger: " + nP.x + " " + nP.y + " " + mUtility.regionPoints.size());
        if (Utility.isFingerStatic(nP)) {
            for (int i = 0; i < mUtility.regionPoints.size(); i++) {                    // for each region
                Log.i(TAG, "For polygonTest: " + mUtility.titles.get(i) + " " + i);
                //if (Imgproc.pointPolygonTest(mUtility.statesContours.get(i), nP, false) > 0*/
                if (mUtility.polygonTest(nP, mUtility.regionPoints.get(i))) {            // if finger is in region i
                    Log.i(TAG, "TEST1: IN THE IF " + "i = " + i + " ");
                    Log.i(TAG, "polygontestpassed");
                    Log.i(TAG, "PulsedPolygon: " + pulsedPolygon + " " + Utility.titles.get(i) + " " + i);
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

                    if ((previousState != i) && pulseState!=2) {
                        previousState = i;
                        String speakStr = mUtility.titles.get(i);
                        Log.i("PULSE", "toSpeak: " + speakStr);
                        speakOut(speakStr, getApplicationContext());
                    }
                    if (pulseState == 2 && previousState == i) {
                        pulseState = 0;
                        final String toDescribe = mUtility.descriptions.get(pulsedPolygon);
                        Log.i("PULSE", "pulse detected... toDescribe: " + toDescribe);
                        if (toDescribe.startsWith("$AUDIO$")) {
                            envpath = Environment.getDataDirectory() + File.separator + "Tactile Reader";

                            mUtility.playAudio(envpath + File.separator + filename, toDescribe);

                            Log.wtf("MTP", "parsing: " + envpath + "/" + toDescribe);
                        } else {
                            Log.i(TAG, "toDescribe: " + toDescribe);
                            speakOut(toDescribe, getApplicationContext());
                        }
                    }
                    break;
                }
            }
        }


    }

    Point[] centroids(String str){
        String[] strPoints = str.split("\\+");
//        List<Point> listPoints = new ArrayList<Point>();
        Point[] points = new Point[3];
        for(int i = 0; i < 3; ++i){
            String strPoint = strPoints[i];
//            if(strPoint.charAt(0)!='{')
//                break;
            String coordinates = strPoint.substring(1, strPoint.length()-1);
            String[] coordArray = coordinates.split(",");
            double x = Double.parseDouble(coordArray[0].trim());
            double y = Double.parseDouble(coordArray[1].trim());
            Point point = new Point(x,y);
//            listPoints.add(point);
            points[i] = point;
        }
        return points;
//        return (Point[])listPoints.toArray();
    }

    int getPulseState(String str) {
        String[] points = str.split("\\+");
        int len = points.length;
        int pulseState = Integer.valueOf(points[len-1]);
        return pulseState;
    }

    // can move to utility?
    private Point normalizePoint(Point P) {
        double scalingFactor;

        // Log.i(TAG, "Corner1: " + blackCentroidsX.get(l) + " " + blackCentroidsY.get(l));
        // Log.i(TAG, "Corner2: " + blackCentroidsX.get(h) + " " + blackCentroidsY.get(h));

        // Find screen dist
        double ySQR = Math.pow((double) blackCentroidsY.get(1).intValue() - (double) blackCentroidsY.get(0).intValue(), 2);
        double xSQR = Math.pow((double) blackCentroidsX.get(1).intValue() - (double) blackCentroidsX.get(0).intValue(), 2);
        double screenDist = Math.pow(xSQR + ySQR, 0.5);

        scalingFactor = ((double) Math.abs(mUtility.Corners[1].x - mUtility.Corners[0].x)) / screenDist;

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

    // common utility
    public void speakOut(String toSpeak, Context applicationContext) {
        if (!mUtility.mp.isPlaying() && !tts.isSpeaking()) {
            final String speakStr = toSpeak;
            tts = new TextToSpeech(applicationContext, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status != TextToSpeech.ERROR) {
                        tts.setLanguage(Locale.ENGLISH);
                        tts.speak(speakStr, TextToSpeech.QUEUE_FLUSH, null);
                    }
                }
            });
        }
    }

    // the Handler that gets information back from the BluetoothService
    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch(message.what) {
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) message.obj;
                    String readMessage = new String(readBuf, 0, message.arg1);
                    Log.i("Bluetooth activity", readMessage);
                    Point[] points = centroids(readMessage);
                    int pulseState = getPulseState(readMessage);
                    show(points, pulseState);
                    break;
            }
        }
    };
}
