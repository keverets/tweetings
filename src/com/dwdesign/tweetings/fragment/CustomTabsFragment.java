package com.dwdesign.tweetings.fragment;

import static com.dwdesign.tweetings.util.Utils.getTabIconDrawable;
import static com.dwdesign.tweetings.util.Utils.getTabIconObject;
import static com.dwdesign.tweetings.util.Utils.getTabTypeName;

import com.dwdesign.popupmenu.PopupMenu;
import com.dwdesign.popupmenu.PopupMenu.OnMenuItemClickListener;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.adapter.SeparatedListAdapter;
import com.dwdesign.tweetings.fragment.CustomTabsFragment.CustomTabsAdapter.CustomTabSpec;
import com.dwdesign.tweetings.fragment.CustomTabsFragment.DefaultTabsAdapter.DefaultTabSpec;
import com.dwdesign.tweetings.fragment.CustomTabsFragment.TabsAdapterInterface.TabSpec;
import com.dwdesign.tweetings.model.Panes;
import com.dwdesign.tweetings.provider.TweetStore.Tabs;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class CustomTabsFragment extends BaseListFragment implements LoaderCallbacks<Cursor>, OnItemLongClickListener,
		OnMenuItemClickListener, OnItemClickListener, Panes.Right {

	private ContentResolver mResolver;
	private SharedPreferences mPreferences;

	private ListView mListView;

	private PopupMenu mPopupMenu;

	private SeparatedListAdapter<TabsAdapterInterface> mAdapter;
	private DefaultTabsAdapter mDefaultTabsAdapter;
	private CustomTabsAdapter mCustomTabsAdapter;

	private CustomTabSpec mSelectedTab;

	private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (BROADCAST_TABS_UPDATED.equals(action)) {
				mSelectedTab = null;
				getLoaderManager().restartLoader(0, null, CustomTabsFragment.this);
			}
		}

	};

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setHasOptionsMenu(true);
		mResolver = getContentResolver();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		final Context context = getActivity();
		mAdapter = new SeparatedListAdapter<TabsAdapterInterface>(context);
		mCustomTabsAdapter = new CustomTabsAdapter(context);
		mDefaultTabsAdapter = new DefaultTabsAdapter(context);
		mAdapter.addSection(getString(R.string.default_tabs), mDefaultTabsAdapter);
		mAdapter.addSection(getString(R.string.custom_tabs), mCustomTabsAdapter);
		setListAdapter(mAdapter);
		mListView = getListView();
		mListView.setOnItemClickListener(this);
		mListView.setOnItemLongClickListener(this);
		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
		switch (requestCode) {
			case REQUEST_ADD_TAB: {
				if (resultCode == Activity.RESULT_OK) {
					final Bundle extras = intent.getExtras();
					if (extras == null) {
						break;
					}
					final ContentValues values = new ContentValues();
					values.put(Tabs.ARGUMENTS, extras.getString(INTENT_KEY_ARGUMENTS));
					values.put(Tabs.NAME, extras.getString(INTENT_KEY_NAME));
					values.put(Tabs.POSITION, mCustomTabsAdapter.getCount());
					values.put(Tabs.TYPE, extras.getString(INTENT_KEY_TYPE));
					values.put(Tabs.ICON, extras.getString(INTENT_KEY_ICON));
					mResolver.insert(Tabs.CONTENT_URI, values);
					getLoaderManager().restartLoader(0, null, this);
				}
				break;
			}
			case REQUEST_EDIT_TAB: {
				if (resultCode == Activity.RESULT_OK && mSelectedTab != null) {
					final Bundle extras = intent.getExtras();
					if (extras == null) {
						break;
					}
					final ContentValues values = new ContentValues();
					values.put(Tabs.ARGUMENTS, extras.getString(INTENT_KEY_ARGUMENTS));
					values.put(Tabs.NAME, extras.getString(INTENT_KEY_NAME));
					values.put(Tabs.TYPE, extras.getString(INTENT_KEY_TYPE));
					values.put(Tabs.ICON, extras.getString(INTENT_KEY_ICON));
					mResolver.update(Tabs.CONTENT_URI, values, Tabs._ID + " = " + mSelectedTab.getId(), null);
					getLoaderManager().restartLoader(0, null, this);
				}
				break;
			}
		}
		super.onActivityResult(requestCode, resultCode, intent);
	}

	@Override
	public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
		return new CursorLoader(getActivity(), Tabs.CONTENT_URI, Tabs.COLUMNS, null, null, Tabs.DEFAULT_SORT_ORDER);
	}

	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
		inflater.inflate(R.menu.menu_custom_tabs, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
		mSelectedTab = null;
		final Object obj = mAdapter.getItem(position);
		if (!(obj instanceof TabsAdapterInterface.TabSpec)) return;
		final TabsAdapterInterface.TabSpec tab = (TabSpec) obj;
		// mSelectedId = mCursor.getLong(_id_idx);
		if (tab instanceof CustomTabsAdapter.CustomTabSpec) {
			final Intent intent = new Intent(INTENT_ACTION_EDIT_CUSTOM_TAB);
			final Bundle extras = new Bundle();
			mSelectedTab = (CustomTabSpec) tab;
			extras.putString(INTENT_KEY_ICON, tab.getIcon());
			extras.putString(INTENT_KEY_NAME, tab.getName());
			extras.putString(INTENT_KEY_TYPE, tab.getType());
			extras.putString(INTENT_KEY_ARGUMENTS, tab.getArguments());
			intent.setPackage(getActivity().getPackageName());
			intent.putExtras(extras);
			startActivityForResult(intent, REQUEST_EDIT_TAB);
		} else if (tab instanceof DefaultTabsAdapter.DefaultTabSpec) {
			final DefaultTabsAdapter.DefaultTabSpec def_tab = (DefaultTabSpec) tab;
			final SharedPreferences.Editor editor = mPreferences.edit();
			editor.putBoolean(def_tab.getPreferenceKey(), !def_tab.isEnabled());
			editor.commit();
			mAdapter.notifyDataSetChanged();
		}

	}

	@Override
	public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, final long id) {
		final Object obj = mAdapter.getItem(position);
		if (!(obj instanceof CustomTabsAdapter.CustomTabSpec)) return false;
		mSelectedTab = (CustomTabsAdapter.CustomTabSpec) obj;
		if (mPopupMenu != null) {
			mPopupMenu.dismiss();
		}
		mPopupMenu = PopupMenu.getInstance(getActivity(), view);
		mPopupMenu.inflate(R.menu.action_custom_tab);
		final Menu menu = mPopupMenu.getMenu();
		final MenuItem upItem = menu.findItem(MENU_UP);
		if (upItem != null) {
			upItem.setVisible(mSelectedTab.getPosition() != 0);
		}
		final MenuItem downItem = menu.findItem(MENU_DOWN);
		if (downItem != null) {
			final int count = mCustomTabsAdapter.getCount();
			downItem.setVisible(count > 1 && mSelectedTab.getPosition() != count - 1);
		}
		mPopupMenu.setOnMenuItemClickListener(this);
		mPopupMenu.show();
		return true;
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader) {
		mCustomTabsAdapter.changeCursor(null);
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor cursor) {
		mCustomTabsAdapter.changeCursor(cursor);
		mAdapter.notifyDataSetChanged();
	}

	@Override
	public boolean onMenuItemClick(final MenuItem item) {
		if (mSelectedTab == null || mSelectedTab.isClosed()) return false;
		final int mSelectedPos = mSelectedTab.getPosition();
		switch (item.getItemId()) {
			case MENU_UP: {
				if (mSelectedPos > 0 && mSelectedPos < mCustomTabsAdapter.getCount()) {
					final long selected_id = mSelectedTab.getId();
					mSelectedTab.moveToPrevious();
					final long previous_id = mSelectedTab.getId();
					final ContentValues values = new ContentValues();
					values.put(Tabs.POSITION, mSelectedPos - 1);
					mResolver.update(Tabs.CONTENT_URI, values, Tabs._ID + " = " + selected_id, null);
					values.put(Tabs.POSITION, mSelectedPos);
					mResolver.update(Tabs.CONTENT_URI, values, Tabs._ID + " = " + previous_id, null);
				}
				break;
			}
			case MENU_DOWN: {
				if (mSelectedPos >= 0 && mSelectedPos < mCustomTabsAdapter.getCount() - 1) {
					mSelectedTab.moveToPosition(mSelectedPos);
					final long selected_id = mSelectedTab.getId();
					mSelectedTab.moveToNext();
					final long next_id = mSelectedTab.getId();
					final ContentValues values = new ContentValues();
					values.put(Tabs.POSITION, mSelectedPos + 1);
					mResolver.update(Tabs.CONTENT_URI, values, Tabs._ID + " = " + selected_id, null);
					values.put(Tabs.POSITION, mSelectedPos);
					mResolver.update(Tabs.CONTENT_URI, values, Tabs._ID + " = " + next_id, null);
				}
				break;
			}
			case MENU_EDIT: {
				mSelectedTab.moveToPosition(mSelectedPos);
				final Intent intent = new Intent(INTENT_ACTION_EDIT_CUSTOM_TAB);
				final Bundle extras = new Bundle();
				extras.putString(INTENT_KEY_ICON, mSelectedTab.getIcon());
				extras.putString(INTENT_KEY_NAME, mSelectedTab.getName());
				extras.putString(INTENT_KEY_TYPE, mSelectedTab.getType());
				extras.putString(INTENT_KEY_ARGUMENTS, mSelectedTab.getArguments());
				intent.setPackage(getActivity().getPackageName());
				intent.putExtras(extras);
				startActivityForResult(intent, REQUEST_EDIT_TAB);
				break;
			}
			case MENU_DELETE: {
				mResolver.delete(Tabs.CONTENT_URI, Tabs._ID + " = " + mSelectedTab.getId(), null);
				break;
			}
		}
		return false;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case MENU_ADD: {
				final Intent intent = new Intent(INTENT_ACTION_NEW_CUSTOM_TAB);
				final Bundle extras = new Bundle();
				extras.putInt(INTENT_KEY_POSITION, mCustomTabsAdapter.getCount());
				intent.setPackage(getActivity().getPackageName());
				intent.putExtras(extras);
				startActivityForResult(intent, REQUEST_ADD_TAB);
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onStart() {
		super.onStart();
		final IntentFilter filter = new IntentFilter(BROADCAST_TABS_UPDATED);
		registerReceiver(mStateReceiver, filter);
	}

	@Override
	public void onStop() {
		if (mPopupMenu != null) {
			mPopupMenu.dismiss();
		}
		unregisterReceiver(mStateReceiver);
		super.onStop();
	}

	public static class CustomTabsAdapter extends SimpleCursorAdapter implements TabsAdapterInterface {

		private CursorIndices mIndices;

		public CustomTabsAdapter(final Context context) {
			super(context, R.layout.two_line_with_icon_list_item, null, new String[] { Tabs.NAME },
					new int[] { android.R.id.text1 }, 0);
		}

		@Override
		public void bindView(final View view, final Context context, final Cursor cursor) {
			super.bindView(view, context, cursor);
			final TextView text2 = (TextView) view.findViewById(android.R.id.text2);
			final ImageView icon = (ImageView) view.findViewById(android.R.id.icon);
			final TabSpec item = getTab(cursor.getPosition());
			text2.setText(getTabTypeName(context, item.getType()));
			icon.setBackgroundResource(R.drawable.gallery_selected_default);
			icon.setImageDrawable(getTabIconDrawable(mContext, getTabIconObject(item.getIcon())));
			view.findViewById(R.id.checkbox).setVisibility(View.GONE);
		}

		@Override
		public void changeCursor(final Cursor cursor) {
			super.changeCursor(cursor);
			if (cursor != null) {
				mIndices = new CursorIndices(cursor);
			}
		}

		@Override
		public TabSpec getItem(final int position) {
			return new CustomTabSpec((Cursor) super.getItem(position), mIndices);
		}

		@Override
		public TabSpec getTab(final int position) {
			return getItem(position);
		}

		static class CursorIndices {
			final int _id, name, icon, type, arguments;

			CursorIndices(final Cursor mCursor) {
				_id = mCursor.getColumnIndex(Tabs._ID);
				icon = mCursor.getColumnIndex(Tabs.ICON);
				name = mCursor.getColumnIndex(Tabs.NAME);
				type = mCursor.getColumnIndex(Tabs.TYPE);
				arguments = mCursor.getColumnIndex(Tabs.ARGUMENTS);
			}
		}

		static class CustomTabSpec implements Cursor, TabsAdapterInterface.TabSpec {

			final Cursor cursor;
			final CursorIndices indices;

			CustomTabSpec(final Cursor cursor, final CursorIndices indices) {
				this.cursor = cursor;
				this.indices = indices;
			}

			@Override
			public void close() {
				cursor.close();
			}

			@Override
			public void copyStringToBuffer(final int columnIndex, final CharArrayBuffer buffer) {
				cursor.copyStringToBuffer(columnIndex, buffer);
			}

			@Override
			public void deactivate() {
				cursor.deactivate();
			}

			@Override
			public String getArguments() {
				return cursor.getString(indices.arguments);
			}

			@Override
			public byte[] getBlob(final int columnIndex) {
				return cursor.getBlob(columnIndex);
			}

			@Override
			public int getColumnCount() {
				return cursor.getColumnCount();
			}

			@Override
			public int getColumnIndex(final String columnName) {
				return cursor.getColumnIndex(columnName);
			}

			@Override
			public int getColumnIndexOrThrow(final String columnName) throws IllegalArgumentException {
				return cursor.getColumnIndexOrThrow(columnName);
			}

			@Override
			public String getColumnName(final int columnIndex) {
				return cursor.getColumnName(columnIndex);
			}

			@Override
			public String[] getColumnNames() {
				return cursor.getColumnNames();
			}

			@Override
			public int getCount() {
				return cursor.getCount();
			}

			@Override
			public double getDouble(final int columnIndex) {
				return cursor.getDouble(columnIndex);
			}

			@Override
			public Bundle getExtras() {
				return cursor.getExtras();
			}

			@Override
			public float getFloat(final int columnIndex) {
				return cursor.getFloat(columnIndex);
			}

			@Override
			public String getIcon() {
				return cursor.getString(indices.icon);
			}

			public long getId() {
				return cursor.getLong(indices._id);
			}

			@Override
			public int getInt(final int columnIndex) {
				return cursor.getInt(columnIndex);
			}

			@Override
			public long getLong(final int columnIndex) {
				return cursor.getLong(columnIndex);
			}

			@Override
			public String getName() {
				return cursor.getString(indices.name);
			}

			@Override
			public int getPosition() {
				return cursor.getPosition();
			}

			@Override
			public short getShort(final int columnIndex) {
				return cursor.getShort(columnIndex);
			}

			@Override
			public String getString(final int columnIndex) {
				return cursor.getString(columnIndex);
			}

			@Override
			public String getType() {
				return cursor.getString(indices.type);
			}

			@TargetApi(Build.VERSION_CODES.HONEYCOMB)
			@Override
			public int getType(final int columnIndex) {
				return cursor.getType(columnIndex);
			}

			@Override
			public boolean getWantsAllOnMoveCalls() {
				return cursor.getWantsAllOnMoveCalls();
			}

			@Override
			public boolean isAfterLast() {
				return cursor.isAfterLast();
			}

			@Override
			public boolean isBeforeFirst() {
				return cursor.isBeforeFirst();
			}

			@Override
			public boolean isClosed() {
				return cursor.isClosed();
			}

			@Override
			public boolean isDefault() {
				return false;
			}

			@Override
			public boolean isFirst() {
				return cursor.isFirst();
			}

			@Override
			public boolean isLast() {
				return cursor.isLast();
			}

			@Override
			public boolean isNull(final int columnIndex) {
				return cursor.isNull(columnIndex);
			}

			@Override
			public boolean move(final int offset) {
				return cursor.move(offset);
			}

			@Override
			public boolean moveToFirst() {
				return cursor.moveToFirst();
			}

			@Override
			public boolean moveToLast() {
				return cursor.moveToLast();
			}

			@Override
			public boolean moveToNext() {
				return cursor.moveToNext();
			}

			@Override
			public boolean moveToPosition(final int position) {
				return cursor.moveToPosition(position);
			}

			@Override
			public boolean moveToPrevious() {
				return cursor.moveToPrevious();
			}

			@Override
			public void registerContentObserver(final ContentObserver observer) {
				cursor.registerContentObserver(observer);
			}

			@Override
			public void registerDataSetObserver(final DataSetObserver observer) {
				cursor.registerDataSetObserver(observer);
			}

			@Override
			@Deprecated
			public boolean requery() {
				return cursor.requery();
			}

			@Override
			public Bundle respond(final Bundle extras) {
				return cursor.respond(extras);
			}

			@Override
			public void setNotificationUri(final ContentResolver cr, final Uri uri) {
				cursor.setNotificationUri(cr, uri);
			}

			@Override
			public String toString() {
				return getName();
			}

			@Override
			public void unregisterContentObserver(final ContentObserver observer) {
				cursor.unregisterContentObserver(observer);
			}

			@Override
			public void unregisterDataSetObserver(final DataSetObserver observer) {
				cursor.unregisterDataSetObserver(observer);
			}

		}

	}

	public static class DefaultTabsAdapter extends ArrayAdapter<DefaultTabsAdapter.DefaultTabSpec> implements
			TabsAdapterInterface {

		final SharedPreferences prefs;
		final Context context;

		public DefaultTabsAdapter(final Context context) {
			super(context, R.layout.two_line_with_icon_list_item, android.R.id.text1);
			this.context = context;
			prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
			reload();
		}

		@Override
		public DefaultTabSpec getTab(final int position) {
			return getItem(position);
		}

		@Override
		public View getView(final int position, final View convertView, final ViewGroup parent) {
			final View view = super.getView(position, convertView, parent);
			final ImageView icon = (ImageView) view.findViewById(android.R.id.icon);
			final CheckBox checkbox = (CheckBox) view.findViewById(R.id.checkbox);
			final DefaultTabSpec item = getTab(position);
			icon.setBackgroundResource(R.drawable.gallery_selected_default);
			icon.setImageDrawable(getTabIconDrawable(context, getTabIconObject(item.getIcon())));
			view.findViewById(android.R.id.text2).setVisibility(View.GONE);
			checkbox.setChecked(item.isEnabled());
			return view;
		}

		void reload() {
			add(new DefaultTabSpec(prefs, context.getString(R.string.home), "home", PREFERENCE_KEY_SHOW_HOME_TAB));
			add(new DefaultTabSpec(prefs, context.getString(R.string.mentions), "mention",
					PREFERENCE_KEY_SHOW_MENTIONS_TAB));
			add(new DefaultTabSpec(prefs, context.getString(R.string.direct_messages), "message",
					PREFERENCE_KEY_SHOW_MESSAGES_TAB));
			add(new DefaultTabSpec(prefs, context.getString(R.string.accounts), "accounts",
					PREFERENCE_KEY_SHOW_ACCOUNTS_TAB));
		}

		static class DefaultTabSpec implements TabsAdapterInterface.TabSpec {
			final SharedPreferences prefs;
			final String name, icon, prefs_key;

			DefaultTabSpec(final SharedPreferences prefs, final String name, final String icon, final String prefs_key) {
				this.name = name;
				this.icon = icon;
				this.prefs_key = prefs_key;
				this.prefs = prefs;
			}

			@Override
			public String getArguments() {
				return null;
			}

			@Override
			public String getIcon() {
				return icon;
			}

			@Override
			public String getName() {
				return name;
			}

			public String getPreferenceKey() {
				return prefs_key;
			}

			@Override
			public String getType() {
				return null;
			}

			@Override
			public boolean isDefault() {
				return true;
			}

			public boolean isEnabled() {
				return prefs.getBoolean(prefs_key, true);
			}

			@Override
			public String toString() {
				return name;
			}
		}
	}

	static interface TabsAdapterInterface extends ListAdapter {

		TabSpec getTab(int position);

		static interface TabSpec {
			String getArguments();

			String getIcon();

			String getName();

			String getType();

			boolean isDefault();
		}
	}
}