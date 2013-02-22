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
package com.dwdesign.tweetings.appwidget.service;

import com.dwdesign.tweetings.appwidget.Constants;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.appwidget.adapter.StatusesAdapter;
import com.dwdesign.tweetings.provider.TweetStore.Statuses;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.RemoteViewsService;

@TargetApi(11)
public class ListWidgetHomeTimelineService extends RemoteViewsService implements Constants {

	public static final String BROADCAST_REFRESH_ALL = "com.dwdesign.tweetings.appwidget.REFRESH_ALL";

	private StatusesAdapter mHomeTimelineAdapter;

	@Override
	public void onCreate() {
		super.onCreate();
		mHomeTimelineAdapter = new HomeTimelineAdapter(this);
	}

	@Override
	public RemoteViewsFactory onGetViewFactory(final Intent intent) {
		return mHomeTimelineAdapter;
	}

	public static class HomeTimelineAdapter extends StatusesAdapter {

		public HomeTimelineAdapter(final Context context) {
			super(context, R.layout.list_status_item);
		}

		@Override
		public Uri getContentUri() {
			return Statuses.CONTENT_URI;
		}

	}

}
