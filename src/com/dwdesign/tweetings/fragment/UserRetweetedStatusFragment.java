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

import static com.dwdesign.tweetings.util.Utils.openStatus;
import static com.dwdesign.tweetings.util.Utils.openUserProfile;

import java.util.List;

import com.dwdesign.tweetings.activity.HomeActivity;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.loader.UserListTimelineLoader;
import com.dwdesign.tweetings.loader.UserRetweetedStatusLoader;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.model.ParcelableUser;
import com.dwdesign.tweetings.model.StatusViewHolder;
import com.dwdesign.tweetings.util.NoDuplicatesLinkedList;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.AdapterView;

public class UserRetweetedStatusFragment extends ParcelableStatusesListFragment {

	private ParcelableStatus mSelectedStatus;
	private TweetingsApplication mApplication;
	
	@Override
	public Loader<List<ParcelableStatus>> newLoaderInstance(final Bundle args) {
		long account_id = -1, max_id = -1, since_id = -1, status_id = -1;
		boolean is_home_tab = false;
		if (args != null) {
			account_id = args.getLong(INTENT_KEY_ACCOUNT_ID, -1);
			max_id = args.getLong(INTENT_KEY_MAX_ID, -1);
			since_id = args.getLong(INTENT_KEY_SINCE_ID, -1);
			status_id = args.getLong(INTENT_KEY_STATUS_ID, -1);
		}
		return new UserRetweetedStatusLoader(getActivity(), account_id, status_id, max_id, since_id,
				getData(), getClass().getSimpleName(), is_home_tab);
	}
	
	@Override
	public void scrollToStatusId(long statusId) {
		
	}
	
	@Override
	public void onItemClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		mSelectedStatus = null;
		final Object tag = view.getTag();
		if (tag instanceof StatusViewHolder) {
			final ParcelableStatus status = mSelectedStatus = getListAdapter().findStatus(id);
			if (status == null) return;
			final StatusViewHolder holder = (StatusViewHolder) tag;
			if (holder.show_as_gap) {
				getStatuses(new long[] { status.account_id }, new long[] { status.status_id }, null);
			} else {
				 openUserProfile(getActivity(), status.account_id, status.retweeted_by_id, status.retweeted_by_screen_name);
			}
		}
	}
	
	/*@Override
	public Loader<List<ParcelableUser>> newLoaderInstance() {
		final Bundle args = getArguments();
		long account_id = -1, max_id = -1, status_id = -1;
		if (args != null) {
			account_id = args.getLong(INTENT_KEY_ACCOUNT_ID, -1);
			max_id = args.getLong(INTENT_KEY_MAX_ID, -1);
			status_id = args.getLong(INTENT_KEY_STATUS_ID, -1);
		}
		int page = 1;
		if (max_id > 0) {
			final int prefs_load_item_limit = getSharedPreferences().getInt(PREFERENCE_KEY_LOAD_ITEM_LIMIT,
					PREFERENCE_DEFAULT_LOAD_ITEM_LIMIT);
			final int load_item_limit = prefs_load_item_limit > 100 ? 100 : prefs_load_item_limit;
			final int pos = getListAdapter().findItemPositionByUserId(max_id);
			if (pos > 0) {
				page = pos / load_item_limit + 1;
			}
		}
		return new UserRetweetedStatusLoader(getActivity(), account_id, status_id, page, getData());
	}*/

}
