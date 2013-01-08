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

package com.dwdesign.tweetings.adapter;

import static com.dwdesign.tweetings.util.Utils.getBiggerTwitterProfileImage;
import static com.dwdesign.tweetings.util.Utils.matcherEnd;
import static com.dwdesign.tweetings.util.Utils.matcherGroup;
import static com.dwdesign.tweetings.util.Utils.matcherStart;
import static com.dwdesign.tweetings.util.Utils.parseURL;

import java.util.regex.Matcher;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.provider.TweetStore.CachedUsers;
import com.dwdesign.tweetings.provider.TweetStore.Statuses;
import com.dwdesign.tweetings.util.LazyImageLoader;
import com.twitter.Regex;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.SpannableString;
import android.view.View;
import android.widget.FilterQueryProvider;
import android.widget.ImageView;
import android.widget.TextView;

public class UserAutoCompleteAdapter extends SimpleCursorAdapter implements Constants {

	private Cursor mCursor;

	private final ContentResolver mResolver;
	private final LazyImageLoader mProfileImageLoader;
	private final SharedPreferences mPreferences;
	private static final String[] FROM = new String[] { CachedUsers.NAME, CachedUsers.SCREEN_NAME };
	private static final int[] TO = new int[] { android.R.id.text1, android.R.id.text2 };

	private int mProfileImageUrlIdx, mScreenNameIdx, mStatusText;
	private String mLookupText;
	
	private boolean isHash = false;

	private boolean mCursorClosed = false;

	private final boolean mDisplayProfileImage, mDisplayHiResProfileImage;

	public UserAutoCompleteAdapter(final Context context) {
		super(context, R.layout.user_autocomplete_list_item, null, FROM, TO, 0);
		mPreferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mResolver = context.getContentResolver();
		final Context app_context = context.getApplicationContext();
		mProfileImageLoader = app_context instanceof TweetingsApplication ? ((TweetingsApplication) app_context)
				.getProfileImageLoader() : null;
		mDisplayProfileImage = mPreferences != null ? mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_PROFILE_IMAGE,
				true) : true;
		mDisplayHiResProfileImage = context.getResources().getBoolean(R.bool.hires_profile_image);
	}

	@Override
	public void bindView(final View view, final Context context, final Cursor cursor) {
		if (mCursorClosed) return;
		super.bindView(view, context, cursor);
		if (mLookupText != null) {
			
			String statusText = cursor.getString(mStatusText);
			String finalUrl = statusText;
			SpannableString spannable = new SpannableString(statusText);
			final Matcher matcher = Regex.VALID_HASHTAG.matcher(spannable);
			while (matcher.find()) {
				final String url = matcherGroup(matcher, Regex.VALID_HASHTAG_GROUP_HASHTAG_FULL);
				if (url.toLowerCase().startsWith(mLookupText.toLowerCase())) {
					finalUrl = url;
					//break;
				}
			}
			
			final ImageView image_view = (ImageView) view.findViewById(android.R.id.icon);
			image_view.setVisibility(View.GONE);
			TextView text_view1 = (TextView) view.findViewById(android.R.id.text1);
			text_view1.setText(finalUrl);
			TextView text_view2 = (TextView) view.findViewById(android.R.id.text2);
			text_view2.setVisibility(View.GONE);
			//text_view2.setText(cursor.getString(mStatusText));
			
		}
		else {
			final ImageView image_view = (ImageView) view.findViewById(android.R.id.icon);
			image_view.setVisibility(mDisplayProfileImage ? View.VISIBLE : View.GONE);
			if (mDisplayProfileImage && mProfileImageLoader != null) {
				final String profile_image_url_string = cursor.getString(mProfileImageUrlIdx);
				mProfileImageLoader.displayImage(parseURL(cursor.getString(mProfileImageUrlIdx)), image_view);
				if (mDisplayHiResProfileImage) {
					mProfileImageLoader.displayImage(parseURL(getBiggerTwitterProfileImage(profile_image_url_string)),
							image_view);
				} else {
					mProfileImageLoader.displayImage(parseURL(profile_image_url_string), image_view);
				}
			}
			TextView text_view1 = (TextView) view.findViewById(android.R.id.text1);
			text_view1.setText(cursor.getString(mScreenNameIdx));
			TextView text_view2 = (TextView) view.findViewById(android.R.id.text2);
			text_view2.setText(cursor.getString(mScreenNameIdx));
			
		}
		
	}

	@Override
	public void changeCursor(final Cursor cursor) {
		if (mCursorClosed) return;
		if (cursor != null) {
			if (mLookupText != null) {
				mStatusText = cursor.getColumnIndexOrThrow(Statuses.TEXT_PLAIN);
			}
			else {
				mProfileImageUrlIdx = cursor.getColumnIndexOrThrow(Statuses.PROFILE_IMAGE_URL);
				mScreenNameIdx = cursor.getColumnIndexOrThrow(CachedUsers.SCREEN_NAME);
			}
		}
		mCursor = cursor;
		super.changeCursor(mCursor);
	}

	public void closeCursor() {
		if (mCursor != null && !mCursor.isClosed()) {
			mCursor.close();
		}
		mCursor = null;
		mCursorClosed = true;
	}

	@Override
	public CharSequence convertToString(final Cursor cursor) {
		if (mCursorClosed) return null;
		if (isHash && mLookupText != null) {
			String statusText = cursor.getString(mStatusText);
			SpannableString spannable = new SpannableString(statusText);
			final Matcher matcher = Regex.VALID_HASHTAG.matcher(spannable);
			while (matcher.find()) {
				final String url = matcherGroup(matcher, Regex.VALID_HASHTAG_GROUP_HASHTAG_FULL);
				if (url.toLowerCase().startsWith(mLookupText.toLowerCase())) {
					return url;
				}
			}
		}
		else {
			return "@" + cursor.getString(mScreenNameIdx);
		}
		return null;
	}

	public boolean isCursorClosed() {
		return mCursorClosed;
	}

	@Override
	public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
		if (mCursorClosed) return null;
		final FilterQueryProvider filter = getFilterQueryProvider();
		if (filter != null) return filter.runQuery(constraint);
		final StringBuilder where = new StringBuilder();
		constraint = constraint != null ? constraint.toString().replaceAll("_", "^_") : null;
		constraint = constraint != null ? constraint.toString().replaceAll("'", ""): null;
		isHash = false;
		if (constraint != null) {
			if (constraint.length() > 1 && constraint.charAt(0) == '@') {
				isHash = false;
				
				mLookupText = null;
				final String lookup = constraint.subSequence(1, constraint.length() - 1).toString();
				where.append(CachedUsers.SCREEN_NAME + " LIKE '" + lookup + "%' ESCAPE '^'");
				where.append(" OR ");
				where.append(CachedUsers.NAME + " LIKE '" + lookup + "%' ESCAPE '^'");
				return mResolver.query(CachedUsers.CONTENT_URI, CachedUsers.COLUMNS, lookup != null ? where.toString()
						: null, null, null);
			}
			else if (constraint.length() > 0 && constraint.charAt(0) == '@') {
				isHash = false;
				mLookupText = null;
				
				return mResolver.query(CachedUsers.CONTENT_URI, CachedUsers.COLUMNS, null, null, null);
			}
			else if (constraint.length() > 1 && constraint.charAt(0) == '#') {
				isHash = true;
				mLookupText = constraint.toString();
				where.append(Statuses.TEXT_PLAIN + " LIKE '%" + constraint + "%' ESCAPE '^'");
				return mResolver.query(Statuses.CONTENT_URI, Statuses.COLUMNS, constraint != null ? where.toString()
						: null, null, null);
			}
			else if (constraint.length() > 0 && constraint.charAt(0) == '#') {
				isHash = true;
				mLookupText = null;
				
				return mResolver.query(Statuses.CONTENT_URI, Statuses.COLUMNS, null, null, null);
			}
		}
		return null;
	}

}
