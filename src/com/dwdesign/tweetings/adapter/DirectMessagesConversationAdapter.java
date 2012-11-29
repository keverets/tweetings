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

import static com.dwdesign.tweetings.util.Utils.findDirectMessageInDatabases;
import static com.dwdesign.tweetings.util.Utils.formatToLongTimeString;
import static com.dwdesign.tweetings.util.Utils.getBiggerTwitterProfileImage;
import static com.dwdesign.tweetings.util.Utils.openUserProfile;
import static com.dwdesign.tweetings.util.Utils.parseURL;

import java.net.URL;

import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.model.DirectMessageConversationViewHolder;
import com.dwdesign.tweetings.model.DirectMessageCursorIndices;
import com.dwdesign.tweetings.model.ParcelableDirectMessage;
import com.dwdesign.tweetings.util.DirectMessagesAdapterInterface;
import com.dwdesign.tweetings.util.LazyImageLoader;
import com.dwdesign.tweetings.util.OnLinkClickHandler;
import com.dwdesign.tweetings.util.TwidereLinkify;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.Gravity;
import android.view.View.OnClickListener;
import android.view.View;
import android.view.ViewGroup;

public class DirectMessagesConversationAdapter extends SimpleCursorAdapter implements DirectMessagesAdapterInterface,
	OnClickListener {

	private boolean mDisplayProfileImage, mDisplayName, mDisplayNameBoth;
	private final LazyImageLoader mImageLoader;
	private float mTextSize;
	private final Context mContext;
	private DirectMessageCursorIndices mIndices;
	private final boolean mDisplayHiResProfileImage;

	public DirectMessagesConversationAdapter(final Context context, final LazyImageLoader loader) {
		super(context, R.layout.direct_message_list_item, null, new String[0], new int[0], 0);
		mContext = context;
		mImageLoader = loader;
		mDisplayHiResProfileImage = context.getResources().getBoolean(R.bool.hires_profile_image);
	}

	@Override
	public void bindView(final View view, final Context context, final Cursor cursor) {
		final int position = cursor.getPosition();
		final DirectMessageConversationViewHolder holder = (DirectMessageConversationViewHolder) view.getTag();

		final long account_id = cursor.getLong(mIndices.account_id);
		final long message_timestamp = cursor.getLong(mIndices.message_timestamp);
		final long sender_id = cursor.getLong(mIndices.sender_id);

		final boolean is_outgoing = account_id == sender_id;

		final String name = mDisplayName ? cursor.getString(mIndices.sender_name) : cursor
				.getString(mIndices.sender_screen_name);

		holder.setTextSize(mTextSize);
		final TwidereLinkify linkify = new TwidereLinkify(holder.text);
		linkify.setOnLinkClickListener(new OnLinkClickHandler(context, account_id));
		linkify.addAllLinks();
		holder.text.setMovementMethod(null);
		holder.name.setText(name);
		holder.name.setGravity(is_outgoing ? Gravity.LEFT : Gravity.RIGHT);
		holder.text.setText(cursor.getString(mIndices.text));
		holder.text.setGravity(is_outgoing ? Gravity.LEFT : Gravity.RIGHT);
		holder.time.setText(formatToLongTimeString(mContext, message_timestamp));
		holder.time.setGravity(is_outgoing ? Gravity.RIGHT : Gravity.LEFT);
		holder.profile_image_left.setVisibility(mDisplayProfileImage && is_outgoing ? View.VISIBLE : View.GONE);
		holder.profile_image_right.setVisibility(mDisplayProfileImage && !is_outgoing ? View.VISIBLE : View.GONE);
		if (mDisplayProfileImage) {
			final String sender_profile_image_url_string = cursor.getString(mIndices.sender_profile_image_url);
			final URL sender_profile_image_url = parseURL(mDisplayHiResProfileImage ? getBiggerTwitterProfileImage(sender_profile_image_url_string)
					: sender_profile_image_url_string);

			mImageLoader.displayImage(sender_profile_image_url, holder.profile_image_left);
			mImageLoader.displayImage(sender_profile_image_url, holder.profile_image_right);
			holder.profile_image_left.setTag(position);
			holder.profile_image_right.setTag(position);
		}

		super.bindView(view, context, cursor);
	}

	@Override
	public ParcelableDirectMessage findItem(final long id) {
		final int count = getCount();
		for (int i = 0; i < count; i++) {
			if (getItemId(i) == id) return getDirectMessage(i);
		}
		return null;
	}

	public ParcelableDirectMessage getDirectMessage(final int position) {
		final Cursor item = getItem(position);
		final long account_id = item.getLong(mIndices.account_id);
		final long message_id = item.getLong(mIndices.message_id);
		return findDirectMessageInDatabases(mContext, account_id, message_id);
	}

	@Override
	public Cursor getItem(final int position) {
		return (Cursor) super.getItem(position);
	}

	@Override
	public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
		final View view = super.newView(context, cursor, parent);
		final Object tag = view.getTag();
		if (!(tag instanceof DirectMessageConversationViewHolder)) {
			final DirectMessageConversationViewHolder holder = new DirectMessageConversationViewHolder(view);
			view.setTag(holder);
			holder.profile_image_left.setOnClickListener(this);
			holder.profile_image_right.setOnClickListener(this);
		}
		return view;
	}

	@Override
	public void setDisplayName(final boolean display) {
		if (display != mDisplayName) {
			mDisplayName = display;
			notifyDataSetChanged();
		}
	}
	
	@Override
	public void setDisplayNameBoth(final boolean display) {
		if (display != mDisplayNameBoth) {
			mDisplayNameBoth = display;
			notifyDataSetChanged();
		}
	}
	
	@Override
	public void onClick(final View view) {
		final Object tag = view.getTag();
		final ParcelableDirectMessage status = tag instanceof Integer ? getDirectMessage((Integer) tag) : null;
		if (status == null) return;
		switch (view.getId()) {
			case R.id.profile_image_left:
			case R.id.profile_image_right: {
				if (mContext instanceof Activity) {
					openUserProfile((Activity) mContext, status.account_id, status.sender_id, status.sender_screen_name);
				}
				break;
			}
		}
	}

	@Override
	public void setDisplayProfileImage(final boolean display) {
		if (display != mDisplayProfileImage) {
			mDisplayProfileImage = display;
			notifyDataSetChanged();
		}
	}

	@Override
	public void setTextSize(final float text_size) {
		if (text_size != mTextSize) {
			mTextSize = text_size;
			notifyDataSetChanged();
		}
	}

	@Override
	public Cursor swapCursor(final Cursor cursor) {
		if (cursor != null) {
			mIndices = new DirectMessageCursorIndices(cursor);
		} else {
			mIndices = null;
		}
		return super.swapCursor(cursor);
	}
}
