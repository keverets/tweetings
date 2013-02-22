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

import static com.dwdesign.tweetings.util.Utils.getInlineImagePreviewDisplayOptionInt;
import static com.dwdesign.tweetings.util.Utils.isFiltered;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.util.SynchronizedStateSavedList;

import twitter4j.Paging;
import twitter4j.Status;
import twitter4j.TwitterException;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ConcurrentModificationException;

public abstract class Twitter4JStatusLoader extends ParcelableStatusesLoader {

	private final long mMaxId, mSinceId;
	private final int mInlineImagePreviewDisplayOption;

	public Twitter4JStatusLoader(final Context context, final long account_id, final long max_id, final long since_id, final List<ParcelableStatus> data,
			final String class_name, final boolean is_home_tab) {
		super(context, account_id, data, class_name, is_home_tab);
		mMaxId = max_id;
		mSinceId = since_id;
		final String inline_image_preview_display_option = context.getSharedPreferences(SHARED_PREFERENCES_NAME,
				Context.MODE_PRIVATE).getString(PREFERENCE_KEY_INLINE_IMAGE_PREVIEW_DISPLAY_OPTION,
			 	INLINE_IMAGE_PREVIEW_DISPLAY_OPTION_SMALL);
			 	mInlineImagePreviewDisplayOption = getInlineImagePreviewDisplayOptionInt(inline_image_preview_display_option);
	}

	public abstract List<Status> getStatuses(Paging paging) throws TwitterException;

	@SuppressWarnings("unchecked")
	@Override
	public SynchronizedStateSavedList<ParcelableStatus, Long> loadInBackground() {
		final SynchronizedStateSavedList<ParcelableStatus, Long> data = getData();
		List<Status> statuses = null;
		
		final Context context = getContext();
		final SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		final int load_item_limit = prefs.getInt(PREFERENCE_KEY_LOAD_ITEM_LIMIT, PREFERENCE_DEFAULT_LOAD_ITEM_LIMIT);
		try {
			final Paging paging = new Paging();
			paging.setCount(load_item_limit);
			if (mMaxId > 0) {
				paging.setMaxId(mMaxId);
			}
			if (mSinceId > 0) {
				paging.setSinceId(mSinceId);
			}
			statuses = getStatuses(paging);
		} catch (final TwitterException e) {
			e.printStackTrace();
		}
		if (statuses != null) {
			final boolean insert_gap = load_item_limit == statuses.size() && data.size() > 0;
			final Status min_status = statuses.size() > 0 ? Collections.min(statuses) : null;
			final long min_status_id = min_status != null ? min_status.getId() : -1;
			for (final Status status : statuses) {
				final long id = status.getId();
				deleteStatus(id);
				data.add(new ParcelableStatus(status, mAccountId, min_status_id > 0 && min_status_id == id && insert_gap, mInlineImagePreviewDisplayOption == INLINE_IMAGE_PREVIEW_DISPLAY_OPTION_CODE_LARGE_HIGH));
			}
		}
		try {
			final List<ParcelableStatus> statuses_to_remove = new ArrayList<ParcelableStatus>();
			for (final ParcelableStatus status : data) {
				if (isFiltered(context, status.screen_name, status.source, status.text_plain) && !status.is_gap) {
					statuses_to_remove.add(status);
				}
			}
			data.removeAll(statuses_to_remove);
			Collections.sort(data);
		} catch (final ConcurrentModificationException e) {
			Log.w(LOGTAG, e);
		}
		return data;
	}
}
