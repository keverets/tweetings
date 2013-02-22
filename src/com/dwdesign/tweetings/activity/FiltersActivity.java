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

package com.dwdesign.tweetings.activity;

import com.dwdesign.actionbarcompat.ActionBar;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.adapter.TabsAdapter;
import com.dwdesign.tweetings.fragment.BaseFiltersFragment.FilteredKeywordsFragment;
import com.dwdesign.tweetings.fragment.BaseFiltersFragment.FilteredSourcesFragment;
import com.dwdesign.tweetings.fragment.BaseFiltersFragment.FilteredUsersFragment;
import com.dwdesign.tweetings.view.ExtendedViewPager;
import com.dwdesign.tweetings.view.TabPageIndicator;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class FiltersActivity extends BaseActivity implements OnCheckedChangeListener {

	private ActionBar mActionBar;

	private ExtendedViewPager mViewPager;
	private TabPageIndicator mIndicator;
	private CompoundButton mToggle;
	private SharedPreferences mPrefs;
	

	private TabsAdapter mAdapter;
	
	@Override
	public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
		mPrefs.edit().putBoolean(PREFERENCE_KEY_ENABLE_FILTER, isChecked).commit();

	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();
		mViewPager = (ExtendedViewPager) findViewById(R.id.pager);
		mIndicator = (TabPageIndicator) findViewById(android.R.id.tabs);
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mPrefs = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		final boolean filter_enabled = mPrefs.getBoolean(PREFERENCE_KEY_ENABLE_FILTER, false);
		setContentView(R.layout.filters);
		mActionBar = getSupportActionBar();
		mActionBar.setDisplayShowTitleEnabled(false);
		mActionBar.setDisplayHomeAsUpEnabled(true);
		mActionBar.setDisplayShowCustomEnabled(true);
		mActionBar.setCustomView(R.layout.actionbar_filters);
		final View view = mActionBar.getCustomView();
		mToggle = (CompoundButton) view.findViewById(R.id.toggle);
		mToggle.setOnCheckedChangeListener(this);
		mToggle.setChecked(filter_enabled);
		mAdapter = new TabsAdapter(this, getSupportFragmentManager(), mIndicator);
		mAdapter.addTab(FilteredUsersFragment.class, null, getString(R.string.users), R.drawable.ic_tab_accounts, 0);
		mAdapter.addTab(FilteredKeywordsFragment.class, null, getString(R.string.keywords), R.drawable.ic_tab_ribbon, 1);
		mAdapter.addTab(FilteredSourcesFragment.class, null, getString(R.string.sources), R.drawable.ic_tab_twitter, 2);
		mViewPager.setAdapter(mAdapter);
		// mViewPager.setPagingEnabled(false);
		mIndicator.setViewPager(mViewPager);
		mAdapter.setDisplayLabel(true);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.menu_filter, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case MENU_HOME:
				finish();
				break;
			case MENU_ADD:
				return false;
		}
		return super.onOptionsItemSelected(item);
	}
}