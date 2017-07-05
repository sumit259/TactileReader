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
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.core.Point;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.opencv.samples.colorblobdetect.ColorBlobDetectionActivity.ffState;
import static org.opencv.samples.colorblobdetect.ColorBlobDetectionActivity.ppState;
import static org.opencv.samples.colorblobdetect.ColorBlobDetectionActivity.pulsedPolygon;

public class ShowCentroidsActivity extends Activity {

    private static final String TAG = "ShowCentroidsActivity";

    // member object for service
    private BluetoothService mBluetoothService = null;
    // local bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;

    String filename;
    SharedPreferences sp;
    private TextToSpeech tts;

    private Utility mUtility;

    private List<Integer> blackCentroidsX;
    private List<Integer> blackCentroidsY;
    private int fingerCentroidX;
    private int fingerCentroidY;
    public int pulsedPolygon = -1;
    private int previousState = -1;

    // variables for play pause, fast forward,
    public static int fastForwardText = 2;
    public static int fastForwardAudio = 5000; // in milliseconds
    public static int ppState = -1;
    public static int ffPrevState = -1;
    private int currSpeak;
    private int currSpeakAudio;
    private boolean inPolygon = false;
    private boolean isOnlineMode;
    private boolean cloudAlertGiven = false;

    final String envpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Tactile Reader";

    TextView corner1, corner2, finger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_centroids);

        Log.i(TAG, "in onCreate of ShowCentroidsActivity");

        sp = PreferenceManager.getDefaultSharedPreferences(this);
        filename = sp.getString("context_name", null);

        Log.i(TAG, "about to parse " + filename);
        mUtility = new Utility(getApplicationContext());
        isOnlineMode = getIntent().getExtras().getBoolean("is_online_mode");
        mUtility.setIsOnlineMode(isOnlineMode);
        mUtility.parseFile(filename);

        corner1 = (TextView)findViewById(R.id.Corner1);
        corner2 = (TextView)findViewById(R.id.Corner2);
        finger  = (TextView)findViewById(R.id.fingerCentroid);

        // bluetooth initialisation
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null){
            Log.i("Bluetooth service", "Bluetooth not available on this device");
        }
        else if(!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, Constants.REQUEST_ENABLE_BT_RECEIVER);
        }else {
            if (mBluetoothService == null) {
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
//                    tts.setLanguage(Locale.ENGLISH);
                    tts.setLanguage(new Locale("hin", "IND", "variant"));
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

    public void onDestroy() {
        Log.i(TAG, "in onDestroy of ShowCentroidsActivity");
        super.onDestroy();
        tts.stop();
        if(mBluetoothService != null)
            mBluetoothService.stop();
        tts.stop();
        mUtility.stopAudio();
    }

    // Returns size of screen in pixels
    public android.graphics.Point getScreenDimensions() {
        Display display = getWindowManager().getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point();
        display.getSize(size);
        return size;
    }

    void show(Point[] centroids, int pulseState, int ppState, int ffState){
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

        if(isOnlineMode && !mUtility.isDataLoaded()){
            Log.wtf("WIKI", "thand rakh");
            String speakStr = "data download in progress, please wait";
            tts.speak(speakStr, TextToSpeech.QUEUE_FLUSH, null, ""+-1);
        }

        Log.i("CENTROIDS", "Finger: " + fingerCentroidX + " " + fingerCentroidY);
        Log.i("CENTROIDS", "Blob1: " + blackCentroidsX.get(0) + " " + blackCentroidsY.get(0));
        Log.i("CENTROIDS", "Blob2: " + blackCentroidsX.get(1) + " " + blackCentroidsY.get(1));
        android.graphics.Point pt = getScreenDimensions();
        Log.i("CENTROIDS", "Size: " + pt.x + " " + pt.y);

//        // tts from centroids
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
                    if(ffState == 2) {
                        ffState = 0;
                        Log.i("Fast_Forward","Fast forwarding by " + fastForwardText);
                        fastForward(pulsedPolygon, fastForwardText, ppState);
                    }
                    Log.i("Fast_Forward", "ffState: " + ffState);

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
                        String speakStr = mUtility.titles.get(i);
                        Log.i("PULSE", "toSpeak: " + speakStr);
//                        speakOut(speakStr, getApplicationContext());
                        tts.speak(speakStr, TextToSpeech.QUEUE_FLUSH, null, "" + -1);
                    }
                    if(ppState != 1 && pulseState == 0) { // pause
                        Log.i("Play_Pause", "Pausing pulsedPolygon = " + pulsedPolygon + " with currSpeak = " + currSpeak +" " +
                                "and currSpeakAudio = " + currSpeakAudio);
                        if(mUtility.isSpeaking()) {
                            Log.i("Play_Pause", "Pausing MediaPlayer");
                            pauseAudio(pulsedPolygon);
                        }
                        if(tts.isSpeaking()) {
                            Log.i("Play_Pause", "Pausing TTS with currSpeak = " + currSpeak);
                            pauseTTS(pulsedPolygon, currSpeak);
                        }
                    }
                    if(pulseState == 2 && previousState == i) {
                        pulseState = 0;
                        final List<String> toDescribeList = mUtility.descriptionStatements.get(pulsedPolygon);
                        Log.i(TAG, "Starting to Speak. ppState: " + ppState + " toDescribeList.size = " + toDescribeList.size());
                        currSpeak = mUtility.lastLocation.get(pulsedPolygon);
                        currSpeakAudio = mUtility.lastLocationAudio.get(pulsedPolygon);
                        Log.i("PLAY_PAUSE", "Speaking with pulsedPolygon = " + pulsedPolygon + " currSpeak = " + currSpeak);
                        if(currSpeak >= toDescribeList.size()-1) {
                            Log.i("Play_Pause", "Resetting currSpeak to 0 from " + currSpeak);
                            currSpeak = 0;
                        }
                        if(ppState != 1) {
                            Log.i(TAG, "About to pause speech at pulsedPolygon = " + pulsedPolygon + " and currSpeak = " + currSpeak);
                            if(tts.isSpeaking())
                                pauseTTS(pulsedPolygon, currSpeak);
                            if(mUtility.isSpeaking())
                                pauseAudio(pulsedPolygon);
                        }
                        for(currSpeak = mUtility.lastLocation.get(pulsedPolygon); currSpeak < toDescribeList.size(); currSpeak++) {
                            if(ppState == 1) {
                                String toDescribe = toDescribeList.get(currSpeak);
                                if(toDescribe.startsWith("$AUDIO$")) {
                                    Log.i(TAG, "Speaking from currSpeakAudio = " + currSpeakAudio);
                                    mUtility.playAudio(envpath + File.separator + filename, toDescribe, currSpeakAudio);
                                    Log.wtf("MTP", "parsing: " + envpath + "/" + toDescribe);
                                } else {
                                    Log.i(TAG, "Speaking from currSpeak = " + currSpeak);
                                    Log.i(TAG, "toDescribe: " + toDescribe);
                                    mUtility.changeLastLocation(pulsedPolygon, currSpeak);
                                    if(!mUtility.mp.isPlaying()) {
                                        final String speakStr = toDescribe;
                                        tts.speak(speakStr, TextToSpeech.QUEUE_ADD, null, ""+currSpeak);
                                    }
                                }
                            }
                        }
                        Log.i(TAG, "Speech Over. currSpeak = " + currSpeak);
                        if(currSpeak >= toDescribeList.size() - 1) {
                            currSpeak = 0;
                            mUtility.changeLastLocation(pulsedPolygon, currSpeak);
                        }
                    }
//                    if (pulseState == 2 && previousState == i) {
//                        pulseState = 0;
//                        final String toDescribe = mUtility.descriptions.get(pulsedPolygon);
//                        Log.i("PULSE", "pulse detected... toDescribe: " + toDescribe);
//                        if (toDescribe.startsWith("$AUDIO$")) {
//                            mUtility.playAudio(envpath + File.separator + filename, toDescribe, 0);
//                            Log.wtf("MTP", "parsing: " + envpath + "/" + toDescribe);
//                        } else {
//                            Log.i(TAG, "toDescribe: " + toDescribe);
//                            speakOut(toDescribe, getApplicationContext());
//                        }
//                    }
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

    private void fastForward(int pulsedPolygon, int numStatements, int ppState) {
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
            Log.i("Fast Forwarding", "Entering new for loop with ppState = " + ppState);
            for(; currSpeak < toDescribeList.size(); currSpeak++) {
                Log.i("Fast Forwarding", "In the new for loop with ppState = " + ppState);
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
        } else {
            Log.i("Fast Forward", "TTS not speaking. Cannot fast forward.");
        }
        if(mUtility.isSpeaking()) {
            mUtility.fastForwardAudio(fastForwardAudio);
        }

    }



    private Point normalizePoint(Point P) {
        double scalingFactor;

        // Find screen dist
        double dy1 = (double) blackCentroidsY.get(1).intValue() - (double) blackCentroidsY.get(0).intValue();
        double dx1 = (double) blackCentroidsX.get(1).intValue() - (double) blackCentroidsX.get(0).intValue();
        double screenDist = Math.pow(dx1*dx1 + dy1*dy1, 0.5);

        double dy2 = mUtility.Corners[1].y - mUtility.Corners[0].y;
        double dx2 = mUtility.Corners[1].x - mUtility.Corners[0].x;
        double tagDist = Math.pow(dx2*dx2 + dy2*dy2, 0.5);
        scalingFactor = tagDist / screenDist;

        double xDash = P.x * scalingFactor;
        double yDash = P.y * scalingFactor;

        double theta = Math.acos((dx1*dx2+dy1*dy2)/(tagDist*screenDist));

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

    private void ensureDiscoverable() {
        if(mBluetoothAdapter.getScanMode()!=BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE){
            Log.i(TAG, "device was not discoverable....");
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivityForResult(discoverableIntent, Constants.REQUEST_MAKE_DISCOVERABLE);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Constants.REQUEST_ENABLE_BT_RECEIVER:
                if (resultCode == Activity.RESULT_OK) {
                    ensureDiscoverable();
                } else {
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Bluetooth was not enabled", Toast.LENGTH_SHORT).show();
                }
                break;
            case Constants.REQUEST_MAKE_DISCOVERABLE:
                if (resultCode == Activity.RESULT_OK) {
                    // start AcceptThread if not running already
                    // 1. if mBluetoothService is null
                    // 2. if mBluetoothService is not null but it was stopped sometime before
                    if(mBluetoothService == null) {
                        mBluetoothService = new BluetoothService(this, mHandler);
                        mBluetoothService.start();
                    }else if(mBluetoothService.getState()==Constants.STATE_NONE){
                        mBluetoothService.start();
                    }
                } else {
                    Log.d(TAG, "make discoverable request denied");
                    Toast.makeText(this, "Bluetooth device was not made discoverable", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    public static Point[] centroids(String str){
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

    public static int getPulseState(String str) {
        String[] points = str.split("\\+");
        int len = points.length;
        if(len < 6)
            return -2;
        String pulse = points[3];
        int id = pulse.indexOf(":");
        pulse = pulse.substring(id+1);
        return Integer.valueOf(pulse);
    }

    public static int getPPState(String str) {
        String[] points = str.split("\\+");
        int len = points.length;
        if(len < 6)
            return -2;
        String pulse = points[4];
        int id = pulse.indexOf(":");
        pulse = pulse.substring(id+1);
        return Integer.valueOf(pulse);
    }

    public static int getFFState(String str) {
        String[] points = str.split("\\+");
        int len = points.length;
        if(len < 6)
            return -2;
        String pulse = points[5];
        int id = pulse.indexOf(":");
        pulse = pulse.substring(id+1);
        String[] splits = pulse.split("\n");
        pulse = splits[0];
        return Integer.valueOf(pulse);
    }

    //     the Handler that gets information back from the BluetoothService
    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch(message.what) {
                case Constants.MESSAGE_TOAST:
                    String msg = message.getData().getString(Constants.TOAST);
                    Toast.makeText(ShowCentroidsActivity.this, msg, Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) message.obj;
                    String readMessage = new String(readBuf, 0, message.arg1);
                    Log.i("Bluetooth activity", readMessage);
                    Point[] points = centroids(readMessage);
                    int pulseState = getPulseState(readMessage);
                    int ppState = getPPState(readMessage);
                    int ffState = getFFState(readMessage);
                    if(pulseState!=-2) {
                        Log.i(TAG, "Calling show with pulseState = " + pulseState + ", ppState = " + ppState + ", ffState = " + ffState);
                        show(points, pulseState, ppState, ffState);
                    }
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    String mConnectedDeviceName = message.getData().getString(Constants.DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
}
