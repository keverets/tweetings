/*
 *				Tweetings - Twitter client for Android
 * 
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

package com.dwdesign.tweetings.activity;

import static com.dwdesign.tweetings.util.Utils.getAccountId;
import static com.dwdesign.tweetings.util.Utils.getDefaultAccountId;
import static com.dwdesign.tweetings.util.Utils.isMyAccount;
import static com.dwdesign.tweetings.util.Utils.parseLong;

import com.dwdesign.actionbarcompat.ActionBar;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.adapter.TabsAdapter;
import com.dwdesign.tweetings.fragment.SearchTweetsFragment;
import com.dwdesign.tweetings.fragment.SearchUsersFragment;
import com.dwdesign.tweetings.provider.RecentSearchProvider;
import com.dwdesign.tweetings.view.ExtendedViewPager;
import com.dwdesign.tweetings.view.TabPageIndicator;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.SearchRecentSuggestions;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManagerTrojan;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

public class SearchActivity extends MultiSelectActivity {

	private ActionBar mActionBar;
	private TabsAdapter mAdapter;

	private Uri mData;
	private final Bundle mArguments = new Bundle();
	
	private TabPageIndicator mIndicator;
	private ExtendedViewPager mViewPager;
	private boolean mDisplayAppIcon;
	private ProgressBar mProgress;

	@Override
	public void onBackStackChanged() {
		super.onBackStackChanged();
		if (!isDualPaneMode()) return;
		final FragmentManager fm = getSupportFragmentManager();
		final Fragment left_pane_fragment = fm.findFragmentById(PANE_LEFT);
		final boolean left_pane_used = left_pane_fragment != null && left_pane_fragment.isAdded();
		setPagingEnabled(!left_pane_used);
		final int count = fm.getBackStackEntryCount();
		if (mActionBar != null && mDisplayAppIcon) {
			mActionBar.setDisplayHomeAsUpEnabled(count > 0);
		}
		if (count == 0) {
			bringLeftPaneToFront();
		}
	}
	
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		SharedPreferences mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		
		if (mPreferences.getBoolean(PREFERENCE_KEY_VOLUME_NAVIGATION, false)) {
			if (event.getAction() == KeyEvent.ACTION_DOWN) {
				switch (event.getKeyCode()) {
					case KeyEvent.KEYCODE_VOLUME_UP:
					{
						Intent broadcast = new Intent();
						broadcast.setAction(BROADCAST_VOLUME_UP);
						sendBroadcast(broadcast);
						//scrollToPrevious();
						return true;
					}
					case KeyEvent.KEYCODE_VOLUME_DOWN: {
						Intent broadcast = new Intent();
						broadcast.setAction(BROADCAST_VOLUME_DOWN);
						sendBroadcast(broadcast);
						//scrollToNext();
						return true;
					}
				}
			}
			if (event.getAction() == KeyEvent.ACTION_UP 
				&& (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP 
					|| event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN)) {
				return true;
			}
		}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();
		mViewPager = (ExtendedViewPager) findViewById(R.id.pager);
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Intent intent = getIntent();
		mArguments.clear();
		mData = intent.getData();
		final boolean is_search_user = mData != null ? QUERY_PARAM_VALUE_USERS.equals(mData
				.getQueryParameter(QUERY_PARAM_TYPE)) : false;
		final String query = Intent.ACTION_SEARCH.equals(intent.getAction()) ? intent
				.getStringExtra(SearchManager.QUERY) : mData != null ? mData.getQueryParameter(QUERY_PARAM_QUERY)
				: null;
		int search_id = -1;
		if (mData != null && mData.getQueryParameter(QUERY_PARAM_ID) != null) {
			search_id = Integer.parseInt(mData.getQueryParameter(QUERY_PARAM_ID));
		}
		if (query == null) {
			finish();
			return;
		}
		if (savedInstanceState == null) {
			final SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
					RecentSearchProvider.AUTHORITY, RecentSearchProvider.MODE);
			suggestions.saveRecentQuery(query, null);
		}
		mArguments.putString(INTENT_KEY_QUERY, query);
		mArguments.putInt(INTENT_KEY_ID, search_id);
		final String param_account_id = mData != null ? mData.getQueryParameter(QUERY_PARAM_ACCOUNT_ID) : null;
		if (param_account_id != null) {
			mArguments.putLong(INTENT_KEY_ACCOUNT_ID, parseLong(param_account_id));
		} else {
			final String param_account_name = mData != null ? mData.getQueryParameter(QUERY_PARAM_ACCOUNT_NAME) : null;
			if (param_account_name != null) {
				mArguments.putLong(INTENT_KEY_ACCOUNT_ID, getAccountId(this, param_account_name));
			} else {
				final long account_id = getDefaultAccountId(this);
				if (isMyAccount(this, account_id)) {
					mArguments.putLong(INTENT_KEY_ACCOUNT_ID, account_id);
				} else {
					finish();
					return;
				}
			}
		}
		mActionBar = getSupportActionBar();
		mActionBar.setCustomView(R.layout.base_tabs);
		mActionBar.setDisplayShowTitleEnabled(false);
		mActionBar.setDisplayHomeAsUpEnabled(true);
		mActionBar.setDisplayShowCustomEnabled(true);
		final View view = mActionBar.getCustomView();
		mProgress = (ProgressBar) view.findViewById(android.R.id.progress);
		mIndicator = (TabPageIndicator) view.findViewById(android.R.id.tabs);
		mAdapter = new TabsAdapter(this, getSupportFragmentManager(), mIndicator);
		mAdapter.setDisplayLabel(true);
		mAdapter.addTab(SearchTweetsFragment.class, mArguments, getString(R.string.search_tweets),
				R.drawable.ic_tab_twitter, 0);
		mAdapter.addTab(SearchUsersFragment.class, mArguments, getString(R.string.search_users),
				R.drawable.ic_tab_person, 1);
		mViewPager.setAdapter(mAdapter);
		mViewPager.setOffscreenPageLimit(1);
		mIndicator.setViewPager(mViewPager);
		mViewPager.setCurrentItem(is_search_user ? 1 : 0);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case MENU_HOME: {
				final FragmentManager fm = getSupportFragmentManager();
			 	if (isDualPaneMode()) {
			 		final int count = fm.getBackStackEntryCount();
			 		if (count == 0) {
			 			onBackPressed();
			 		} else if (!FragmentManagerTrojan.isStateSaved(fm)) {
			 			for (int i = 0; i < count; i++) {
			 				fm.popBackStackImmediate();
			 			}
			 			setSupportProgressBarIndeterminateVisibility(false);
			 		}
			 	} else {
			 		onBackPressed();
			 	}
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void setSupportProgressBarIndeterminateVisibility(final boolean visible) {
		mProgress.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
	}
	
	protected void setPagingEnabled(final boolean enabled) {
		if (mIndicator != null) {
			mIndicator.setPagingEnabled(enabled);
			mIndicator.setEnabled(enabled);
		}
	}
	
	@Override
	int getDualPaneLayoutRes() {
		return R.layout.search_dual_pane;
	}
	
	@Override
	int getNormalLayoutRes() {
		return R.layout.search;
	}
}
