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

package com.dwdesign.tweetings.loader;

import static com.dwdesign.tweetings.util.Utils.getTwitterInstance;

import java.util.ConcurrentModificationException;
import java.util.List;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.util.NoDuplicatesArrayList;
import com.dwdesign.tweetings.util.SynchronizedStateSavedList;

import twitter4j.Twitter;
import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;

public abstract class ParcelableStatusesLoader extends
		AsyncTaskLoader<SynchronizedStateSavedList<ParcelableStatus, Long>> implements Constants {

	protected final Twitter mTwitter;
	protected final long mAccountId;
	private final String mClassName;
	private final SynchronizedStateSavedList<ParcelableStatus, Long> mData = new SynchronizedStateSavedList<ParcelableStatus, Long>();
	private final boolean mFirstLoad, mIsHomeTab;

	private Long mLastViewedId;

	public ParcelableStatusesLoader(final Context context, final long account_id, final List<ParcelableStatus> data,
			final String class_name, final boolean is_home_tab) {
		super(context);
		mClassName = class_name;
		mTwitter = getTwitterInstance(context, account_id, true);
		mAccountId = account_id;
		mFirstLoad = data == null;
		if (data != null) {
			mData.addAll(data);
		}
		mIsHomeTab = is_home_tab;
	}

	public Long getLastViewedId() {
		return mLastViewedId;
	}

	protected boolean containsStatus(final long status_id) {
		for (final ParcelableStatus status : mData) {
			if (status.status_id == status_id) return true;
		}
		return false;
	}

	protected boolean deleteStatus(final long status_id) {
		try {
			final NoDuplicatesArrayList<ParcelableStatus> data_to_remove = new NoDuplicatesArrayList<ParcelableStatus>();
			for (final ParcelableStatus status : mData) {
				if (status.status_id == status_id) {
					data_to_remove.add(status);
				}
			}
			return mData.removeAll(data_to_remove);
		} catch (final ConcurrentModificationException e) {
			// This shouldn't happen.
		}
		return false;
	}

	protected String getClassName() {
		return mClassName;
	}

	protected SynchronizedStateSavedList<ParcelableStatus, Long> getData() {
		return mData;
	}

	protected boolean isFirstLoad() {
		return mFirstLoad;
	}

	protected boolean isHomeTab() {
		return mIsHomeTab;
	}

	@Override
	protected void onStartLoading() {
		forceLoad();
	}

	protected void setLastViewedId(final Long id) {
		mLastViewedId = id;
	}

}
