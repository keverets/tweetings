/*
 *				Tweetings - Twitter client for Android
 * 
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

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.fragment.NativeMapFragment;
import com.dwdesign.tweetings.fragment.NativeNearbyMapFragment;
import com.dwdesign.tweetings.fragment.WebMapFragment;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

public class MapViewerActivity extends FragmentActivity implements Constants, OnClickListener {

	private Fragment mFragment;

	@Override
	public void onClick(final View view) {
		switch (view.getId()) {
			case R.id.close: {
				onBackPressed();
				break;
			}
			case R.id.center: {
				getLocationAndCenterMap();
				break;
			}
		}

	}
	
	protected void getLocationAndCenterMap() {
		LocationManager mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		final Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		final String provider = mLocationManager.getBestProvider(criteria, true);

		Location mRecentLocation = null;
		
		if (provider != null) {
			mRecentLocation = mLocationManager.getLastKnownLocation(provider);
		} else {
			Toast.makeText(this, R.string.cannot_get_location, Toast.LENGTH_SHORT).show();
		}
		
		if (mRecentLocation != null && isNativeMapSupported()) {
			NativeMapFragment aFragment = (NativeMapFragment) mFragment;
			aFragment.setCenter(mRecentLocation);
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.map_viewer);
		final Uri uri = getIntent().getData();
		if (uri == null || !AUTHORITY_MAP.equals(uri.getAuthority())) {
			finish();
			return;
		}
		final Bundle bundle = new Bundle();
		final String param_lat = uri.getQueryParameter(QUERY_PARAM_LAT);
		final String param_lng = uri.getQueryParameter(QUERY_PARAM_LNG);
		if (param_lat == null || param_lng == null) {
			finish();
			return;
		}
		try {
			bundle.putDouble(INTENT_KEY_LATITUDE, Double.valueOf(param_lat));
			bundle.putDouble(INTENT_KEY_LONGITUDE, Double.valueOf(param_lng));
		} catch (final NumberFormatException e) {
			finish();
			return;
		}
		mFragment = isNativeMapSupported() ? new NativeMapFragment() : new WebMapFragment();
		mFragment.setArguments(bundle);
		final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		ft.replace(R.id.map_frame, mFragment).commit();
	}

	private boolean isNativeMapSupported() {
		try {
			Class.forName("com.google.android.maps.MapActivity");
			Class.forName("com.google.android.maps.MapView");
		} catch (final ClassNotFoundException e) {
			return false;
		}
		return true;
	}
}
