/*
 *				Twidere - Twitter client for Android
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
package com.dwdesign.tweetings.appwidget.service;

import com.dwdesign.tweetings.appwidget.Constants;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.appwidget.adapter.StatusesAdapter;
import com.dwdesign.tweetings.provider.TweetStore.Mentions;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViewsService;

@TargetApi(11)
public class ListWidgetMentionsService extends RemoteViewsService implements Constants {

	private StatusesAdapter mMentionsAdapter;

	@Override
	public void onCreate() {
		super.onCreate();
		mMentionsAdapter = new MentionsAdapter(this);
	}

	@Override
	public RemoteViewsFactory onGetViewFactory(final Intent intent) {
		return mMentionsAdapter;
	}

	public static class MentionsAdapter extends StatusesAdapter {

		public MentionsAdapter(final Context context) {
			super(context, R.layout.list_status_item);
		}

		@Override
		public Uri getContentUri() {
			return Mentions.CONTENT_URI;
		}

	}
}
