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

import static com.dwdesign.tweetings.util.Utils.getDefaultAccountId;
import static com.dwdesign.tweetings.util.Utils.getTwitterInstance;
import static com.dwdesign.tweetings.util.Utils.openStatus;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;

import twitter4j.GeoLocation;
import twitter4j.Paging;
import twitter4j.Query;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Tweet;
import twitter4j.Twitter;
import twitter4j.TwitterException;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.activity.GalleryActivity.MediaTimelineTask;
import com.dwdesign.tweetings.fragment.NativeNearbyMapFragment;
import com.dwdesign.tweetings.model.ParcelableLocation;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.view.ExtendedMapView;
import com.dwdesign.tweetings.events.PanChangeListener;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Display;
import android.widget.GridView;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;
import com.google.android.maps.Projection;

public class NativeNearbyMapActivity extends MapActivity implements Constants {

	private ExtendedMapView mMapView;
	
	private NearbySearchTask mNearbySearchTask;
	private static ArrayList<ParcelableStatus> mStatuses;
	private long account_id;
	private Location mRecentLocation;
	
	public static NativeNearbyMapActivity mActivity;
	private LocationManager mLocationManager;
	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	public void setCenter(final Location location) {
		final double lat = location.getLatitude();
		final double lng = location.getLongitude();
		final GeoPoint gp = new GeoPoint((int) (lat * 1E6), (int) (lng * 1E6));
		final MapController mc = mMapView.getController();
		mc.setZoom(16);
		mc.animateTo(gp);
	}
	
	protected void getLocationAndCenterMap() {
		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		final Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		final String provider = mLocationManager.getBestProvider(criteria, true);

		mRecentLocation = null;
		
		if (provider != null) {
			mRecentLocation = mLocationManager.getLastKnownLocation(provider);
			//mLocationManager.requestLocationUpdates(provider, 1000, 0, locationListener); 
		} else {
			Toast.makeText(this, R.string.cannot_get_location, Toast.LENGTH_SHORT).show();
		}
		
		if (mRecentLocation != null) {
			setCenter(mRecentLocation);
			if (mNearbySearchTask != null) {
				mNearbySearchTask.cancel(true);
			}
			mNearbySearchTask = null;
			mNearbySearchTask = new NearbySearchTask(NativeNearbyMapActivity.this, account_id);
			if (mNearbySearchTask != null) {
	        	mNearbySearchTask.execute();
			}
		}
		mMapView.invalidate();
		
	 
	}
	
	private final LocationListener locationListener = new LocationListener() 
	{ 

		@Override 
		public void onLocationChanged(Location location) { 
			mRecentLocation = location;
			if (mRecentLocation != null) {
				setCenter(mRecentLocation);
				if (mNearbySearchTask != null) {
					mNearbySearchTask.cancel(true);
				}
				mNearbySearchTask = null;
				mNearbySearchTask = new NearbySearchTask(NativeNearbyMapActivity.this, account_id);
				if (mNearbySearchTask != null) {
		        	mNearbySearchTask.execute();
				}
			}
		} 
	
		@Override 
		public void onProviderDisabled(String provider) { 
		} 
	
		@Override 
		public void onProviderEnabled(String provider) { 
		} 
	
		@Override 
		public void onStatusChanged(String provider, int status, Bundle extras) { 
		} 

	}; 
	
	@Override
	protected void onCreate(final Bundle icicle) {
		super.onCreate(icicle);
		final Bundle bundle = getIntent().getExtras();
		mMapView = new ExtendedMapView(this, MAPS_API_KEY) {
			{
				setClickable(true);
			}
		};
		mActivity = this;
		final MapController mc = mMapView.getController();
		mc.setZoom(16);
		setContentView(mMapView);
		getLocationAndCenterMap();
		
		mMapView.addPanChangeListener(new PanChangeListener() {
			
			@Override
			public void onPan(GeoPoint old, GeoPoint current) {
				if (mRecentLocation != null) {
					mRecentLocation.setLatitude(microDegreesToDegrees(current.getLatitudeE6()));
					mRecentLocation.setLongitude(microDegreesToDegrees(current.getLongitudeE6()));
					if (mNearbySearchTask != null) {
						mNearbySearchTask.cancel(true);
					}
					mNearbySearchTask = null;
					mNearbySearchTask = new NearbySearchTask(NativeNearbyMapActivity.this, account_id);
			        if (mNearbySearchTask != null) {
			        	mNearbySearchTask.execute();
					}
				}
			}
		});
		
		account_id = getDefaultAccountId(this);
		
        
	}
	
	public static double microDegreesToDegrees(int microDegrees) {
	    return microDegrees / 1E6;
	}
	
	@Override
	public void onDestroy() {
		mStatuses = null;
		
		if (mNearbySearchTask != null) {
			mNearbySearchTask.cancel(true);
		}
		if (mLocationManager != null) {
			//mLocationManager.removeUpdates(locationListener);
		}
		super.onDestroy();
	}

	
	public void setMarkers() {
		if (mStatuses != null && mStatuses.size() >= 1) {
			final List<Overlay> overlays = mMapView.getOverlays();
			for (ParcelableStatus pStatus : mStatuses) {
				ParcelableLocation location = pStatus.location;
				if (location != null) {
					final double lat = location.latitude, lng = location.longitude;
					final GeoPoint gp = new GeoPoint((int) (lat * 1E6), (int) (lng * 1E6));
					final Drawable d = getResources().getDrawable(R.drawable.ic_map_marker);
					final Itemization markers = new Itemization(d);
					final OverlayItem overlayitem = new OverlayItem(gp, "", "");
					markers.addOverlay(overlayitem);
					overlays.add(markers);
				}
			}
		}
		mMapView.invalidate();
	}
	
	class NearbySearchTask extends AsyncTask<Void, Void, twitter4j.Status[]> {

		private final Twitter twitter;
		private long account_id;

		private NearbySearchTask(final Context context, final long account_id) {
			twitter = getTwitterInstance(context, account_id, true);
			this.account_id = account_id;
		}

		@Override
		protected twitter4j.Status[] doInBackground(final Void... args) {
			twitter4j.Status[] tweets = null;
			try {
				final Query query = new Query();
				GeoLocation location = new GeoLocation(mRecentLocation.getLatitude(), mRecentLocation.getLongitude());
				query.setGeoCode(location, 1.5, "km");
				query.setRpp(50);
				tweets = twitter != null ? twitter.search(query).getStatuses() : null;
			} catch (final TwitterException e) {
				e.printStackTrace();
			}
			return tweets;
			
		}

		@Override
		protected void onCancelled() {
			setProgressBarIndeterminateVisibility(false);
			super.onCancelled();
		}

		@Override
		protected void onPostExecute(final twitter4j.Status[] tweets) {
			if (tweets == null) return;
			if (mStatuses == null) {
				mStatuses = new ArrayList<ParcelableStatus>();
			}
			if (tweets != null) {
				final int size = tweets.length;
				for (int i = 0; i < size; i++) {
					final twitter4j.Status tweet = tweets[i];
					ParcelableStatus pStatus = new ParcelableStatus(tweet, account_id, false);
					if (pStatus != null) {
	            		mStatuses.add(pStatus);
	            	}
				}
			}
			setMarkers();
			setProgressBarIndeterminateVisibility(false);
			super.onPostExecute(tweets);
		}

		@Override
		protected void onPreExecute() {
			setProgressBarIndeterminateVisibility(true);
			super.onPreExecute();
		}

	}

	static class Itemization extends ItemizedOverlay<OverlayItem> {

		private final ArrayList<OverlayItem> mOverlays = new ArrayList<OverlayItem>();

		public Itemization(final Drawable defaultMarker) {
			super(boundCenterBottom(defaultMarker));
		}

		public void addOverlay(final OverlayItem overlay) {
			mOverlays.add(overlay);
			populate();
		}

		@Override
		public int size() {
			return mOverlays.size();
		}
		
		@Override
		protected boolean onTap(final int index) {
		  OverlayItem item = mOverlays.get(index);
		  GeoPoint geoLocation = item.getPoint();
		  for (ParcelableStatus pStatus : mStatuses) {
			  ParcelableLocation statusLocation = pStatus.location;
			  
			  DecimalFormat twoDForm = new DecimalFormat("#.######");
		      double latitude = Double.valueOf(twoDForm.format(statusLocation.latitude));
		      double longitude = Double.valueOf(twoDForm.format(statusLocation.longitude));
			  double locationLatitude = Double.valueOf(microDegreesToDegrees(geoLocation.getLatitudeE6()));
			  double locationLongitude = Double.valueOf(microDegreesToDegrees(geoLocation.getLongitudeE6()));
			  
			  if ((latitude == locationLatitude && longitude == locationLongitude) ||
					  (statusLocation.latitude == microDegreesToDegrees(geoLocation.getLatitudeE6()) && statusLocation.longitude == microDegreesToDegrees(geoLocation.getLongitudeE6()))) {
				  openStatus(mActivity, pStatus);
				  break;
			  }
		  }
		  return true;
		}

		@Override
		protected OverlayItem createItem(final int i) {
			return mOverlays.get(i);
		}
		
		protected static Drawable boundCenterBottom(final Drawable d) {
			d.setBounds(-d.getIntrinsicWidth() / 2, -d.getIntrinsicHeight(), d.getIntrinsicWidth() / 2, 0);
			return d;
		}
	}

	
}
