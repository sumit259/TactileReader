package org.opencv.samples.colorblobdetect;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;


public class StartActivity extends Activity {

    Spinner spinner;
    Button newCon, scan;
    Button listen;
    TextView colortv;
    TextView colortvpp;
    TextView colortvff;
    TextView status_connection;
    RadioGroup or;
    Button make_discoverable;
    ToggleButton toggleOnlineMode;

    // online mode
    boolean isOnlineMode;

    // bluetooth mode is on or off
    int bluetoothMode;

    // points to a value out of 3 modes
    int appRunningMode;

    String envpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Tactile Reader";

    // local bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // member object for service
//    private BluetoothService mBluetoothService = null;
    // to be connected Bluetooth device address
    private String mBluetoothDeviceAddress = null;

    private static final String TAG = "OCVSample::Activity";

    SharedPreferences sp;

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

        // selecting color of tags for reading
        colortv = (TextView)findViewById(R.id.colortv);
        colortv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(StartActivity.this, SelectColorActivity.class);
                i.putExtra("isMain", true);
                i.putExtra("isFF", false);
                Log.i(TAG, "Starting SelectColorActivity with isMain = true");
                startActivity(i);
                finish();
            }
        });

//      // colortv.setContentDescription("tag color");
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
        // selecting color of tag for play/pause
        colortvpp = (TextView)findViewById(R.id.colortvpp);
        colortvpp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(StartActivity.this, SelectColorActivity.class);
                i.putExtra("isMain", false);
                i.putExtra("isFF", false);
                Log.i(TAG, "Starting SelectColorActivity with isMain = false");
                startActivity(i);
                finish();
            }
        });
        JSONArray savedColorPP = new JSONArray();
        try {
            savedColorPP = new JSONArray(sp.getString("touched_color_rgba_pp", "[]"));
        } catch(JSONException e) {
            e.printStackTrace();
        }
        if(!(savedColorPP==null || savedColorPP.length()==0)) {
            try {
                colortvpp.setBackgroundColor(Color.argb(savedColorPP.getInt(3), savedColorPP.getInt(0), savedColorPP.getInt(1), savedColorPP.getInt(2)));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        // selecting color for fast forward
        colortvff = (TextView)findViewById(R.id.colortvff);
        colortvff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(StartActivity.this, SelectColorActivity.class);
                i.putExtra("isMain", false);
                i.putExtra("isFF", true);
                startActivity(i);
                finish();
            }
        });
        JSONArray savedColorFF = new JSONArray();
        try {
            savedColorFF = new JSONArray(sp.getString("touched_color_rgba_ff", "[]"));
        } catch(JSONException e) {
            e.printStackTrace();
        }
        if(!(savedColorFF==null || savedColorFF.length()==0)) {
            try {
                colortvff.setBackgroundColor(Color.argb(savedColorFF.getInt(3), savedColorFF.getInt(0), savedColorFF.getInt(1), savedColorFF.getInt(2)));
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

        toggleOnlineMode = (ToggleButton) findViewById(R.id.toggleButton);
        toggleOnlineMode.setChecked(false);
        toggleOnlineMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isOnlineMode = isChecked;
                Log.i("WIKI", "checked change: " + isChecked);
            }
        });

        status_connection = (TextView)findViewById(R.id.status_connect);
        status_connection.setText("");

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
                Intent intent;
                switch(appRunningMode){
                    case Constants.SCAN_AND_READ:
                        // TODO: work as self cam, open ColorBlobDetectionActivity with tts variable set
                        Log.i(TAG, "opening colorBlobDetectionActivity with TTS enabled");
                        intent = new Intent(StartActivity.this, ColorBlobDetectionActivity.class);
                        intent.putExtra("is_bluetooth", false);
                        intent.putExtra("is_online_mode", isOnlineMode);
                        startActivity(intent);
                        break;
                    case Constants.SCAN_AND_SEND:
                        // TODO: work as sender, open ColorBlobDetectionActivity with tts variable unset
                        Log.i(TAG, "opening colorBlobDetectionActivity with TTS disabled");
                        intent = new Intent(StartActivity.this, ColorBlobDetectionActivity.class);
                        intent.putExtra("is_bluetooth", true);
                        intent.putExtra("is_online_mode", isOnlineMode);
                        Log.i(TAG, "address being sent: " + mBluetoothDeviceAddress);
                        intent.putExtra("bluetoothAddress", mBluetoothDeviceAddress);
                        startActivity(intent);
                        break;
                    case Constants.RECEIVE_AND_READ:
                        // TODO: work as receiver, open ShowCentroidsActivity
                        Log.i(TAG, "opening ShowCentroidsActivity");
                        intent = new Intent(StartActivity.this, ShowCentroidsActivity.class);
                        intent.putExtra("is_online_mode", isOnlineMode);
                        startActivity(intent);
                        break;
                }
            }
        });

        listen = (Button)findViewById(R.id.button_listen);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null){
            Log.i("Bluetooth service", "Bluetooth not available on this device");
            listen.setVisibility(View.INVISIBLE);
            listen.setEnabled(false);
        }

        make_discoverable = (Button)findViewById(R.id.discoverable);
        make_discoverable.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                ensureDiscoverable();
            }
        });

        bluetoothMode = Constants.BLUETOOTH_MODE_OFF;
        appRunningMode = Constants.SCAN_AND_READ;
        listen.setText(R.string.turn_bt_on);
        final Context context = this;
        listen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(bluetoothMode == Constants.BLUETOOTH_MODE_OFF) {
                    final CharSequence[] choice = {"sender", "receiver"};
                    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
                    alertBuilder.setTitle("set mode");
                    alertBuilder.setSingleChoiceItems(choice, -1, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if(choice[which]=="sender"){
                                status_connection.setText(R.string.status_connected_sender);
                                bluetoothMode = Constants.BLUETOOTH_MODE_ON;
                                appRunningMode = Constants.SCAN_AND_SEND;
                                Log.i(TAG, "Now Connecting");
                                enableBluetoothAsSender();
                                listen.setText(R.string.turn_bt_off);
                            }else{
                                status_connection.setText(R.string.status_connected_receiver);
                                bluetoothMode = Constants.BLUETOOTH_MODE_ON;
                                appRunningMode = Constants.RECEIVE_AND_READ;
                                enableBluetoothAsReceiver();
                                listen.setText(R.string.turn_bt_off);
                            }
                            dialog.cancel();
                        }
                    });
                    AlertDialog alert = alertBuilder.create();
                    alert.show();
                }
                else {
                    bluetoothMode = Constants.BLUETOOTH_MODE_OFF;
                    appRunningMode = Constants.SCAN_AND_READ;
                    listen.setText(R.string.turn_bt_on);
                    status_connection.setText("");
                }
//                Intent intent = new Intent(StartActivity.this, ShowCentroidsActivity.class);
//                startActivity(intent);
            }
        });
    }

    private void selectAndConnectToDevice(){
        Log.i(TAG, "opening SelectDeviceActivity");
        Intent intent = new Intent(StartActivity.this, SelectDeviceActivity.class);
        startActivityForResult(intent, Constants.REQUEST_CONNECT_DEVICE);
    }

    private void enableBluetoothAsSender(){
        if(!mBluetoothAdapter.isEnabled()) {
            Log.i(TAG, "bluetooth was not enabled.. turning it on");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, Constants.REQUEST_ENABLE_BT_SENDER);
        }else {
//            if(mBluetoothService == null) {
                selectAndConnectToDevice();
//            }
        }
    }

    private void enableBluetoothAsReceiver(){
        if(!mBluetoothAdapter.isEnabled()) {
            Log.i(TAG, "bluetooth was not enabled.. turning it on");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, Constants.REQUEST_ENABLE_BT_RECEIVER);
        }else {
//            if(mBluetoothService == null) {
                ensureDiscoverable();
//            }
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
            case Constants.REQUEST_CONNECT_DEVICE:
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(Constants.EXTRA_DEVICE_ADDRESS);
                    Log.i(TAG, "attempting to make a connection with " + address);
                    mBluetoothDeviceAddress = address;
                }
                break;
            case Constants.REQUEST_ENABLE_BT_RECEIVER:
                if (resultCode == Activity.RESULT_OK) {
                    ensureDiscoverable();
                } else {
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Bluetooth was not enabled", Toast.LENGTH_SHORT).show();
                }
                break;
            case Constants.REQUEST_ENABLE_BT_SENDER:
                // TODO: start scan activity, start bluetooth service and connect to the device         --> done
                if (resultCode == Activity.RESULT_OK) {
                    selectAndConnectToDevice();
                } else {
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Bluetooth was not enabled", Toast.LENGTH_SHORT).show();
                }
                break;
            case Constants.REQUEST_MAKE_DISCOVERABLE:
                // TODO: start bluetooth service and run AcceptThread           --> done
                // start AcceptThread if not running already
                // 1. if mBluetoothService is null
                // 2. if mBluetoothService is not null but it was stopped sometime before
//                if(mBluetoothService == null) {
//                    mBluetoothService = new BluetoothService(this, mHandler);
//                    mBluetoothService.start();
//                }else if(mBluetoothService.getState()==Constants.STATE_NONE){
//                    mBluetoothService.start();
//                }
                break;
        }
    }


}
