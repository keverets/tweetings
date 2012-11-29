package com.dwdesign.tweetings.appwidget.util;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

public final class SetRemoteAdapterAccessor {

	@TargetApi(14)
	public static void setRemoteAdapter(final RemoteViews views, final int viewId, final Intent intent) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			views.setRemoteAdapter(viewId, intent);
		}
	}
}
