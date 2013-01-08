package com.dwdesign.tweetings.fragment;

import static com.dwdesign.tweetings.util.Utils.getActivatedAccountIds;
import static com.dwdesign.tweetings.util.Utils.getAccountUsername;
import static com.dwdesign.tweetings.util.Utils.openUserListTimeline;

import java.util.ArrayList;
import java.util.List;

import com.dwdesign.popupmenu.PopupMenu.OnMenuItemClickListener;
import com.dwdesign.tweetings.activity.HomeActivity;
import com.dwdesign.tweetings.adapter.SeparatedListAdapter;
import com.dwdesign.tweetings.adapter.UserListsAdapter;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.loader.BaseUserListsLoader;
import com.dwdesign.tweetings.loader.UserListsLoader;
import com.dwdesign.tweetings.model.ListAction;
import com.dwdesign.tweetings.model.Panes;
import com.dwdesign.tweetings.model.ParcelableUserList;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;

public class ListsFragment extends BaseListFragment implements
	LoaderCallbacks<List<ParcelableUserList>>, OnItemClickListener, OnItemLongClickListener, 
	Panes.Left, OnMenuItemClickListener {

	private SeparatedListAdapter<UserListsAdapter> mAdapter;
	
	private SharedPreferences mPreferences;
	private ListView mListView;
	private TweetingsApplication mApplication;
	private long mCursor = -1;
	private Fragment mDetailFragment;
	
	private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (BROADCAST_ACCOUNT_LIST_DATABASE_UPDATED.equals(action)) {
				if (!getLoaderManager().hasRunningLoaders()) {
					ListsFragment.this.resetLists();
				}
			}
		}
	};
	
	public void resetLists() {
		mAdapter.clear();
		final long[] activated_ids = getActivatedAccountIds(getActivity());
		int i = 0;
		for (final long account_id : activated_ids) {
			UserListsAdapter listAdapter = new UserListsAdapter(getActivity());
			String user_name = getAccountUsername(getActivity(), account_id);
			final float text_size = mPreferences.getFloat(PREFERENCE_KEY_TEXT_SIZE, PREFERENCE_DEFAULT_TEXT_SIZE);
			final boolean display_profile_image = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_PROFILE_IMAGE, true);
			final boolean display_name = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_NAME, false);
			listAdapter.setDisplayProfileImage(display_profile_image);
			listAdapter.setTextSize(text_size);
			listAdapter.setDisplayName(display_name);
			mAdapter.addSection("@" + user_name, listAdapter);
			
			final Bundle args = new Bundle();
			args.putLong(INTENT_KEY_ACCOUNT_ID, account_id);
			args.putLong(INTENT_KEY_USER_ID, account_id);
			args.putString(INTENT_KEY_SCREEN_NAME, user_name);
			args.putInt(INTENT_KEY_POSITION, i);
			try {
				getLoaderManager().destroyLoader(i);
			} catch (Exception e) {

			}
			getLoaderManager().initLoader(i, args, this);
			i++;
		}
		
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mApplication = getApplication();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mAdapter = new SeparatedListAdapter<UserListsAdapter>(getActivity());
		resetLists();
		
		mListView = getListView();
		setListAdapter(mAdapter);
		mListView.setOnItemClickListener(this);
		mListView.setOnItemLongClickListener(this);
	}

	
	
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	
	@Override
	public final void onItemClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		final Object obj = mAdapter.getItem(position);
		final ParcelableUserList list = (ParcelableUserList) obj;
		openUserListTimeline(getActivity(), list.account_id, list.list_id, list.user_id, list.user_name, list.name);
	}
	
	@Override
	public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, final long id) {
		final Object obj = mAdapter.getItem(position);
		final ParcelableUserList list = (ParcelableUserList) obj;
		openUserListDetails(getActivity(), list.account_id, list.list_id, list.user_id,
				list.user_screen_name, list.name);
		return true;
	}
	
	private void openUserListDetails(final Activity activity, final long account_id, final int list_id, final long user_id, final String screen_name,
			final String list_name) {
		if (activity == null) return;
		if (activity instanceof HomeActivity && ((HomeActivity) activity).isDualPaneMode()) {
			final HomeActivity home_activity = (HomeActivity) activity;
			if (mDetailFragment instanceof UserProfileFragment && mDetailFragment.isAdded()) {
				((UserProfileFragment) mDetailFragment).getUserInfo(account_id, user_id, screen_name);
			} else {
				mDetailFragment = new UserListDetailsFragment();
				final Bundle args = new Bundle();
				args.putLong(INTENT_KEY_ACCOUNT_ID, account_id);
				args.putInt(INTENT_KEY_LIST_ID, list_id);
				args.putLong(INTENT_KEY_USER_ID, user_id);
				args.putString(INTENT_KEY_SCREEN_NAME, screen_name);
				args.putString(INTENT_KEY_LIST_NAME, list_name);
				mDetailFragment.setArguments(args);
				home_activity.showAtPane(HomeActivity.PANE_RIGHT, mDetailFragment, true);
			}
		} else {
			final Uri.Builder builder = new Uri.Builder();
			builder.scheme(SCHEME_TWEETINGS);
			builder.authority(AUTHORITY_LIST_DETAILS);
			builder.appendQueryParameter(QUERY_PARAM_ACCOUNT_ID, String.valueOf(account_id));
			if (list_id > 0) {
				builder.appendQueryParameter(QUERY_PARAM_LIST_ID, String.valueOf(list_id));
			}
			if (user_id > 0) {
				builder.appendQueryParameter(QUERY_PARAM_USER_ID, String.valueOf(user_id));
			}
			if (screen_name != null) {
				builder.appendQueryParameter(QUERY_PARAM_SCREEN_NAME, screen_name);
			}
			if (list_name != null) {
				builder.appendQueryParameter(QUERY_PARAM_LIST_NAME, list_name);
			}
			activity.startActivity(new Intent(Intent.ACTION_VIEW, builder.build()));
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		//final IntentFilter filter = new IntentFilter(BROADCAST_ACCOUNT_LIST_DATABASE_UPDATED);
		//registerReceiver(mStatusReceiver, filter);
	}

	@Override
	public void onStop() {
		super.onStop();
		//unregisterReceiver(mStatusReceiver);
	}

	@Override
	public boolean onMenuItemClick(MenuItem item) {
		// TODO Auto-generated method stub
		return false;
	}
	
	public Loader<List<ParcelableUserList>> newLoaderInstance(final long account_id, final long user_id, final String screen_name, final int position) {
		return new UserListsLoader(getActivity(), account_id, user_id, screen_name, -1, null, position);
	}
	
	@Override
	public Loader<List<ParcelableUserList>> onCreateLoader(final int id, final Bundle args) {
		setProgressBarIndeterminateVisibility(true);
		long account_id = args.getLong(INTENT_KEY_ACCOUNT_ID, -1);
		long user_id = args.getLong(INTENT_KEY_USER_ID, -1);
		String screen_name = args.getString(INTENT_KEY_SCREEN_NAME);
		int position = args.getInt(INTENT_KEY_POSITION, 0);
		return newLoaderInstance(account_id, user_id, screen_name, position);
	}
	
	@Override
	public void onLoaderReset(final Loader<List<ParcelableUserList>> loader) {
		setProgressBarIndeterminateVisibility(false);
	}

	@Override
	public void onLoadFinished(final Loader<List<ParcelableUserList>> loader, final List<ParcelableUserList> data) {
		setProgressBarIndeterminateVisibility(false);
		int position = ((UserListsLoader)loader).getPosition();
		if (position >= 0) {
			ArrayList<UserListsAdapter> adapters = mAdapter.getAdapters();
			try {
				UserListsAdapter listAdapter = adapters.get(position);
				listAdapter.setData(data, true);
				listAdapter.notifyDataSetChanged();
				mAdapter.notifyDataSetChanged();
			}
			catch (Exception e) {
				
			}
		
		}
		if (loader instanceof BaseUserListsLoader) {
			final long cursor = ((BaseUserListsLoader) loader).getNextCursor();
			if (cursor != -2) {
				mCursor = cursor;
			}
		}
		setListShown(true);
	}
	
	
	
}