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

package com.dwdesign.tweetings.adapter;

import static com.dwdesign.tweetings.util.Utils.getBiggerTwitterProfileImage;
import static com.dwdesign.tweetings.util.Utils.parseURL;

import java.util.List;

import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.model.ParcelableUserList;
import com.dwdesign.tweetings.model.UserListViewHolder;
import com.dwdesign.tweetings.util.BaseAdapterInterface;
import com.dwdesign.tweetings.util.ImageLoaderWrapper;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

public class UserListsAdapter extends ArrayAdapter<ParcelableUserList> implements BaseAdapterInterface {

	private final ImageLoaderWrapper mProfileImageLoader;
	private boolean mDisplayProfileImage, mDisplayName, mDisplayNameBoth;
	private final boolean mDisplayHiResProfileImage;
	private float mTextSize;

	public UserListsAdapter(final Context context) {
		super(context, R.layout.user_list_list_item, R.id.description);
		final TweetingsApplication application = TweetingsApplication.getInstance(context);
		mProfileImageLoader = application.getImageLoaderWrapper();
		mDisplayHiResProfileImage = context.getResources().getBoolean(R.bool.hires_profile_image);
		application.getServiceInterface();
	}

	public ParcelableUserList findItem(final long id) {
		final int count = getCount();
		for (int i = 0; i < count; i++) {
			if (getItemId(i) == id) return getItem(i);
		}
		return null;
	}

	public ParcelableUserList findItemByUserId(final int list_id) {
		final int count = getCount();
		for (int i = 0; i < count; i++) {
			final ParcelableUserList item = getItem(i);
			if (item.list_id == list_id) return item;
		}
		return null;
	}

	@Override
	public View getView(final int position, final View convertView, final ViewGroup parent) {
		final View view = super.getView(position, convertView, parent);
		final Object tag = view.getTag();
		UserListViewHolder holder = null;
		if (tag instanceof UserListViewHolder) {
			holder = (UserListViewHolder) tag;
		} else {
			holder = new UserListViewHolder(view);
			view.setTag(holder);
		}
		final ParcelableUserList user_list = getItem(position);
		holder.setTextSize(mTextSize);
		holder.name.setText(user_list.name);
		holder.owner.setText(mDisplayName ? user_list.user_name : user_list.user_screen_name);
		holder.profile_image.setVisibility(mDisplayProfileImage ? View.VISIBLE : View.GONE);
		if (mDisplayProfileImage) {
			if (mDisplayHiResProfileImage) {
				mProfileImageLoader.displayProfileImage(holder.profile_image, getBiggerTwitterProfileImage(user_list.user_profile_image_url_string));
			} else {
				mProfileImageLoader.displayProfileImage(holder.profile_image, user_list.user_profile_image_url_string);
			}
		}
		return view;
	}

	public void setData(final List<ParcelableUserList> data) {
		setData(data, false);
	}

	public void setData(final List<ParcelableUserList> data, final boolean clear_old) {
		if (clear_old) {
			clear();
		}
		if (data == null) return;
		for (final ParcelableUserList user : data) {
			if (clear_old || findItemByUserId(user.list_id) == null) {
				add(user);
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
	public void setTextSize(final float text_size) {
		if (text_size != mTextSize) {
			mTextSize = text_size;
			notifyDataSetChanged();
		}
	}
}
