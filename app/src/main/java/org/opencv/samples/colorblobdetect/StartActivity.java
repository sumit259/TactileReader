package org.opencv.samples.colorblobdetect;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.app.Activity;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.opencv.samples.colorblobdetect.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class StartActivity extends Activity {

    Spinner spinner;
    Button newCon, scan;
    SharedPreferences sp;
    TextView colortv;
    RadioGroup or;



//    String envpath = Environment.getDataDirectory().getPath() + File.separator + "Tactile Reader";
    String envpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Tactile Reader";


    public BluetoothAdapter mBluetoothAdapter;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private BluetoothServerSocket mServerSocket;
    private BluetoothSocket mBluetoothSocket;
    private static final String NAME = "StartActivity";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        sp = PreferenceManager.getDefaultSharedPreferences(this);

        spinner = (Spinner)findViewById(R.id.spinner);

        ArrayList<String> filenames = new ArrayList<>();
        Log.wtf("MTP", "Path: " + envpath);
        File f = new File(envpath);
        Log.d("MTP", "Dir read 1");

        if (!f.exists()) {
            f.mkdir();
            Log.wtf("MTP", "MKDIR executed");
        }

        File files[] = f.listFiles();
        if(files!=null){
            Log.d("MTP", "Size: "+ files.length);
            for (int i=0; i < files.length; i++) {
                filenames.add(files[i].getName());
                Log.d("MTP", "FileName:" + files[i].getName());
            }
        }


        if(filenames==null){
            filenames.add(0, "No saved Contexts");
        }
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, filenames);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        spinner.setAdapter(dataAdapter);

        colortv = (TextView)findViewById(R.id.colortv);
        colortv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(StartActivity.this, SelectColorActivity.class);
                startActivity(i);
                finish();
            }
        });

        JSONArray savedColor = new JSONArray();
        try {
            savedColor = new JSONArray(sp.getString("touched_color_rgba", "[]"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if(!(savedColor==null || savedColor.length()==0)){
            try {
                colortv.setBackgroundColor(Color.argb(savedColor.getInt(3), savedColor.getInt(0), savedColor.getInt(1), savedColor.getInt(2)));
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        int checkedOr = sp.getInt("orientation", 1);

        or = (RadioGroup)findViewById(R.id.orient);
        Utility.orientation = or.indexOfChild(findViewById(checkedOr)) + 1;
        or.check(checkedOr);
        or.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {

                int index = group.indexOfChild(findViewById(group.getCheckedRadioButtonId())) + 1;
                Log.wtf("OCVSample::Activity", "Orientation: " + index);

                sp.edit().putInt("orientation", checkedId).apply();
                Utility.orientation = index;

                Log.wtf("MTP", "orient: " + Utility.getOrientation());

//                Log.wtf("OCVSample::Activity", "Orientation: " + Utility.orientation);



            }
        });

        newCon = (Button)findViewById(R.id.newbutton);
        newCon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StartActivity.this, FirstActivity.class);
                startActivity(intent);
                finish();
            }
        });

        scan = (Button)findViewById(R.id.scanbutton);
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit().putString("context_name", spinner.getSelectedItem().toString()).apply();
                Log.wtf("MTP", "context_name: " + sp.getString("context_name", "nonon"));
                Intent intent = new Intent(StartActivity.this, ColorBlobDetectionActivity.class);
                startActivity(intent);
            }
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Log.i("BLUTOOTH", "Bluetooth Starting");

        // bluetooth initialization
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // device does not support bluetooth. exit.
            Log.e("error", "Bluetooth Device not Supported");
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        // this works if the phone is paired only with the required device. UNPAIR ALL DEVICES!!!
        // TODO correct this
        BluetoothDevice mDevice = null;
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        Log.i("BLUETOOTH", "Paired Devices Size = " + pairedDevices.size());
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                mDevice = device;
            }
            mConnectThread = new ConnectThread(mDevice);
            mConnectThread.start();
            try {
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                Log.e("THREAD", "Error in sleeping");
                e.printStackTrace();
            }
            Log.i("BLUETOOTH", "Connection Established");
        }
        String fingerCentroidStr = "anda";
        try {
            Log.i("RANDOM", mConnectedThread.toString());
            mConnectedThread.write(fingerCentroidStr);
            Log.i("BLUETOOTH", "Finger coordinates sent: " + fingerCentroidStr);
        } catch (IOException e) {
            Log.e("BLUETOOTH", "Error Sending Coordinates!");
            e.printStackTrace();
        }
//        try {
//            Log.i("BLUETOOTH", "Creating conected thread");
//            mServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
//            Log.i("BLUETOOTH", "Server socket connected");
//            mBluetoothSocket = mServerSocket.accept(30000);
//            Log.i("BLUETOOTH", "Bluetooth Socket created");
//
//            Log.i("BLUETOOTH", "Adapter selected");
//        } catch (IOException e) {
//            e.printStackTrace();
//            Log.e("BLUETOOTH", "Error selecting adapter");
//        }

    }


    // TODO make runnable
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        // TODO find uif of app
        private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
            }
            mmSocket = tmp;
        }

        public void run() {
            mBluetoothAdapter.cancelDiscovery();
            Log.i("DEBUG", "test");
            try {
                Log.i("DEBUG", "test1");
                mmSocket.connect();
                Log.i("DEBUG", "test2");
                mConnectedThread = new ConnectedThread(mmSocket);
                Log.i("BLUETOOTH", "mconnected thread: " + mConnectedThread.toString());
            } catch (IOException connectException) {
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    // exception
                }
                return;
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                // exception
            }
        }
    }

    // TODO make runnable
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                // exception handling
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void write(String s) throws IOException {
            mmOutStream.write(s.getBytes());
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int begin = 0;
            int bytes = 0;
            while (true) {
                try {
                    bytes = mmInStream.read(buffer, bytes, buffer.length - bytes);
                    mHandler.obtainMessage(1);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("error", "close() of connect socket failed", e);
            }
        }
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            byte[] writeBuf = (byte[]) msg.obj;
            int begin = (int)msg.arg1;
            int end = (int)msg.arg2;

            switch(msg.what) {
                case 1:
                    String writeMessage = new String(writeBuf);
                    writeMessage = writeMessage.substring(begin, end);
                    break;
            }
        }
    };


}
