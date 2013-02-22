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

package com.dwdesign.tweetings.fragment;

import java.util.List;

import twitter4j.internal.org.json.JSONObject;

import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.fragment.CustomTabsFragment.CustomTabsAdapter;
import com.dwdesign.tweetings.loader.TweetSearchLoader;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.provider.TweetStore.Tabs;
import com.dwdesign.tweetings.util.SynchronizedStateSavedList;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

public class SearchTweetsFragment extends ParcelableStatusesListFragment {

	private boolean mIsStatusesSaved = false;
	private boolean isVisibleToUser = false;
	private int mSearchId = -1;
	private long mAccountId;
	private String mQuery;
	Menu optionsMenu;
	
	// Begin Sync Code
	private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
        	final String action = intent.getAction();
        	if (BROADCAST_VOLUME_UP.equals(action)) {
        		if (isVisible()) {
					int currentPosition = getListView().getFirstVisiblePosition();  
				    if (currentPosition == 0)   
				        return;  
				    getListView().setSelection(currentPosition - 1);  
				    getListView().clearFocus(); 
        		}
			}
			else if (BROADCAST_VOLUME_DOWN.equals(action)) {
				if (isVisible()) {
					int currentPosition = getListView().getFirstVisiblePosition();  
				    if (currentPosition == getListView().getCount() - 1)   
				        return;  
				    getListView().setSelection(currentPosition + 1);  
				    getListView().clearFocus(); 
				}
			}
        }
    };
    
    @Override
    public void setMenuVisibility(final boolean visible) {
        super.setMenuVisibility(visible);
        isVisibleToUser = visible;
    }
    
    @Override
	public void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_VOLUME_UP);
		filter.addAction(BROADCAST_VOLUME_DOWN);
        registerReceiver(receiver, filter);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(receiver);
	}
	
	@Override
	public Loader<SynchronizedStateSavedList<ParcelableStatus, Long>> newLoaderInstance(final Bundle args) {
		long account_id = -1, max_id = -1, since_id = -1;
		String query = null;
		boolean is_home_tab = false;
		if (args != null) {
			account_id = args.getLong(INTENT_KEY_ACCOUNT_ID);
			max_id = args.getLong(INTENT_KEY_MAX_ID, -1);
			since_id = args.getLong(INTENT_KEY_SINCE_ID, -1);
			query = args.getString(INTENT_KEY_QUERY);
			is_home_tab = args.getBoolean(INTENT_KEY_IS_HOME_TAB);
		}
		return new TweetSearchLoader(getActivity(), account_id, query, max_id, since_id, getData(), getClass()
				.getSimpleName(), is_home_tab);
	}

	
	@Override
	public void scrollToStatusId(long statusId) {
		
	}
	
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		
	}
	
	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case MENU_SAVE: {
				final TweetingsApplication application = getApplication();
				application.getServiceInterface().createSavedSearch(mAccountId, mQuery);
				break;
			}
			case MENU_DELETE: {
				final TweetingsApplication application = getApplication();
				application.getServiceInterface().destroySavedSearch(mAccountId, mSearchId);
				break;
			}
			case MENU_ADD_TAB: {
				CustomTabsAdapter mAdapter;
				mAdapter = new CustomTabsAdapter(getActivity());
				ContentResolver mResolver;
				mResolver = getContentResolver();
				final String tabName = mQuery;
				final String tabType = AUTHORITY_SEARCH_TWEETS;
				final String tabIcon = "search";
				final long account_id = mAccountId;
				final String tabArguments = "{\"account_id\":" + account_id + ",\"query\":" + JSONObject.quote(mQuery) + "}";
				final ContentValues values = new ContentValues();
				values.put(Tabs.ARGUMENTS, tabArguments);
				values.put(Tabs.NAME, tabName);
				values.put(Tabs.POSITION, mAdapter.getCount());
				values.put(Tabs.TYPE, tabType);
				values.put(Tabs.ICON, tabIcon);
				mResolver.insert(Tabs.CONTENT_URI, values);
				Toast.makeText(this.getActivity(), R.string.search_tab_added, Toast.LENGTH_SHORT).show();
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		menu.clear();
		inflater.inflate(R.menu.menu_searchtweets, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public void onPrepareOptionsMenu(final Menu menu) {
		super.onPrepareOptionsMenu(menu);
		optionsMenu = menu;

		final MenuItem itemSave = menu.findItem(MENU_SAVE);
		final MenuItem itemDelete = menu.findItem(MENU_DELETE_SUBMENU);
		if (mSearchId > 0) {
			itemSave.setVisible(false);
			itemDelete.setVisible(true);
		}
		else {
			itemSave.setVisible(true);
			itemDelete.setVisible(false);
		}
		
	}
	
	@Override
	public void onDestroy() {
		saveStatuses();
		super.onDestroy();
	}

	@Override
	public void onDestroyView() {
		saveStatuses();
		super.onDestroyView();
	}
	
	private void saveStatuses() {
		if (mIsStatusesSaved) return;
		final int first_visible_position = getListView().getFirstVisiblePosition();
		final long status_id = getListAdapter().findItemIdByPosition(first_visible_position);
		TweetSearchLoader.writeSerializableStatuses(this, getActivity(), getData(), status_id, getArguments());
		mIsStatusesSaved = true;
	}

}
