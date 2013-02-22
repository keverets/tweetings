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

package com.dwdesign.tweetings.preference;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;

import android.content.Context;
import android.util.AttributeSet;

public class ImagePreloadPreference extends MultiSelectListPreference implements Constants {

	public ImagePreloadPreference(final Context context) {
		this(context, null);
	}

	public ImagePreloadPreference(final Context context, final AttributeSet attrs) {
		this(context, attrs, android.R.attr.preferenceStyle);
	}

	public ImagePreloadPreference(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected boolean[] getDefaults() {
		return new boolean[] { true, true };
	}

	@Override
	protected String[] getKeys() {
		return new String[] { PREFERENCE_KEY_PRELOAD_PROFILE_IMAGES, PREFERENCE_KEY_PRELOAD_PREVIEW_IMAGES };
	}

	@Override
	protected String[] getNames() {
		return getContext().getResources().getStringArray(R.array.entries_image_preload_option);
	}

}