package com.dwdesign.tweetings.appwidget.util;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.provider.TweetStore;
import com.dwdesign.tweetings.provider.TweetStore.Accounts;
import com.dwdesign.tweetings.provider.TweetStore.Filters;
import com.dwdesign.tweetings.provider.TweetStore.Statuses;

import android.content.Context;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Environment;

public final class Utils implements Constants {

	private static final UriMatcher CONTENT_PROVIDER_URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

	static {
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_STATUSES, URI_STATUSES);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_ACCOUNTS, URI_ACCOUNTS);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_MENTIONS, URI_MENTIONS);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_DRAFTS, URI_DRAFTS);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_CACHED_USERS, URI_CACHED_USERS);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_FILTERED_USERS, URI_FILTERED_USERS);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_FILTERED_KEYWORDS, URI_FILTERED_KEYWORDS);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_FILTERED_SOURCES, URI_FILTERED_SOURCES);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_DIRECT_MESSAGES, URI_DIRECT_MESSAGES);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_DIRECT_MESSAGES_INBOX,
				URI_DIRECT_MESSAGES_INBOX);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_DIRECT_MESSAGES_OUTBOX,
				URI_DIRECT_MESSAGES_OUTBOX);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_DIRECT_MESSAGES_CONVERSATION + "/#/#",
				URI_DIRECT_MESSAGES_CONVERSATION);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_DIRECT_MESSAGES_CONVERSATION_SCREEN_NAME
				+ "/#/*", URI_DIRECT_MESSAGES_CONVERSATION_SCREEN_NAME);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_DIRECT_MESSAGES_CONVERSATIONS_ENTRY,
				URI_DIRECT_MESSAGES_CONVERSATIONS_ENTRY);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_TRENDS_DAILY, URI_TRENDS_DAILY);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_TRENDS_WEEKLY, URI_TRENDS_WEEKLY);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_TRENDS_LOCAL, URI_TRENDS_LOCAL);
		CONTENT_PROVIDER_URI_MATCHER.addURI(TweetStore.AUTHORITY, TABLE_TABS, URI_TABS);
	}

	public static final String AVAILABLE_URL_SCHEME_PREFIX = "(https?:\\/\\/)?";
	public static final String AVAILABLE_IMAGE_SHUFFIX = "(png|jpeg|jpg|gif|bmp)";

	public static final String TWITTER_PROFILE_IMAGES_AVAILABLE_SIZES = "(bigger|normal|mini)";
	private static final String STRING_PATTERN_TWITTER_PROFILE_IMAGES_NO_SCHEME = "([\\w\\d]+)\\.twimg\\.com\\/profile_images\\/([\\d\\w\\-_]+)\\/([\\d\\w\\-_]+)_"
			+ TWITTER_PROFILE_IMAGES_AVAILABLE_SIZES + "(\\.?" + AVAILABLE_IMAGE_SHUFFIX + ")?";
	private static final String STRING_PATTERN_TWITTER_PROFILE_IMAGES = AVAILABLE_URL_SCHEME_PREFIX
			+ STRING_PATTERN_TWITTER_PROFILE_IMAGES_NO_SCHEME;

	public static final Pattern PATTERN_TWITTER_PROFILE_IMAGES = Pattern.compile(STRING_PATTERN_TWITTER_PROFILE_IMAGES,
			Pattern.CASE_INSENSITIVE);

	private static Map<Long, Integer> sAccountColors = new LinkedHashMap<Long, Integer>();

	public static String buildActivatedStatsWhereClause(final Context context, final String selection) {
		if (context == null) return null;
		final long[] account_ids = getActivatedAccountIds(context);
		final StringBuilder builder = new StringBuilder();
		if (selection != null) {
			builder.append(selection);
			builder.append(" AND ");
		}

		builder.append(Statuses.ACCOUNT_ID + " IN ( ");
		builder.append(ArrayUtils.toString(account_ids, ',', true));
		builder.append(" )");

		return builder.toString();
	}

	public static String buildFilterWhereClause(final String table, final String selection) {
		if (table == null) return null;
		if (table.equals("mentions")) return null;
		final StringBuilder builder = new StringBuilder();
		if (selection != null) {
			builder.append(selection);
			builder.append(" AND ");
		}
		builder.append(Statuses._ID + " NOT IN ( ");
		builder.append("SELECT DISTINCT " + table + "." + Statuses._ID + " FROM " + table);
		builder.append(" WHERE " + table + "." + Statuses.SCREEN_NAME + " IN ( SELECT " + TABLE_FILTERED_USERS + "."
				+ Filters.Users.TEXT + " FROM " + TABLE_FILTERED_USERS + " )");
		builder.append(" AND " + table + "." + Statuses.IS_GAP + " IS NULL");
		builder.append(" OR " + table + "." + Statuses.IS_GAP + " == 0");
		builder.append(" UNION ");
		builder.append("SELECT DISTINCT " + table + "." + Statuses._ID + " FROM " + table + ", "
				+ TABLE_FILTERED_SOURCES);
		builder.append(" WHERE " + table + "." + Statuses.SOURCE + " LIKE '%>'||" + TABLE_FILTERED_SOURCES + "."
				+ Filters.Sources.TEXT + "||'</a>%'");
		builder.append(" AND " + table + "." + Statuses.IS_GAP + " IS NULL");
		builder.append(" OR " + table + "." + Statuses.IS_GAP + " == 0");
		builder.append(" UNION ");
		builder.append("SELECT DISTINCT " + table + "." + Statuses._ID + " FROM " + table + ", "
				+ TABLE_FILTERED_KEYWORDS);
		builder.append(" WHERE " + table + "." + Statuses.TEXT_PLAIN + " LIKE '%'||" + TABLE_FILTERED_KEYWORDS + "."
				+ Filters.Keywords.TEXT + "||'%'");
		builder.append(" AND " + table + "." + Statuses.IS_GAP + " IS NULL");
		builder.append(" OR " + table + "." + Statuses.IS_GAP + " == 0");
		builder.append(" )");

		return builder.toString();
	}

	public static int getAccountColor(final Context context, final long account_id) {
		if (context == null) return Color.TRANSPARENT;
		Integer color = sAccountColors.get(account_id);
		if (color == null) {
			final Cursor cur = context.getContentResolver().query(Accounts.CONTENT_URI,
					new String[] { Accounts.USER_COLOR }, Accounts.USER_ID + "=" + account_id, null, null);
			if (cur == null) return Color.TRANSPARENT;
			if (cur.getCount() <= 0) {
				cur.close();
				return Color.TRANSPARENT;
			}
			cur.moveToFirst();
			color = cur.getInt(cur.getColumnIndexOrThrow(Accounts.USER_COLOR));
			cur.close();
			sAccountColors.put(account_id, color);
		}
		return color;
	}

	public static long[] getActivatedAccountIds(final Context context) {
		long[] accounts = new long[0];
		if (context == null) return accounts;
		final String[] cols = new String[] { Accounts.USER_ID };
		final Cursor cur = context.getContentResolver().query(Accounts.CONTENT_URI, cols, Accounts.IS_ACTIVATED + "=1",
				null, Accounts.USER_ID);
		if (cur != null) {
			final int idx = cur.getColumnIndexOrThrow(Accounts.USER_ID);
			cur.moveToFirst();
			accounts = new long[cur.getCount()];
			int i = 0;
			while (!cur.isAfterLast()) {
				accounts[i] = cur.getLong(idx);
				i++;
				cur.moveToNext();
			}
			cur.close();
		}
		return accounts;
	}

	public static String getBiggerTwitterProfileImage(final String url) {
		if (url == null) return null;
		if (PATTERN_TWITTER_PROFILE_IMAGES.matcher(url).matches())
			return replaceLast(url, "_" + TWITTER_PROFILE_IMAGES_AVAILABLE_SIZES, "_bigger");
		return url;
	}

	public static String getFilename(final String string) {
		if (string == null) return null;
		return string.replaceFirst("https?:\\/\\/", "").replaceAll("[^a-zA-Z0-9]", "_");
	}

	public static Bitmap getRoundedCornerBitmap(final Resources resources, final Bitmap bitmap) {

		final float density = resources.getDisplayMetrics().density;
		final Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
		final Canvas canvas = new Canvas(output);

		final int color = 0xff424242;
		final Paint paint = new Paint();
		final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
		final RectF rectF = new RectF(rect);
		final float roundPx = density * 4;

		paint.setAntiAlias(true);
		canvas.drawARGB(0, 0, 0, 0);
		paint.setColor(color);
		canvas.drawRoundRect(rectF, roundPx, roundPx, paint);

		paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
		canvas.drawBitmap(bitmap, rect, rect, paint);

		return output;
	}

	public static int getTableId(final Uri uri) {
		if (uri == null) return -1;
		return CONTENT_PROVIDER_URI_MATCHER.match(uri);
	}

	public static String getTableNameForContentUri(final Uri uri) {
		if (uri == null) return null;
		switch (getTableId(uri)) {
			case URI_ACCOUNTS:
				return TABLE_ACCOUNTS;
			case URI_STATUSES:
				return TABLE_STATUSES;
			case URI_MENTIONS:
				return TABLE_MENTIONS;
			case URI_DRAFTS:
				return TABLE_DRAFTS;
			case URI_CACHED_USERS:
				return TABLE_CACHED_USERS;
			case URI_FILTERED_USERS:
				return TABLE_FILTERED_USERS;
			case URI_FILTERED_KEYWORDS:
				return TABLE_FILTERED_KEYWORDS;
			case URI_FILTERED_SOURCES:
				return TABLE_FILTERED_SOURCES;
			case URI_DIRECT_MESSAGES:
				return TABLE_DIRECT_MESSAGES;
			case URI_DIRECT_MESSAGES_INBOX:
				return TABLE_DIRECT_MESSAGES_INBOX;
			case URI_DIRECT_MESSAGES_OUTBOX:
				return TABLE_DIRECT_MESSAGES_OUTBOX;
			case URI_DIRECT_MESSAGES_CONVERSATION:
				return TABLE_DIRECT_MESSAGES_CONVERSATION;
			case URI_DIRECT_MESSAGES_CONVERSATION_SCREEN_NAME:
				return TABLE_DIRECT_MESSAGES_CONVERSATION_SCREEN_NAME;
			case URI_DIRECT_MESSAGES_CONVERSATIONS_ENTRY:
				return TABLE_DIRECT_MESSAGES_CONVERSATIONS_ENTRY;
			case URI_TRENDS_DAILY:
				return TABLE_TRENDS_DAILY;
			case URI_TRENDS_WEEKLY:
				return TABLE_TRENDS_WEEKLY;
			case URI_TRENDS_LOCAL:
				return TABLE_TRENDS_LOCAL;
			case URI_TABS:
				return TABLE_TABS;
			default:
				return null;
		}
	}

	public static File getTweetingsCacheDir() {
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
			return new File(Environment.getExternalStorageDirectory(), "/Android/data/"
					+ "com.dwdesign.tweetings/cache/profile_images");
		return null;
	}

	public static String replaceLast(final String text, final String regex, final String replacement) {
		if (text == null || regex == null || replacement == null) return text;
		return text.replaceFirst("(?s)" + regex + "(?!.*?" + regex + ")", replacement);
	}
}
