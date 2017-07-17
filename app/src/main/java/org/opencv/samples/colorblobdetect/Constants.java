package org.opencv.samples.colorblobdetect;

/**
 * Created by sumit on 25-Sep-16.
 */

public interface Constants {

    // Constants for current connection state
    int STATE_NONE = 0;         // doing nothing
    int STATE_LISTEN = 1;       // listening for incoming connections
    int STATE_CONNECTING = 2;   // initiating an outgoing connection
    int STATE_CONNECTED = 3;    // connected to a remote device

    // Message types sent from the handler
    int MESSAGE_STATE_CHANGE = 1;
    int MESSAGE_READ = 2;
    int MESSAGE_WRITE = 3;
    int MESSAGE_DEVICE_NAME = 4;
    int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothService Handler
    String DEVICE_NAME = "device_name";
    String TOAST = "toast";

    // Bluetooth modes
    int BLUETOOTH_MODE_ON = 1;
    int BLUETOOTH_MODE_OFF = 2;

    // App working mode
    int SCAN_AND_READ = 1;
    int SCAN_AND_SEND = 2;
    int RECEIVE_AND_READ = 3;

    String EXTRA_DEVICE_ADDRESS = "device_address";

    // Intent request codes
    int REQUEST_CONNECT_DEVICE = 1;
    int REQUEST_ENABLE_BT_SENDER = 2;
    int REQUEST_ENABLE_BT_RECEIVER = 3;
    int REQUEST_MAKE_DISCOVERABLE = 4;

    // External permissions request codes
    int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    int MY_PERMISSIONS_REQUEST_OPEN_CAMERA = 2;
    int MY_PERMISSIONS_REQUEST_START_SCAN_BLUETOOTH = 3;
}
