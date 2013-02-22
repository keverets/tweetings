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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.util.AttributeSet;

public class NotificationTypePreference extends MultiSelectListPreference implements Constants {

	protected String[] getNames() {
		return getContext().getResources().getStringArray(R.array.entries_notification_type);
	}
	
	protected String[] getKeys() {
		return new String[]{ PREFERENCE_KEY_NOTIFICATION_HAVE_SOUND,
				PREFERENCE_KEY_NOTIFICATION_HAVE_VIBRATION,
				PREFERENCE_KEY_NOTIFICATION_HAVE_LIGHTS };
	}
	
	protected boolean[] getDefaults() {
		return new boolean[]{ true, true, true };
	}

	public NotificationTypePreference(final Context context) {
		this(context, null);
	}

	public NotificationTypePreference(final Context context, final AttributeSet attrs) {
		this(context, attrs, android.R.attr.preferenceStyle);
	}

	public NotificationTypePreference(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
	}

}
