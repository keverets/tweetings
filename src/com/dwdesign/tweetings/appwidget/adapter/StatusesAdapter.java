package com.dwdesign.tweetings.appwidget.adapter;

import static com.dwdesign.tweetings.appwidget.util.Utils.buildActivatedStatsWhereClause;
import static com.dwdesign.tweetings.appwidget.util.Utils.buildFilterWhereClause;
import static com.dwdesign.tweetings.appwidget.util.Utils.getAccountColor;
import static com.dwdesign.tweetings.appwidget.util.Utils.getActivatedAccountIds;
import static com.dwdesign.tweetings.appwidget.util.Utils.getFilename;
import static com.dwdesign.tweetings.appwidget.util.Utils.getRoundedCornerBitmap;
import static com.dwdesign.tweetings.appwidget.util.Utils.getTableNameForContentUri;
import static com.dwdesign.tweetings.appwidget.util.Utils.getTweetingsCacheDir;
import static com.dwdesign.tweetings.util.Utils.getBiggerTwitterProfileImage;
import static com.dwdesign.tweetings.util.Utils.parseURL;

import java.io.File;
import java.net.URL;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.model.StatusCursorIndices;
import com.dwdesign.tweetings.provider.TweetStore.Statuses;
import com.dwdesign.tweetings.util.HtmlEscapeHelper;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService.RemoteViewsFactory;

@TargetApi(11)
public abstract class StatusesAdapter implements RemoteViewsFactory, Constants {

	private final int layout;

	private final Context context;
	private final ContentResolver resolver;
	private final Resources resources;
	private final SharedPreferences preferences;
	private Cursor cursor;
	private StatusCursorIndices indices;
	private TweetingsApplication mApplication;

	private boolean should_show_account_color;

	public StatusesAdapter(final Context context, final int layout) {
		this.layout = layout;
		this.context = context;
		resolver = context.getContentResolver();
		resources = context.getResources();
		preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mApplication = TweetingsApplication.getInstance(context);
		
		
	}

	public abstract Uri getContentUri();

	@Override
	public int getCount() {
		return cursor != null ? cursor.getCount() : 0;
	}

	@Override
	public long getItemId(final int position) {
		if (cursor == null) return -1;
		cursor.moveToPosition(position);
		return cursor.getLong(indices.status_id);
	}

	@Override
	public RemoteViews getLoadingView() {
		// return new RemoteViews(context.getPackageName(),
		// R.layout.status_item_loading);
		return null;
	}

	@Override
	public RemoteViews getViewAt(final int position) {
		final RemoteViews views = new RemoteViews(context.getPackageName(), layout);
		if (cursor == null || indices == null) return views;
		cursor.moveToPosition(position);
		if (!preferences.getBoolean(PREFERENCE_KEY_DISPLAY_NAME, false)) {
			views.setTextViewText(R.id.name, cursor.getString(indices.screen_name));
		} else {
			views.setTextViewText(R.id.name, cursor.getString(indices.name));
		}
		views.setTextViewText(R.id.text, HtmlEscapeHelper.unescape(cursor.getString(indices.text)));
		views.setTextViewText(R.id.time, DateUtils.getRelativeTimeSpanString(cursor.getLong(indices.status_timestamp)));
		
		final Uri.Builder uri_builder = new Uri.Builder();
		uri_builder.scheme(SCHEME_TWEETINGS);
		uri_builder.authority(AUTHORITY_STATUS);
		uri_builder.appendQueryParameter(QUERY_PARAM_ACCOUNT_ID,
				String.valueOf(cursor.getLong(indices.account_id)));
		uri_builder.appendQueryParameter(QUERY_PARAM_STATUS_ID,
				String.valueOf(cursor.getLong(indices.status_id)));
		//final Intent intent = new Intent(Intent.ACTION_VIEW, uri_builder.build());
		//final PendingIntent pending_intent = PendingIntent.getActivity(context, 0, intent,
		//		Intent.FLAG_ACTIVITY_NEW_TASK);
		
		//views.setOnClickPendingIntent(R.id.tweet_item, pending_intent);
		
		//views.setPendingIntentTemplate(R.id.tweet_item, pending_intent);*/
		
		Intent fillInIntent = new Intent();
		fillInIntent.setData(uri_builder.build());
		views.setOnClickFillInIntent(R.id.tweet_item, fillInIntent);
		
		if (!preferences.getBoolean(PREFERENCE_KEY_DISPLAY_PROFILE_IMAGE, true)) {
			views.setViewVisibility(R.id.profile_image, View.GONE);
		} else {
			views.setViewVisibility(R.id.profile_image, View.VISIBLE);
			
			try {
				final String profile_image_url = cursor.getString(indices.profile_image_url);
				URL final_url = parseURL(getBiggerTwitterProfileImage(profile_image_url));
				
				File profile_image_file = mApplication.mProfileImageLoader.getCachedImageFile(String.valueOf(final_url));
				final Bitmap profile_image = profile_image_file != null && profile_image_file.isFile() ? BitmapFactory
						.decodeFile(profile_image_file.getPath()) : null;
						
				
				/*
				
				final File cache_dir = getTweetingsCacheDir();
				final String profile_image_url = cursor.getString(indices.profile_image_url);
				final String file_name = getFilename(resources.getBoolean(R.bool.hires_profile_image) ? getBiggerTwitterProfileImage(profile_image_url)
						: profile_image_url);
				final File profile_image_file = cache_dir != null && cache_dir.isDirectory() && file_name != null ? new File(
						cache_dir, file_name) : null;
				final Bitmap profile_image = profile_image_file != null && profile_image_file.isFile() ? BitmapFactory
						.decodeFile(profile_image_file.getPath()) : null;*/
				if (profile_image != null) {
					views.setImageViewBitmap(R.id.profile_image, getRoundedCornerBitmap(resources, profile_image));
				} else {
					views.setImageViewResource(R.id.profile_image, R.drawable.ic_profile_image_default);
				}
			}catch (Exception e) {
				views.setImageViewResource(R.id.profile_image, R.drawable.ic_profile_image_default);
			}
		}
		final long account_id = cursor.getLong(indices.account_id);
		views.setInt(R.id.account_color, "setBackgroundColor",
				should_show_account_color ? getAccountColor(context, account_id) : Color.TRANSPARENT);

		return views;
	}

	@Override
	public int getViewTypeCount() {
		return 1;
	}

	@Override
	public boolean hasStableIds() {
		return true;
	}

	@Override
	public void onCreate() {
	}

	@Override
	public void onDataSetChanged() {
		final Uri uri = getContentUri();
		final String[] cols = new String[] { Statuses._ID, Statuses.ACCOUNT_ID, Statuses.STATUS_ID, Statuses.TEXT,
				Statuses.SCREEN_NAME, Statuses.NAME, Statuses.STATUS_TIMESTAMP, Statuses.PROFILE_IMAGE_URL };
		String where = buildActivatedStatsWhereClause(context, null);
		if (preferences.getBoolean(PREFERENCE_KEY_ENABLE_FILTER, false)) {
			final String table = getTableNameForContentUri(uri);
			where = buildFilterWhereClause(table, where);
		}
		cursor = resolver.query(uri, cols, where, null, Statuses.DEFAULT_SORT_ORDER);
		indices = new StatusCursorIndices(cursor);
		should_show_account_color = getActivatedAccountIds(context).length > 1;
	}

	@Override
	public void onDestroy() {
		if (cursor != null) {
			cursor.close();
		}
		cursor = null;
	}
}