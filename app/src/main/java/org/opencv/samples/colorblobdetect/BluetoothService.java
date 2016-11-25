package org.opencv.samples.colorblobdetect;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Created by chandna on 9-Sep-16.
 */
public class BluetoothService {

    private static final String TAG = BluetoothService.class.getSimpleName();

    // Member fields
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;

    private AcceptThread mAcceptThread;
    private ConnectedThread mConnectedThread;
    private ConnectThread mConnectThread;
    private int mState;

    private final String NAME = "Tactile Reader";
    private final UUID uuid = UUID.fromString("ff7b2473-5739-4f52-849f-ba46afd9a707");

    public BluetoothService(Context context, Handler handler) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = Constants.STATE_NONE;
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
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
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
        if(mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
        setState(Constants.STATE_LISTEN);
    }

    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);
        // cancel any thread attempting to make a connection
        if(mState == Constants.STATE_CONNECTING) {
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
        setState(Constants.STATE_CONNECTING);
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
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(Constants.STATE_CONNECTED);
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
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Bluetooth Service Stopped");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        setState(Constants.STATE_NONE);
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
            if(mState != Constants.STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // perform the write synchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI activity
     * */
    private void connectionFailed() {
        setState(Constants.STATE_LISTEN);

        //send a failure message back to the activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * Indicate that the connection was list and notify UI
     * */
    private void connectionLost() {
        setState(Constants.STATE_LISTEN);

        // send a failure message
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost ");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either succeeds
     * or fails
     * */
    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket temp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given bluetooth device
            try {
                temp = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                Log.e(TAG, "Socket Creation failed", e);
            }
            mmSocket = temp;
        }
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
                try {

                    Class<?> clazz = mmSocket.getRemoteDevice().getClass();
                    Class<?>[] paramTypes = new Class<?>[] {Integer.TYPE};
                    Method m = clazz.getMethod("createRfcommSocket", paramTypes);
                    Object[] params = new Object[] {Integer.valueOf(1)};
                    mmSocket = (BluetoothSocket) m.invoke(mmSocket.getRemoteDevice(), params);
                    Log.i("ConnectThread", "Connection Attempt 2");
                    mmSocket.connect();
                    Log.i("ConnectThread", "Connection Attempt 2 Succeeded");
                } catch (NoSuchMethodException e) {
                    Log.e(TAG, "No Such Method", e);
                } catch (IllegalAccessException e) {
                    Log.e(TAG, "No Such Method", e);
                } catch (InvocationTargetException e) {
                    Log.e(TAG, "No Such Method", e);
                } catch (IOException e) {
                    // unable to connect; close the socket and get out
                    Log.e("ConnectThread", "connection exception", e);
                    try {
                        Log.i("ConnectThread", "Closing Socket");
                        mmSocket.close();
                    } catch (IOException closeException) {
                        Log.e("BLUETOOTH", "Closing Socket", closeException);
                    }
                }
                return;
            }

            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }

            connected(mmSocket, mmDevice);

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

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            Log.i(TAG, "Attempting to get tmpIn");
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Getting tmpIn Failed", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "Begin mConnectedThread");
            byte[] buffer = new byte[1024]; // buffer store for stream
            int bytes; // buffer returned from read()

            // keep listening to instream
            while(true) {
                try {
                    // READ from InputStream
                    bytes = mmInStream.read(buffer);
                    mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    Log.e("BLUETOOTH", "Reading bytes from mmInStream failed", e);
                    break;
                }
            }
        }

        // call this from main activity to send data to the remote device
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);

                // share the sent message back to UI activity
                mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, bytes)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e("BLUETOOTH", e.toString());
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
            // keep listening until exception occurs or socket is returned
            while(mState != Constants.STATE_CONNECTED) {
                try {
                    Log.e("BLUETOOTH", "waiting for a client");
                    sock = mmServerSocket.accept();
                    if(sock!=null){
                        Log.e("BLUETOOTH", "non null socket acquired!!");
                    }else{
                        Log.e("BLUETOOTH","null socket acquired");
                    }
                } catch (IOException e) {
                    Log.e("BLUETOOTH", "accept() failed", e);
                    break;
                }
                Log.i("BLUETOOTH", "got out of accept()...");
                // if connection accepted
                if(sock != null) {

                    synchronized (BluetoothService.this) {
                        switch(mState) {
                            case Constants.STATE_LISTEN:
                            case Constants.STATE_CONNECTING:
                                Log.e("BLUETOOTH", "device found: "+sock.getRemoteDevice().getName());
                                // situation normal. start connected thread
                                connected(sock, sock.getRemoteDevice());
                                break;
                            case Constants.STATE_NONE:
                            case Constants.STATE_CONNECTED:
                                // either not ready or laready connected. terminate new socket
                                try {
                                    sock.close();
                                } catch (IOException e) {
                                    Log.e("BLUETOOTH", "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                    try{
                        mmServerSocket.close();
                    }catch(IOException e){
                        Log.e("BLUETOOTH", "IOexception while closing the server socket");
                        e.printStackTrace();
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
