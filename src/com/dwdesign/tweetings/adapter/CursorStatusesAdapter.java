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

import static android.text.format.DateUtils.getRelativeTimeSpanString;
import static com.dwdesign.tweetings.Constants.INTENT_ACTION_VIEW_IMAGE;
import static com.dwdesign.tweetings.util.HtmlEscapeHelper.unescape;
import static com.dwdesign.tweetings.util.Utils.findStatusInDatabases;
import static com.dwdesign.tweetings.util.Utils.formatSameDayTime;
import static com.dwdesign.tweetings.util.Utils.getAccountColor;
import static com.dwdesign.tweetings.util.Utils.getAccountUsername;
import static com.dwdesign.tweetings.util.Utils.getInlineImagePreviewDisplayOptionInt;
import static com.dwdesign.tweetings.util.Utils.getAllAvailableImage;
import static com.dwdesign.tweetings.util.Utils.getBiggerTwitterProfileImage;
import static com.dwdesign.tweetings.util.Utils.getPreviewImage;
import static com.dwdesign.tweetings.util.Utils.getStatusBackground;
import static com.dwdesign.tweetings.util.Utils.getStatusTypeIconRes;
import static com.dwdesign.tweetings.util.Utils.getUserColor;
import static com.dwdesign.tweetings.util.Utils.isMyRetweet;
import static com.dwdesign.tweetings.util.Utils.isNullOrEmpty;
import static com.dwdesign.tweetings.util.Utils.openUserProfile;
import static com.dwdesign.tweetings.util.Utils.parseURL;
import static com.dwdesign.tweetings.util.Utils.openImage;
import static com.dwdesign.tweetings.util.Utils.setMenuForStatus;

import java.util.ArrayList;

import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.fragment.CursorStatusesListFragment;
import com.dwdesign.tweetings.model.ImageSpec;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.model.PreviewImage;
import com.dwdesign.tweetings.model.StatusCursorIndices;
import com.dwdesign.tweetings.model.StatusViewHolder;
import com.dwdesign.tweetings.util.LazyImageLoader;
import com.dwdesign.tweetings.util.NoDuplicatesLinkedList;
import com.dwdesign.tweetings.util.OnLinkClickHandler;
import com.dwdesign.tweetings.util.StatusesAdapterInterface;
import com.dwdesign.tweetings.util.TwidereLinkify;
import com.dwdesign.tweetings.Constants;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.AdapterView;
import android.widget.ListView;

public class CursorStatusesAdapter extends SimpleCursorAdapter implements Constants, StatusesAdapterInterface, OnClickListener {

	private boolean mDisplayProfileImage, mDisplayName, mDisplayNameBoth, mShowAccountColor, mShowAbsoluteTime,
		mGapDisallowed, mMultiSelectEnabled, mFastProcessingEnabled, mMentionsHighlightDisabled, mShowLinks, mDisplaySensitiveContents;
	private final LazyImageLoader mProfileImageLoader, mPreviewImageLoader;
	private float mTextSize;
	private final Context mContext;
	private StatusCursorIndices mIndices;
	private final ArrayList<Long> mSelectedStatusIds;
	private final boolean mDisplayHiResProfileImage;
	private int mInlineImagePreviewDisplayOption;
	
	public CursorStatusesAdapter(final Context context) {
		super(context, R.layout.status_list_item, null, new String[0], new int[0], 0);
		mContext = context;
		final TweetingsApplication application = TweetingsApplication.getInstance(context);
		mSelectedStatusIds = application.getSelectedStatusIds();
		mProfileImageLoader = application.getProfileImageLoader();
		mPreviewImageLoader = application.getPreviewImageLoader();
		mDisplayHiResProfileImage = context.getResources().getBoolean(R.bool.hires_profile_image);
	}

	@Override
	public void bindView(final View view, final Context context, final Cursor cursor) {
		final int position = cursor.getPosition();
		StatusViewHolder holder = (StatusViewHolder) view.getTag();

		final boolean is_gap = cursor.getShort(mIndices.is_gap) == 1;

		final boolean show_gap = is_gap && !mGapDisallowed;

		holder.setShowAsGap(show_gap);
		
		final long account_id = cursor.getLong(mIndices.account_id);
		
		holder.setAccountColorEnabled(mShowAccountColor);

		if (mShowAccountColor) {
			holder.setAccountColor(getAccountColor(mContext, account_id));
		}
		
		if (!show_gap) {

			final String retweeted_by = mDisplayName ? cursor.getString(mIndices.retweeted_by_name) : cursor
					.getString(mIndices.retweeted_by_screen_name);
			final String text = cursor.getString(mIndices.text);
			final String screen_name = cursor.getString(mIndices.screen_name);
			final String display_name = mDisplayName ? cursor.getString(mIndices.name) : screen_name;
			final String name = cursor.getString(mIndices.name);
			final String in_reply_to_screen_name = cursor.getString(mIndices.in_reply_to_screen_name);

			final long user_id = cursor.getLong(mIndices.user_id);
			final long status_id = cursor.getLong(mIndices.status_id);
			final long status_timestamp = cursor.getLong(mIndices.status_timestamp);
			final long retweet_count = cursor.getLong(mIndices.retweet_count);

			final boolean is_favorite = cursor.getShort(mIndices.is_favorite) == 1;
			final boolean is_protected = cursor.getShort(mIndices.is_protected) == 1;
			final boolean is_verified = cursor.getShort(mIndices.is_verified) == 1;

			final boolean has_location = !isNullOrEmpty(cursor.getString(mIndices.location));
			final boolean is_retweet = !isNullOrEmpty(retweeted_by) && cursor.getShort(mIndices.is_retweet) == 1;
			final boolean is_reply = !isNullOrEmpty(in_reply_to_screen_name)
					&& cursor.getLong(mIndices.in_reply_to_status_id) > 0;

			if (mMultiSelectEnabled) {
				holder.setSelected(mSelectedStatusIds.contains(status_id));
			} else {
				holder.setSelected(false);
			}
			
			if (!mFastProcessingEnabled) {
				boolean is_mine = false;
				if (account_id > 0 && screen_name != null && mContext != null && getAccountUsername(mContext, account_id) != null && getAccountUsername(mContext, account_id).equals(screen_name)) {
					is_mine = true;
				}
				holder.setUserColor(getUserColor(mContext, user_id));
				if (text != null) {
					holder.setHighlightColor(getStatusBackground(
							mMentionsHighlightDisabled ? false : text.toLowerCase().contains('@' + getAccountUsername(mContext,
									account_id).toLowerCase()), is_favorite, is_retweet, is_mine));
				}
			}
			else {
				holder.setUserColor(Color.TRANSPARENT);
				holder.setHighlightColor(Color.TRANSPARENT);
			}
			
			final PreviewImage preview = getPreviewImage(text, mInlineImagePreviewDisplayOption);
			//final PreviewImage preview = !mFastProcessingEnabled || mDisplayImagePreview ? getPreviewImage(text,
					//mDisplayImagePreview) : null;
			final boolean has_media = preview != null ? preview.has_image : false;

			holder.setTextSize(mTextSize);

			if (mShowLinks) {
			 	 holder.text.setText(Html.fromHtml(text));
			 	 final TwidereLinkify linkify = new TwidereLinkify(holder.text);
			 	 linkify.setOnLinkClickListener(new OnLinkClickHandler(context, account_id));
			 	 linkify.addAllLinks();
			} else {
				holder.text.setText(unescape(text));
			}
			holder.text.setMovementMethod(null);
			
			/*if (mShowLinks) {
				holder.text.setText(TwidereLinkify.twitterifyText(account_id, mContext, text));
				holder.text.setMovementMethod(LinkMovementMethod.getInstance());
				holder.text.setLinksClickable(false);
				holder.text.setTag(position);
				holder.text.setOnClickListener(this);
				holder.text.setOnLongClickListener(this);
			}
			else {
				holder.text.setText(unescape(text));
			}*/
			
			//holder.name.setCompoundDrawablesWithIntrinsicBounds(getUserTypeIconRes(is_verified, is_protected), 0, 0, 0);
			if (mDisplayNameBoth) {
				holder.name.setText(name);
				if (holder.name2 != null) {
					holder.name2.setText("@" + screen_name);
				}
			}
			else {
				holder.name.setText(display_name);
			}
			if (holder.name2 != null) {
				holder.name2.setVisibility(mDisplayNameBoth ? View.VISIBLE : View.GONE);
			}
			
			if (mShowAbsoluteTime) {
				holder.time.setText(formatSameDayTime(context, status_timestamp));
			} else {
				holder.time.setText(getRelativeTimeSpanString(status_timestamp));
			}
			holder.time.setCompoundDrawablesWithIntrinsicBounds(0, 0,
					getStatusTypeIconRes(is_favorite, has_location, has_media), 0);

			holder.reply_retweet_status.setVisibility(is_retweet || is_reply ? View.VISIBLE : View.GONE);
			if (is_retweet) {
				holder.reply_retweet_status.setText(retweet_count > 1 ? mContext.getString(
						R.string.retweeted_by_with_count, retweeted_by, retweet_count - 1) : mContext.getString(
						R.string.retweeted_by, retweeted_by));
				holder.reply_retweet_status.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_indicator_retweet, 0,
						0, 0);
			} else if (is_reply) {
				holder.reply_retweet_status.setText(mContext.getString(R.string.in_reply_to, in_reply_to_screen_name));
				holder.reply_retweet_status.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_indicator_reply, 0,
						0, 0);
			}
			holder.profile_image.setVisibility(mDisplayProfileImage ? View.VISIBLE : View.GONE);
			if (mDisplayProfileImage) {
				final String profile_image_url_string = cursor.getString(mIndices.profile_image_url);
				if (mDisplayHiResProfileImage) {
					mProfileImageLoader.displayImage(parseURL(getBiggerTwitterProfileImage(profile_image_url_string)),
							holder.profile_image);
				} else {
					mProfileImageLoader.displayImage(parseURL(profile_image_url_string), holder.profile_image);
				}
				holder.profile_image.setTag(position);
			}
			final boolean has_preview = mInlineImagePreviewDisplayOption != INLINE_IMAGE_PREVIEW_DISPLAY_OPTION_CODE_NONE
				 	 && has_media && preview.matched_url != null;
			holder.image_preview_frame.setVisibility(has_preview ? View.VISIBLE : View.GONE);
			if (has_preview) {
				final MarginLayoutParams lp = (MarginLayoutParams) holder.image_preview_frame.getLayoutParams();
			 	if (mInlineImagePreviewDisplayOption == INLINE_IMAGE_PREVIEW_DISPLAY_OPTION_CODE_LARGE || mInlineImagePreviewDisplayOption == INLINE_IMAGE_PREVIEW_DISPLAY_OPTION_CODE_LARGE_HIGH) {
			 		lp.width = LayoutParams.MATCH_PARENT;
			 		lp.leftMargin = 0;
			 		holder.image_preview_frame.setLayoutParams(lp);
			 	} else if (mInlineImagePreviewDisplayOption == INLINE_IMAGE_PREVIEW_DISPLAY_OPTION_CODE_SMALL) {
			 		final Resources res = mContext.getResources();
			 		lp.width = res.getDimensionPixelSize(R.dimen.image_preview_width);
			 		lp.leftMargin = (int) (res.getDisplayMetrics().density * 16);
			 		holder.image_preview_frame.setLayoutParams(lp);
			 	}
				
			 	final boolean is_possibly_sensitive = cursor.getInt(mIndices.is_possibly_sensitive) == 1;
				if (is_possibly_sensitive && !mDisplaySensitiveContents) {
					holder.image_preview.setImageResource(R.drawable.image_preview_nsfw);
				} else {
					mPreviewImageLoader.displayImage(parseURL(preview.matched_url), holder.image_preview);	
				}
				holder.image_preview_frame.setTag(position);
			}
		}

		super.bindView(view, context, cursor);
	}

	@Override
	public long findItemIdByPosition(final int position) {
		if (position >= 0 && position < getCount()) return getItem(position).getLong(mIndices.status_id);
		return -1;
	}
	
	public int findNearestItemPositionByStatusId(final long status_id) {
		final int count = getCount();
		for (int i = 0; i < count; i++) {
			long status_to_check = getItem(i).getLong(mIndices.status_id);
			if (status_to_check < status_id) {
				return i;
			}
		}
		return -1;
	}

	public int findItemPositionByStatusId(final long status_id) {
		final int count = getCount();
		for (int i = 0; i < count; i++) {
			if (getItem(i).getLong(mIndices.status_id) == status_id) return i;
		}
		return -1;
	}

	@Override
	public ParcelableStatus findStatus(final long id) {
		final int count = getCount();
		for (int i = 0; i < count; i++) {
			if (getItemId(i) == id) return getStatus(i);
		}
		return null;
	}

	@Override
	public Cursor getItem(final int position) {
		return (Cursor) super.getItem(position);
	}

	public ParcelableStatus getStatus(final int position) {
		final Cursor cur = getItem(position);
		final long account_id = cur.getLong(mIndices.account_id);
		final long status_id = cur.getLong(mIndices.status_id);
		return findStatusInDatabases(mContext, account_id, status_id);
	}

	@Override
	public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
		final View view = super.newView(context, cursor, parent);
		final Object tag = view.getTag();
		if (!(tag instanceof StatusViewHolder)) {
			final StatusViewHolder holder = new StatusViewHolder(view);
			view.setTag(holder);
			holder.profile_image.setOnClickListener(this);
			holder.image_preview_frame.setOnClickListener(this);
		}
		return view;
	}
	
	@Override
	public void onClick(final View view) {
		final Object tag = view.getTag();
		final ParcelableStatus status = tag instanceof Integer ? getStatus((Integer) tag) : null;
		if (status == null) return;
			
		switch (view.getId()) {
			case R.id.image_preview_frame: {
				final ImageSpec spec = getAllAvailableImage(status.image_orig_url_string);
				if (spec != null) {
					openImage(mContext, Uri.parse(spec.full_image_link), status.is_possibly_sensitive);
				}
				break;
			}
			case R.id.profile_image: {
				if (mContext instanceof Activity) {
					openUserProfile((Activity) mContext, status.account_id, status.user_id, status.screen_name);
				}
				break;
			}
		}
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
	
	@Override
	public void setFastProcessingEnabled(final boolean enabled) {
		if (enabled != mFastProcessingEnabled) {
			mFastProcessingEnabled = enabled;
			notifyDataSetChanged();
		}
	}
	
	@Override
	public void setDisplaySensitiveContents(final boolean display) {
		if (display != mDisplaySensitiveContents) {
			mDisplaySensitiveContents = display;
			notifyDataSetChanged();
		}
	}
	
	@Override
	public void setGapDisallowed(final boolean disallowed) {
		if (mGapDisallowed != disallowed) {
			mGapDisallowed = disallowed;
			notifyDataSetChanged();
		}

	}
	
	@Override
	public void setInlineImagePreviewDisplayOption(final String option) {
		if (option != null && !option.equals(mInlineImagePreviewDisplayOption)) {
			mInlineImagePreviewDisplayOption = getInlineImagePreviewDisplayOptionInt(option);
			notifyDataSetChanged();
		}
	}
	
	@Override
	public void setMentionsHightlightDisabled(final boolean disable) {
		if (disable != mMentionsHighlightDisabled) {
			mMentionsHighlightDisabled = disable;
			notifyDataSetChanged();
		}
	}
	
	@Override
	public void setMultiSelectEnabled(final boolean multi) {
		if (mMultiSelectEnabled != multi) {
			mMultiSelectEnabled = multi;
			notifyDataSetChanged();
		}
	}

	@Override
	public void setShowAbsoluteTime(final boolean show) {
		if (show != mShowAbsoluteTime) {
			mShowAbsoluteTime = show;
			notifyDataSetChanged();
		}
	}
	
	@Override
	public void setShowLinks(final boolean links) {
		if (links != mShowLinks) {
			mShowLinks = links;
			notifyDataSetChanged();
		}
	}

	@Override
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

	@Override
	public Cursor swapCursor(final Cursor cursor) {
		if (cursor != null) {
			mIndices = new StatusCursorIndices(cursor);
		} else {
			mIndices = null;
		}
		return super.swapCursor(cursor);
	}
}
