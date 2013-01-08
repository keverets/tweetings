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

package com.dwdesign.tweetings.app;

import static com.dwdesign.tweetings.util.Utils.isNullOrEmpty;
import static com.dwdesign.tweetings.util.Utils.setUserAgent;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Hashtable;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;

import java.util.ArrayList;

import com.dwdesign.gallery3d.app.IGalleryApplication;
import com.dwdesign.gallery3d.data.DataManager;
import com.dwdesign.gallery3d.data.DownloadCache;
import com.dwdesign.gallery3d.util.GalleryUtils;
import com.dwdesign.gallery3d.util.ThreadPool;
import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.activity.HomeActivity;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.model.ParcelableUser;
import com.dwdesign.tweetings.util.AsyncTaskManager;
import com.dwdesign.tweetings.util.ImageLoaderUtils;
import com.dwdesign.tweetings.util.LazyImageLoader;
import com.dwdesign.tweetings.util.NoDuplicatesLinkedList;
import com.dwdesign.tweetings.util.ServiceInterface;


import android.app.Application;
import android.content.Intent;
import android.app.PendingIntent;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.AsyncTask;
import android.provider.Settings.Secure;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;

public class TweetingsApplication extends Application implements Constants, OnSharedPreferenceChangeListener, IGalleryApplication {

	public LazyImageLoader mProfileImageLoader, mPreviewImageLoader;
	private AsyncTaskManager mAsyncTaskManager;
	private SharedPreferences mPreferences;
	private ServiceInterface mServiceInterface;
	
	private BackupManager backupManager;
	
	private boolean mMultiSelectActive = false;
	
	private final ItemsList mSelectedItems = new ItemsList();
	
	private final ArrayList<Long> mSelectedStatusIds = new ArrayList<Long>();
	private final ArrayList<Long> mSelectedUserIds = new ArrayList<Long>();
	
	public static final String PARAM_SYNC_TYPE = "PARAM_SYNC_TYPE";
	public static final String PARAM_SYNC_ID = "PARAM_SYNC_ID";
	public static String BROADCAST_SYNC_ACTION = "com.dwdesign.tweetings.broadcast.SYNCRECIEVED";
	
	private static final String DOWNLOAD_FOLDER = "download";

	private static final long DOWNLOAD_CAPACITY = 64 * 1024 * 1024; // 64M
	private DataManager mDataManager;

	private ThreadPool mThreadPool;

	private DownloadCache mDownloadCache;

	private String mBrowserUserAgent;
	
	public AsyncTaskManager getAsyncTaskManager() {
		if (mAsyncTaskManager == null) {
			mAsyncTaskManager = AsyncTaskManager.getInstance();
		}
		return mAsyncTaskManager;
	}
	
	public String getBrowserUserAgent() {
		return mBrowserUserAgent;
	}

	public LazyImageLoader getPreviewImageLoader() {
		if (mPreviewImageLoader != null) return mPreviewImageLoader;
		final int preview_image_size = getResources().getDimensionPixelSize(R.dimen.image_preview_preferred_width);
		return mPreviewImageLoader = new LazyImageLoader(this, DIR_NAME_CACHED_THUMBNAILS, 0, preview_image_size,
				preview_image_size);
	}

	public LazyImageLoader getProfileImageLoader() {
		if (mProfileImageLoader != null) return mProfileImageLoader;
		final int profile_image_size = getResources().getDimensionPixelSize(R.dimen.profile_image_size);
		return mProfileImageLoader = new LazyImageLoader(this, DIR_NAME_PROFILE_IMAGES,
				R.drawable.ic_profile_image_default, profile_image_size, profile_image_size);
	}
	
	public ItemsList getSelectedItems() {
		return mSelectedItems;
	}
	
	public ArrayList<Long> getSelectedStatusIds() {
		return mSelectedStatusIds;
	}
	
	public ArrayList<Long> getSelectedUserIds() {
		return mSelectedUserIds;
	}

	public ServiceInterface getServiceInterface() {
		if (mServiceInterface != null && mServiceInterface.test()) {
			mServiceInterface.cancelShutdown();
			return mServiceInterface;
		}
		return mServiceInterface = ServiceInterface.getInstance(this);
	}
	
	public boolean isMultiSelectActive() {
		return mMultiSelectActive;
	}

	public boolean isDebugBuild() {
		return DEBUG;
	}
	
	@Override
	public synchronized DataManager getDataManager() {
		if (mDataManager == null) {
			mDataManager = new DataManager(this);
		}
		return mDataManager;
	}

	@Override
	public synchronized DownloadCache getDownloadCache() {
		if (mDownloadCache == null) {
			final File cacheDir = new File(getExternalCacheDir(), DOWNLOAD_FOLDER);

			if (!cacheDir.isDirectory()) {
				cacheDir.mkdirs();
			}

			if (!cacheDir.isDirectory()) throw new RuntimeException("fail to create: " + cacheDir.getAbsolutePath());
			mDownloadCache = new DownloadCache(this, cacheDir, DOWNLOAD_CAPACITY);
		}
		return mDownloadCache;
	}

	@Override
	public synchronized ThreadPool getThreadPool() {
		if (mThreadPool == null) {
			mThreadPool = new ThreadPool();
		}
		return mThreadPool;
	}
	
	@Override
	public void onCreate() {
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE);
		mPreferences.registerOnSharedPreferenceChangeListener(this);
		super.onCreate();
		initializeAsyncTask();
		GalleryUtils.initialize(this);
		mBrowserUserAgent = new WebView(this).getSettings().getUserAgentString();
		mServiceInterface = ServiceInterface.getInstance(this);
		registerPush();
		backupManager = new BackupManager(this);
		
	}
	
	private void initializeAsyncTask() {
		// AsyncTask class needs to be loaded in UI thread.
		// So we load it here to comply the rule.
		try {
			Class.forName(AsyncTask.class.getName());
		} catch (final ClassNotFoundException e) {
		}
	}
	
	public void registerPush() {
		//Log.w("C2DM", "start registration process");
		final String consumer_key = mPreferences.getString(PREFERENCE_KEY_CONSUMER_KEY, null);
		final String consumer_secret = mPreferences.getString(PREFERENCE_KEY_CONSUMER_SECRET, null);
		
		if (isNullOrEmpty(consumer_key) || isNullOrEmpty(consumer_secret)) {
			Intent intent = new Intent("com.google.android.c2dm.intent.REGISTER");
			intent.putExtra("app", PendingIntent.getBroadcast(this, 0, new Intent(), 0));
			intent.putExtra("sender", C2DM_SENDER);
			startService(intent);
		}
	}
	
	public void register(View view) {
		final String consumer_key = mPreferences.getString(PREFERENCE_KEY_CONSUMER_KEY, null);
		final String consumer_secret = mPreferences.getString(PREFERENCE_KEY_CONSUMER_SECRET, null);
		
		if (isNullOrEmpty(consumer_key) || isNullOrEmpty(consumer_secret)) {
			Intent intent = new Intent("com.google.android.c2dm.intent.REGISTER");
			intent.putExtra("app", PendingIntent.getBroadcast(this, 0, new Intent(), 0));
			intent.putExtra("sender", C2DM_SENDER);
			startService(intent);
		}
	}

	@Override
	public void onLowMemory() {
		if (mProfileImageLoader != null) {
			mProfileImageLoader.clearMemoryCache();
		}
		if (mPreviewImageLoader != null) {      
			mPreviewImageLoader.clearMemoryCache();
		}
		super.onLowMemory();
	}

	@Override
	public void onSharedPreferenceChanged(final SharedPreferences preferences, final String key) {
		if (mServiceInterface != null
				&& (PREFERENCE_KEY_AUTO_REFRESH.equals(key) || PREFERENCE_KEY_REFRESH_INTERVAL.equals(key))) {
			mServiceInterface.stopAutoRefresh();
			if (preferences.getBoolean(PREFERENCE_KEY_AUTO_REFRESH, false)) {
				mServiceInterface.startAutoRefresh();
			}
		} else if (PREFERENCE_KEY_ENABLE_PROXY.equals(key)) {
			reloadConnectivitySettings();
		}
		registerPush();
		if (backupManager == null) {
			backupManager = new BackupManager(this);
		}
		backupManager.dataChanged();
	}

	public void reloadConnectivitySettings() {
		if (mPreviewImageLoader != null) {
			mPreviewImageLoader.reloadConnectivitySettings();
		}
		if (mProfileImageLoader != null) {
			mProfileImageLoader.reloadConnectivitySettings();
		}
	}
	
	public void startMultiSelect() {
		mMultiSelectActive = true;
		final Intent intent = new Intent(BROADCAST_MULTI_SELECT_STATE_CHANGED);
		intent.setPackage(getPackageName());
		sendBroadcast(intent);
	}

	public void stopMultiSelect() {
		mSelectedItems.clear();
		mMultiSelectActive = false;
		final Intent intent = new Intent(BROADCAST_MULTI_SELECT_STATE_CHANGED);
		intent.setPackage(getPackageName());
		sendBroadcast(intent);
	}
	
	public static TweetingsApplication getInstance(final Context context) {
		return context != null ? (TweetingsApplication) context.getApplicationContext() : null;
	}

	@SuppressWarnings("serial")
	public class ItemsList extends NoDuplicatesLinkedList<Object> {

		@Override
		public boolean add(final Object object) {
			final boolean ret = super.add(object);
			if (object instanceof ParcelableStatus) {
				mSelectedStatusIds.add(((ParcelableStatus) object).status_id);
			} else if (object instanceof ParcelableUser) {
				mSelectedUserIds.add(((ParcelableUser) object).user_id);
			}
			final Intent intent = new Intent(BROADCAST_MULTI_SELECT_ITEM_CHANGED);
			intent.setPackage(getPackageName());
			sendBroadcast(intent);
			return ret;
		}

		@Override
		public void clear() {
			super.clear();
			mSelectedStatusIds.clear();
			mSelectedUserIds.clear();
			final Intent intent = new Intent(BROADCAST_MULTI_SELECT_ITEM_CHANGED);
			intent.setPackage(getPackageName());
			sendBroadcast(intent);
		}

		@Override
		public boolean remove(final Object object) {
			final boolean ret = super.remove(object);
			if (object instanceof ParcelableStatus) {
				mSelectedStatusIds.remove(((ParcelableStatus) object).status_id);
			} else if (object instanceof ParcelableUser) {
				mSelectedUserIds.remove(((ParcelableUser) object).user_id);
			}
			final Intent intent = new Intent(BROADCAST_MULTI_SELECT_ITEM_CHANGED);
			intent.setPackage(getPackageName());
			sendBroadcast(intent);
			return ret;
		}

	}
	
	public void saveSync(String timeline, String statusId, final long account_id, String screen_name) {
		final SharedPreferences preferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		final String username = screen_name;
		final String syncTimeline = timeline;
		final String syncStatusId = statusId;
		if (preferences.getBoolean(PREFERENCE_KEY_SYNC_ENABLED, false)) {
			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					String signingUrl = TWITTER_VERIFY_CREDENTIALS_JSON;
					ServiceInterface mService = getServiceInterface();
					String oAuthEchoHeader = mService.generateOAuthEchoHeader(account_id);
					String syncUrl = "";
					try {
						if (preferences.getString(PREFERENCE_KEY_SYNC_TYPE, "tweetmarker").equals("tweetmarker")) {
							
							syncUrl = "https://api.tweetmarker.net/v1/lastread?collection=" + syncTimeline + "&username=" + username + "&api_key=" + TWEETMARKER_API_KEY;
							HttpClient httpClient = new DefaultHttpClient();
							HttpPost postRequest = new HttpPost(syncUrl);
							postRequest.addHeader("X-Verify-Credentials-Authorization", oAuthEchoHeader);
							postRequest.addHeader("X-Auth-Service-Provider", TWITTER_VERIFY_CREDENTIALS_JSON);
						
							StringEntity reqEntity = new StringEntity(syncStatusId, HTTP.UTF_8);
							postRequest.setEntity(reqEntity);
							
							HttpResponse response = httpClient.execute(postRequest);
							
							BufferedReader reader = new BufferedReader(new InputStreamReader(
							response.getEntity().getContent(), "UTF-8"));
							String sResponse;
							StringBuilder s = new StringBuilder();
			
							while ((sResponse = reader.readLine()) != null) {
								s = s.append(sResponse);
							}
							//Log.i("TweetMarker Update", syncTimeline + " " + syncStatusId + " " + s.toString());
							
						}
						else {
							String deviceId = Secure.getString(getContentResolver(),
									Secure.ANDROID_ID);
							
							syncUrl = TWEETINGS_SYNC_POST_URL + "?did=" + deviceId + "&android=1&u=" + username + "&tl=" + syncTimeline + "&tlv=" + syncStatusId;
							HttpClient httpClient = new DefaultHttpClient();
							HttpResponse response = httpClient.execute(new HttpGet(syncUrl));
						    
							BufferedReader reader = new BufferedReader(new InputStreamReader(
							response.getEntity().getContent(), "UTF-8"));
							String sResponse;
							StringBuilder s = new StringBuilder();
			
							while ((sResponse = reader.readLine()) != null) {
								s = s.append(sResponse);
							}
							//Log.i("Tweetings Sync Update", syncUrl + syncTimeline + " " + syncStatusId + " " + s.toString());
						}
					} catch (Exception e) {
						Log.e("Sync", e.getMessage());
					  //Toast.makeText(getApplicationContext(), "Network exception" + e.getMessage(), Toast.LENGTH_SHORT).show();
				  }
				}
			};
			new Thread(runnable).start();
		}
	}
	
	public void getSync(String timeline, long account_id, String screen_name) {
		final SharedPreferences preferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		final String username = screen_name;
		final String syncTimeline = timeline;
		if (preferences.getBoolean(PREFERENCE_KEY_SYNC_ENABLED, false)) {
			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					String syncUrl = "";
					try {
						if (preferences.getString(PREFERENCE_KEY_SYNC_TYPE, "tweetmarker").equals("tweetmarker")) {
							
							syncUrl = "https://api.tweetmarker.net/v1/lastread?collection=" + syncTimeline + "&username=" + username + "&api_key=" + TWEETMARKER_API_KEY;
							HttpClient httpClient = new DefaultHttpClient();
							HttpResponse response = httpClient.execute(new HttpGet(syncUrl));
							
							BufferedReader reader = new BufferedReader(new InputStreamReader(
							response.getEntity().getContent(), "UTF-8"));
							String sResponse;
							StringBuilder s = new StringBuilder();
			
							while ((sResponse = reader.readLine()) != null) {
								s = s.append(sResponse);
							}
							//Log.i("TweetMarker Retrieve", syncTimeline + " " + s.toString());
							
							Intent broadcast = new Intent();
							broadcast.putExtra(PARAM_SYNC_TYPE, syncTimeline);
							broadcast.putExtra(PARAM_SYNC_ID, s.toString());
							
					        broadcast.setAction(BROADCAST_SYNC_ACTION);
					        sendBroadcast(broadcast);

						}
						else {
							String deviceId = Secure.getString(getContentResolver(),
									Secure.ANDROID_ID);
							
							syncUrl = TWEETINGS_SYNC_GET_URL + "?did=" + deviceId + "&android=1&u=" + username + "&tl=" + syncTimeline;
							HttpClient httpClient = new DefaultHttpClient();
							HttpResponse response = httpClient.execute(new HttpGet(syncUrl));
						    
							BufferedReader reader = new BufferedReader(new InputStreamReader(
							response.getEntity().getContent(), "UTF-8"));
							String sResponse;
							StringBuilder s = new StringBuilder();
			
							while ((sResponse = reader.readLine()) != null) {
								s = s.append(sResponse);
							}
							//Log.i("Tweetings Sync Retrieve", syncUrl + syncTimeline + " " + s.toString());
							
							Intent broadcast = new Intent();
							broadcast.putExtra(PARAM_SYNC_TYPE, syncTimeline);
							broadcast.putExtra(PARAM_SYNC_ID, s.toString());
							
					        broadcast.setAction(BROADCAST_SYNC_ACTION);
					        sendBroadcast(broadcast);
						}
					} catch (Exception e) {
						Log.e("Sync", e.getMessage());
					  //Toast.makeText(getApplicationContext(), "Network exception" + e.getMessage(), Toast.LENGTH_SHORT).show();
				  }
				}
			};
			new Thread(runnable).start();
		}
		
		
	}

	@Override
	public Context getAndroidContext() {
		return this;
	}
}
