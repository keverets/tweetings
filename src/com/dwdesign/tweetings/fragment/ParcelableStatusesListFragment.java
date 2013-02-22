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

import static com.dwdesign.tweetings.util.HtmlEscapeHelper.unescape;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.dwdesign.tweetings.adapter.ParcelableStatusesAdapter;
import com.dwdesign.tweetings.loader.ParcelableStatusesLoader;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.provider.TweetStore.Statuses;
import com.dwdesign.tweetings.util.NoDuplicatesArrayList;
import com.dwdesign.tweetings.util.SynchronizedStateSavedList;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.content.Loader;
import android.text.Html;
import android.text.Spanned;
import android.widget.ListView;

public abstract class ParcelableStatusesListFragment extends BaseStatusesListFragment<SynchronizedStateSavedList<ParcelableStatus, Long>> {

	private SharedPreferences mPreferences;
	
	private ParcelableStatusesAdapter mAdapter;
	private ListView mListView;

	private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (BROADCAST_STATUS_DESTROYED.equals(action)) {
				final long status_id = intent.getLongExtra(INTENT_KEY_STATUS_ID, -1);
				final boolean succeed = intent.getBooleanExtra(INTENT_KEY_SUCCEED, false);
				if (status_id > 0 && succeed) {
					deleteStatus(status_id);
				}
			} else if (BROADCAST_RETWEET_CHANGED.equals(action)) {
				final long status_id = intent.getLongExtra(INTENT_KEY_STATUS_ID, -1);
				final boolean retweeted = intent.getBooleanExtra(INTENT_KEY_RETWEETED, false);
				if (status_id > 0 && !retweeted) {
					deleteStatus(status_id);
				}
			} else if (BROADCAST_TWITLONGER_EXPANDED.equals(action)) {
				final String expanded_status_text = intent.getStringExtra(INTENT_KEY_TWITLONGER_EXPANDED_TEXT);
				final String original_url = intent.getStringExtra(INTENT_KEY_TWITLONGER_ORIGINAL_URL);
				final String user = intent.getStringExtra(INTENT_KEY_TWITLONGER_USER);
				
				NoDuplicatesArrayList<ParcelableStatus> statuses = mAdapter.getStatuses();
	    		
	            for (ParcelableStatus status : statuses) {
	            	if (status.text_html.contains(original_url) && status.screen_name.equals(user)) {
	            		status.text_html = expanded_status_text;
	            		status.text_plain = unescape(expanded_status_text);
	            		Spanned expanded_text = Html.fromHtml(expanded_status_text);
	    				status.text = expanded_text;
	    				getLoaderManager().restartLoader(0, null, ParcelableStatusesListFragment.this);
	    				break;
	            	}
	            }
			}

		}

	};

	public final void deleteStatus(final long status_id) {
		if (status_id <= 0 || mData == null) return;
		final ArrayList<ParcelableStatus> data_to_remove = new ArrayList<ParcelableStatus>();
		for (final ParcelableStatus status : mData) {
			if (status.status_id == status_id || status.retweet_id == status_id) {
				data_to_remove.add(status);
			}
		}
		mData.removeAll(data_to_remove);
		mAdapter.setData(mData);
	}

	@Override
	public final long[] getLastStatusIds() {
		final int last_idx = mAdapter.getCount() - 1;
		final long last_id = last_idx >= 0 ? mAdapter.getItem(last_idx).status_id : -1;
		return last_id > 0 ? new long[] { last_id } : null;
	}
	
	@Override
	public final long[] getNewestStatusIds() {
		final long last_id = mAdapter.getCount() > 0 ? mAdapter.getItem(0).status_id : -1;
		return last_id > 0 ? new long[] { last_id } : null;
	}

	@Override
	public final ParcelableStatusesAdapter getListAdapter() {
		return mAdapter;
	}

	@Override
	public final int getStatuses(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
		final long max_id = max_ids != null && max_ids.length == 1 ? max_ids[0] : -1;
		final long since_id = since_ids != null && since_ids.length == 1 ? since_ids[0] : -1;
		final Bundle args = (Bundle) getArguments().clone();
		args.putLong(INTENT_KEY_MAX_ID, max_id);
		args.putLong(INTENT_KEY_SINCE_ID, since_id);
		getLoaderManager().restartLoader(0, args, this);
		return -1;
	}

	public boolean isLoaderUsed() {
		return true;
	}

	public abstract Loader<SynchronizedStateSavedList<ParcelableStatus, Long>> newLoaderInstance(Bundle args);

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		if (savedInstanceState != null) {
			final List<ParcelableStatus> saved = savedInstanceState.getParcelableArrayList(INTENT_KEY_DATA);
			if (saved != null) {
				mData = new SynchronizedStateSavedList<ParcelableStatus, Long>(saved);
			}
		}
		mAdapter = new ParcelableStatusesAdapter(getActivity());
		mAdapter.setData(mData);
		super.onActivityCreated(savedInstanceState);
		mListView = getListView();
		mPreferences = getSharedPreferences();
	}

	@Override
	public final Loader<SynchronizedStateSavedList<ParcelableStatus, Long>> onCreateLoader(final int id,
			final Bundle args) {
		if (isLoaderUsed()) {
			setProgressBarIndeterminateVisibility(true);
		}
		return newLoaderInstance(args);
	}

	public void onDataLoaded(final Loader<SynchronizedStateSavedList<ParcelableStatus, Long>> loader,
			final ParcelableStatusesAdapter adapter) {
		if (loader instanceof ParcelableStatusesLoader) {
			final Long last_viewed_id = ((ParcelableStatusesLoader) loader).getLastViewedId();
			if (last_viewed_id != null && mPreferences.getBoolean(PREFERENCE_KEY_REMEMBER_POSITION, true)) {
				final int position = adapter.findItemPositionByStatusId(last_viewed_id);
				if (position > -1 && position < mListView.getCount()) {
					mListView.setSelection(position);
				}
			}
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
	}

	@Override
	public final void onLoaderReset(final Loader<SynchronizedStateSavedList<ParcelableStatus, Long>> loader) {
		super.onLoaderReset(loader);
		if (!isLoaderUsed()) return;
		onRefreshComplete();
		setProgressBarIndeterminateVisibility(false);
	}

	@Override
	public final void onLoadFinished(final Loader<SynchronizedStateSavedList<ParcelableStatus, Long>> loader,
			final SynchronizedStateSavedList<ParcelableStatus, Long> data) {
		super.onLoadFinished(loader, data);
		if (!isLoaderUsed()) return;
		mAdapter.setData(data);
		onDataLoaded(loader, mAdapter);
		onRefreshComplete();
		setProgressBarIndeterminateVisibility(false);
	}

	@Override
	public final void onPostStart() {
		if (isActivityFirstCreated()) {
			getLoaderManager().restartLoader(0, getArguments(), this);
		}
	}

	@Override
	public void onPullDownToRefresh() {
		final int count = mAdapter.getCount();
		final ParcelableStatus status = count > 0 ? mAdapter.getItem(0) : null;
		if (status != null) {
			getStatuses(new long[] { status.account_id }, null, new long[] { status.status_id });
		}
	}

	@Override
	public void onPullUpToRefresh() {
		final int count = mAdapter.getCount();
		final ParcelableStatus status = count > 0 ? mAdapter.getItem(count - 1) : null;
		if (status != null) {
			getStatuses(new long[] { status.account_id }, new long[] { status.status_id }, null);
		}
	}

	@Override
	public void onSaveInstanceState(final Bundle outState) {
		if (mData != null) {
			outState.putParcelableArrayList(INTENT_KEY_DATA, new ArrayList<ParcelableStatus>(mData));
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onStart() {
		super.onStart();
		final IntentFilter filter = new IntentFilter(BROADCAST_STATUS_DESTROYED);
		filter.addAction(BROADCAST_RETWEET_CHANGED);
		filter.addAction(BROADCAST_TWITLONGER_EXPANDED);
		registerReceiver(mStateReceiver, filter);
	}

	@Override
	public void onStop() {
		unregisterReceiver(mStateReceiver);
		super.onStop();
	}
	
}
