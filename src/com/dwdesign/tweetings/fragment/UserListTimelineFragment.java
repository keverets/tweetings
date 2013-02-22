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

import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.loader.UserListTimelineLoader;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.util.SynchronizedStateSavedList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class UserListTimelineFragment extends ParcelableStatusesListFragment {
	
	private boolean mIsStatusesSaved = false;
	private boolean isVisibleToUser = false;
	
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
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//setHasOptionsMenu(true);
	}
	
	@Override
	public void scrollToStatusId(long statusId) {
		
	}
	
	@Override
	public Loader<SynchronizedStateSavedList<ParcelableStatus, Long>> newLoaderInstance(final Bundle args) {
		int list_id = -1;
		long account_id = -1, max_id = -1, since_id = -1, user_id = -1;
		String screen_name = null, list_name = null;
		boolean is_home_tab = false;
		if (args != null) {
			list_id = args.getInt(INTENT_KEY_LIST_ID, -1);
			account_id = args.getLong(INTENT_KEY_ACCOUNT_ID, -1);
			max_id = args.getLong(INTENT_KEY_MAX_ID, -1);
			since_id = args.getLong(INTENT_KEY_SINCE_ID, -1);
			user_id = args.getLong(INTENT_KEY_USER_ID, -1);
			screen_name = args.getString(INTENT_KEY_SCREEN_NAME);
			list_name = args.getString(INTENT_KEY_LIST_NAME);
			is_home_tab = args.getBoolean(INTENT_KEY_IS_HOME_TAB);
		}
		return new UserListTimelineLoader(getActivity(), account_id, list_id, user_id, screen_name, list_name, max_id,
				since_id, getData(), getClass().getSimpleName(), is_home_tab);
	}
	
	/*@Override
	public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_home, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}
	
	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		
		}
		return super.onOptionsItemSelected(item);
	}*/

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
		UserListTimelineLoader.writeSerializableStatuses(this, getActivity(), getData(), status_id, getArguments());
		mIsStatusesSaved = true;
	}

}
