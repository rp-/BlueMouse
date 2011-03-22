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

package com.oldsch00l.BlueMouse;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
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
    private static final boolean D = true;

    // Message types sent from the BluetoothSerialService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothSerialService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // Layout Views
    private TextView mTitle;
    private EditText mOutEditText;
    private Button mSendButton;
    
    // Map stuff
    private MapController mMapController;
    private MapView mMapView;
	private MyLocationOverlay mLocationOverlay;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothSerialService mSerialService = null;
    
    //GPS stuff
    LocationManager mLocationManager = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);

        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);
        
		//map activity
		mMapView = (MapView)findViewById(R.id.mapview);
		mMapView.setBuiltInZoomControls(true);

		mMapController = mMapView.getController();
		mMapController.setZoom(14);

		mLocationOverlay = new MyLocationOverlay(this, mMapView);
		mMapView.getOverlays().add(mLocationOverlay);
		mLocationOverlay.enableCompass();

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0, mLocationUpdateListener);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0, mLocationUpdateListener);
        //mLocationManager.addNmeaListener(mNMEAListener);
        
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (mSerialService == null) setupChat();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        mLocationOverlay.enableMyLocation();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mSerialService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mSerialService.getState() == BluetoothSerialService.STATE_NONE) {
              // Start the Bluetooth chat services
              mSerialService.start();
            }
        }
    }

    private void setupChat() {
        Log.d(TAG, "setupChat()");
        // Initialize the send button with a listener that for click events
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            }
        });

        // Initialize the BluetoothSerialService to perform bluetooth connections
        mSerialService = new BluetoothSerialService(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    @Override
    public synchronized void onPause() {
    	mLocationOverlay.disableMyLocation();
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        mLocationManager.removeUpdates(mLocationUpdateListener);
        mLocationManager.removeNmeaListener(mNMEAListener);
        if (mSerialService != null) mSerialService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mSerialService.getState() != BluetoothSerialService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothSerialService to write
            byte[] send = message.getBytes();
            mSerialService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    // The Handler that gets information back from the BluetoothSerialService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothSerialService.STATE_CONNECTED:
                    mTitle.setText(getString(R.string.title_connected_to, mConnectedDeviceName));
                    break;
                case BluetoothSerialService.STATE_CONNECTING:
                    mTitle.setText(R.string.title_connecting);
                    break;
                case BluetoothSerialService.STATE_LISTEN:
                case BluetoothSerialService.STATE_NONE:
                    mTitle.setText(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mSerialService.connect(device);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupChat();
            } else {
                // User did not enable Bluetooth or an error occured
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
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
        case R.id.scan:
            // Launch the DeviceListActivity to see devices and do scan
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            return true;
        case R.id.discoverable:
            // Ensure this device is discoverable by others
            ensureDiscoverable();
            return true;
        }
        return false;
    }
    
	public static SimpleDateFormat HHMMSS =
		new SimpleDateFormat("HHmmss.000");
	
	public static SimpleDateFormat DDMMYY =
		new SimpleDateFormat("ddMMyy");
    
	NmeaListener mNMEAListener = new NmeaListener(){

        @Override
        public void onNmeaReceived(long timestamp, String nmea) {
            // TODO Auto-generated method stub
       		//if(D) Log.v(TAG, nmea);
       		mSerialService.write(nmea.getBytes());
        }

    };
	
    private LocationListener mLocationUpdateListener = new LocationListener() {
    	private boolean zoomToMe = true;
    	
		public void onLocationChanged(Location location) {
			 
			if(zoomToMe) {
				int latE6 = (int)(location.getLatitude() * 1E6);
				int lonE6 = (int)(location.getLongitude() * 1E6);
				mMapController.animateTo(new GeoPoint(latE6, lonE6));
				zoomToMe = false;
			}
			
			String sGPRMC = getNMEARMC(location);
			byte[] msg = sGPRMC.getBytes();
			mSerialService.write(msg);
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

	public static int getNMEAChecksum(final StringBuilder sbString) {
		int checksum = 0;

		for (int i = 0; i < sbString.length(); i++) {
			if(sbString.charAt(i) != '*' && sbString.charAt(i) != '$')
				checksum ^= sbString.charAt(i);
		}
		return checksum;
	}
	
	public static String getNMEARMC(final Location loc) {
   		//$GPRMC,053117.000,V,4812.7084,N,01619.3522,E,0.14,237.29,070311,,,N*76
   		StringBuilder sbGPRMC = new StringBuilder();
   		DecimalFormat locFormat = new DecimalFormat("####.####");
   		
   		char cNorthSouth = loc.getLatitude() >= 0 ? 'N' : 'S';
   		char cEastWest = loc.getLongitude() >= 0 ? 'E' : 'W';
   		
   		DecimalFormat shortFormat = new DecimalFormat("##.#");
   		
   		sbGPRMC.append("$GPRMC,");
   		sbGPRMC.append(HHMMSS.format(new Date(loc.getTime())));
   		sbGPRMC.append(",A,");
   		sbGPRMC.append(locFormat.format(loc.getLatitude()));
   		sbGPRMC.append(",");
   		sbGPRMC.append(cNorthSouth);
   		sbGPRMC.append(",");
   		sbGPRMC.append(locFormat.format(loc.getLongitude()));
   		sbGPRMC.append(',');
   		sbGPRMC.append(cEastWest);
   		sbGPRMC.append(',');
   		//sbGPRMC.append(location.getSpeed());
   		sbGPRMC.append(",");
   		sbGPRMC.append(shortFormat.format(loc.getBearing()));
   		sbGPRMC.append(",");
   		sbGPRMC.append(DDMMYY.format(new Date(loc.getTime())));
   		sbGPRMC.append(",,,");
   		sbGPRMC.append("A");
   		sbGPRMC.append("*");
   		int checksum = getNMEAChecksum(sbGPRMC);
   		sbGPRMC.append(java.lang.Integer.toHexString(checksum));
   		//if(D) Log.v(TAG, sbGPRMC.toString());
   		sbGPRMC.append("\r\n");
   		
   		return sbGPRMC.toString();
	}
   
	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

}