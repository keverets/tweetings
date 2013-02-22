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

import static com.dwdesign.tweetings.util.Utils.openStatus;
import static com.dwdesign.tweetings.util.Utils.openUserRetweetedStatus;

import java.util.List;

import com.dwdesign.tweetings.loader.RetweetedToMeLoader;
import com.dwdesign.tweetings.loader.UserListTimelineLoader;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.model.StatusViewHolder;
import com.dwdesign.tweetings.util.NoDuplicatesLinkedList;
import com.dwdesign.tweetings.util.SynchronizedStateSavedList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.AdapterView;

public class RetweetedToMeFragment extends ParcelableStatusesListFragment {

	private boolean mIsStatusesSaved = false;
	private boolean isVisibleToUser = false;
	
	// Begin Sync Code
	private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
        	final String action = intent.getAction();
        	if (BROADCAST_VOLUME_UP.equals(action)) {
        		//if (isVisible()) {
					int currentPosition = getListView().getFirstVisiblePosition();  
				    if (currentPosition == 0)   
				        return;  
				    getListView().setSelection(currentPosition - 1);  
				    getListView().clearFocus(); 
        		//}
			}
			else if (BROADCAST_VOLUME_DOWN.equals(action)) {
				//if (isVisible()) {
					int currentPosition = getListView().getFirstVisiblePosition();  
				    if (currentPosition == getListView().getCount() - 1)   
				        return;  
				    getListView().setSelection(currentPosition + 1);  
				    getListView().clearFocus(); 
				//}
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
		boolean is_home_tab = false;
		if (args != null) {
			account_id = args.getLong(INTENT_KEY_ACCOUNT_ID, -1);
			max_id = args.getLong(INTENT_KEY_MAX_ID, -1);
			since_id = args.getLong(INTENT_KEY_SINCE_ID, -1);
			is_home_tab = args.getBoolean(INTENT_KEY_IS_HOME_TAB);
		}
		return new RetweetedToMeLoader(getActivity(), account_id, max_id, since_id, getData(), getClass().getSimpleName(),
				is_home_tab);
	}
	
	@Override
	public void onItemClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		final Object tag = view.getTag();
		if (tag instanceof StatusViewHolder) {
			final ParcelableStatus status = getListAdapter().findStatus(id);
			if (status == null) return;
			final StatusViewHolder holder = (StatusViewHolder) tag;
			if (holder.show_as_gap) {
			} else {
				gapStatusId = -1;
				openUserRetweetedStatus(getActivity(), status.account_id, status.retweet_id > 0 ? status.retweet_id
						: status.status_id);
			}
		}
	}

	@Override
	public boolean onItemLongClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		return false;
	}
	
	@Override
	public void scrollToStatusId(long statusId) {
		
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
		RetweetedToMeLoader.writeSerializableStatuses(this, getActivity(), getData(), status_id, getArguments());
		mIsStatusesSaved = true;
	}
}
