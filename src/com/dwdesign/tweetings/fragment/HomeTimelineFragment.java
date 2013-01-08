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

package com.dwdesign.tweetings.fragment;

import java.util.Timer;
import java.util.TimerTask;

import com.dwdesign.tweetings.adapter.CursorStatusesAdapter;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.provider.TweetStore.Statuses;
import com.dwdesign.tweetings.util.ServiceInterface;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;

public class HomeTimelineFragment extends CursorStatusesListFragment implements OnTouchListener {

	private SharedPreferences mPreferences;
	private ListView mListView;
	private ServiceInterface mService;
	private boolean isVisibleToUser = false;

	private boolean mShouldRestorePosition = false;
	private long mMinIdToRefresh;
	private Timer syncTimer;
	private Activity mActivity;
	private boolean isReadTrackingSuspended = false;

	private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			final Bundle extras = intent.getExtras();
			if (BROADCAST_HOME_TIMELINE_REFRESHED.equals(action)) {
				onRefreshComplete();
				if (extras != null) {
					mMinIdToRefresh = extras.getBoolean(INTENT_KEY_SUCCEED) ? extras.getLong(INTENT_KEY_MIN_ID, -1)
							: -1;
				} else {
					mMinIdToRefresh = -1;
				}
				if (mPreferences.getBoolean(PREFERENCE_KEY_SYNC_ENABLED, false)) {
					HomeTimelineFragment.this.scrollToSavedPosition();
				}
				
			} else if (BROADCAST_HOME_TIMELINE_DATABASE_UPDATED.equals(action)) {
				if (isAdded() && !isDetached()) {
					getLoaderManager().restartLoader(0, null, HomeTimelineFragment.this);
				}
			} else if (BROADCAST_REFRESHSTATE_CHANGED.equals(action)) {
				if (mService.isHomeTimelineRefreshing()) {
					setRefreshing(false);
				}
			}
			
		}
	};
	
	// Begin Sync Code
	private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
        	final String action = intent.getAction();
        	if (TweetingsApplication.BROADCAST_SYNC_ACTION.equals(action)) {
	            String syncType = intent.getExtras().getString(TweetingsApplication.PARAM_SYNC_TYPE);
	        	String syncId = intent.getExtras().getString(TweetingsApplication.PARAM_SYNC_ID);
	        	if (syncType.equals("timeline")) {
	        		HomeTimelineFragment.this.syncPositionReceived = syncId;
	        		HomeTimelineFragment.this.scrollToSavedPosition();
	        	}
        	}
        	else if (BROADCAST_VOLUME_UP.equals(action)) {
        		if (isVisible()) {
					int currentPosition = mListView.getFirstVisiblePosition();  
				    if (currentPosition == 0)   
				        return;  
				    mListView.setSelection(currentPosition - 1);  
				    mListView.clearFocus(); 
        		}
			}
			else if (BROADCAST_VOLUME_DOWN.equals(action)) {
				if (isVisible()) {
					int currentPosition = mListView.getFirstVisiblePosition();  
				    if (currentPosition == mListView.getCount() - 1)   
				        return;  
				    mListView.setSelection(currentPosition + 1);  
				    mListView.clearFocus(); 
				}
			}
        }
    };
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mActivity = getActivity();
		
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		if (syncTimer != null) {
			syncTimer.cancel();
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter();
        filter.addAction(TweetingsApplication.BROADCAST_SYNC_ACTION);
        filter.addAction(BROADCAST_VOLUME_UP);
		filter.addAction(BROADCAST_VOLUME_DOWN);
		
        registerReceiver(receiver, filter);
        if (mPreferences.getBoolean(PREFERENCE_KEY_SYNC_ENABLED, false)) {
        	getSync();
        }
	}
	
	@Override
    public void setMenuVisibility(final boolean visible) {
        super.setMenuVisibility(visible);
        isVisibleToUser = visible;
    }
	
	@Override
	public void onPause() {
		super.onPause();
		if (mPreferences.getBoolean(PREFERENCE_KEY_SYNC_ENABLED, false)) {
        	saveSync();
        }
		unregisterReceiver(receiver);
	}
	
	public void scheduleSync() {
		if (syncTimer != null) {
			syncTimer.cancel();
			syncTimer = null;
		}
		syncTimer = new Timer();
		syncTimer.schedule(new TimerTask() {			
			@Override
			public void run() {
				HomeTimelineFragment.this.saveSync();
			}
			
		}, 4000);
	}
	
	protected void saveSync() {
		final CursorStatusesAdapter adapter = getListAdapter();
		int currentPosition = getListView().getFirstVisiblePosition();
		long accountid = com.dwdesign.tweetings.util.Utils.getDefaultAccountId(getActivity());
		String username = com.dwdesign.tweetings.util.Utils.getAccountUsername(getActivity(), accountid);
		long statusId = adapter.findItemIdByPosition(currentPosition);
		TweetingsApplication app = (TweetingsApplication)getApplication();
	  	app.saveSync("timeline", "" + statusId, accountid, username);
	  	if (syncTimer != null) {
			syncTimer.cancel();
		}
	}
	
	protected void getSync() {
		String syncTimeline = "timeline";
		final long accountid = com.dwdesign.tweetings.util.Utils.getDefaultAccountId(getActivity());
		final String username = com.dwdesign.tweetings.util.Utils.getAccountUsername(getActivity(), accountid);
		getApplication().getSync(syncTimeline, accountid, username);
		//scrollToSavedPosition();
	}
	
	protected void scrollToSavedPosition() {
		final CursorStatusesAdapter adapter = getListAdapter();
		try {
			if (this.syncPositionReceived != null && !this.syncPositionReceived.equals("(null)")) {
				if ((this.syncPositionReceived != null && this.syncPosition == null) || (this.syncPositionReceived != null && this.syncPosition != null && !this.syncPositionReceived.equals(this.syncPosition))) {
					int index = adapter.findItemPositionByStatusId(Long.parseLong(this.syncPositionReceived));
					 this.scrollStatusId = this.syncPositionReceived;
					 this.syncPosition = this.syncPositionReceived;
					 if (index >= 0 && index < adapter.getCount()) {
						mListView.setSelection(index);
					 }
					 else if (index == -1) {
						 int guessedPosition = adapter.findNearestItemPositionByStatusId(Long.parseLong(this.syncPositionReceived));
						 if (guessedPosition > 0) {
							 getListView().setSelection(guessedPosition);
						 }
					 }
				}
			}
		}
		catch (NumberFormatException ex) {
			
		}
	}
	
	// End Sync code

	@Override
	public Uri getContentUri() {
		return Statuses.CONTENT_URI;
	}

	@Override
	public int getStatuses(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
		//if (max_ids == null) return mService.refreshAll();
		isReadTrackingSuspended = true;
		return mService.getHomeTimelineWithSinceId(account_ids, max_ids, since_ids);
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mShouldRestorePosition = true;
		mService = getServiceInterface();
		super.onActivityCreated(savedInstanceState);
		mListView = getListView();
		//mListView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
		mListView.setOnTouchListener(this);
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
		isReadTrackingSuspended = true;
		final Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
		  @Override
		  public void run() {
		    isReadTrackingSuspended = false;
		  }
		}, 500);
		final CursorStatusesAdapter adapter = getListAdapter();
		long last_viewed_id = -1;
		{
			final int position = mListView.getFirstVisiblePosition();
			if (position > 0) {
				last_viewed_id = adapter.findItemIdByPosition(position);
			}
		}
		super.onLoadFinished(loader, data);
		
		final boolean remember_position = mPreferences.getBoolean(PREFERENCE_KEY_REMEMBER_POSITION, true);
		if (gapStatusId > 0) {
			
			if (mPreferences.getBoolean(PREFERENCE_KEY_GAP_POSITION, true)) {
				scrollToStatusId(gapStatusId);
			}
			gapStatusId = -1;
			return;
		}
		if (mShouldRestorePosition && remember_position) {
			final long status_id = mPreferences.getLong(PREFERENCE_KEY_SAVED_HOME_TIMELINE_ID, -1);
			final int position = adapter.findItemPositionByStatusId(status_id);
			if (position > -1 && position < mListView.getCount()) {
				mListView.setSelection(position);
			}
			mShouldRestorePosition = false;
			return;
		}
		if (mMinIdToRefresh > 0 && remember_position) {
			final int position = adapter.findItemPositionByStatusId(last_viewed_id > 0 ? last_viewed_id
					: mMinIdToRefresh);
			if (position >= 0 && position < mListView.getCount()) {
				mListView.setSelection(position);
			}
			mMinIdToRefresh = -1;
			return;
		}
		
		final int position = adapter.findItemPositionByStatusId(last_viewed_id > 0 ? last_viewed_id: mMinIdToRefresh);		
		if (position > 0) {
			mListView.setSelection(position);
		}
	}
	
	@Override
	public void onScrollStateChanged(final AbsListView view, final int scrollState) {
		super.onScrollStateChanged(view, scrollState);
		switch (scrollState) {
			case SCROLL_STATE_IDLE:
				HomeTimelineFragment.this.scheduleSync();
				final int first_visible_position = view.getFirstVisiblePosition();
				final long status_id = getListAdapter().findItemIdByPosition(first_visible_position);
				mPreferences.edit().putLong(PREFERENCE_KEY_SAVED_HOME_TIMELINE_ID, status_id).commit();
				break;
			case SCROLL_STATE_TOUCH_SCROLL:
				if (syncTimer != null) {
					syncTimer.cancel();
				}
				break;
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		final IntentFilter filter = new IntentFilter(BROADCAST_HOME_TIMELINE_REFRESHED);
		filter.addAction(BROADCAST_ACCOUNT_LIST_DATABASE_UPDATED);
		filter.addAction(BROADCAST_HOME_TIMELINE_DATABASE_UPDATED);
		filter.addAction(BROADCAST_REFRESHSTATE_CHANGED);
		registerReceiver(mStatusReceiver, filter);
		 if (mService.isHomeTimelineRefreshing()) {
			setRefreshing(false);
		} else {
			onRefreshComplete();
		}
	}

	@Override
	public void onStop() {
		unregisterReceiver(mStatusReceiver);
		super.onStop();
	}

	@Override
	public boolean onTouch(final View view, final MotionEvent ev) {
		switch (ev.getAction()) {
			case MotionEvent.ACTION_DOWN: {
				mService.clearNotification(NOTIFICATION_ID_HOME_TIMELINE);
				break;
			}
		}
		return false;
	}

	@Override
	public void onScroll(final AbsListView view, final int firstVisibleItem, final int visibleItemCount, final int totalItemCount) {
		super.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		if (firstVisibleItem == 0 && !isReadTrackingSuspended) {
			Intent intent = new Intent(BROADCAST_TABS_READ_TWEETS);
			intent.putExtra(INTENT_KEY_UPDATE_TAB, TAB_HOME);
			mActivity.sendBroadcast(intent);
		} else if (firstVisibleItem > 0 && isReadTrackingSuspended) {
			final Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
			  @Override
			  public void run() {
			    isReadTrackingSuspended = false;
			  }
			}, 500);
		}
	}
	
	
}
