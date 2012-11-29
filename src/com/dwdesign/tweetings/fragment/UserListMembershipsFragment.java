package com.dwdesign.tweetings.fragment;

import java.util.List;

import com.dwdesign.tweetings.loader.UserListMembershipsLoader;
import com.dwdesign.tweetings.model.ParcelableUserList;

import android.support.v4.content.Loader;

public class UserListMembershipsFragment extends BaseUserListsListFragment {

	@Override
	public Loader<List<ParcelableUserList>> newLoaderInstance(final long account_id, final long user_id, final String screen_name) {
		return new UserListMembershipsLoader(getActivity(), account_id, user_id, screen_name, getCursor(), getData());
	}

}
