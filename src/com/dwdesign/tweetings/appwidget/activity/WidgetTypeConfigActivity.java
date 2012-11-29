package com.dwdesign.tweetings.appwidget.activity;

import com.dwdesign.tweetings.appwidget.Constants;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.appwidget.provider.ListWidgetProvider;

import android.app.ListActivity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class WidgetTypeConfigActivity extends ListActivity implements Constants {

	private ArrayAdapter<CharSequence> mAdapter;
	private SharedPreferences mPreferences;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mAdapter = ArrayAdapter.createFromResource(this, R.array.ticker_types, android.R.layout.simple_list_item_1);
		mPreferences = getSharedPreferences(WIDGETS_PREFERENCES_NAME, MODE_PRIVATE);
		setListAdapter(mAdapter);
	}

	@Override
	protected void onListItemClick(final ListView l, final View v, final int position, final long id) {
		final Intent intent = getIntent();
		final Bundle extras = intent.getExtras();
		final int widget_id = extras != null ? extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
				AppWidgetManager.INVALID_APPWIDGET_ID) : AppWidgetManager.INVALID_APPWIDGET_ID;
		if (extras == null || widget_id == AppWidgetManager.INVALID_APPWIDGET_ID) {
			finish();
			return;
		}
		final SharedPreferences.Editor editor = mPreferences.edit();
		switch (position) {
			case 0:
				editor.putInt(String.valueOf(widget_id), WIDGET_TYPE_HOME_TIMELINE);
				break;
			case 1:
				editor.putInt(String.valueOf(widget_id), WIDGET_TYPE_MENTIONS);
				break;
			default:
				setResult(RESULT_CANCELED);
				finish();
				return;
		}
		editor.apply();
		setResult(RESULT_OK, new Intent().putExtras(extras));
		final AppWidgetManager manager = AppWidgetManager.getInstance(this);

		final Intent list_broadcast = new Intent(this, ListWidgetProvider.class);
		list_broadcast.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
		list_broadcast.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,
				manager.getAppWidgetIds(new ComponentName(this, ListWidgetProvider.class)));
		sendBroadcast(list_broadcast);
		
		finish();
	}

}
