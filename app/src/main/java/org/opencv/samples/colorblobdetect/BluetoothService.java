package org.opencv.samples.colorblobdetect;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

/**
 * Created by chandan on 9/9/16.
 */

/**
 * This class does all the work for setting up and managing Bluetooth connections
 * with other devices. It has threads listening for incoming connections, a thread
 * for connecting with a device, and a thread for performing data transmissions when
 * connected. This has been taken from https://android.googlesource.com/platform/development/+/eclair-passion-release/samples/BluetoothChat/src/com/example/android/BluetoothChat/BluetoothChatService.java
 * */
public class BluetoothService {

    private static final String TAG = "BluetoothService";

    // Member fields
    private final BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice;
    private BluetoothSocket socket;
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectedThread mConnectedThread;
    private ConnectThread mConnectThread;
    private int mState;

    // Constants for current connection state
    public static final int STATE_NONE = 0;         // doing nothing
    public static final int STATE_LISTEN = 1;       // listening for incoming connections
    public static final int STATE_CONNECTING = 2;   // initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;    // connected to a remote device

    // Message types sent from the handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;


    private final String NAME = "Tactile Reader";
    private final UUID uuid = UUID.fromString("ff7b2473-5739-4f52-849f-ba46afd9a707");

    public BluetoothService(Context context, Handler handler) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    /**
     * Set the current state of the chat connection
     * @param state An integer defining the current connection state
     * */
    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        //give new state to the Handler
        mHandler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state
     * */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the BLuetoothService. Starts AcceptThread to begin a session
     * in listening (server) mode. Called by Activity onResume() {???}
     * */
    public synchronized  void start() {
        Log.d(TAG, "start()");

        //cancel any thread attempting to make a connection
        if(mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        // cancel any thread currently running a connecion
        if(mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        // start thread to listen on BluetoothServerSocket
//        if(mAcceptThread == null) {
//            mAcceptThread = new AcceptThread();
//            mAcceptThread.start();
//        }
//        setState(STATE_LISTEN);

    }

    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);
        // cancel accept thread
        if(mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        // cancel any thread attempting to make a connection
        if(mState == STATE_CONNECTING) {
            if(mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }
        // cancel any thread currently running a connection
        if(mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        // start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket the BluetoothSocket on which connection was made
     * @param device the BluetoothDevice that has been connected
     * */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "Bluetooth Device Connected");

        //cancel the thread that completed the connection
        if(mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        // cancel the accept thread because we only want to connect to one device
        if(mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        //send the name of the connected device back to the UI activity
        Message msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString("DEVICE_NAME", device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    // stop all threads
    public synchronized void stop() {
        Log.d(TAG, "stop");
        if(mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if(mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if(mAcceptThread != null) {
            mAcceptThread.cancel();;
            mAcceptThread = null;
        }
        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     * */
    public void write(byte[] out) {
        // create temp object
        ConnectedThread r;
        synchronized (this) {
            if(mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // perform the write synchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI activity
     * */
    private void connectionFailed() {
        setState(STATE_LISTEN);

        //send a failure message back to the activity
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("TOAST", "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * Indicate that the connection was list and notify UI
     * */
    private void connectionLost() {
        setState(STATE_LISTEN);

        // send a failure message
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("TOAST", "Device connetion was lost ");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either succeesds
     * or fails
     * */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket temp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given bluetooth device
            try {
                temp = device.createRfcommSocketToServiceRecord(uuid);
//                temp = (BluetoothSocket)
//                        mmDevice.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(mmDevice,1);
            }catch (IOException e) {
                Log.e(TAG, "Socket Creation failed", e);
            }

            mmSocket = temp;
        }
//
//        public void run() {
//            Log.i("ConnectThread", "BEGIN Connect Thread");
//            setName("ConnectThread");
//
//            // to avoid slow connection
//            mBluetoothAdapter.cancelDiscovery();
//
//            try {
//                Log.i("ConnectThread", "Connection Attempt 1");
//                // connect device through socket. this will block until succeeded or exception
//                mmSocket.connect();
//                Log.i("ConnectThread", "Connection Attempt 1 Succeeded\nMMSOCKET = "+mmSocket.toString() );
//            } catch (IOException connectException) {
//                Log.e(TAG, "Connection failed in first attempt", connectException);
//                try {
//                    Class<?> clazz = mmSocket.getRemoteDevice().getClass();
//                    Class<?>[] paramTypes = new Class<?>[] {Integer.TYPE};
//                    Method m = clazz.getMethod("createRfcommSocket", paramTypes);
//                    Object[] params = new Object[] {Integer.valueOf(1)};
//                    mmSocket = (BluetoothSocket) m.invoke(mmSocket.getRemoteDevice(), params);
//                    Log.i("ConnectThread", "Connection Attempt 2");
//                    mmSocket.connect();
//                    Log.i("ConnectThread", "Connection Attempt 2 Succeeded");
//                } catch (NoSuchMethodException e) {
//                    Log.e(TAG, "No Such Method", e);
//                } catch (IllegalAccessException e) {
//                    Log.e(TAG, "No Such Method", e);
//                } catch (InvocationTargetException e) {
//                    Log.e(TAG, "No Such Method", e);
//                } catch (IOException e) {
//                    // unable to connect; close the socket and get out
//                    Log.e("ConnectThread", "connection exception", e);
//                    try {
//                        Log.i("ConnectThread", "Closing Socket");
//                        mmSocket.close();
//                    } catch (IOException closeException) {
//                        Log.e("BLUETOOTH", "Closing Socket", closeException);
//                    }
//                }
//                connected(mmSocket, mmDevice);
////                }
////                BluetoothService.this.start();
//                return;
//            }
//
//            synchronized (BluetoothService.this) {
//                mConnectThread = null;
//            }
//
//            // manageConnectedSocket(mmsocket);
//
//        }



        public void run() {
            Log.i("ConnectThread", "BEGIN Connect Thread");
            setName("ConnectThread");

            // to avoid slow connection
            mBluetoothAdapter.cancelDiscovery();

            try {
                Log.i("ConnectThread", "Connection Attempt 1");
                // connect device through socket. this will block until succeeded or exception
                mmSocket.connect();
                Log.i("ConnectThread", "Connection Attempt 1 Succeeded\nMMSOCKET = "+mmSocket.toString() );
            } catch (IOException connectException) {
                Log.e(TAG, "Connection failed in first attempt", connectException);
                // unable to connect; close the socket and get out
                Log.e("ConnectThread", "connection exception", connectException);
                try {
                    Log.i("ConnectThread", "Closing Socket");
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e("BLUETOOTH", "Closing Socket", closeException);
                }
//                }
//                BluetoothService.this.start();
                return;
            }

            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }

            connected(mmSocket, mmDevice);

            // manageConnectedSocket(mmsocket);

        }




        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("BLUETOOTH", e.toString());
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private Handler mHandler;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d("BLUETOOTH", "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // get instr and outstr, using temp obj because mmstr are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e("BLUETOOTH", e.toString());
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i("BLUETOOTH", "Begin mConnectedThread");
            byte[] buffer = new byte[1024]; // buffer store for stream
            int bytes; // buffer returned from read()

            // keep listening to instream
            while(true) {
//                try {
//                    // READ from InputStream
////                    bytes = mmInStream.read(buffer);
//                    // handle the received message
////                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
//                } catch (IOException e) {
//                    Log.e("BLUETOOTH", e.toString());
//                    break;
//                }
            }
        }

        // call this from main activity to send data to the remote device
        public void write(byte[] bytes) {
            try {
                if(mmOutStream == null) {
                    Log.e(TAG, "mmOutStream is null in ConnectedThread.write()");
                }
                mmOutStream.write(bytes);

                // share the sent message back to UI activity
//                mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, bytes)
//                        .sendToTarget();
            } catch (IOException e) {
                Log.e("ConnectedThread", "Bluetooth Not Connected", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("BLUETOOTH", e.toString());
            }
        }
    }

    // for server : starts listening for connection
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            Log.e(TAG, "AcceptThread Constructor called");
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket temp = null;
            try {
                temp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, uuid);
            }
            catch (IOException e) {
                Log.e("BLUETOOTH", "listen() failed", e);
            }
            mmServerSocket = temp;
        }

        public void run() {
            BluetoothSocket sock = null;
//            socket = null;
            // keep listening until exception occurs or socket is returned
            while(mState != STATE_CONNECTED) {
                try {
                    sock = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e("BLUETOOTH", "accept() failed", e);
                    break;
                }
                // if connection accepted
                if(socket != null) {

                    synchronized (BluetoothService.this) {
                        switch(mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // situation normal. start connected thread
                                connected(sock, sock.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // either not ready or laready connected. terminate new socket
                                try {
                                    sock.close();
                                } catch (IOException e) {
                                    Log.e("BLUETOOTH", "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
        }

        public void cancel() {
            Log.d("BLUETOOTH", "Cancel mAcceptThread");
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e("BLUETOOTH", "Can't cancel mAcceptThread", e);
            }
        }
    }


}
