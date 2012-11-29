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

package com.dwdesign.tweetings.fragment;

import com.dwdesign.actionbarcompat.ActionBarFragmentActivity;
import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.util.ServiceInterface;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.support.v4.app.ListFragment;
import android.view.Menu;
import android.view.MenuInflater;

public class BaseListFragment extends ListFragment implements Constants {

	private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if ((BaseListFragment.this.getClass().getName() + SHUFFIX_SCROLL_TO_TOP).equals(action))
				if (getListView() != null) {
					getListView().setSelection(0);
				}
		}
	};

	public ActionBarFragmentActivity getActionBarActivity() {
		final Activity activity = getActivity();
		if (activity instanceof ActionBarFragmentActivity) return (ActionBarFragmentActivity) activity;
		return null;
	}

	public TweetingsApplication getApplication() {
		final Activity activity = getActivity();
		if (activity != null) return (TweetingsApplication) activity.getApplication();
		return null;
	}

	public ContentResolver getContentResolver() {
		final Activity activity = getActivity();
		if (activity != null) return activity.getContentResolver();
		return null;
	}
	
	public ServiceInterface getServiceInterface() {
		return getApplication() != null ? getApplication().getServiceInterface() : null;
	}

	public SharedPreferences getSharedPreferences(final String name, final int mode) {
		final Activity activity = getActivity();
		if (activity != null) return activity.getSharedPreferences(name, mode);
		return null;
	}

	public Object getSystemService(final String name) {
		final Activity activity = getActivity();
		if (activity != null) return activity.getSystemService(name);
		return null;
	}
	
	public void onPostStart() {
	}

	@Override
	public void onStart() {
		super.onStart();
		final IntentFilter filter = new IntentFilter(getClass().getName() + SHUFFIX_SCROLL_TO_TOP);
		registerReceiver(mStateReceiver, filter);
		onPostStart();
	}

	@Override
	public void onStop() {
		unregisterReceiver(mStateReceiver);
		super.onStop();
	}

	public void registerReceiver(final BroadcastReceiver receiver, final IntentFilter filter) {
		final Activity activity = getActivity();
		if (activity == null) return;
		activity.registerReceiver(receiver, filter);
	}

	public void setProgressBarIndeterminateVisibility(final boolean visible) {
		final Activity activity = getActivity();
		if (activity instanceof ActionBarFragmentActivity) {
			((ActionBarFragmentActivity) activity).setSupportProgressBarIndeterminateVisibility(visible);
		}
	}

	public void unregisterReceiver(final BroadcastReceiver receiver) {
		final Activity activity = getActivity();
		if (activity == null) return;
		activity.unregisterReceiver(receiver);
	}

	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		// TODO Auto-generated method stub
	}
}
