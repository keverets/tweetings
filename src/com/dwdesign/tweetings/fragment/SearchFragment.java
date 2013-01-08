package com.dwdesign.tweetings.fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import twitter4j.ResponseList;
import twitter4j.SavedSearch;

import com.dwdesign.popupmenu.PopupMenu;
import com.dwdesign.popupmenu.PopupMenu.OnMenuItemClickListener;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.adapter.ListActionAdapter;
import com.dwdesign.tweetings.adapter.SeparatedListAdapter;
import com.dwdesign.tweetings.adapter.UserListsAdapter;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.fragment.SavedSearchesListFragment.SavedSearchesAdapter;
import com.dwdesign.tweetings.fragment.UserProfileFragment.UserRecentPhotosAction;
import com.dwdesign.tweetings.loader.SavedSearchesLoader;
import com.dwdesign.tweetings.loader.UserListsLoader;
import com.dwdesign.tweetings.model.ListAction;
import com.dwdesign.tweetings.model.Panes;
import com.dwdesign.tweetings.model.ParcelableUserList;

import static com.dwdesign.tweetings.util.Utils.getAccountUsername;
import static com.dwdesign.tweetings.util.Utils.getActivatedAccountIds;
import static com.dwdesign.tweetings.util.Utils.openSavedSearches;
import static com.dwdesign.tweetings.util.Utils.openTrends;
import static com.dwdesign.tweetings.util.Utils.openTweetSearch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Adapter;
import android.widget.ListAdapter;
import android.widget.ListView;

public class SearchFragment extends BaseListFragment implements
		OnItemClickListener, OnItemLongClickListener, Panes.Left, OnMenuItemClickListener, LoaderCallbacks<ResponseList<SavedSearch>> {

	private SeparatedListAdapter<Adapter> mAdapter;
	private ListActionAdapter mSearchAdapter;
	private SavedSearchesAdapter mSavedSearchesAdapter;
	private long[] account_ids;
	private int mSelectedId;
	
	private SharedPreferences mPreferences;
	private ListView mListView;
	private TweetingsApplication mApplication;
	private PopupMenu mPopupMenu;
	
	private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (BROADCAST_ACCOUNT_LIST_DATABASE_UPDATED.equals(action)) {
				if (!getLoaderManager().hasRunningLoaders()) {
					SearchFragment.this.loadSavedSearches();
				}
			}
			else if (BROADCAST_SEARCH_CHANGED.equals(action)) {
				SearchFragment.this.loadSavedSearches();
			}
		}
	};

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mApplication = getApplication();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mAdapter = new SeparatedListAdapter<Adapter>(getActivity());
		mSearchAdapter = new ListActionAdapter(getActivity());
		mSearchAdapter.add(new SearchAction());
		mSearchAdapter.add(new TrendsAction());
		
		loadSavedSearches();
		setListShown(false);
		
		mListView = getListView();
		setListAdapter(mAdapter);
		mListView.setOnItemClickListener(this);
		mListView.setOnItemLongClickListener(this);
	}
	
	private void loadSavedSearches() {
		try {
			mAdapter.clear();
			mAdapter.addSection(getString(R.string.search_menu), mSearchAdapter);
			
			final long[] activated_ids = getActivatedAccountIds(getActivity());
			int i = 1;
			account_ids = null;
			account_ids = new long[activated_ids.length];
			for (final long account_id : activated_ids) {
				try {
					getLoaderManager().destroyLoader(i);
				} catch (Exception e) {
					
				}
				account_ids[i-1] = account_id;
				String user_name = getAccountUsername(getActivity(), account_id);
				
				mSavedSearchesAdapter = new SavedSearchesAdapter(getActivity());
				mAdapter.addSection("@" + user_name + ": " + getString(R.string.saved_searches), mSavedSearchesAdapter);
				final Bundle args = new Bundle();
				args.putLong(INTENT_KEY_ACCOUNT_ID, account_id);
				args.putInt(INTENT_KEY_POSITION, i);
				getLoaderManager().initLoader(i, args, this);
				i++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	
	@Override
	public final void onItemClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		final Object obj = mAdapter.getItem(position);
		final int section = mAdapter.getSection(position);
		if (obj.getClass().getSimpleName().startsWith("SavedSearch")) {
			final SavedSearch search = (SavedSearch) obj;
			long account_id = account_ids[section-1];
			openTweetSearch(getActivity(), account_id, search.getQuery(), search.getId());
		}
		else {
			final ListAction row = (ListAction) obj;
			row.onClick();
		}
	}

	public boolean onItemLongClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		final Object obj = mAdapter.getItem(position);
		if (obj.getClass().getSimpleName().startsWith("SavedSearch")) {
			mSelectedId = position;
			mPopupMenu = PopupMenu.getInstance(getActivity(), view);
			mPopupMenu.inflate(R.menu.action_savedsearch);
			mPopupMenu.setOnMenuItemClickListener(this);
			mPopupMenu.show();
			return true;
		}
		return false;
	}
	
	public boolean onMenuItemClick(final MenuItem item) {
		switch (item.getItemId()) {
			case MENU_DELETE: {
				final SavedSearch obj = (SavedSearch) mAdapter.getItem(mSelectedId);
				final int section = mAdapter.getSection(mSelectedId);
				long account_id = account_ids[section-1];
				final TweetingsApplication application = getApplication();
				application.getServiceInterface().destroySavedSearch(account_id, obj.getId());
				break;
			}
		}
		return super.onContextItemSelected(item);
	}

	class SearchAction extends ListAction {

		@Override
		public long getId() {
			return 1;
		}
		
		@Override
		public String getName() {
			return getString(R.string.search);
		}
		
		@Override
		public void onClick() {
			getActivity().onSearchRequested();
		}
	}
	
	class SearchToolsAction extends ListAction {

		@Override
		public long getId() {
			return 1;
		}
		
		@Override
		public String getName() {
			return getString(R.string.saved_searches);
		}
		
		@Override
		public void onClick() {
			final long default_account_id = mPreferences.getLong(PREFERENCE_KEY_DEFAULT_ACCOUNT_ID, -1);
			openSavedSearches(getActivity(), default_account_id);
		}
	}
	
	class TrendsAction extends ListAction {

		@Override
		public long getId() {
			return 2;
		}
		
		@Override
		public String getName() {
			return getString(R.string.trends);
		}
		
		@Override
		public void onClick() {
			final long account_id = mPreferences.getLong(PREFERENCE_KEY_DEFAULT_ACCOUNT_ID, -1);
			openTrends(getActivity(), account_id);
		}
	}
	
	private static final Comparator<SavedSearch> POSITION_COMPARATOR = new Comparator<SavedSearch>() {

		@Override
		public int compare(final SavedSearch object1, final SavedSearch object2) {
			return object1.getPosition() - object2.getPosition();
		}

	};
	
	@Override
	public Loader<ResponseList<SavedSearch>> onCreateLoader(final int id, final Bundle args) {
		setProgressBarIndeterminateVisibility(true);
		long account_id = args.getLong(INTENT_KEY_ACCOUNT_ID, -1);
		int position = args.getInt(INTENT_KEY_POSITION, 1);
		return new SavedSearchesLoader(getActivity(), account_id, position);
	}
	
	@Override
	public void onLoaderReset(final Loader<ResponseList<SavedSearch>> loader) {
		setProgressBarIndeterminateVisibility(false);
	}

	@Override
	public void onLoadFinished(final Loader<ResponseList<SavedSearch>> loader, final ResponseList<SavedSearch> data) {
		setProgressBarIndeterminateVisibility(false);
		if (data != null) {
			Collections.sort(data, POSITION_COMPARATOR);
		}
		int position = ((SavedSearchesLoader)loader).getPosition();
		
		if (position >= 1) {
			ArrayList<Adapter> adapters = mAdapter.getAdapters();
			try {
				SavedSearchesAdapter searchAdapter = (SavedSearchesAdapter) adapters.get(position);
				searchAdapter.setData(data);
				searchAdapter.notifyDataSetChanged();
				mAdapter.notifyDataSetChanged();
			}
			catch (Exception e) {
				
			}
		
		}
		setListShown(true);
	}
	
	@Override
	public void onStart() {
		super.onStart();
		final IntentFilter filter = new IntentFilter(BROADCAST_SEARCH_CHANGED);
		//filter.addAction(BROADCAST_ACCOUNT_LIST_DATABASE_UPDATED);
		registerReceiver(mStatusReceiver, filter);
	}

	@Override
	public void onStop() {
		if (mPopupMenu != null) {
			mPopupMenu.dismiss();
		}
		unregisterReceiver(mStatusReceiver);
		super.onStop();
	}
}