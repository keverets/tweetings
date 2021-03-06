package com.dwdesign.tweetings.adapter;

import static android.text.format.DateUtils.formatSameDayTime;
import static android.text.format.DateUtils.getRelativeTimeSpanString;
import static com.dwdesign.tweetings.provider.TweetStore.DirectMessages.ConversationsEntry.IDX_ACCOUNT_ID;
import static com.dwdesign.tweetings.provider.TweetStore.DirectMessages.ConversationsEntry.IDX_CONVERSATION_ID;
import static com.dwdesign.tweetings.provider.TweetStore.DirectMessages.ConversationsEntry.IDX_NAME;
import static com.dwdesign.tweetings.provider.TweetStore.DirectMessages.ConversationsEntry.IDX_PROFILE_IMAGE_URL;
import static com.dwdesign.tweetings.provider.TweetStore.DirectMessages.ConversationsEntry.IDX_SCREEN_NAME;
import static com.dwdesign.tweetings.provider.TweetStore.DirectMessages.ConversationsEntry.IDX_TEXT;
import static com.dwdesign.tweetings.util.Utils.getAccountColor;
import static com.dwdesign.tweetings.util.Utils.getBiggerTwitterProfileImage;
import static com.dwdesign.tweetings.util.Utils.getUserColor;
import static com.dwdesign.tweetings.util.Utils.parseURL;

import java.text.DateFormat;

import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.model.DirectMessageEntryViewHolder;
import com.dwdesign.tweetings.provider.TweetStore.DirectMessages.ConversationsEntry;
import com.dwdesign.tweetings.util.BaseAdapterInterface;
import com.dwdesign.tweetings.util.ImageLoaderWrapper;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.View;
import android.view.ViewGroup;
import com.dwdesign.tweetings.app.TweetingsApplication;

public class DirectMessagesEntryAdapter extends SimpleCursorAdapter implements BaseAdapterInterface {

	private boolean mDisplayProfileImage, mDisplayName, mDisplayNameBoth, mShowAccountColor, mShowAbsoluteTime, mFastProcessingEnabled;
	private final ImageLoaderWrapper mLazyImageLoader;
	private float mTextSize;
	private final boolean mDisplayHiResProfileImage;

	public DirectMessagesEntryAdapter(final Context context) {
		super(context, R.layout.direct_messages_entry_item, null, new String[0], new int[0], 0);
		mLazyImageLoader = TweetingsApplication.getInstance(context).getImageLoaderWrapper();
		mDisplayHiResProfileImage = context.getResources().getBoolean(R.bool.hires_profile_image);
	}

	@Override
	public void bindView(final View view, final Context context, final Cursor cursor) {
		final DirectMessageEntryViewHolder holder = (DirectMessageEntryViewHolder) view.getTag();

		final long account_id = cursor.getLong(ConversationsEntry.IDX_ACCOUNT_ID);
		final long conversation_id = cursor.getLong(ConversationsEntry.IDX_CONVERSATION_ID);
		final long message_timestamp = cursor.getLong(ConversationsEntry.IDX_MESSAGE_TIMESTAMP);
		final boolean is_outgoing = cursor.getInt(ConversationsEntry.IDX_IS_OUTGOING) == 1;

		final String name = mDisplayName ? cursor.getString(IDX_NAME) : cursor.getString(IDX_SCREEN_NAME);

		holder.setAccountColorEnabled(mShowAccountColor);

		if (mShowAccountColor) {
			holder.setAccountColor(getAccountColor(mContext, account_id));
		}
		
		if (!mFastProcessingEnabled) {
			holder.setUserColor(getUserColor(mContext, conversation_id));
		} else {
			holder.setUserColor(Color.TRANSPARENT);
		}

		holder.setTextSize(mTextSize);
		holder.name.setText(name);
		holder.text.setText(cursor.getString(IDX_TEXT));
		if (mShowAbsoluteTime) {
			holder.time.setText(formatSameDayTime(message_timestamp, System.currentTimeMillis(), DateFormat.MEDIUM,
					DateFormat.SHORT));
		} else {
			holder.time.setText(getRelativeTimeSpanString(message_timestamp));
		}
		holder.time.setCompoundDrawablesWithIntrinsicBounds(0, 0, is_outgoing ? R.drawable.ic_indicator_outgoing
				: R.drawable.ic_indicator_incoming, 0);
		holder.profile_image.setVisibility(mDisplayProfileImage ? View.VISIBLE : View.GONE);
		if (mDisplayProfileImage) {
			final String profile_image_url_string = cursor.getString(IDX_PROFILE_IMAGE_URL);
			if (mDisplayHiResProfileImage) {
				mLazyImageLoader.displayProfileImage(holder.profile_image, getBiggerTwitterProfileImage(profile_image_url_string));
			} else {
				mLazyImageLoader.displayProfileImage(holder.profile_image, profile_image_url_string);
			}
		}

		super.bindView(view, context, cursor);
	}

	public long findAccountId(final long id) {
		final int count = getCount();
		for (int i = 0; i < count; i++) {
			if (getItemId(i) == id) return ((Cursor) getItem(i)).getLong(IDX_ACCOUNT_ID);
		}
		return -1;
	}

	public long findConversationId(final long id) {
		final int count = getCount();
		for (int i = 0; i < count; i++) {
			if (getItemId(i) == id) return ((Cursor) getItem(i)).getLong(IDX_CONVERSATION_ID);
		}
		return -1;
	}

	@Override
	public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
		final View view = super.newView(context, cursor, parent);
		final Object tag = view.getTag();
		if (!(tag instanceof DirectMessageEntryViewHolder)) {
			view.setTag(new DirectMessageEntryViewHolder(view, context));
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
	public void setDisplayProfileImage(final boolean display) {
		if (display != mDisplayProfileImage) {
			mDisplayProfileImage = display;
			notifyDataSetChanged();
		}
	}
	
	public void setFastProcessingEnabled(final boolean enabled) {
		if (enabled != mFastProcessingEnabled) {
			mFastProcessingEnabled = enabled;
			notifyDataSetChanged();
		}
	}

	public void setShowAbsoluteTime(final boolean show) {
		if (show != mShowAbsoluteTime) {
			mShowAbsoluteTime = show;
			notifyDataSetChanged();
		}
	}

	public void setShowAccountColor(final boolean show) {
		if (show != mShowAccountColor) {
			mShowAccountColor = show;
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
}
