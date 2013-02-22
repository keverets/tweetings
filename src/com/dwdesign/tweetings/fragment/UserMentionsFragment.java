package com.dwdesign.tweetings.fragment;

import static com.dwdesign.tweetings.util.Utils.getAccountUsername;

import java.util.List;

import com.dwdesign.tweetings.loader.DummyParcelableStatusesLoader;
import com.dwdesign.tweetings.loader.TweetSearchLoader;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.util.SynchronizedStateSavedList;
import com.dwdesign.tweetings.util.Utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.Loader;

public class UserMentionsFragment extends SearchTweetsFragment {
	
	private boolean mIsStatusesSaved = false;
	private boolean isVisibleToUser = false;
	
	// Begin Sync Code
	private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
        	final String action = intent.getAction();
        	if (BROADCAST_VOLUME_UP.equals(action)) {
        		//if (isVisible()) {
					int currentPosition = getListView().getFirstVisiblePosition();  
				    if (currentPosition == 0)   
				        return;  
				    getListView().setSelection(currentPosition - 1);  
				    getListView().clearFocus(); 
        		//}
			}
			else if (BROADCAST_VOLUME_DOWN.equals(action)) {
				//if (isVisible()) {
					int currentPosition = getListView().getFirstVisiblePosition();  
				    if (currentPosition == getListView().getCount() - 1)   
				        return;  
				    getListView().setSelection(currentPosition + 1);  
				    getListView().clearFocus(); 
				//}
			}
        }
    };
	
    @Override
	public Loader<SynchronizedStateSavedList<ParcelableStatus, Long>> newLoaderInstance(final Bundle args) {
		final long account_id = args != null ? args.getLong(INTENT_KEY_ACCOUNT_ID, -1) : -1;
		if (args == null) return new DummyParcelableStatusesLoader(getActivity(), account_id, getData());
		final long max_id = args.getLong(INTENT_KEY_MAX_ID, -1);
		final long since_id = args.getLong(INTENT_KEY_SINCE_ID, -1);
		final String screen_name = args.getString(INTENT_KEY_SCREEN_NAME);
		final boolean is_home_tab = args.getBoolean(INTENT_KEY_IS_HOME_TAB);
		getListAdapter().setMentionsHightlightDisabled(
				Utils.equals(getAccountUsername(getActivity(), account_id), screen_name));
		if (screen_name == null) return new DummyParcelableStatusesLoader(getActivity(), account_id, getData());
		return new TweetSearchLoader(getActivity(), account_id, screen_name.startsWith("@") ? screen_name : "@"
				+ screen_name, max_id, since_id, getData(), getClass().getSimpleName(), is_home_tab);
	}
	
	
    
    @Override
	public void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_VOLUME_UP);
		filter.addAction(BROADCAST_VOLUME_DOWN);
        registerReceiver(receiver, filter);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(receiver);
	}
	
	@Override
	public void onDestroy() {
		saveStatuses();
		super.onDestroy();
	}

	@Override
	public void onDestroyView() {
		saveStatuses();
		super.onDestroyView();
	}
	
	private void saveStatuses() {
		if (mIsStatusesSaved) return;
		final int first_visible_position = getListView().getFirstVisiblePosition();
		final long status_id = getListAdapter().findItemIdByPosition(first_visible_position);
		TweetSearchLoader.writeSerializableStatuses(this, getActivity(), getData(), status_id, getArguments());
		mIsStatusesSaved = true;
	}
}