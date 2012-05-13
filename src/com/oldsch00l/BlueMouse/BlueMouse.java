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

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;

/**
 * This is the main Activity that displays the current chat session.
 */
public class BlueMouse extends MapActivity {
	// Debugging
	private static final String TAG = "BlueMouse";

	// Message types sent from BlueMouseService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_CONNECTED = 4;
	public static final int MESSAGE_TOAST = 5;
	public static final int MESSAGE_UPDATE_LOC = 6;
	public static final int MESSAGE_DEVICES = 7;
	public static final int MESSAGE_DEVICE_DISCONNECTED = 8;

	// Key names received from the BlueMouseService Handler
	public static final String TOAST = "Toast";

	public static final String EXTRA_DEVICE_NAME = "device_name";
	public static final String EXTRA_CURRENT_LOC = "CURRENT_LOC";
	public static final String EXTRA_CONNECTED_DEVICES = "CONNECTED_DEVICES";

	// Intent request codes
	// private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	// relase commands
	private static final String FOCUS_CAMERA = "$PFOOR,0,1*45\r\n";
	private static final String PRESS_SHUTTER = "$PFOOR,1,1*44\r\n";
	private static final String RELEASE_SHUTTER = "$PFOOR,0,0*44\r\n";

	// Layout Views
	private TextView mTitle;
	private TextView mTVLocation;
	private Button mRelaseCameraButton;

	// Map stuff
	private MapController mMapController;
	private MapView mMapView;
	private MyLocationOverlay mLocationOverlay;

	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the chat services
	private BlueMouseService mSerialService = null;

	// List of connected device names
	private List<String> mConnectedDevices = new ArrayList<String>();

	// Timer stuff
	private Timer mTimer = new Timer();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Set up the window layout
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.main);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,
				R.layout.custom_title);

		// release button action
		mRelaseCameraButton = (Button) findViewById(R.id.button_release_camera);
		mRelaseCameraButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				releaseCamera();
			}
		});

		// Set up the custom title
		mTitle = (TextView) findViewById(R.id.title_left_text);
		mTitle.setText(R.string.app_name);
		mTitle = (TextView) findViewById(R.id.title_right_text);

		// Current location Textview
		mTVLocation = (TextView) findViewById(R.id.tvCurrentLoc);

		// map activity
		mMapView = (MapView) findViewById(R.id.mapview);
		mMapView.setBuiltInZoomControls(true);

		mMapController = mMapView.getController();
		mMapController.setZoom(14);

		mLocationOverlay = new MyLocationOverlay(this, mMapView);
		mMapView.getOverlays().add(mLocationOverlay);

		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		requestLocationUpdates();

		doBindService();
	}

	private void requestLocationUpdates() {
		mLocationOverlay.enableCompass();
		mLocationOverlay.enableMyLocation();
	}

	private void stopLocationUpdates() {
		mLocationOverlay.disableCompass();
		mLocationOverlay.disableMyLocation();
	}

	private boolean mIsBound;
	private ServiceConnection mServiceCon = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mSerialService = ((BlueMouseService.BluetoothSerialBinder) service)
					.getService();
			mSerialService.setHandler(mHandler);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mSerialService = null;
		}
	};

	void doBindService() {
		// Establish a connection with the service. We use an explicit
		// class name because we want a specific service implementation that
		// we know will be running in our own process (and thus won't be
		// supporting component replacement by other applications).
		bindService(new Intent(BlueMouse.this, BlueMouseService.class),
				mServiceCon, Context.BIND_AUTO_CREATE);
		mIsBound = true;
	}

	void doUnbindService() {
		if (mIsBound) {
			// Detach our existing connection.
			unbindService(mServiceCon);
			mIsBound = false;
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		Log.e(TAG, "++ ON START ++");

		// If BT is not on, request that it be enabled.
		// setupChat() will then be called during onActivityResult
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		} else {
			startBlueMouseService();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		requestLocationUpdates();

		// Performing this check in onResume() covers the case in which BT was
		// not enabled during onStart(), so we were paused to enable it...
		// onResume() will be called when ACTION_REQUEST_ENABLE activity
		// returns.
		startBlueMouseService();
	}

	@Override
	public void onPause() {
		stopLocationUpdates();
		super.onPause();
	}

	@Override
	public void onStop() {
		stopLocationUpdates();
		super.onStop();
	}

	@Override
	public void onDestroy() {
		// Unbind the BlueMouseService
		stopLocationUpdates();
		doUnbindService();
		super.onDestroy();
	}

	private void startBlueMouseService() {
		Log.d(TAG, "startBlueMouseService()");
		// Initialize the startBlueMouseService to perform bluetooth
		// connections
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		int channel = -1;
		if( sp.getBoolean("forcechannel", false) )
		{
			channel = Integer.parseInt(sp.getString("portnumber", "1"));
		}
		Intent i = new Intent(this, BlueMouseService.class);
		i.putExtra(BlueMouseService.EXTRA_CHANNEL, channel);
		startService(i);
	}

	private void ensureDiscoverable() {
		Log.d(TAG, "ensure discoverable");
		if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent discoverableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(
					BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivity(discoverableIntent);
		}
	}

	/**
	 * Will send the correct codes to release the camera.
	 *
	 * Right now this is very basic and just a proof of concept.
	 *
	 * focus camera (half press): $PFOOR,0,1*45<CR><LF>
	 *
	 * press shutter (full press): $PFOOR,1,1*44<CR><LF>
	 *
	 * release shutter: $PFOOR,0,0*44<CR><LF>
	 */
	private void releaseCamera() {
		if (mSerialService.getState() != BlueMouseService.STATE_CONNECTED) {
			Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT)
					.show();
			return;
		}

		mTimer.schedule(new SendStringTask(FOCUS_CAMERA), 0);

		mTimer.schedule(new SendStringTask(PRESS_SHUTTER), 1000);

		mTimer.schedule(new SendStringTask(RELEASE_SHUTTER), 1500);
	}

	// public static SimpleDateFormat LogDate = new
	// SimpleDateFormat("HH:mm:ss");

	private static String stripUnleashedAddress(String sDeviceName) {
		String sStripped = sDeviceName;
		if (sDeviceName != null) {
			if (sDeviceName.startsWith("Unleashed"))
			{
				 sStripped = sDeviceName.substring(
						0, sDeviceName.lastIndexOf(' '));
			}
		}
		return sStripped;
	}

	private void updateTitle() {
		String sTitle = getString(R.string.title_not_connected);
		if(mConnectedDevices.size() > 0) {
			sTitle = getString(R.string.title_connected_to) + " ";
			sTitle += stripUnleashedAddress(mConnectedDevices.get(0));
			for(int i = 1; i < mConnectedDevices.size(); i++) {
				sTitle += ", " + stripUnleashedAddress(mConnectedDevices.get(i));
			}
		}
		mTitle.setText(sTitle);
	}

	// The Handler that gets information back from the BluetoothSerialService
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE: {
					Log.d(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);

					switch (msg.arg1) {
						case BlueMouseService.STATE_CONNECTED: {
						}
						break;
						case BlueMouseService.STATE_CONNECTING: {
							mTitle.setText(R.string.title_connecting);
						}
						break;
						case BlueMouseService.STATE_LISTEN:
						case BlueMouseService.STATE_NONE: {
							mTitle.setText(R.string.title_not_connected);
						}
						break;
					}
				}
				break;
			case MESSAGE_DEVICES: {
					mConnectedDevices = msg.getData().getStringArrayList(EXTRA_CONNECTED_DEVICES);
					updateTitle();
				}
				break;
			case MESSAGE_TOAST: {
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
			}
				break;
			case MESSAGE_UPDATE_LOC: {
				String sLoc = msg.getData().getString(EXTRA_CURRENT_LOC);
				mTVLocation.setText(sLoc);
			}
				break;
			case MESSAGE_DEVICE_CONNECTED: {
					String sDevice = msg.getData().getString(EXTRA_DEVICE_NAME);
					mConnectedDevices.add(sDevice);
					updateTitle();
					Toast.makeText(getBaseContext(), String.format("Connected to %s.",
							sDevice), Toast.LENGTH_SHORT).show();
				}
				break;

			case MESSAGE_DEVICE_DISCONNECTED: {
					String sDevice = msg.getData().getString(EXTRA_DEVICE_NAME);
					mConnectedDevices.remove(sDevice);
					updateTitle();
					Toast.makeText(getBaseContext(), String.format("Connection to %s lost.",
							sDevice), Toast.LENGTH_SHORT).show();
				}
				break;
			}
		}
	};

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		/*
		 * case REQUEST_CONNECT_DEVICE: // When DeviceListActivity returns with
		 * a device to connect if (resultCode == Activity.RESULT_OK) { // Get
		 * the device MAC address String address = data.getExtras()
		 * .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS); // Get the
		 * BLuetoothDevice object BluetoothDevice device =
		 * mBluetoothAdapter.getRemoteDevice(address); // Attempt to connect to
		 * the device mSerialService.connect(device); } break;
		 */
		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth is now enabled, we can start the service
				startBlueMouseService();
			} else {
				// User did not enable Bluetooth or an error occured
				Log.d(TAG, "BT not enabled");
				Toast.makeText(this, R.string.bt_not_enabled_leaving,
						Toast.LENGTH_SHORT).show();
				stopService(new Intent(this, BlueMouseService.class));
				finish();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.discoverable: {
			// Ensure this device is discoverable by others
			ensureDiscoverable();
			return true;
		}
		case R.id.menu_settings: {
			Intent settingsActivity = new Intent(getBaseContext(),
                    Preferences.class);
			startActivity(settingsActivity);
			return true;
		}
		case R.id.menu_exit: {
			stopLocationUpdates();
			stopService(new Intent(this, BlueMouseService.class));
			finish();
			return true;
		}
		}
		return false;
	}

	public void zoomToPosition(View v) {
		GeoPoint location = mLocationOverlay.getMyLocation();
		if (location != null) {
			mMapController.animateTo(location);
		} else {
			Toast.makeText(this, getString(R.string.currently_no_location),
					Toast.LENGTH_SHORT);
		}
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	private class SendStringTask extends TimerTask {
		private String mString = "";

		public SendStringTask(String sData) {
			mString = sData;
		}

		@Override
		public void run() {
			byte[] msg = mString.getBytes();
			mSerialService.write(msg);
		}

	}
}
