package com.dwdesign.tweetings.model;

import com.dwdesign.tweetings.provider.TweetStore.Drafts;
import com.dwdesign.tweetings.util.ArrayUtils;

import android.database.Cursor;

public class DraftItem {

	public final long[] account_ids;
	public final long _id, in_reply_to_status_id;
	public final String text, media_uri, in_reply_to_name, in_reply_to_screen_name;
	public final boolean is_quote, is_image_attached, is_photo_attached, is_queued, is_possibly_sensitive;

	public DraftItem(final Cursor cursor, final int position) {
		cursor.moveToPosition(position);
		_id = cursor.getLong(cursor.getColumnIndex(Drafts._ID));
		text = cursor.getString(cursor.getColumnIndex(Drafts.TEXT));
		media_uri = cursor.getString(cursor.getColumnIndex(Drafts.IMAGE_URI));
		account_ids = ArrayUtils.fromString(cursor.getString(cursor.getColumnIndex(Drafts.ACCOUNT_IDS)), ',');
		in_reply_to_status_id = cursor.getLong(cursor.getColumnIndex(Drafts.IN_REPLY_TO_STATUS_ID));
		in_reply_to_name = cursor.getString(cursor.getColumnIndex(Drafts.IN_REPLY_TO_NAME));
		in_reply_to_screen_name = cursor.getString(cursor.getColumnIndex(Drafts.IN_REPLY_TO_SCREEN_NAME));
		is_quote = cursor.getShort(cursor.getColumnIndex(Drafts.IS_QUOTE)) == 1;
		is_queued = cursor.getShort(cursor.getColumnIndex(Drafts.IS_QUEUED)) == 1;
		is_image_attached = cursor.getShort(cursor.getColumnIndex(Drafts.IS_IMAGE_ATTACHED)) == 1;
		is_photo_attached = cursor.getShort(cursor.getColumnIndex(Drafts.IS_PHOTO_ATTACHED)) == 1;
		is_possibly_sensitive = cursor.getShort(cursor.getColumnIndex(Drafts.IS_POSSIBLY_SENSITIVE)) == 1;
	}

}