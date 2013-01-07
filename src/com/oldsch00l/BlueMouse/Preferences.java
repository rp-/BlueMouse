package com.oldsch00l.BlueMouse;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;

public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	public static final String KEY_UPDATE_INTERVAL = "update_interval";

	private EditTextPreference mEditPrefUpdateInterval;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preference);

		mEditPrefUpdateInterval = (EditTextPreference)getPreferenceScreen().findPreference(KEY_UPDATE_INTERVAL);
	}

//	private String getStringResourceByName(String aString)
//	{
//	  String packageName = "com.oldsch00l.BlueMouse";
//	  int resId = getResources().getIdentifier(aString, "string", packageName);
//	  return getString(resId);
//	}

	@Override
	protected void onPause() {
		super.onPause();

		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		SharedPreferences sp = getPreferenceScreen().getSharedPreferences();
		mEditPrefUpdateInterval.setSummary(sp.getString(KEY_UPDATE_INTERVAL, "2000") + " ms");

		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if(key.equals(KEY_UPDATE_INTERVAL)) {
			mEditPrefUpdateInterval.setSummary(sharedPreferences.getString(KEY_UPDATE_INTERVAL, "2000") + " ms");
		}
	}
}
