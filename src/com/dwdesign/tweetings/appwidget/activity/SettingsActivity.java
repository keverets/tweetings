package com.dwdesign.tweetings.appwidget.activity;

import com.dwdesign.tweetings.appwidget.Constants;
import com.dwdesign.tweetings.R;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity implements Constants, OnSharedPreferenceChangeListener {

	@Override
	public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
		final AppWidgetManager manager = AppWidgetManager.getInstance(this);
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getPreferenceManager().setSharedPreferencesName(SHARED_PREFERENCES_NAME);
		getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this);
		addPreferencesFromResource(R.xml.settings);
	}

}
