package com.oldsch00l.BlueMouse;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class BlueMouse extends Activity {
	private BluetoothAdapter mBluetoothAdapter = null;
	
	// Intent request codes
	private static final int REQUEST_ENABLE_BT = 1;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
        if (mBluetoothAdapter == null) {
        	Log.e(getString(R.string.app_name), "Bluetooth is not available");
        	finish();
        	return;
        }
    }
    
	@Override
	protected void onStart() {
		super.onStart();
		
		// if BT is not on, request to enable it.
		if(!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		} else {
			
		}
	}

    @Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}
}