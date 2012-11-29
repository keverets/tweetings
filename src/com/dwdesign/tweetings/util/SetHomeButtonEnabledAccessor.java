package com.dwdesign.tweetings.util;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class SetHomeButtonEnabledAccessor {

	public static void setHomeButtonEnabled(final Activity activity, final boolean enabled) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			final ActionBar action_bar = activity.getActionBar();
			action_bar.setHomeButtonEnabled(enabled);
		}
	}
}
