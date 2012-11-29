package com.dwdesign.tweetings.appwidget.provider;

import com.dwdesign.tweetings.activity.LinkHandlerActivity;
import com.dwdesign.tweetings.appwidget.Constants;
import com.dwdesign.tweetings.appwidget.ExtensionApplication;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.appwidget.service.ListWidgetHomeTimelineService;
import com.dwdesign.tweetings.appwidget.service.ListWidgetMentionsService;
import com.dwdesign.tweetings.util.ServiceInterface;
import com.dwdesign.tweetings.appwidget.util.SetRemoteAdapterAccessor;
import com.dwdesign.tweetings.app.TweetingsApplication;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

@TargetApi(11)
public class ListWidgetProvider extends AppWidgetProvider implements Constants {

	public static final String BROADCAST_REFRESH_ALL = "com.dwdesign.tweetings.appwidget.REFRESH_ALL";

	@Override
	public void onDeleted(final Context context, final int[] appWidgetIds) {
		final SharedPreferences.Editor editor = context.getSharedPreferences(WIDGETS_PREFERENCES_NAME,
				Context.MODE_PRIVATE).edit();
		for (final int id : appWidgetIds) {
			editor.remove(String.valueOf(id));
		}
		editor.apply();
		super.onDeleted(context, appWidgetIds);
	}

	@TargetApi(11)
	@Override
	public void onReceive(final Context context, final Intent intent) {
		final SharedPreferences preferences = context.getSharedPreferences(WIDGETS_PREFERENCES_NAME,
				Context.MODE_PRIVATE);
		final String action = intent.getAction();
		final AppWidgetManager manager = AppWidgetManager.getInstance(context);
		final int[] ids = manager.getAppWidgetIds(new ComponentName(context, getClass()));
		if (com.dwdesign.tweetings.Constants.BROADCAST_HOME_TIMELINE_DATABASE_UPDATED.equals(action)
				|| com.dwdesign.tweetings.Constants.BROADCAST_MENTIONS_DATABASE_UPDATED.equals(action)) {
			for (final int id : ids) {
				final int list_widget_type = preferences.getInt(String.valueOf(id), WIDGET_TYPE_HOME_TIMELINE);
				switch (list_widget_type) {
					case WIDGET_TYPE_MENTIONS:
						if (com.dwdesign.tweetings.Constants.BROADCAST_MENTIONS_DATABASE_UPDATED.equals(action)) {
							manager.notifyAppWidgetViewDataChanged(id, R.id.list_view);
						}
						break;
					case WIDGET_TYPE_HOME_TIMELINE:
						if (com.dwdesign.tweetings.Constants.BROADCAST_HOME_TIMELINE_DATABASE_UPDATED.equals(action)) {
							manager.notifyAppWidgetViewDataChanged(id, R.id.list_view);
						}
						break;
				}
			}

		} else if (com.dwdesign.tweetings.Constants.BROADCAST_ACCOUNT_LIST_DATABASE_UPDATED.equals(action)) {
			manager.notifyAppWidgetViewDataChanged(ids, R.id.list_view);
		} else if (com.dwdesign.tweetings.Constants.BROADCAST_REFRESHSTATE_CHANGED.equals(action)) {
			onUpdate(context, manager, ids);
		} else if (BROADCAST_REFRESH_ALL.equals(action)) {
			final ServiceInterface service = ((TweetingsApplication) context.getApplicationContext())
					.getServiceInterface();
			new Thread() {
				@Override
				public void run() {
					service.waitForService();
					service.refreshAll();
				}
			}.start();
		}
		super.onReceive(context, intent);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onUpdate(final Context context, final AppWidgetManager manager, final int[] ids) {
		final SharedPreferences preferences = context.getSharedPreferences(WIDGETS_PREFERENCES_NAME,
				Context.MODE_PRIVATE);
		//final ServiceInterface service = ((TweetingsApplication) context.getApplicationContext()).getServiceInterface();
		final ServiceInterface service = TweetingsApplication.getInstance(context).getServiceInterface();
		service.waitForService();
		final boolean is_mentions_refreshing = service.isMentionsRefreshing();
		final boolean is_home_timeline_refreshing = service.isHomeTimelineRefreshing();
		for (final int id : ids) {
			final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.list_widget);
			final PendingIntent compose_intent = PendingIntent.getActivity(context, 0, new Intent(
					com.dwdesign.tweetings.Constants.INTENT_ACTION_COMPOSE), 0);
			final PendingIntent home_intent = PendingIntent.getActivity(context, 0, new Intent(
					com.dwdesign.tweetings.Constants.INTENT_ACTION_HOME), Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
			final PendingIntent refresh_intent = PendingIntent.getBroadcast(context, 0, new Intent(
					BROADCAST_REFRESH_ALL), 0);
			views.setOnClickPendingIntent(R.id.compose, compose_intent);
			views.setOnClickPendingIntent(R.id.refresh, refresh_intent);
			views.setOnClickPendingIntent(R.id.top_bar, home_intent);

			final Intent adapter_intent;
			final int widget_type = preferences.getInt(String.valueOf(id), WIDGET_TYPE_HOME_TIMELINE);
			switch (widget_type) {
				case WIDGET_TYPE_MENTIONS:
					views.setViewVisibility(R.id.refresh_progress, is_mentions_refreshing ? View.VISIBLE : View.GONE);
					views.setViewVisibility(R.id.refresh, !is_mentions_refreshing ? View.VISIBLE : View.GONE);
					views.setInt(R.id.title, "setText", R.string.mentions);
					adapter_intent = new Intent(context, ListWidgetMentionsService.class);
					break;
				default:
					views.setViewVisibility(R.id.refresh_progress, is_home_timeline_refreshing ? View.VISIBLE
							: View.GONE);
					views.setViewVisibility(R.id.refresh, !is_home_timeline_refreshing ? View.VISIBLE : View.GONE);
					views.setInt(R.id.title, "setText", R.string.home_timeline);
					adapter_intent = new Intent(context, ListWidgetHomeTimelineService.class);
					break;
			}
			/*final Uri.Builder uri_builder = new Uri.Builder();
			uri_builder.scheme(com.dwdesign.tweetings.Constants.SCHEME_TWEETINGS);
			uri_builder.authority(com.dwdesign.tweetings.Constants.AUTHORITY_STATUS);
			final Intent intent = new Intent(Intent.ACTION_VIEW, uri_builder.build());
			final PendingIntent pending_intent = PendingIntent.getActivity(context, 0, intent,
					Intent.FLAG_ACTIVITY_NEW_TASK);*/
			final Intent intent = new Intent(context, LinkHandlerActivity.class);
			intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            
			final PendingIntent pending_intent = PendingIntent.getActivity(context, 0, intent,
					Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
			
			views.setPendingIntentTemplate(R.id.list_view, pending_intent);
			
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
				SetRemoteAdapterAccessor.setRemoteAdapter(views, R.id.list_view, adapter_intent);
			} else {
				views.setRemoteAdapter(id, R.id.list_view, adapter_intent);
			}
			manager.updateAppWidget(id, views);
		}
	}

}
