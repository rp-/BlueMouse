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

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
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
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	// Key names received from the BlueMouseService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "Toast";

	// Intent request codes
	// private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	// relase commands
	private static final String FOCUS_CAMERA = "$PFOOR,0,1*45\r\n";
	private static final String PRESS_SHUTTER = "$PFOOR,1,1*44\r\n";
	private static final String RELEASE_SHUTTER = "$PFOOR,0,0*44\r\n";

	// Layout Views
	private TextView mTitle;
	private Button mRelaseCameraButton;

	// Map stuff
	private MapController mMapController;
	private MapView mMapView;
	private MyLocationOverlay mLocationOverlay;

	// Name of the connected device
	private String mConnectedDeviceName = null;
	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the chat services
	private BlueMouseService mSerialService = null;

	// Timer stuff
	private Timer mTimer;
	private TimerTask mNMEARMCTask = new NMEARMCTask();
	private TimerTask mNMEAGGATask = new NMEAGGATask();

	// GPS stuff
	private LocationManager mLocationManager = null;

	// NMEA strings
	private String mCurRMCString = null;
	private String mCurGGAString = null;

	// cur location
	private Location mCurLocation = null;

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

		// map activity
		mMapView = (MapView) findViewById(R.id.mapview);
		mMapView.setBuiltInZoomControls(true);

		mMapController = mMapView.getController();
		mMapController.setZoom(14);

		mLocationOverlay = new MyLocationOverlay(this, mMapView);
		mMapView.getOverlays().add(mLocationOverlay);
		mLocationOverlay.enableCompass();

		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		requestLocationUpdates();

		doBindService();
	}

	private void requestLocationUpdates() {
		mLocationOverlay.enableMyLocation();
		mLocationManager.requestLocationUpdates(
				LocationManager.NETWORK_PROVIDER, 2000, 0,
				mLocationUpdateListener);
		mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				2000, 0, mLocationUpdateListener);
		mLocationManager.addNmeaListener(mNMEAListener);
	}

	private void stopLocationUpdates() {
		mLocationOverlay.disableCompass();
		mLocationOverlay.disableMyLocation();
		mLocationManager.removeUpdates(mLocationUpdateListener);
		mLocationManager.removeNmeaListener(mNMEAListener);
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

	private void startBlueMouseService() {
		Log.d(TAG, "setupBlueMouse()");
		// Initialize the BluetoothSerialService to perform bluetooth
		// connections
		startService(new Intent(this, BlueMouseService.class));
	}

	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onStop() {
		super.onStop();
	}

	@Override
	public void onDestroy() {
		// Unbind the BlueMouseService
		doUnbindService();
		super.onDestroy();
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

	public static SimpleDateFormat LogDate = new SimpleDateFormat("HH:mm:ss");

	// The Handler that gets information back from the BluetoothSerialService
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				Log.d(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);

				switch (msg.arg1) {
				case BlueMouseService.STATE_CONNECTED: {
					mTitle.setText(getString(R.string.title_connected_to,
							mConnectedDeviceName));
					mTimer = new Timer();
					mNMEAGGATask.cancel();
					mNMEARMCTask.cancel();
					mNMEAGGATask = new NMEAGGATask();
					mNMEARMCTask = new NMEARMCTask();
					mTimer.schedule(mNMEAGGATask, 0, 1000);
					mTimer.schedule(mNMEARMCTask, 0, 2500);
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
				case BlueMouseService.STATE_DISCONNECTED: {
					if (mTimer != null) {
						mTimer.cancel();
					}
				}
					break;
				}
			case MESSAGE_DEVICE_NAME: {
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				if (mConnectedDeviceName != null) {
					if (mConnectedDeviceName.startsWith("Unleashed"))
						mConnectedDeviceName = mConnectedDeviceName.substring(
								0, mConnectedDeviceName.lastIndexOf(' '));
					Toast.makeText(getApplicationContext(),
							"Connected to " + mConnectedDeviceName,
							Toast.LENGTH_SHORT).show();
				}
			}
				break;
			case MESSAGE_TOAST: {
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
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

	NmeaListener mNMEAListener = new NmeaListener() {

		@Override
		public void onNmeaReceived(long timestamp, String nmea) {
			// TODO Auto-generated method stub
			// if(D) Log.v(TAG, nmea);
			if (nmea.startsWith("$GPRMC")) {
				mCurRMCString = nmea;
			} else if (nmea.startsWith("$GPGGA")) {
				mCurGGAString = nmea;
			}
		}

	};

	private LocationListener mLocationUpdateListener = new LocationListener() {
		private boolean zoomToMe = true;

		public void onLocationChanged(Location location) {

			if (zoomToMe) {
				int latE6 = (int) (location.getLatitude() * 1E6);
				int lonE6 = (int) (location.getLongitude() * 1E6);
				mMapController.animateTo(new GeoPoint(latE6, lonE6));
				zoomToMe = false;
			}

			mCurLocation = new Location(location); // copy location
		}

		@Override
		public void onProviderDisabled(String provider) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onProviderEnabled(String provider) {
			zoomToMe = true;
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			// TODO Auto-generated method stub

		}
	};

	/**
	 * Creates a NMEA checksum for a sentence.
	 *
	 * The checksum is calculated by XOR every char value, between '$' and
	 * '*'(end), with the current sum.
	 *
	 * @param sbString
	 *            String to calculate the checksum.
	 * @return The checksum.
	 */
	public static int getNMEAChecksum(final StringBuilder sbString) {
		int checksum = 0;

		for (int i = 0; i < sbString.length(); i++) {
			if (sbString.charAt(i) != '*' && sbString.charAt(i) != '$')
				checksum ^= sbString.charAt(i);
		}
		return checksum;
	}

	public static DecimalFormat locFormat = new DecimalFormat("0000.####");
	public static DecimalFormat shortFormat = new DecimalFormat("##.#");

	public static SimpleDateFormat HHMMSS = new SimpleDateFormat("HHmmss.000",
			Locale.UK);

	public static SimpleDateFormat DDMMYY = new SimpleDateFormat("ddMMyy",
			Locale.UK);
	static {
		HHMMSS.setTimeZone(TimeZone.getTimeZone("GMT"));
		DDMMYY.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	/**
	 * Creates a valid NMEA GGA Global Positioning System Fix Data.
	 *
	 * Example:
	 * $GPGGA,191410,4735.5634,N,00739.3538,E,1,04,4.4,351.5,M,48.0,M,,*45
	 *
	 * @param loc
	 *            object to transfer into a GGA sentence.
	 * @return The GGA sentence as String.
	 */
	public static String getNMEAGGA(final Location loc) {
		StringBuilder sbGPGGA = new StringBuilder();

		char cNorthSouth = loc.getLatitude() >= 0 ? 'N' : 'S';
		char cEastWest = loc.getLongitude() >= 0 ? 'E' : 'W';

		Date curDate = new Date();
		sbGPGGA.append("$GPGGA,");
		sbGPGGA.append(HHMMSS.format(curDate));
		sbGPGGA.append(',');
		sbGPGGA.append(getCorrectPosition(loc.getLatitude()));
		sbGPGGA.append(",");
		sbGPGGA.append(cNorthSouth);
		sbGPGGA.append(',');
		sbGPGGA.append(getCorrectPosition(loc.getLongitude()));
		sbGPGGA.append(',');
		sbGPGGA.append(cEastWest);
		sbGPGGA.append(',');
		sbGPGGA.append('1'); // quality
		sbGPGGA.append(',');
		Bundle bundle = loc.getExtras();
		int satellites = bundle.getInt("satellites", 5);
		sbGPGGA.append(satellites);
		sbGPGGA.append(',');
		sbGPGGA.append(',');
		if (loc.hasAltitude())
			sbGPGGA.append(shortFormat.format(loc.getAltitude()));
		sbGPGGA.append(',');
		sbGPGGA.append('M');
		sbGPGGA.append(',');
		sbGPGGA.append(',');
		sbGPGGA.append('M');
		sbGPGGA.append(',');
		sbGPGGA.append("*");
		int checksum = getNMEAChecksum(sbGPGGA);
		sbGPGGA.append(java.lang.Integer.toHexString(checksum));
		sbGPGGA.append("\r\n");

		return sbGPGGA.toString();
	}

	/**
	 * Returns the correct NMEA position string.
	 *
	 * Android location object returns the data in the format that is not
	 * excpected by the NMEA data set. We have to multiple the minutes and
	 * seconds by 60.
	 *
	 * @param degree
	 *            value from the Location.getLatitude() or
	 *            Location.getLongitude()
	 * @return The correct formated string for a NMEA data set.
	 */
	public static String getCorrectPosition(double degree) {
		double val = degree - (int) degree;
		val *= 60;

		val = (int) degree * 100 + val;
		return locFormat.format(Math.abs(val));
	}

	/**
	 * Creates a valid NMEA RMC Recommended Minimum Sentence C.
	 *
	 * Example:
	 * $GPRMC,053117.000,V,4812.7084,N,01619.3522,E,0.14,237.29,070311,,,N*76
	 *
	 * @param loc
	 *            object to transfer into a RMC sentence.
	 * @return The RMC sentence as String.
	 */
	public static String getNMEARMC(final Location loc) {
		// $GPRMC,053117.000,V,4812.7084,N,01619.3522,E,0.14,237.29,070311,,,N*76
		StringBuilder sbGPRMC = new StringBuilder();

		char cNorthSouth = loc.getLatitude() >= 0 ? 'N' : 'S';
		char cEastWest = loc.getLongitude() >= 0 ? 'E' : 'W';

		Date curDate = new Date();
		sbGPRMC.append("$GPRMC,");
		sbGPRMC.append(HHMMSS.format(curDate));
		sbGPRMC.append(",A,");
		sbGPRMC.append(getCorrectPosition(loc.getLatitude()));
		sbGPRMC.append(",");
		sbGPRMC.append(cNorthSouth);
		sbGPRMC.append(",");
		sbGPRMC.append(getCorrectPosition(loc.getLongitude()));
		sbGPRMC.append(',');
		sbGPRMC.append(cEastWest);
		sbGPRMC.append(',');
		// sbGPRMC.append(location.getSpeed());
		sbGPRMC.append(",");
		sbGPRMC.append(shortFormat.format(loc.getBearing()));
		sbGPRMC.append(",");
		sbGPRMC.append(DDMMYY.format(curDate));
		sbGPRMC.append(",,,");
		sbGPRMC.append("A");
		sbGPRMC.append("*");
		int checksum = getNMEAChecksum(sbGPRMC);
		sbGPRMC.append(java.lang.Integer.toHexString(checksum));
		// if(D) Log.v(TAG, sbGPRMC.toString());
		sbGPRMC.append("\r\n");

		return sbGPRMC.toString();
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	private class NMEAGGATask extends TimerTask {

		@Override
		public void run() {
			byte[] msg = null;
			// if GPS provider isn't enabled and
			// we don't have an update from the NMEA listener
			// create our own GGA sentence from the current location
			// if available
			if (!mLocationManager
					.isProviderEnabled(LocationManager.GPS_PROVIDER)
					|| mCurGGAString == null) {
				if (mCurLocation != null) {
					String sGGA = getNMEAGGA(mCurLocation);
					msg = sGGA.getBytes();
				}
			} else {
				msg = mCurGGAString.getBytes();
			}
			mSerialService.write(msg);
		}
	}

	private class NMEARMCTask extends TimerTask {

		@Override
		public void run() {
			byte[] msg = null;
			// if GPS provider isn't enabled and
			// we don't have an update from the NMEA listener
			// create our own RMC sentence from the current location
			// if available
			if (!mLocationManager
					.isProviderEnabled(LocationManager.GPS_PROVIDER)
					|| mCurRMCString == null) {
				if (mCurLocation != null) {
					String sRMC = getNMEARMC(mCurLocation);
					msg = sRMC.getBytes();
				}
			} else {
				msg = mCurRMCString.getBytes();
			}
			mSerialService.write(msg);
		}

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
