package com.dwdesign.tweetings.loader;

import java.util.List;

import com.dwdesign.tweetings.model.ParcelableUserList;

import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.UserList;
import android.content.Context;

public class UserListsLoader extends BaseUserListsLoader {

	private final long mUserId;
	private final String mScreenName;
	private int mPosition;

	public UserListsLoader(final Context context, final long account_id, final long user_id,
			final String screen_name, final long cursor, final List<ParcelableUserList> data) {
		super(context, account_id, cursor, data);
		mUserId = user_id;
		mScreenName = screen_name;
		mPosition = -1;
	}
	
	public UserListsLoader(final Context context, final long account_id, final long user_id,
			final String screen_name, final long cursor, final List<ParcelableUserList> data, final int position) {
		super(context, account_id, cursor, data, position);
		mUserId = user_id;
		mScreenName = screen_name;
		mPosition = position;
	}

	@Override
	public ResponseList<UserList> getUserLists() throws TwitterException {
		final Twitter twitter = getTwitter();
		if (twitter == null) return null;
		if (mUserId > 0)
			return twitter.getUserLists(mUserId, -1);
		else if (mScreenName != null) return twitter.getUserLists(mScreenName, -1);
		return null;
	}

}
