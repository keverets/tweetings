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

public class NotificationContentPreference extends Preference implements Constants, OnPreferenceClickListener,
		OnMultiChoiceClickListener, OnClickListener {

	private boolean[] checked_items;
	private SharedPreferences prefs;

	public NotificationContentPreference(final Context context) {
		this(context, null);
	}

	public NotificationContentPreference(final Context context, final AttributeSet attrs) {
		this(context, attrs, android.R.attr.preferenceStyle);
	}

	public NotificationContentPreference(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
		checked_items = new boolean[7];
		setOnPreferenceClickListener(this);
	}

	@Override
	public void onClick(final DialogInterface dialog, final int which) {
		if (prefs == null) return;
		switch (which) {
			case DialogInterface.BUTTON_POSITIVE:
				final SharedPreferences.Editor editor = prefs.edit();
				editor.putBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_HOME_TIMELINE, checked_items[0]);
				editor.putBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_MENTIONS, checked_items[1]);
				editor.putBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_DIRECT_MESSAGES, checked_items[2]);
				editor.putBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_LISTS, checked_items[3]);
				editor.putBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_FOLLOWS, checked_items[4]);
				editor.putBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_FAV, checked_items[5]);
				editor.putBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_RT, checked_items[6]);
				
				editor.commit();
				break;
		}

	}

	@Override
	public void onClick(final DialogInterface dialog, final int which, final boolean isChecked) {
		checked_items[which] = isChecked;
	}

	@Override
	public boolean onPreferenceClick(final Preference preference) {
		prefs = getSharedPreferences();
		if (prefs == null) return false;
		checked_items = new boolean[] { prefs.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_HOME_TIMELINE, false),
				prefs.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_MENTIONS, false),
				prefs.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_DIRECT_MESSAGES, false),
				prefs.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_LISTS, false),
				prefs.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_FOLLOWS, false),
				prefs.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_FAV, false),
				prefs.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_RT, false) };

		final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

		builder.setTitle(getTitle());
		builder.setPositiveButton(android.R.string.ok, this);
		builder.setNegativeButton(android.R.string.cancel, this);
		builder.setMultiChoiceItems(R.array.entries_notification_content, checked_items, this);
		builder.show();

		return true;
	}

}
