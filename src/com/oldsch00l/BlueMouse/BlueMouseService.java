/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 *  This File was edited and further relicensed under GPL v3.
 *  Copyright (C) 2011 Rene Peinthor.
 *
 *  This file is part of BlueMouse.
 *
 *  BlueMouse is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  BlueMouse is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with BlueMouse.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oldsch00l.BlueMouse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for incoming
 * connections, a thread for connecting with a device, and a thread for
 * performing data transmissions when connected.
 */
public class BlueMouseService extends Service {
	// Debugging
	private static final String TAG = "BlueMouseService";

	// Name for the SDP record when creating server socket
	// private static final String NAME = "SPP slave";

	// Unique UUID for this application
	// private static final UUID MY_UUID =
	// UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //SPP uuid
	// private static final UUID MY_UUID =
	// UUID.fromString("551fb220-5329-11e0-b8af-0800200c9a66"); //SPP uuid

	// Member fields
	private final BluetoothAdapter mAdapter;
	private Handler mHandler = new Handler();
	private AcceptThread mAcceptThread;
	private List<ConnectedThread> mConnectedList;
	//private ConnectedThread mConnectedThread;
	private int mState;

	// private ConnectThread mConnectThread;

	private TimerTask mNMEARMCTask = new NMEARMCTask();
	private TimerTask mNMEAGGATask = new NMEAGGATask();

	// Timer stuff
	private Timer mTimer;

	// GPS stuff
	private LocationManager mLocationManager = null;
	private NmeaListener mNMEAListener = null;
	private LocationListener mLocationUpdateListener = null;

	// NMEA strings
	private String mCurRMCString = null;
	private String mCurGGAString = null;

	// cur location
	private Location mCurLocation = null;

	// Constants that indicate the current connection state
	public static final int STATE_NONE = 0; // we're doing nothing
	public static final int STATE_LISTEN = 1; // now listening for incoming
												// connections
	public static final int STATE_CONNECTING = 2; // now initiating an outgoing
													// connection
	public static final int STATE_CONNECTED = 3; // now connected to a remote
													// device
	public static final int STATE_DISCONNECTED = 4; // client disconnected

	public static final int NOTIFICATION_ID = 1;

	// Database Helper
	public static BlueMouseSQLite DBHelper;
	// Writeable Database
	public static SQLiteDatabase DB;

	/**
	 * Constructor. Prepares a new BluetoothChat session.
	 *
	 * @param context
	 *            The UI Activity Context
	 * @param handler
	 *            A Handler to send messages back to the UI Activity
	 */
	public BlueMouseService() {
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mState = STATE_NONE;
		mConnectedList = new ArrayList<BlueMouseService.ConnectedThread>();
	}

	/**
	 * Set the Handler from the BlueMouse activity so we see whats going on from
	 * the service.
	 *
	 * Only 1 Handler can be set.
	 *
	 * @param handler
	 *            handler to send messages to
	 */
	public void setHandler(Handler handler) {
		Log.d(TAG, "Handler set");
		mHandler = handler;
		updateUI();
	}

	/**
	 * Set the current state of the bluetooth connection
	 *
	 * @param state
	 *            An integer defining the current connection state
	 */
	private synchronized void setState(int state) {
		Log.d(TAG, "setState() " + mState + " -> " + state);
		mState = state;

		// Give the new state to the Handler so the UI Activity can update
		mHandler.obtainMessage(BlueMouse.MESSAGE_STATE_CHANGE, state, -1)
				.sendToTarget();
	}

	/**
	 * Return the current connection state.
	 */
	public synchronized int getState() {
		return mState;
	}

	/**
	 * Sends current status to the UI.
	 */
	public synchronized void updateUI() {
		// Send the name of the connected device back to the UI Activity
		getConnnectedDevices();

		setState(getState());
	}

	public void getConnnectedDevices() {
		Message msg = mHandler.obtainMessage(BlueMouse.MESSAGE_DEVICES);
		Bundle bundle = new Bundle();
		ArrayList<String> deviceNames = new ArrayList<String>();
		for(ConnectedThread conn : mConnectedList) {
			deviceNames.add(conn.getSocket().getRemoteDevice().getName());
		}
		bundle.putStringArrayList(BlueMouse.EXTRA_CONNECTED_DEVICES, deviceNames);
		msg.setData(bundle);
		mHandler.sendMessage(msg);
	}

	/**
	 * Start the ConnectThread to initiate a connection to a remote device.
	 *
	 * @param device
	 *            The BluetoothDevice to connect
	 */
	/*
	 * public synchronized void connect(BluetoothDevice device) { if (D)
	 * Log.d(TAG, "connect to: " + device);
	 *
	 * // Cancel any thread attempting to make a connection if (mState ==
	 * STATE_CONNECTING) { if (mConnectThread != null) {mConnectThread.cancel();
	 * mConnectThread = null;} }
	 *
	 * // Cancel any thread currently running a connection if (mConnectedThread
	 * != null) {mConnectedThread.cancel(); mConnectedThread = null;}
	 *
	 * // Start the thread to connect with the given device mConnectThread = new
	 * ConnectThread(device); mConnectThread.start();
	 * setState(STATE_CONNECTING); }
	 */

	/**
	 * Start the ConnectedThread to begin managing a Bluetooth connection
	 *
	 * @param socket
	 *            The BluetoothSocket on which the connection was made
	 * @param device
	 *            The BluetoothDevice that has been connected
	 */
	public synchronized void connected(BluetoothSocket socket,
			BluetoothDevice device) {
		Log.d(TAG, "connected " + device.getName());

		// Cancel the thread that completed the connection
		// if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread
		// = null;}

		// Cancel any thread currently running a connection
/*		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}*/

		// Add device to DB
		Cursor c = DB.query(BlueMouseSQLite.TABLE_DEVICES, null, "address=?", new String[] { device.getAddress() }, null, null, null);
		if(c.moveToFirst()) {
			ContentValues cv = new ContentValues();
			cv.put("last_connect", BlueMouseSQLite.SDateFormat.format(new Date()));
			DB.update(BlueMouseSQLite.TABLE_DEVICES, cv, "address=?", new String[] { device.getAddress() });
		} else {
			ContentValues cv = new ContentValues();
			cv.put("address", device.getAddress());
			cv.put("name", device.getName());
			cv.put("last_connect", BlueMouseSQLite.SDateFormat.format(new Date()));
			DB.insert(BlueMouseSQLite.TABLE_DEVICES, null, cv);
		}
		c.close();

		// Start the thread to manage the connection and perform transmissions
		ConnectedThread connection = new ConnectedThread(socket);
		connection.start();
		mConnectedList.add(connection);
		/*
		mConnectedThread = new ConnectedThread(socket);
		mConnectedThread.start();
		*/

		// Send the name of the connected device back to the UI Activity
		Message msg = mHandler.obtainMessage(BlueMouse.MESSAGE_DEVICE_CONNECTED);
		Bundle bundle = new Bundle();
		bundle.putString(BlueMouse.EXTRA_DEVICE_NAME, device.getName());
		msg.setData(bundle);
		mHandler.sendMessage(msg);

		if(getState() != STATE_CONNECTED) {
			setState(STATE_CONNECTED);

			mNMEAGGATask.cancel();
			mNMEARMCTask.cancel();
			mNMEAGGATask = new NMEAGGATask();
			mNMEARMCTask = new NMEARMCTask();
			if(mTimer != null)
				mTimer.cancel();
			mTimer = new Timer();
			mTimer.schedule(mNMEAGGATask, 0, 1000);
			mTimer.schedule(mNMEARMCTask, 0, 2500);
		}
	}

	/**
	 * Write to the ConnectedThread in an unsynchronized manner
	 *
	 * @param out
	 *            The bytes to write
	 * @see ConnectedThread#write(byte[])
	 */
	public void write(byte[] out) {
		if (out != null) {
			// Synchronize a copy of the ConnectedThread
			if(getState() != STATE_CONNECTED)
				return;

			for(ConnectedThread conn : mConnectedList) {
				conn.write(out);
			}
		}
	}

	/**
	 * Indicate that the connection attempt failed and notify the UI Activity.
	 */
	/*
	 * private void connectionFailed() { setState(STATE_LISTEN);
	 *
	 * // Send a failure message back to the Activity Message msg =
	 * mHandler.obtainMessage(BlueMouse.MESSAGE_TOAST); Bundle bundle = new
	 * Bundle(); bundle.putString("Toast", "Unable to connect device");
	 * msg.setData(bundle); mHandler.sendMessage(msg); }
	 */

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
	private void connectionLost(ConnectedThread conn) {
		Message msg = mHandler.obtainMessage(BlueMouse.MESSAGE_DEVICE_DISCONNECTED);
		Bundle bundle = new Bundle();
		bundle.putString(BlueMouse.EXTRA_DEVICE_NAME, conn.getSocket().getRemoteDevice().getName());
		msg.setData(bundle);
		mHandler.sendMessage(msg);

		mConnectedList.remove(conn);
		conn.cancel();

		if(mConnectedList.size() == 0) {
			if (mTimer != null) {
				mTimer.cancel();
			}
			mNMEAGGATask.cancel();
			mNMEARMCTask.cancel();
			setState(STATE_LISTEN);
		}

		// restart accept thread
		// synchronized (this) {
		// if(mAcceptThread != null) mAcceptThread.cancel();
		// mAcceptThread = new AcceptThread();
		// mAcceptThread.start();
		// }
	}

	/**
	 * This thread runs while listening for incoming connections. It behaves
	 * like a server-side client. It runs until a connection is accepted (or
	 * until cancelled).
	 */
	private class AcceptThread extends Thread {
		// The local server socket
		private final BluetoothServerSocket mmServerSocket;

		public AcceptThread() {
			BluetoothServerSocket tmp = null;

			// Create a new listening server socket
			try {
				// tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME,
				// MY_UUID);
				Method m = mAdapter.getClass().getMethod("listenUsingRfcommOn",
						new Class[] { int.class });
				tmp = (BluetoothServerSocket) m.invoke(mAdapter, 1);
			}
			// catch (IOException e) {
			// Log.e(TAG, "create() failed", e);
			// }
			catch (SecurityException e) {
				Log.e(TAG, "create() failed", e);
			} catch (NoSuchMethodException e) {
				Log.e(TAG, "create() failed", e);
			} catch (IllegalArgumentException e) {
				Log.e(TAG, "create() failed", e);
			} catch (IllegalAccessException e) {
				Log.e(TAG, "create() failed", e);
			} catch (InvocationTargetException e) {
				Log.e(TAG, "create() failed", e);
			} catch (Exception e) {
				Log.e(TAG, "create() failed", e);
			}

			mmServerSocket = tmp;
		}

		public void run() {
			// wait until we have a handler to the activity
			while (mHandler == null) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}
			if (mmServerSocket == null) {
				Log.e(TAG, "Unable to create rfcom on channel 1");
				return;
			}
			setState(STATE_LISTEN);
			Log.d(TAG, "BEGIN mAcceptThread" + this);
			setName("AcceptThread");
			BluetoothSocket socket = null;

			// Listen to the server socket if we're not connected
			while (true) {
				try {
					// This is a blocking call and will only return on a
					// successful connection or an exception
					socket = mmServerSocket.accept();
				} catch (IOException e) {
					Log.e(TAG, "accept() failed", e);
					break;
				}

				// If a connection was accepted
				if (socket != null) {
					synchronized (BlueMouseService.this) {
						switch (mState) {
						case STATE_LISTEN:
						case STATE_CONNECTING:
						case STATE_CONNECTED:
							// Situation normal. Start the connected thread.
							connected(socket, socket.getRemoteDevice());
							break;
						case STATE_NONE:
						//case STATE_CONNECTED:
							// Either not ready or already connected. Terminate
							// new socket.
							try {
								socket.close();
							} catch (IOException e) {
								Log.e(TAG, "Could not close unwanted socket", e);
							}
							break;
						}
					}
				}
			}
			Log.i(TAG, "END mAcceptThread");
		}

		public void cancel() {
			Log.d(TAG, "cancel " + this);
			try {
				if (mmServerSocket != null)
					mmServerSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of server failed", e);
			}
		}
	}

	/**
	 * This thread runs while attempting to make an outgoing connection with a
	 * device. It runs straight through; the connection either succeeds or
	 * fails.
	 *
	 * Currently not used
	 */
	/*
	 * private class ConnectThread extends Thread { private final
	 * BluetoothSocket mmSocket; private final BluetoothDevice mmDevice;
	 *
	 * public ConnectThread(BluetoothDevice device) { mmDevice = device;
	 * BluetoothSocket tmp = null;
	 *
	 * // Get a BluetoothSocket for a connection with the // given
	 * BluetoothDevice try { tmp =
	 * device.createRfcommSocketToServiceRecord(MY_UUID); } catch (IOException
	 * e) { Log.e(TAG, "create() failed", e); } mmSocket = tmp; }
	 *
	 * public void run() { Log.i(TAG, "BEGIN mConnectThread");
	 * setName("ConnectThread");
	 *
	 * // Always cancel discovery because it will slow down a connection
	 * mAdapter.cancelDiscovery();
	 *
	 * // Make a connection to the BluetoothSocket try { // This is a blocking
	 * call and will only return on a // successful connection or an exception
	 * mmSocket.connect(); } catch (IOException e) { connectionFailed(); //
	 * Close the socket try { mmSocket.close(); } catch (IOException e2) {
	 * Log.e(TAG, "unable to close() socket during connection failure", e2); }
	 * // Start the service over to restart listening mode
	 * BluetoothSerialService.this.start(); return; }
	 *
	 * // Reset the ConnectThread because we're done synchronized
	 * (BluetoothSerialService.this) { mConnectThread = null; }
	 *
	 * // Start the connected thread connected(mmSocket, mmDevice); }
	 *
	 * public void cancel() { try { mmSocket.close(); } catch (IOException e) {
	 * Log.e(TAG, "close() of connect socket failed", e); } } }
	 */

	/**
	 * This thread runs during a connection with a remote device. It handles all
	 * incoming and outgoing transmissions.
	 */
	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			Log.d(TAG, "create ConnectedThread");
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the BluetoothSocket input and output streams
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				Log.e(TAG, "temp sockets not created", e);
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run() {
			Log.d(TAG, "BEGIN mConnectedThread");
			byte[] buffer = new byte[1024];
			// int bytes;

			// Keep listening to the InputStream while connected
			while (true) {
				try {
					// Read from the InputStream
					/* bytes = */mmInStream.read(buffer);

					// Send the obtained bytes to the UI Activity
					// mHandler.obtainMessage(BlueMouse.MESSAGE_READ, bytes, -1,
					// buffer)
					// .sendToTarget();
				} catch (IOException e) {
					Log.e(TAG, "disconnected", e);
					try {
						mmInStream.close();
						mmOutStream.close();
						mmSocket.close();
					} catch (IOException e1) {
						Log.e(TAG, "socket close failed", e1);
					} finally{
						connectionLost(this);
					}
					break;
				}
			}
		}

		/**
		 * Write to the connected OutStream.
		 *
		 * @param buffer
		 *            The bytes to write
		 */
		public void write(byte[] buffer) {
			try {
				mmOutStream.write(buffer);
				mmOutStream.flush();

				// Share the sent message back to the UI Activity
				// mHandler.obtainMessage(BlueMouse.MESSAGE_WRITE, -1, -1,
				// buffer)
				// .sendToTarget();
			} catch (IOException e) {
				Log.e(TAG, "Exception during write", e);
			}
		}

		public void cancel() {
			try {
				mmInStream.close();
				mmOutStream.close();
				mmSocket.close();
				mConnectedList.remove(this);
			} catch (IOException e) {
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}

		public BluetoothSocket getSocket() {
			return mmSocket;
		}
	}

	private class NMEAGGATask extends TimerTask {

		@Override
		public void run() {
			String sGGAMsg = null;
			// if GPS provider isn't enabled and
			// we don't have an update from the NMEA listener
			// create our own GGA sentence from the current location
			// if available
			if (!mLocationManager
					.isProviderEnabled(LocationManager.GPS_PROVIDER)
					|| mCurGGAString == null) {
				if (mCurLocation != null) {
					sGGAMsg = NMEAHelper.getNMEAGGA(mCurLocation);
				}
			} else {
				sGGAMsg = mCurGGAString;
			}
			write(sGGAMsg.getBytes());
		}
	}

	private class NMEARMCTask extends TimerTask {

		@Override
		public void run() {
			String sRMCMsg = null;
			// if GPS provider isn't enabled and
			// we don't have an update from the NMEA listener
			// create our own RMC sentence from the current location
			// if available
			if (!mLocationManager
					.isProviderEnabled(LocationManager.GPS_PROVIDER)
					|| mCurRMCString == null) {
				if (mCurLocation != null) {
					sRMCMsg = NMEAHelper.getNMEARMC(mCurLocation);
				}
			} else {
				sRMCMsg = mCurRMCString;
			}
			Message message = mHandler.obtainMessage(BlueMouse.MESSAGE_UPDATE_LOC);
			Bundle bundle = new Bundle();
			bundle.putString(BlueMouse.EXTRA_CURRENT_LOC,
					String.format(
						"LAT: %.4f\nLONG: %.4f",
						mCurLocation.getLatitude(),
						mCurLocation.getLongitude()
					)
				);
			message.setData(bundle);
			mHandler.sendMessage(message);
			Log.d(TAG, sRMCMsg);
			write(sRMCMsg.getBytes());
		}

	}

	/**
	 * Class for clients to access. Because we know this service always runs in
	 * the same process as its clients, we don't need to deal with IPC.
	 */
	public class BluetoothSerialBinder extends Binder {
		BlueMouseService getService() {
			return BlueMouseService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	// This is the object that receives interactions from clients. See
	// RemoteService for a more complete example.
	private final IBinder mBinder = new BluetoothSerialBinder();

	private NotificationManager mNM;

	@Override
	public void onCreate() {
		// Init DB
		DBHelper = new BlueMouseSQLite(getBaseContext(), BlueMouseSQLite.NAME, null, BlueMouseSQLite.VERSION);
		DB = DBHelper.getWritableDatabase();

		// Get Managers
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		// Init location listeners
		mLocationUpdateListener = new LocationListener() {

			@Override
			public void onLocationChanged(Location location) {
				mCurLocation = new Location(location); // copy location
			}

			@Override
			public void onProviderDisabled(String provider) {
			}

			@Override
			public void onProviderEnabled(String provider) {
			}

			@Override
			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}
		};

		mNMEAListener = new NmeaListener() {

			@Override
			public void onNmeaReceived(long timestamp, String nmea) {
				if (nmea.startsWith("$GPRMC") && !nmea.startsWith("$GPRMC,,")) {
					mCurRMCString = nmea;
				} else {
					mCurRMCString = null;
				}
				if (nmea.startsWith("$GPGGA") && !nmea.startsWith("$GPGGA,,")) {
					mCurGGAString = nmea;
				} else {
					mCurGGAString = null;
				}
			}

		};

		// Display a notification about us starting. We put an icon in the
		// status bar.
		showNotification();
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, TAG + " destroyed");

		mLocationManager.removeUpdates(mLocationUpdateListener);
		mLocationManager.removeNmeaListener(mNMEAListener);

		// if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread
		// = null;}
		for(ConnectedThread conn : mConnectedList) {
			conn.cancel();
			mConnectedList.remove(conn);
		}
		/*
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		*/
		if (mAcceptThread != null) {
			mAcceptThread.cancel();
			mAcceptThread = null;
		}
		setState(STATE_NONE);

		// Close DB
		DB.close();

		// Cancel the notification
		mNM.cancel(NOTIFICATION_ID);
	}

	/**
	 * Start the Serial service. Specifically start AcceptThread to begin a
	 * session in listening (server) mode. Called by the Activity onResume()
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "start " + TAG);

		// Start the thread to listen on a BluetoothServerSocket
		if (mAcceptThread == null) {
			Log.d(TAG, "create new accept Thread");

			// Cancel any thread attempting to make a connection
			// if (mConnectThread != null) {mConnectThread.cancel();
			// mConnectThread = null;}

			// Cancel any thread currently running a connection
			/*
			if (mConnectedThread != null) {
				mConnectedThread.cancel();
				mConnectedThread = null;
			}
			*/

			mAcceptThread = new AcceptThread();
			mAcceptThread.start();

			Log.d(TAG, "request location updates");
			mLocationManager.requestLocationUpdates(
					LocationManager.NETWORK_PROVIDER, 2000, 0,
					mLocationUpdateListener);
			mLocationManager.requestLocationUpdates(
					LocationManager.GPS_PROVIDER, 2000, 0,
					mLocationUpdateListener);
			mLocationManager.addNmeaListener(mNMEAListener);
		}

		return START_STICKY;
	}

	/**
	 * Show a notification while this service is running.
	 */
	private void showNotification() {
		CharSequence text = "BlueMouse running...";

		// Set the icon, scrolling text and timestamp
		Notification notification = new Notification(
				R.drawable.notify_service_icon, text,
				System.currentTimeMillis());

		// The PendingIntent to launch our activity if the user selects this
		// notification
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, BlueMouse.class), 0);

		// Keep the notification there until the service is destroyed
		notification.flags |= Notification.FLAG_NO_CLEAR;

		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(this, getText(R.string.app_name), text,
				contentIntent);

		// Send the notification.
		mNM.notify(NOTIFICATION_ID, notification);
	}
}