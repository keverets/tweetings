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

package com.dwdesign.tweetings.provider;

import static com.dwdesign.tweetings.util.DatabaseUpgradeHelper.safeUpgrade;
import static com.dwdesign.tweetings.util.Utils.clearAccountColor;
import static com.dwdesign.tweetings.util.Utils.clearAccountName;
import static com.dwdesign.tweetings.util.Utils.getTableId;
import static com.dwdesign.tweetings.util.Utils.getTableNameForContentUri;
import static com.dwdesign.tweetings.util.Utils.showErrorToast;
import static com.dwdesign.tweetings.util.Utils.isOnWifi;

import java.util.List;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.model.ImageSpec;
import com.dwdesign.tweetings.provider.TweetStore.Accounts;
import com.dwdesign.tweetings.provider.TweetStore.CachedTrends;
import com.dwdesign.tweetings.provider.TweetStore.CachedUsers;
import com.dwdesign.tweetings.provider.TweetStore.DirectMessages;
import com.dwdesign.tweetings.provider.TweetStore.Drafts;
import com.dwdesign.tweetings.provider.TweetStore.Filters;
import com.dwdesign.tweetings.provider.TweetStore.Mentions;
import com.dwdesign.tweetings.provider.TweetStore.Statuses;
import com.dwdesign.tweetings.provider.TweetStore.Tabs;
import com.dwdesign.tweetings.util.ArrayUtils;
import com.dwdesign.tweetings.util.ImagePreloader;
import com.dwdesign.tweetings.util.Utils;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;

public final class TweetStoreProvider extends ContentProvider implements Constants {
	
	private Context mContext;
	
	private SQLiteDatabase database;
	private SharedPreferences mPreferences;
	private ImagePreloader mImagePreloader;

	private final Handler mErrorToastHandler = new Handler() {

		@Override
		public void handleMessage(final Message msg) {
			if (msg.obj instanceof Exception) {
				showErrorToast(getContext(), null, msg.obj, false);
			}
			super.handleMessage(msg);
		}

	};

	@Override
	public int bulkInsert(final Uri uri, final ContentValues[] values) {
		final String table = getTableNameForContentUri(uri);
		int result = 0;
		if (table != null) {
			database.beginTransaction();
			for (final ContentValues contentValues : values) {
				database.insert(table, null, contentValues);
				result++;
			}
			database.setTransactionSuccessful();
			database.endTransaction();
		}
		if (result > 0) {
			onDatabaseUpdated(uri, false);
		}
		onNewItemsInserted(uri, values);
		return result;
	};

	@Override
	public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
		final String table = getTableNameForContentUri(uri);
		int result = 0;
		if (table != null) {
			try {
				result = database.delete(table, selection, selectionArgs);
			} catch (final SQLiteException e) {
				mErrorToastHandler.sendMessage(mErrorToastHandler.obtainMessage(0, e));
			}
		}
		if (result > 0) {
			onDatabaseUpdated(uri, false);
		}
		return result;
	}

	@Override
	public String getType(final Uri uri) {
		return null;
	}

	@Override
	public Uri insert(final Uri uri, final ContentValues values) {
		final String table = getTableNameForContentUri(uri);
		if (table == null) return null;
		if (TABLE_DIRECT_MESSAGES_CONVERSATION.equals(table))
			// read-only here.
			return null;
		else if (TABLE_DIRECT_MESSAGES.equals(table)) // read-only here.
			return null;
		else if (TABLE_DIRECT_MESSAGES_CONVERSATIONS_ENTRY.equals(table)) // read-only
																			// here.
			return null;
		final long row_id = database.insert(table, null, values);
		onDatabaseUpdated(uri, true);
		onNewItemsInserted(uri, values);
		try {
			return Uri.withAppendedPath(uri, String.valueOf(row_id));
		} catch (final SQLiteException e) {
			mErrorToastHandler.sendMessage(mErrorToastHandler.obtainMessage(0, e));
		}
		return null;
	}
	
	private void onNewItemsInserted(final Uri uri, final ContentValues... values) {
		if (uri == null || values == null || values.length == 0) return;
		preloadImages(values);
	}

	private void preloadImages(final ContentValues... values) {
		if (values == null) return;
		boolean shouldPreload = true;
		if (mPreferences.getBoolean(PREFERENCE_KEY_PRELOAD_WIFI_ONLY, true) 
				&& (mPreferences.getBoolean(PREFERENCE_KEY_PRELOAD_PROFILE_IMAGES, false) 
						|| mPreferences.getBoolean(PREFERENCE_KEY_PRELOAD_PREVIEW_IMAGES, false))) {
			shouldPreload = false;
			if (isOnWifi(mContext)) {
				shouldPreload = true;
			}
		}
		if (shouldPreload) {
			for (final ContentValues v : values) {
				if (mPreferences.getBoolean(PREFERENCE_KEY_PRELOAD_PROFILE_IMAGES, false)) {
					final String profile_image_url = v.getAsString(Statuses.PROFILE_IMAGE_URL);
					if (profile_image_url != null) {
						mImagePreloader.preloadImage(DIR_NAME_IMAGE_CACHE, profile_image_url);
					}
					final String sender_profile_image_url = v.getAsString(DirectMessages.SENDER_PROFILE_IMAGE_URL);
					if (sender_profile_image_url != null) {
						mImagePreloader.preloadImage(DIR_NAME_IMAGE_CACHE, sender_profile_image_url);
					}
					final String recipient_profile_image_url = v.getAsString(DirectMessages.RECIPIENT_PROFILE_IMAGE_URL);
					if (recipient_profile_image_url != null) {
						mImagePreloader.preloadImage(DIR_NAME_IMAGE_CACHE, recipient_profile_image_url);
					}
				}
				if (mPreferences.getBoolean(PREFERENCE_KEY_PRELOAD_PREVIEW_IMAGES, false)) {
					final String text_html = v.getAsString(Statuses.TEXT);
					for (final ImageSpec spec : Utils.getImagesInStatus(text_html)) {
						if (spec != null && spec.preview_image_link != null) {
							mImagePreloader.preloadImage(DIR_NAME_IMAGE_CACHE, spec.preview_image_link);
						}
					}
				}
			}
		}
	}

	@Override
	public boolean onCreate() {
		mContext = getContext();
		database = new DatabaseHelper(getContext(), DATABASES_NAME, DATABASES_VERSION).getWritableDatabase();
		mImagePreloader = new ImagePreloader(mContext);
		mPreferences = mContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		return database != null;
	}

	@Override
	public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs, final String sortOrder) {
		final String table = getTableNameForContentUri(uri);
		if (table == null) return null;
		final String projection_string = projection != null ? ArrayUtils.toString(projection, ',', false) : "*";
		if (TABLE_DIRECT_MESSAGES_CONVERSATION.equals(table)) {
			// read-only here.
			final List<String> segments = uri.getPathSegments();
			if (segments.size() != 3) return null;
			final StringBuilder sql_builder = new StringBuilder();
			sql_builder.append("SELECT " + projection_string);
			sql_builder.append(" FROM " + TABLE_DIRECT_MESSAGES_INBOX);
			sql_builder.append(" WHERE " + DirectMessages.ACCOUNT_ID + " = " + segments.get(1));
			sql_builder.append(" AND " + DirectMessages.SENDER_ID + " = " + segments.get(2));
			if (selection != null) {
				sql_builder.append(" AND " + selection);
			}
			sql_builder.append(" UNION ");
			sql_builder.append("SELECT " + projection_string);
			sql_builder.append(" FROM " + TABLE_DIRECT_MESSAGES_OUTBOX);
			sql_builder.append(" WHERE " + DirectMessages.ACCOUNT_ID + " = " + segments.get(1));
			sql_builder.append(" AND " + DirectMessages.RECIPIENT_ID + " = " + segments.get(2));
			if (selection != null) {
				sql_builder.append(" AND " + selection);
			}
			sql_builder.append(" ORDER BY "
					+ (sortOrder != null ? sortOrder : DirectMessages.Conversation.DEFAULT_SORT_ORDER));
			try {
				return database.rawQuery(sql_builder.toString(), selectionArgs);
			} catch (final SQLiteException e) {
				mErrorToastHandler.sendMessage(mErrorToastHandler.obtainMessage(0, e));
			}
		} else if (TABLE_DIRECT_MESSAGES_CONVERSATION_SCREEN_NAME.equals(table)) {
			// read-only here.
			final List<String> segments = uri.getPathSegments();
			if (segments.size() != 3) return null;
			final StringBuilder sql_builder = new StringBuilder();
			sql_builder.append("SELECT " + projection_string);
			sql_builder.append(" FROM " + TABLE_DIRECT_MESSAGES_INBOX);
			sql_builder.append(" WHERE " + DirectMessages.ACCOUNT_ID + " = " + segments.get(1));
			sql_builder.append(" AND " + DirectMessages.SENDER_SCREEN_NAME + " = '" + segments.get(2) + "'");
			if (selection != null) {
				sql_builder.append(" AND " + selection);
			}
			sql_builder.append(" UNION ");
			sql_builder.append("SELECT " + projection_string);
			sql_builder.append(" FROM " + TABLE_DIRECT_MESSAGES_OUTBOX);
			sql_builder.append(" WHERE " + DirectMessages.ACCOUNT_ID + " = " + segments.get(1));
			sql_builder.append(" AND " + DirectMessages.RECIPIENT_SCREEN_NAME + " = '" + segments.get(2) + "'");
			if (selection != null) {
				sql_builder.append(" AND " + selection);
			}
			sql_builder.append(" ORDER BY "
					+ (sortOrder != null ? sortOrder : DirectMessages.Conversation.DEFAULT_SORT_ORDER));
			try {
				return database.rawQuery(sql_builder.toString(), selectionArgs);
			} catch (final SQLiteException e) {
				mErrorToastHandler.sendMessage(mErrorToastHandler.obtainMessage(0, e));
			}
		} else if (TABLE_DIRECT_MESSAGES.equals(table)) {
			// read-only here.
			final StringBuilder sql_builder = new StringBuilder();
			sql_builder.append("SELECT " + projection_string);
			sql_builder.append(" FROM " + TABLE_DIRECT_MESSAGES_INBOX);
			if (selection != null) {
				sql_builder.append(" WHERE " + selection);
			}
			sql_builder.append(" UNION ");
			sql_builder.append("SELECT " + projection_string);
			sql_builder.append(" FROM " + TABLE_DIRECT_MESSAGES_OUTBOX);
			if (selection != null) {
				sql_builder.append(" WHERE " + selection);
			}
			sql_builder.append(" ORDER BY " + (sortOrder != null ? sortOrder : DirectMessages.DEFAULT_SORT_ORDER));
			try {
				return database.rawQuery(sql_builder.toString(), selectionArgs);
			} catch (final SQLiteException e) {
				mErrorToastHandler.sendMessage(mErrorToastHandler.obtainMessage(0, e));
			}
		} else if (TABLE_DIRECT_MESSAGES_CONVERSATIONS_ENTRY.equals(table)) {
			try {
				return database.rawQuery(DirectMessages.ConversationsEntry.buildSQL(selection), null);
			} catch (final SQLiteException e) {
				mErrorToastHandler.sendMessage(mErrorToastHandler.obtainMessage(0, e));
			}
		} else {
			try {
				return database.query(table, projection, selection, selectionArgs, null, null, sortOrder);
			} catch (final SQLiteException e) {
				mErrorToastHandler.sendMessage(mErrorToastHandler.obtainMessage(0, e));
			}
		}
		return null;
	}

	@Override
	public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
		final String table = getTableNameForContentUri(uri);
		int result = 0;
		if (table != null) {
			if (TABLE_DIRECT_MESSAGES_CONVERSATION.equals(table))
				// read-only here.
				return 0;
			else if (TABLE_DIRECT_MESSAGES.equals(table)) // read-only here.
				return 0;
			else if (TABLE_DIRECT_MESSAGES_CONVERSATIONS_ENTRY.equals(table)) // read-only
																				// here.
				return 0;
			try {
				result = database.update(table, values, selection, selectionArgs);
			} catch (final SQLiteException e) {
				mErrorToastHandler.sendMessage(mErrorToastHandler.obtainMessage(0, e));
			}
		}
		if (result > 0) {
			boolean notifyUpdate = true;
			if (TABLE_ACCOUNTS.equals(table)) {
				if (selection != null && selection.endsWith("AND 1 = 1")) {
					notifyUpdate = false;
				}
			}
			if (notifyUpdate) {
				onDatabaseUpdated(uri, false);
			}
		}
		return result;
	}

	private void onDatabaseUpdated(final Uri uri, final boolean is_insert) {
		if (uri == null || "false".equals(uri.getQueryParameter(QUERY_PARAM_NOTIFY))) return;
		final Context context = getContext();
		switch (getTableId(uri)) {
			case URI_ACCOUNTS: {
				clearAccountColor();
				clearAccountName();
				context.sendBroadcast(new Intent(BROADCAST_ACCOUNT_LIST_DATABASE_UPDATED));
				break;
			}
			case URI_DRAFTS: {
				context.sendBroadcast(new Intent(BROADCAST_DRAFTS_DATABASE_UPDATED));
				break;
			}
			case URI_STATUSES: {
				if (!is_insert || "true".equals(uri.getQueryParameter(QUERY_PARAM_NOTIFY))) {
					context.sendBroadcast(new Intent(BROADCAST_HOME_TIMELINE_DATABASE_UPDATED).putExtra(
							INTENT_KEY_SUCCEED, true));
				}
				break;
			}
			case URI_MENTIONS: {
				if (!is_insert || "true".equals(uri.getQueryParameter(QUERY_PARAM_NOTIFY))) {
					context.sendBroadcast(new Intent(BROADCAST_MENTIONS_DATABASE_UPDATED).putExtra(INTENT_KEY_SUCCEED,
							true));
				}
				break;
			}
			case URI_DIRECT_MESSAGES_INBOX: {
				if (!is_insert || "true".equals(uri.getQueryParameter(QUERY_PARAM_NOTIFY))) {
					context.sendBroadcast(new Intent(BROADCAST_RECEIVED_DIRECT_MESSAGES_DATABASE_UPDATED).putExtra(
							INTENT_KEY_SUCCEED, true));
				}
				break;
			}
			case URI_DIRECT_MESSAGES_OUTBOX: {
				if (!is_insert || "true".equals(uri.getQueryParameter(QUERY_PARAM_NOTIFY))) {
					context.sendBroadcast(new Intent(BROADCAST_SENT_DIRECT_MESSAGES_DATABASE_UPDATED).putExtra(
							INTENT_KEY_SUCCEED, true));
				}
				break;
			}
			case URI_TRENDS_DAILY:
			case URI_TRENDS_WEEKLY:
			case URI_TRENDS_LOCAL:
				if (!is_insert || "true".equals(uri.getQueryParameter(QUERY_PARAM_NOTIFY))) {
					context.sendBroadcast(new Intent(BROADCAST_TRENDS_UPDATED).putExtra(INTENT_KEY_SUCCEED, true));
				}
				break;
			case URI_TABS: {
				if (!"false".equals(uri.getQueryParameter(QUERY_PARAM_NOTIFY))) {
					context.sendBroadcast(new Intent(BROADCAST_TABS_UPDATED).putExtra(INTENT_KEY_SUCCEED, true));
				}
				break;
			}
			case URI_FILTERED_USERS:
			case URI_FILTERED_KEYWORDS:
			case URI_FILTERED_SOURCES:
				if (!"false".equals(uri.getQueryParameter(QUERY_PARAM_NOTIFY))) {
					context.sendBroadcast(new Intent(BROADCAST_FILTERS_UPDATED).putExtra(INTENT_KEY_SUCCEED, true));
				}
				 break;
			default:
				return;
		}
		context.sendBroadcast(new Intent(BROADCAST_DATABASE_UPDATED));
	}

	public static class DatabaseHelper extends SQLiteOpenHelper {

		public DatabaseHelper(final Context context, final String name, final int version) {
			super(context, name, null, version);
		}

		@Override
		public void onCreate(final SQLiteDatabase db) {
			db.beginTransaction();
			db.execSQL(createTable(TABLE_ACCOUNTS, Accounts.COLUMNS, Accounts.TYPES, true));
			db.execSQL(createTable(TABLE_STATUSES, Statuses.COLUMNS, Statuses.TYPES, true));
			db.execSQL(createTable(TABLE_MENTIONS, Mentions.COLUMNS, Mentions.TYPES, true));
			db.execSQL(createTable(TABLE_DRAFTS, Drafts.COLUMNS, Drafts.TYPES, true));
			db.execSQL(createTable(TABLE_CACHED_USERS, CachedUsers.COLUMNS, CachedUsers.TYPES, true));
			db.execSQL(createTable(TABLE_FILTERED_USERS, Filters.Users.COLUMNS, Filters.Users.TYPES, true));
			db.execSQL(createTable(TABLE_FILTERED_KEYWORDS, Filters.Keywords.COLUMNS, Filters.Keywords.TYPES, true));
			db.execSQL(createTable(TABLE_FILTERED_SOURCES, Filters.Sources.COLUMNS, Filters.Sources.TYPES, true));
			db.execSQL(createTable(TABLE_FILTERED_SOURCES, Filters.Sources.COLUMNS, Filters.Sources.TYPES, true));
			db.execSQL(createTable(TABLE_DIRECT_MESSAGES_INBOX, DirectMessages.Inbox.COLUMNS,
					DirectMessages.Inbox.TYPES, true));
			db.execSQL(createTable(TABLE_DIRECT_MESSAGES_OUTBOX, DirectMessages.Outbox.COLUMNS,
					DirectMessages.Outbox.TYPES, true));
			db.execSQL(createTable(TABLE_TRENDS_DAILY, CachedTrends.Daily.COLUMNS, CachedTrends.Daily.TYPES, true));
			db.execSQL(createTable(TABLE_TRENDS_WEEKLY, CachedTrends.Weekly.COLUMNS, CachedTrends.Weekly.TYPES, true));
			db.execSQL(createTable(TABLE_TRENDS_LOCAL, CachedTrends.Local.COLUMNS, CachedTrends.Local.TYPES, true));
			db.execSQL(createTable(TABLE_TABS, Tabs.COLUMNS, Tabs.TYPES, false));
			db.setTransactionSuccessful();
			db.endTransaction();
		}

		@Override
		public void onDowngrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
			handleVersionChange(db);
		}

		@Override
		public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
			handleVersionChange(db);
		}

		private String createTable(final String tableName, final String[] columns, final String[] types, final boolean create_if_not_exists) {
			if (tableName == null || columns == null || types == null || types.length != columns.length
					|| types.length == 0)
				throw new IllegalArgumentException("Invalid parameters for creating table " + tableName);
			final StringBuilder stringBuilder = new StringBuilder(create_if_not_exists ? "CREATE TABLE IF NOT EXISTS "
					: "CREATE TABLE ");

			stringBuilder.append(tableName);
			stringBuilder.append(" (");
			final int length = columns.length;
			for (int n = 0, i = length; n < i; n++) {
				if (n > 0) {
					stringBuilder.append(", ");
				}
				stringBuilder.append(columns[n]).append(' ').append(types[n]);
			}
			return stringBuilder.append(");").toString();
		}

		private void handleVersionChange(SQLiteDatabase db) {
			safeUpgrade(db, TABLE_ACCOUNTS, Accounts.COLUMNS, Accounts.TYPES, true, false);
			safeUpgrade(db, TABLE_STATUSES, Statuses.COLUMNS, Statuses.TYPES, true, true);
			safeUpgrade(db, TABLE_MENTIONS, Mentions.COLUMNS, Mentions.TYPES, true, true);
			safeUpgrade(db, TABLE_DRAFTS, Drafts.COLUMNS, Drafts.TYPES, true, false);
			safeUpgrade(db, TABLE_CACHED_USERS, CachedUsers.COLUMNS, CachedUsers.TYPES, true, true);
			safeUpgrade(db, TABLE_FILTERED_USERS, Filters.Users.COLUMNS, Filters.Users.TYPES, true, false);
			safeUpgrade(db, TABLE_FILTERED_KEYWORDS, Filters.Keywords.COLUMNS, Filters.Keywords.TYPES, true, false);
			safeUpgrade(db, TABLE_FILTERED_SOURCES, Filters.Sources.COLUMNS, Filters.Sources.TYPES, true, false);
			safeUpgrade(db, TABLE_DIRECT_MESSAGES_INBOX, DirectMessages.Inbox.COLUMNS, DirectMessages.Inbox.TYPES,
					true, true);
			safeUpgrade(db, TABLE_DIRECT_MESSAGES_OUTBOX, DirectMessages.Outbox.COLUMNS, DirectMessages.Outbox.TYPES,
					true, true);
			safeUpgrade(db, TABLE_TRENDS_DAILY, CachedTrends.Daily.COLUMNS, CachedTrends.Daily.TYPES, true, true);
			safeUpgrade(db, TABLE_TRENDS_WEEKLY, CachedTrends.Weekly.COLUMNS, CachedTrends.Weekly.TYPES, true, true);
			safeUpgrade(db, TABLE_TRENDS_LOCAL, CachedTrends.Local.COLUMNS, CachedTrends.Local.TYPES, true, true);
			safeUpgrade(db, TABLE_TABS, Tabs.COLUMNS, Tabs.TYPES, true, false);
		}

	}

}
