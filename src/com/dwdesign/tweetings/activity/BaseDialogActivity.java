/*
 *				Tweetings - Twitter client for Android
 * 
 * Copyright (C) 2012-2013 RBD Solutions Limited <apps@tweetings.net>
 * Copyright (C) 2012 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.dwdesign.tweetings.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.support.v4.app.FragmentActivity;
import android.view.WindowManager;
import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.util.ActivityThemeChangeInterface;

import static com.dwdesign.tweetings.util.Utils.restartActivity;
import com.dwdesign.tweetings.util.SetLayerTypeAccessor;
import android.view.Window;
import android.view.View;

@SuppressLint("Registered")
class BaseDialogActivity extends FragmentActivity implements Constants, ActivityThemeChangeInterface {

	private int mThemeId;
	private boolean mHardwareAccelerated;
	private boolean mTabConfiguration, mTabConfiguration2;

	public TweetingsApplication getTweetingsApplication() {
		return (TweetingsApplication) getApplication();
	}
	
	public boolean isTabConfigurationChanged() {
		final SharedPreferences preferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		boolean tab_display_label_default = getResources().getBoolean(R.bool.tab_display_label);
		final boolean tab_display_label = preferences.getBoolean(PREFERENCE_KEY_DISPLAY_TAB_LABEL, false);
		final boolean tab_hide_label = preferences.getBoolean(PREFERENCE_KEY_HIDE_TAB_LABEL, false);
		if (mTabConfiguration != tab_display_label || mTabConfiguration2 != tab_hide_label) {
			return true;
		}
		return false;
	}

	@Override
	public boolean isThemeChanged() {
		final SharedPreferences preferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		final int new_theme_id = preferences.getBoolean(PREFERENCE_KEY_DARK_THEME, true) ? R.style.Theme_Twidere_Dialog
				: R.style.Theme_Twidere_Light_Dialog;
		return new_theme_id != mThemeId;
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		setHardwareAcceleration();
		setTheme();
		setTabConfiguration();
		setTabConfiguration2();
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (isThemeChanged() || isHardwareAccelerationChanged() || isTabConfigurationChanged()) {
			restart();
		}
	}
	
	public void restart() {
		boolean show_anim = false;
		try {
			final float transition_animation = Settings.System.getFloat(getContentResolver(),
																		Settings.Global.TRANSITION_ANIMATION_SCALE);
			show_anim = transition_animation > 0.0;
		} catch (final SettingNotFoundException e) {
			e.printStackTrace();
		}
		restartActivity(this, show_anim);
	}
	
	public void setTabConfiguration() {
		final SharedPreferences preferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		boolean tab_display_label_default = getResources().getBoolean(R.bool.tab_display_label);
		mTabConfiguration = preferences.getBoolean(PREFERENCE_KEY_DISPLAY_TAB_LABEL, false);
	}
	
	public void setTabConfiguration2() {
		final SharedPreferences preferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		boolean tab_display_label_default = getResources().getBoolean(R.bool.tab_display_label);
		mTabConfiguration2 = preferences.getBoolean(PREFERENCE_KEY_HIDE_TAB_LABEL, false);
	}

	@Override
	public void setTheme() {
		final SharedPreferences preferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mThemeId = preferences.getBoolean(PREFERENCE_KEY_DARK_THEME, true) ? R.style.Theme_Twidere_Dialog
				: R.style.Theme_Twidere_Light_Dialog;
		setTheme(mThemeId);
	}
	
	public void setHardwareAcceleration() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			final SharedPreferences preferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
			final boolean hardware_acceleration = mHardwareAccelerated = preferences.getBoolean(PREFERENCE_KEY_HARDWARE_ACCELERATION, true);
			final Window w = getWindow();
			if (hardware_acceleration) {
				w.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
						WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
			}
		}
	}

	public boolean isHardwareAccelerationChanged() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) return false;
		final SharedPreferences preferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		final boolean hardware_acceleration = preferences.getBoolean(PREFERENCE_KEY_HARDWARE_ACCELERATION, true);
		return mHardwareAccelerated != hardware_acceleration;
	}
}
