package com.dwdesign.tweetings.loader;

import static com.dwdesign.tweetings.util.Utils.getTwitterInstance;
import com.dwdesign.tweetings.Constants;
import twitter4j.ResponseList;
import twitter4j.SavedSearch;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.content.AsyncTaskLoader;

public class SavedSearchesLoader extends AsyncTaskLoader<ResponseList<SavedSearch>> implements Constants {

	private final Twitter twitter;
	private Context mContext;
	private int mPosition = -1;

	public SavedSearchesLoader(final Context context, final long account_id) {
		super(context);
		twitter = getTwitterInstance(context, account_id, false);
		mContext = context;
		mPosition = -1;
	}
	
	public SavedSearchesLoader(final Context context, final long account_id, final int position) {
		super(context);
		twitter = getTwitterInstance(context, account_id, false);
		mContext = context;
		mPosition = position;
	}

	public int getPosition() {
		return mPosition;
	}
	
	@Override
	public ResponseList<SavedSearch> loadInBackground() {
		if (twitter == null) return null;
		try {
			SharedPreferences mPreferences = mContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
			if (mPreferences.getBoolean(PREFERENCE_KEY_API_V1, true) == true) {
				return twitter.getSavedSearchesv1();
			}
			return twitter.getSavedSearches();
		} catch (final TwitterException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void onStartLoading() {
		forceLoad();
	}

}
