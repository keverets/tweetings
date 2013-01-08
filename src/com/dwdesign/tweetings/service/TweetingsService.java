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

package com.dwdesign.tweetings.service;

import static com.dwdesign.tweetings.util.Utils.buildQueryUri;
import static com.dwdesign.tweetings.util.Utils.getAccountUsername;
import static com.dwdesign.tweetings.util.Utils.getActivatedAccountIds;
import static com.dwdesign.tweetings.util.Utils.getAllStatusesIds;
import static com.dwdesign.tweetings.util.Utils.getImagePathFromUri;
import static com.dwdesign.tweetings.util.Utils.getImageUploadStatus;
import static com.dwdesign.tweetings.util.Utils.getTwitterInstance;
import static com.dwdesign.tweetings.util.Utils.hasActiveConnection;
import static com.dwdesign.tweetings.util.Utils.isNullOrEmpty;
import static com.dwdesign.tweetings.util.Utils.getNewestMessageIdsFromDatabase;
import static com.dwdesign.tweetings.util.Utils.getNewestStatusIdsFromDatabase;
import static com.dwdesign.tweetings.util.Utils.makeCachedUserContentValues;
import static com.dwdesign.tweetings.util.Utils.makeDirectMessageContentValues;
import static com.dwdesign.tweetings.util.Utils.makeStatusContentValues;
import static com.dwdesign.tweetings.util.Utils.makeTrendsContentValues;
import static com.dwdesign.tweetings.util.Utils.notifyForUpdatedUri;
import static com.dwdesign.tweetings.util.Utils.parseInt;
import static com.dwdesign.tweetings.util.Utils.parseString;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.HttpClientFactory;
import com.dwdesign.tweetings.ITweetingsService;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.activity.HomeActivity;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.provider.TweetStore;
import com.dwdesign.tweetings.provider.TweetStore.CachedTrends;
import com.dwdesign.tweetings.provider.TweetStore.CachedUsers;
import com.dwdesign.tweetings.provider.TweetStore.DirectMessages;
import com.dwdesign.tweetings.provider.TweetStore.Drafts;
import com.dwdesign.tweetings.provider.TweetStore.Mentions;
import com.dwdesign.tweetings.provider.TweetStore.Statuses;
import com.dwdesign.tweetings.util.AsyncTaskManager;
import com.dwdesign.tweetings.util.ImageUploaderInterface;
import com.dwdesign.tweetings.util.ListUtils;
import com.dwdesign.tweetings.util.ManagedAsyncTask;
import com.dwdesign.tweetings.util.TweetShortenerInterface;
import com.dwdesign.tweetings.util.Utils;

import twitter4j.DirectMessage;
import twitter4j.GeoLocation;
import twitter4j.Paging;
import twitter4j.RateLimitStatus;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Trends;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserList;
import twitter4j.auth.AccessToken;
import twitter4j.auth.Authorization;
import twitter4j.auth.OAuthAuthorization;
import twitter4j.internal.http.HttpClientWrapper;
import twitter4j.internal.http.HttpParameter;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.twitter.Validator;

public class TweetingsService extends Service implements Constants {

	private final ServiceStub mBinder = new ServiceStub(this);

	private AsyncTaskManager mAsyncTaskManager;
	private SharedPreferences mPreferences;
	private NotificationManager mNotificationManager;
	private AlarmManager mAlarmManager;
	private ContentResolver mResolver;

	private int mGetHomeTimelineTaskId, mGetMentionsTaskId;
	private int mStoreStatusesTaskId, mStoreMentionsTaskId;
	private int mGetReceivedDirectMessagesTaskId, mGetSentDirectMessagesTaskId;
	private int mStoreReceivedDirectMessagesTaskId, mStoreSentDirectMessagesTaskId;
	private int mGetLocalTrendsTaskId, mGetWeeklyTrendsTaskId, mGetDailyTrendsTaskId;
	private int mStoreLocalTrendsTaskId, mStoreWeeklyTrendsTaskId, mStoreDailyTrendsTaskId;
	
	private boolean mBatteryLow = false;
	private boolean mShouldShutdown = false;

	private int mNewMessagesCount, mNewMentionsCount, mNewStatusesCount;

	private PendingIntent mPendingRefreshHomeTimelineIntent, mPendingRefreshMentionsIntent,
		mPendingRefreshDirectMessagesIntent;

	private BroadcastReceiver mStateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (BROADCAST_REFRESHSTATE_CHANGED.equals(action)) {
				if (!mAsyncTaskManager.hasActivatedTask() && mShouldShutdown) {
					stopSelf();
				}
			} else if (Intent.ACTION_BATTERY_LOW.equals(action)) {
				mBatteryLow = true;
			} else if (Intent.ACTION_BATTERY_OKAY.equals(action)) {
				mBatteryLow = false;
			} else if (BROADCAST_NOTIFICATION_CLEARED.equals(action)) {
				final Bundle extras = intent.getExtras();
				if (extras != null && extras.containsKey(INTENT_KEY_NOTIFICATION_ID)) {
					clearNotification(extras.getInt(INTENT_KEY_NOTIFICATION_ID));
				}
			} else if (hasActiveConnection(context) && !(mBatteryLow && mPreferences.getBoolean(PREFERENCE_KEY_STOP_AUTO_REFRESH_WHEN_BATTERY_LOW, true))) {
				 if (BROADCAST_REFRESH_HOME_TIMELINE.equals(action)) {
					final long[] activated_ids = getActivatedAccountIds(context);
					if (mPreferences.getBoolean(PREFERENCE_KEY_REFRESH_ENABLE_HOME_TIMELINE, false)) {
						if (!isHomeTimelineRefreshing()) {
							getHomeTimeline(activated_ids, null, true);
						}
					}
				} else if (BROADCAST_REFRESH_MENTIONS.equals(action)) {
					final long[] activated_ids = getActivatedAccountIds(context);
					if (mPreferences.getBoolean(PREFERENCE_KEY_REFRESH_ENABLE_MENTIONS, false)) {
						if (!isMentionsRefreshing()) {
							getMentions(activated_ids, null, true);
						}
					}
				} else if (BROADCAST_REFRESH_DIRECT_MESSAGES.equals(action)) {
					final long[] activated_ids = getActivatedAccountIds(context);
					if (mPreferences.getBoolean(PREFERENCE_KEY_REFRESH_ENABLE_DIRECT_MESSAGES, false)) {
						if (!isReceivedDirectMessagesRefreshing()) {
							getReceivedDirectMessages(activated_ids, null, true);
						}
					}
				} 
			}
		}

	};
	
	public void cancelShutdown() {
		mShouldShutdown = false;
	}

	public int addUserListMember(final long account_id, final int list_id, final long user_id, final String screen_name) {
		final AddUserListMemberTask task = new AddUserListMemberTask(account_id, list_id, user_id, screen_name);
		return mAsyncTaskManager.add(task, true);
	}

	public void clearNotification(final int id) {
		switch (id) {
			case NOTIFICATION_ID_HOME_TIMELINE: {
				mNewStatusesCount = 0;
				break;
			}
			case NOTIFICATION_ID_MENTIONS: {
				mNewMentionsCount = 0;
				break;
			}
			case NOTIFICATION_ID_DIRECT_MESSAGES: {
				mNewMessagesCount = 0;
				break;
			}
		}
		mNotificationManager.cancel(id);
	}

	public int createBlock(final long account_id, final long user_id) {
		final CreateBlockTask task = new CreateBlockTask(account_id, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int createFavorite(final long account_id, final long status_id) {
		final CreateFavoriteTask task = new CreateFavoriteTask(account_id, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int createFriendship(final long account_id, final long user_id) {
		final CreateFriendshipTask task = new CreateFriendshipTask(account_id, user_id);
		return mAsyncTaskManager.add(task, true);
	}
	
	public int createMultiBlock(final long account_id, final long[] user_ids) {
		final CreateMultiBlockTask task = new CreateMultiBlockTask(account_id, user_ids);
		return mAsyncTaskManager.add(task, true);
	}

	public int createUserList(final long account_id, final String list_name, final boolean is_public, final String description) {
		final CreateUserListTask task = new CreateUserListTask(account_id, list_name, is_public, description);
		return mAsyncTaskManager.add(task, true);
	}

	public int createUserListSubscription(final long account_id, final int list_id) {
		final CreateUserListSubscriptionTask task = new CreateUserListSubscriptionTask(account_id, list_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int deleteUserListMember(final long account_id, final int list_id, final long user_id) {
		final DeleteUserListMemberTask task = new DeleteUserListMemberTask(account_id, list_id, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyBlock(final long account_id, final long user_id) {
		final DestroyBlockTask task = new DestroyBlockTask(account_id, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyDirectMessage(final long account_id, final long message_id) {
		final DestroyDirectMessageTask task = new DestroyDirectMessageTask(account_id, message_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyFavorite(final long account_id, final long status_id) {
		final DestroyFavoriteTask task = new DestroyFavoriteTask(account_id, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyFriendship(long account_id, final long user_id) {
		final DestroyFriendshipTask task = new DestroyFriendshipTask(account_id, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyStatus(final long account_id, final long status_id) {
		final DestroyStatusTask task = new DestroyStatusTask(account_id, status_id);
		return mAsyncTaskManager.add(task, true);
	}
	
	public int destroySavedSearch(final long account_id, final int search_id) {
		final DestroySavedSearchTask task = new DestroySavedSearchTask(account_id, search_id);
		return mAsyncTaskManager.add(task, true);
	}
	
	public int createSavedSearch(final long account_id, final String query) {
		final CreateSavedSearchTask task = new CreateSavedSearchTask(account_id, query);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyUserList(final long account_id, final int list_id) {
		final DestroyUserListTask task = new DestroyUserListTask(account_id, list_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyUserListSubscription(final long account_id, final int list_id) {
		final DestroyUserListSubscriptionTask task = new DestroyUserListSubscriptionTask(account_id, list_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int getDailyTrends(final long account_id) {
		mAsyncTaskManager.cancel(mGetDailyTrendsTaskId);
		final GetDailyTrendsTask task = new GetDailyTrendsTask(account_id);
		return mGetDailyTrendsTaskId = mAsyncTaskManager.add(task, true);
	}
	
	public int insertStreamToHomeTimeline(final long account_id, final Intent intent) {
		final String action = intent.getAction();
		if (BROADCAST_STREAM_STATUS_RECEIVED.equals(action)) {
			twitter4j.Status status = (twitter4j.Status)intent.getExtras().get("status");
			
        	List<twitter4j.Status> statuses = new ArrayList<twitter4j.Status>();
        	statuses.add(status);
        	ListResponse<twitter4j.Status> result = new ListResponse<twitter4j.Status>(account_id, 0, 0, statuses, null);
        	List<ListResponse<twitter4j.Status>> result2 = new ArrayList<ListResponse<twitter4j.Status>>();
        	result2.add(result);
        	
        	try {
        		return mAsyncTaskManager.add(new StoreHomeTimelineTask(result2, false, false), true);
        	} catch (Exception e) {
        		return -1;
        	}
		}
		return -1;
	}
	
	public int insertStreamToMentions(final long account_id, final Intent intent) {
		final String action = intent.getAction();
		if (BROADCAST_STREAM_STATUS_RECEIVED.equals(action)) {
			twitter4j.Status status = (twitter4j.Status)intent.getExtras().get("status");
			
        	List<twitter4j.Status> statuses = new ArrayList<twitter4j.Status>();
        	statuses.add(status);
        	ListResponse<twitter4j.Status> result = new ListResponse<twitter4j.Status>(account_id, 0, 0, statuses, null);
        	List<ListResponse<twitter4j.Status>> result2 = new ArrayList<ListResponse<twitter4j.Status>>();
        	result2.add(result);
        	
        	try {
        		return mAsyncTaskManager.add(new StoreMentionsTask(result2, false, false), true);
        	} catch (Exception e) {
        		return -1;
        	}
		}
		return -1;
	}
	
	public int insertStreamToInbox(final long account_id, final Intent intent) {
		final String action = intent.getAction();
		if (BROADCAST_STREAM_STATUS_RECEIVED.equals(action)) {
			twitter4j.DirectMessage dm = (twitter4j.DirectMessage)intent.getExtras().get("status");
			
        	List<twitter4j.DirectMessage> statuses = new ArrayList<twitter4j.DirectMessage>();
        	statuses.add(dm);
        	ListResponse<twitter4j.DirectMessage> result = new ListResponse<twitter4j.DirectMessage>(account_id, 0, 0, statuses, null);
        	List<ListResponse<twitter4j.DirectMessage>> result2 = new ArrayList<ListResponse<twitter4j.DirectMessage>>();
        	result2.add(result);
        	
        	try {
        		return mAsyncTaskManager.add(new StoreReceivedDirectMessagesTask(result2, false), true);
        	} catch (Exception e) {
        		return -1;
        	}
		}
		return -1;
	}

	public int getHomeTimeline(final long[] account_ids, final long[] max_ids) {
		final boolean notification = mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_HOME_TIMELINE, false);
		return getHomeTimeline(account_ids, max_ids, notification);
	}
	
	public int getHomeTimelineWithSinceId(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
		final boolean notification = mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_HOME_TIMELINE, false);
		return getHomeTimelineWithSinceId(account_ids, max_ids, since_ids, notification);
	}

	public int getLocalTrends(final long account_id, final int woeid) {
		mAsyncTaskManager.cancel(mGetLocalTrendsTaskId);
		final GetLocalTrendsTask task = new GetLocalTrendsTask(account_id, woeid);
		return mGetLocalTrendsTaskId = mAsyncTaskManager.add(task, true);
	}
	
	public String generateOAuthEchoHeader(final long account_id) {
		final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
		if (twitter != null) {
			Authorization authorization = twitter.getAuthorization();
			if (!(authorization instanceof OAuthAuthorization)) {
				throw new IllegalArgumentException("OAuth authorization is required.");
			}
			OAuthAuthorization oauth = (OAuthAuthorization) authorization;
			
			List<HttpParameter> oauthSignatureParams = oauth.generateOAuthSignatureHttpParams("GET", TWITTER_VERIFY_CREDENTIALS_JSON);
	        return "OAuth realm=\"http://api.twitter.com/\"," + OAuthAuthorization.encodeParameters(oauthSignatureParams, ",", true);
	    
		}
		return null;
	}

	public int getMentions(final long[] account_ids, final long[] max_ids) {
		final boolean notification = mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_MENTIONS, false)
				&& max_ids == null;
		return getMentions(account_ids, max_ids, notification);
	}
	
	public int getMentionsWithSinceId(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
		final boolean notification = mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_MENTIONS, false)
				&& max_ids == null;
		return getMentionsWithSinceId(account_ids, max_ids, since_ids, notification);
	}

	public int getReceivedDirectMessages(final long[] account_ids, final long[] max_ids) {
		final boolean notification = mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_DIRECT_MESSAGES, false)
				&& max_ids == null;
		return getReceivedDirectMessages(account_ids, max_ids, notification);
	}
	
	public int getReceivedDirectMessagesWithSinceId(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
		final boolean notification = mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_DIRECT_MESSAGES, false)
				&& max_ids == null;
		return getReceivedDirectMessagesWithSinceId(account_ids, max_ids, since_ids, notification);
	}

	public int getSentDirectMessages(final long[] account_ids, final long[] max_ids) {
		mAsyncTaskManager.cancel(mGetSentDirectMessagesTaskId);
		final GetSentDirectMessagesTask task = new GetSentDirectMessagesTask(account_ids, max_ids, null);
		return mGetSentDirectMessagesTaskId = mAsyncTaskManager.add(task, true);
	}
	
	public int getSentDirectMessagesWithSinceId(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
		mAsyncTaskManager.cancel(mGetSentDirectMessagesTaskId);
		final GetSentDirectMessagesTask task = new GetSentDirectMessagesTask(account_ids, max_ids, since_ids);
		return mGetSentDirectMessagesTaskId = mAsyncTaskManager.add(task, true);
	}

	public int getWeeklyTrends(final long account_id) {
		mAsyncTaskManager.cancel(mGetWeeklyTrendsTaskId);
		final GetWeeklyTrendsTask task = new GetWeeklyTrendsTask(account_id);
		return mGetWeeklyTrendsTaskId = mAsyncTaskManager.add(task, true);
	}

	public boolean hasActivatedTask() {
		return mAsyncTaskManager.hasActivatedTask();
	}

	public boolean isDailyTrendsRefreshing() {
		return mAsyncTaskManager.isExcuting(mGetDailyTrendsTaskId)
				|| mAsyncTaskManager.isExcuting(mStoreDailyTrendsTaskId);
	}

	public boolean isHomeTimelineRefreshing() {
		return mAsyncTaskManager.isExcuting(mGetHomeTimelineTaskId)
				|| mAsyncTaskManager.isExcuting(mStoreStatusesTaskId);
	}

	public boolean isLocalTrendsRefreshing() {
		return mAsyncTaskManager.isExcuting(mGetLocalTrendsTaskId)
				|| mAsyncTaskManager.isExcuting(mStoreLocalTrendsTaskId);
	}

	public boolean isMentionsRefreshing() {
		return mAsyncTaskManager.isExcuting(mGetMentionsTaskId) || mAsyncTaskManager.isExcuting(mStoreMentionsTaskId);
	}

	public boolean isReceivedDirectMessagesRefreshing() {
		return mAsyncTaskManager.isExcuting(mGetReceivedDirectMessagesTaskId)
				|| mAsyncTaskManager.isExcuting(mStoreReceivedDirectMessagesTaskId);
	}

	public boolean isSentDirectMessagesRefreshing() {
		return mAsyncTaskManager.isExcuting(mGetSentDirectMessagesTaskId)
				|| mAsyncTaskManager.isExcuting(mStoreSentDirectMessagesTaskId);
	}

	public boolean isWeeklyTrendsRefreshing() {
		return mAsyncTaskManager.isExcuting(mGetWeeklyTrendsTaskId)
				|| mAsyncTaskManager.isExcuting(mStoreWeeklyTrendsTaskId);
	}

	@Override
	public IBinder onBind(final Intent intent) {
		return mBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		mAsyncTaskManager = ((TweetingsApplication) getApplication()).getAsyncTaskManager();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE);
		mResolver = getContentResolver();
		mPendingRefreshHomeTimelineIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				BROADCAST_REFRESH_HOME_TIMELINE), 0);
		mPendingRefreshMentionsIntent = PendingIntent.getBroadcast(this, 0, new Intent(BROADCAST_REFRESH_MENTIONS), 0);
		mPendingRefreshDirectMessagesIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				BROADCAST_REFRESH_DIRECT_MESSAGES), 0);
		final IntentFilter filter = new IntentFilter(BROADCAST_REFRESHSTATE_CHANGED);
		filter.addAction(BROADCAST_NOTIFICATION_CLEARED);
		filter.addAction(BROADCAST_REFRESH_HOME_TIMELINE);
		filter.addAction(BROADCAST_REFRESH_MENTIONS);
		filter.addAction(BROADCAST_REFRESH_DIRECT_MESSAGES);
		filter.addAction(Intent.ACTION_BATTERY_LOW);
		filter.addAction(Intent.ACTION_BATTERY_OKAY);
		registerReceiver(mStateReceiver, filter);
		startAutoRefresh();
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(mStateReceiver);
		mNotificationManager.cancelAll();
		if (mPreferences.getBoolean(PREFERENCE_KEY_AUTO_REFRESH, false)) {
			// Auto refresh enabled, so I will try to start service after it was
			// stopped.
			startService(new Intent(INTENT_ACTION_SERVICE));
		}
		super.onDestroy();
	}
	
	public int refreshAll() {
		final long[] account_ids = getActivatedAccountIds(this);
		final long[] since_ids = getNewestStatusIdsFromDatabase(this, Mentions.CONTENT_URI);
		getMentionsWithSinceId(account_ids, null, since_ids);
		final long[] since_idsDm = getNewestMessageIdsFromDatabase(this, DirectMessages.Inbox.CONTENT_URI);
		getReceivedDirectMessagesWithSinceId(account_ids, null, since_idsDm);
		getSentDirectMessagesWithSinceId(account_ids, null, since_idsDm);
		final long[] since_idsHome = getNewestStatusIdsFromDatabase(this, Statuses.CONTENT_URI);
		return getHomeTimelineWithSinceId(account_ids, null, since_idsHome);
		
		/*final long[] account_ids = getActivatedAccountIds(this);
		getMentions(account_ids, null);
		getReceivedDirectMessages(account_ids, null);
		getSentDirectMessages(account_ids, null);
		return getHomeTimeline(account_ids, null);*/
	}
	
	public int reportMultiSpam(final long account_id, final long[] user_ids) {
		final ReportMultiSpamTask task = new ReportMultiSpamTask(account_id, user_ids);
		return mAsyncTaskManager.add(task, true);
	}

	public int reportSpam(final long account_id, final long user_id) {
		final ReportSpamTask task = new ReportSpamTask(account_id, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int retweetStatus(final long account_id, final long status_id) {
		final RetweetStatusTask task = new RetweetStatusTask(account_id, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int sendDirectMessage(final long account_id, final String screen_name, final long user_id, final String message) {
		final SendDirectMessageTask task = new SendDirectMessageTask(account_id, screen_name, user_id, message);
		return mAsyncTaskManager.add(task, true);
	}

	public void shutdownService() {
		// Auto refresh is enabled, so this service cannot be shut down.
		if (mPreferences.getBoolean(PREFERENCE_KEY_AUTO_REFRESH, false)) return;
		if (!mAsyncTaskManager.hasActivatedTask()) {
			stopSelf();
		} else {
			mShouldShutdown = true;
		}
	}

	public boolean startAutoRefresh() {
		stopAutoRefresh();
		if (mPreferences.getBoolean(PREFERENCE_KEY_AUTO_REFRESH, false)) {
			final long update_interval = parseInt(mPreferences.getString(PREFERENCE_KEY_REFRESH_INTERVAL, "30")) * 60 * 1000;
			if (update_interval <= 0) return false;
			mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + update_interval,
					update_interval, mPendingRefreshHomeTimelineIntent);
			mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + update_interval,
					update_interval, mPendingRefreshMentionsIntent);
			mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + update_interval,
					update_interval, mPendingRefreshDirectMessagesIntent);
			return true;
		}
		return false;
	}

	public void stopAutoRefresh() {
		mAlarmManager.cancel(mPendingRefreshHomeTimelineIntent);
		mAlarmManager.cancel(mPendingRefreshMentionsIntent);
		mAlarmManager.cancel(mPendingRefreshDirectMessagesIntent);
	}

	public boolean test() {
		try {
			return startService(new Intent(INTENT_ACTION_SERVICE)) != null;
		} catch (final Exception e) {
			return false;
		}
	}

	public int updateProfile(final long account_id, final String name, final String url, final String location, final String description) {
		final UpdateProfileTask task = new UpdateProfileTask(account_id, name, url, location, description);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateProfileImage(final long account_id, final Uri image_uri, final boolean delete_image) {
		final UpdateProfileImageTask task = new UpdateProfileImageTask(account_id, image_uri, delete_image);
		return mAsyncTaskManager.add(task, true);
	}
	
	public int updateBannerImage(final long account_id, final Uri image_uri, final boolean delete_image) {
		final UpdateBannerImageTask task = new UpdateBannerImageTask(account_id, image_uri, delete_image);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateStatus(final long[] account_ids, final String content, final Location location, final Uri image_uri, final long in_reply_to,
			final boolean is_possibly_sensitive, final boolean delete_image) {
		final UpdateStatusTask task = new UpdateStatusTask(account_ids, content, location, image_uri, in_reply_to,
				is_possibly_sensitive, delete_image);
		return mAsyncTaskManager.add(task, true);
	}
	
	public int scheduleStatus(final String schedule_date, final long[] account_ids, final String content, final Location location, final Uri image_uri, final long in_reply_to,
			final boolean delete_image) {
		final ScheduleStatusTask task = new ScheduleStatusTask(schedule_date, account_ids, content, location, image_uri, in_reply_to,
				delete_image);
		return mAsyncTaskManager.add(task, true);
	}
	
	public int bufferStatus(final long[] account_ids, final String content) {
		final BufferStatusTask task = new BufferStatusTask(account_ids, content);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateUserListDetails(final long account_id, final int list_id, final boolean is_public, final String name, final String description) {
		final UpdateUserListProfileTask task = new UpdateUserListProfileTask(account_id, list_id, is_public, name,
				description);
		return mAsyncTaskManager.add(task, true);
	}

	private Notification buildNotification(final String title, final String message, final int icon, final Intent content_intent,
			final Intent delete_intent) {
		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		builder.setTicker(message);
		builder.setContentTitle(title);
		builder.setContentText(message);
		builder.setAutoCancel(true);
		builder.setWhen(System.currentTimeMillis());
		builder.setSmallIcon(icon);
		if (delete_intent != null) {
			builder.setDeleteIntent(PendingIntent.getBroadcast(this, 0, delete_intent, PendingIntent.FLAG_UPDATE_CURRENT));
		}
		if (content_intent != null) { 
			builder.setContentIntent(PendingIntent.getActivity(this, 0, content_intent, PendingIntent.FLAG_UPDATE_CURRENT));
		}
		int defaults = 0;
		if (mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_HAVE_SOUND, true)) {
			builder.setSound(Uri.parse(mPreferences.getString(PREFERENCE_KEY_NOTIFICATION_RINGTONE,
					Settings.System.DEFAULT_RINGTONE_URI.getPath())), Notification.STREAM_DEFAULT);
		}
		if (mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_HAVE_VIBRATION, true)) {
			defaults |= Notification.DEFAULT_VIBRATE;
		}
		if (mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_HAVE_LIGHTS, true)) {
			final int color_def = getResources().getColor(R.color.holo_blue_dark);
			final int color = mPreferences.getInt(PREFERENCE_KEY_NOTIFICATION_LIGHT_COLOR, color_def);
			builder.setLights(color, 1000, 2000);
		}
		builder.setDefaults(defaults);
		return builder.getNotification();
	}

	private int getHomeTimeline(final long[] account_ids, final long[] max_ids, final boolean is_auto_refresh) {
		mAsyncTaskManager.cancel(mGetHomeTimelineTaskId);
		final GetHomeTimelineTask task = new GetHomeTimelineTask(account_ids, max_ids, null, is_auto_refresh);
		return mGetHomeTimelineTaskId = mAsyncTaskManager.add(task, true);
	}
	
	private int getHomeTimelineWithSinceId(final long[] account_ids, final long[] max_ids, final long[] since_ids, final boolean is_auto_refresh) {
		mAsyncTaskManager.cancel(mGetHomeTimelineTaskId);
		final GetHomeTimelineTask task = new GetHomeTimelineTask(account_ids, max_ids, since_ids, is_auto_refresh);
		return mGetHomeTimelineTaskId = mAsyncTaskManager.add(task, true);
	}

	private int getMentions(final long[] account_ids, final long[] max_ids, final boolean is_auto_refresh) {
		mAsyncTaskManager.cancel(mGetMentionsTaskId);
		final GetMentionsTask task = new GetMentionsTask(account_ids, max_ids, null, is_auto_refresh);
		return mGetMentionsTaskId = mAsyncTaskManager.add(task, true);
	}
	
	private int getMentionsWithSinceId(final long[] account_ids, final long[] max_ids, final long[] since_ids, final boolean is_auto_refresh) {
		mAsyncTaskManager.cancel(mGetMentionsTaskId);
		final GetMentionsTask task = new GetMentionsTask(account_ids, max_ids, since_ids, is_auto_refresh);
		return mGetMentionsTaskId = mAsyncTaskManager.add(task, true);
	}

	private int getReceivedDirectMessages(final long[] account_ids, final long[] max_ids, final boolean is_auto_refresh) {
		mAsyncTaskManager.cancel(mGetReceivedDirectMessagesTaskId);
		final GetReceivedDirectMessagesTask task = new GetReceivedDirectMessagesTask(account_ids, max_ids, null,
				is_auto_refresh);
		return mGetReceivedDirectMessagesTaskId = mAsyncTaskManager.add(task, true);
	}
	
	private int getReceivedDirectMessagesWithSinceId(final long[] account_ids, final long[] max_ids, final long[] since_ids, final boolean is_auto_refresh) {
		mAsyncTaskManager.cancel(mGetReceivedDirectMessagesTaskId);
		final GetReceivedDirectMessagesTask task = new GetReceivedDirectMessagesTask(account_ids, max_ids, since_ids,
				is_auto_refresh);
		return mGetReceivedDirectMessagesTaskId = mAsyncTaskManager.add(task, true);
	}

	private void showErrorToast(final int action_res, final Exception e, final boolean long_message) {
		Utils.showErrorToast(this, getString(action_res), e, long_message);
	}

	class AddUserListMemberTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id, user_id;
		private final int list_id;
		private final String screen_name;

		public AddUserListMemberTask(final long account_id, final int list_id, final long user_id, final String screen_name) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.list_id = list_id;
			this.user_id = user_id;
			this.screen_name = screen_name;
		}

		@Override
		protected SingleResponse<UserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					if (user_id > 0) {
						final UserList list = twitter.addUserListMember(list_id, user_id);
						return new SingleResponse<UserList>(account_id, list, null);
					} else if (screen_name != null) {
						final User user = twitter.showUser(screen_name);
						if (user != null && user.getId() > 0) {
							final UserList list = twitter.addUserListMember(list_id, user.getId());
							return new SingleResponse<UserList>(account_id, list, null);
						}
					}
				} catch (final TwitterException e) {
					return new SingleResponse<UserList>(account_id, null, e);
				}
			}
			return new SingleResponse<UserList>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<UserList> result) {
			final boolean succeed = result != null && result.data != null && result.data.getId() > 0;
			if (succeed) {
				Toast.makeText(TweetingsService.this, R.string.add_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.add_member, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_USER_LIST_MEMBER_DELETED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_LIST_ID, list_id);
			intent.putExtra(INTENT_KEY_SUCCEED, succeed);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class CacheUsersTask extends ManagedAsyncTask<Void, Void, Void> {

		private final List<ListResponse<twitter4j.Status>> responses;

		public CacheUsersTask(final List<ListResponse<twitter4j.Status>> result) {
			super(TweetingsService.this, mAsyncTaskManager);
			responses = result;
		}

		@Override
		protected Void doInBackground(final Void... args) {

			for (final ListResponse<twitter4j.Status> response : responses) {
				final List<twitter4j.Status> statuses = response.list;
				if (statuses == null || statuses.size() <= 0) {
					continue;
				}
				final List<ContentValues> cached_users_list = new ArrayList<ContentValues>();
				final List<Long> user_ids = new ArrayList<Long>();

				for (final twitter4j.Status status : statuses) {
					if (status == null) {
						continue;
					}
					final User user = status.getUser();
					final long user_id = user.getId();

					if (!user_ids.contains(user_id)) {
						user_ids.add(user_id);
						cached_users_list.add(makeCachedUserContentValues(user));
					}

				}

				mResolver.delete(CachedUsers.CONTENT_URI,
						CachedUsers.USER_ID + " IN (" + ListUtils.toString(user_ids, ',', true) + " )", null);
				mResolver.bulkInsert(CachedUsers.CONTENT_URI,
						cached_users_list.toArray(new ContentValues[cached_users_list.size()]));

			}
			return null;
		}

	}

	class CreateBlockTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private long account_id;
		private long user_id;

		public CreateBlockTask(final long account_id, final long user_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_id = user_id;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final User user = twitter.createBlock(user_id);
					return new SingleResponse<User>(account_id, user, null);
				} catch (final TwitterException e) {
					return new SingleResponse<User>(account_id, null, e);
				}
			}
			return new SingleResponse<User>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result != null && result.data != null && result.data.getId() > 0) {
				for (final Uri uri : Utils.STATUSES_URIS) {
					final String where = Statuses.ACCOUNT_ID + " = " + account_id + " AND " + Statuses.USER_ID + " = "
						 	+ user_id;
					mResolver.delete(uri, where, null);
					
				}
				//I bet you don't want to see this user in your auto complete list.
				final String where = CachedUsers.USER_ID + " = " + user_id;
				mResolver.delete(CachedUsers.CONTENT_URI, where, null);
				Toast.makeText(TweetingsService.this, R.string.user_blocked, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.block, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_BLOCKSTATE_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class CreateFavoriteTask extends ManagedAsyncTask<Void, Void, SingleResponse<twitter4j.Status>> {

		private long account_id;

		private long status_id;

		public CreateFavoriteTask(final long account_id, final long status_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected SingleResponse<twitter4j.Status> doInBackground(final Void... params) {

			if (account_id < 0) return new SingleResponse<twitter4j.Status>(account_id, null, null);

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final twitter4j.Status status = twitter.createFavorite(status_id);
					return new SingleResponse<twitter4j.Status>(account_id, status, null);
				} catch (final TwitterException e) {
					return new SingleResponse<twitter4j.Status>(account_id, null, e);
				}
			}
			return new SingleResponse<twitter4j.Status>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<twitter4j.Status> result) {

			if (result.data != null) {
				final long status_id = result.data.getId();
				final ContentValues values = new ContentValues();
				values.put(Statuses.IS_FAVORITE, 1);
				final StringBuilder where = new StringBuilder();
				where.append(Statuses.ACCOUNT_ID + " = " + result.account_id);
				where.append(" AND ");
				where.append("(");
				where.append(Statuses.STATUS_ID + "=" + status_id);
				where.append(" OR ");
				where.append(Statuses.RETWEET_ID + "=" + status_id);
				where.append(")");
				for (final Uri uri : TweetStore.STATUSES_URIS) {
					mResolver.update(uri, values, where.toString(), null);
				}
				final Intent intent = new Intent(BROADCAST_FAVORITE_CHANGED);
				intent.putExtra(INTENT_KEY_STATUS_ID, status_id);
				intent.putExtra(INTENT_KEY_FAVORITED, true);
				sendBroadcast(intent);
				Toast.makeText(TweetingsService.this, R.string.favorite_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.fav, result.exception, true);
			}
			super.onPostExecute(result);
		}

	}

	class CreateFriendshipTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private long account_id;
		private long user_id;

		public CreateFriendshipTask(final long account_id, final long user_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_id = user_id;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final User user = twitter.createFriendship(user_id);
					return new SingleResponse<User>(account_id, user, null);
				} catch (final TwitterException e) {
					return new SingleResponse<User>(account_id, null, e);
				}
			}
			return new SingleResponse<User>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result != null && result.data != null) {
				Toast.makeText(TweetingsService.this, R.string.follow_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.follow, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_FRIENDSHIP_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}
	
	class CreateMultiBlockTask extends ManagedAsyncTask<Void, Void, ListResponse<Long>> {

		private long account_id;
		private long[] user_ids;

		public CreateMultiBlockTask(final long account_id, final long[] user_ids) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_ids = user_ids;
		}

		@Override
		protected ListResponse<Long> doInBackground(final Void... params) {

			final List<Long> blocked_users = new ArrayList<Long>();
			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				for (final long user_id : user_ids) {
					try {
						final User user = twitter.createBlock(user_id);
						if (user == null || user.getId() <= 0) {
							continue;
						}
						blocked_users.add(user.getId());
					} catch (final TwitterException e) {
						e.printStackTrace();
					}
				}
			}
			return new ListResponse<Long>(account_id, -1, -1, blocked_users, null);
		}

		@Override
		protected void onPostExecute(final ListResponse<Long> result) {
			if (result != null) {
				final String user_ids = ListUtils.toString(result.list, ',', false);
				for (final Uri uri : Utils.STATUSES_URIS) {
					final String where = Statuses.ACCOUNT_ID + " = " + account_id + " AND " + Statuses.USER_ID
							+ " IN (" + user_ids + ")";
					mResolver.delete(uri, where, null);
				}
				//I bet you don't want to see these users in your auto complete list.
				final String where = CachedUsers.USER_ID + " IN (" + user_ids + ")";
				mResolver.delete(CachedUsers.CONTENT_URI, where, null);
				Toast.makeText(TweetingsService.this, R.string.users_blocked, Toast.LENGTH_SHORT).show();
			}
			final Intent intent = new Intent(BROADCAST_MULTI_BLOCKSTATE_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_ids);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class CreateUserListSubscriptionTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id;
		private final int list_id;

		public CreateUserListSubscriptionTask(final long account_id, final int list_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.list_id = list_id;
		}

		@Override
		protected SingleResponse<UserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final UserList list = twitter.createUserListSubscription(list_id);
					return new SingleResponse<UserList>(account_id, list, null);
				} catch (final TwitterException e) {
					return new SingleResponse<UserList>(account_id, null, e);
				}
			}
			return new SingleResponse<UserList>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<UserList> result) {
			final boolean succeed = result != null && result.data != null && result.data.getId() > 0;
			if (succeed) {
				Toast.makeText(TweetingsService.this, R.string.follow_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.block, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_USER_LIST_SUBSCRIPTION_CHANGED);
			intent.putExtra(INTENT_KEY_LIST_ID, list_id);
			intent.putExtra(INTENT_KEY_SUCCEED, succeed);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class CreateUserListTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id;
		private final String list_name, description;
		private final boolean is_public;

		public CreateUserListTask(final long account_id, final String list_name, final boolean is_public, final String description) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.list_name = list_name;
			this.description = description;
			this.is_public = is_public;
		}

		@Override
		protected SingleResponse<UserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					if (list_name != null) {
						final UserList list = twitter.createUserList(list_name, is_public, description);
						return new SingleResponse<UserList>(account_id, list, null);
					}
				} catch (final TwitterException e) {
					return new SingleResponse<UserList>(account_id, null, e);
				}
			}
			return new SingleResponse<UserList>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<UserList> result) {
			final boolean succeed = result != null && result.data != null && result.data.getId() > 0;
			if (succeed) {
				Toast.makeText(TweetingsService.this, R.string.create_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.create, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_USER_LIST_CREATED);
			intent.putExtra(INTENT_KEY_SUCCEED, succeed);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class DeleteUserListMemberTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id, user_id;
		private final int list_id;

		public DeleteUserListMemberTask(final long account_id, final int list_id, final long user_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.list_id = list_id;
			this.user_id = user_id;
		}

		@Override
		protected SingleResponse<UserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final UserList list = twitter.deleteUserListMember(list_id, user_id);
					return new SingleResponse<UserList>(account_id, list, null);
				} catch (final TwitterException e) {
					return new SingleResponse<UserList>(account_id, null, e);
				}
			}
			return new SingleResponse<UserList>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<UserList> result) {
			final boolean succeed = result != null && result.data != null && result.data.getId() > 0;
			if (succeed) {
				Toast.makeText(TweetingsService.this, R.string.delete_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.create, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_USER_LIST_MEMBER_DELETED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_LIST_ID, list_id);
			intent.putExtra(INTENT_KEY_SUCCEED, succeed);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class DestroyBlockTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private long account_id;
		private long user_id;

		public DestroyBlockTask(final long account_id, final long user_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_id = user_id;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final User user = twitter.destroyBlock(user_id);
					return new SingleResponse<User>(account_id, user, null);
				} catch (final TwitterException e) {
					return new SingleResponse<User>(account_id, null, e);
				}
			}
			return new SingleResponse<User>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result != null && result.data != null) {
				Toast.makeText(TweetingsService.this, R.string.user_unblocked, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.unblock, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_BLOCKSTATE_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class DestroyDirectMessageTask extends ManagedAsyncTask<Void, Void, SingleResponse<DirectMessage>> {

		private final Twitter twitter;
		private final long message_id;
		private final long account_id;

		public DestroyDirectMessageTask(final long account_id, final long message_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			this.account_id = account_id;
			this.message_id = message_id;
		}

		@Override
		protected SingleResponse<DirectMessage> doInBackground(final Void... args) {
			if (twitter == null) return new SingleResponse<DirectMessage>(account_id, null, null);
			try {
				return new SingleResponse<DirectMessage>(account_id, twitter.destroyDirectMessage(message_id), null);
			} catch (final TwitterException e) {
				return new SingleResponse<DirectMessage>(account_id, null, e);
			}
		}

		@Override
		protected void onPostExecute(final SingleResponse<DirectMessage> result) {
			super.onPostExecute(result);
			if (result == null) return;
			if (result.data != null && result.data.getId() > 0) {
				Toast.makeText(TweetingsService.this, R.string.delete_success, Toast.LENGTH_SHORT).show();
				final String where = DirectMessages.MESSAGE_ID + " = " + message_id;

				mResolver.delete(DirectMessages.Inbox.CONTENT_URI, where, null);
				mResolver.delete(DirectMessages.Outbox.CONTENT_URI, where, null);
			} else {
				showErrorToast(R.string.delete, result.exception, true);
			}
		}

	}

	class DestroyFavoriteTask extends ManagedAsyncTask<Void, Void, SingleResponse<twitter4j.Status>> {

		private long account_id;

		private long status_id;

		public DestroyFavoriteTask(final long account_id, final long status_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected SingleResponse<twitter4j.Status> doInBackground(final Void... params) {

			if (account_id < 0) {
				new SingleResponse<twitter4j.Status>(account_id, null, null);
			}

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final twitter4j.Status status = twitter.destroyFavorite(status_id);
					return new SingleResponse<twitter4j.Status>(account_id, status, null);
				} catch (final TwitterException e) {
					return new SingleResponse<twitter4j.Status>(account_id, null, e);
				}
			}
			return new SingleResponse<twitter4j.Status>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<twitter4j.Status> result) {

			if (result.data != null) {
				final long status_id = result.data.getId();
				final ContentValues values = new ContentValues();
				values.put(Statuses.IS_FAVORITE, 0);
				final StringBuilder where = new StringBuilder();
				where.append(Statuses.ACCOUNT_ID + " = " + result.account_id);
				where.append(" AND ");
				where.append("(");
				where.append(Statuses.STATUS_ID + "=" + status_id);
				where.append(" OR ");
				where.append(Statuses.RETWEET_ID + "=" + status_id);
				where.append(")");
				for (final Uri uri : TweetStore.STATUSES_URIS) {
					mResolver.update(uri, values, where.toString(), null);
				}
				final Intent intent = new Intent(BROADCAST_FAVORITE_CHANGED);
				intent.putExtra(INTENT_KEY_USER_ID, account_id);
				intent.putExtra(INTENT_KEY_STATUS_ID, status_id);
				intent.putExtra(INTENT_KEY_FAVORITED, false);
				sendBroadcast(intent);
				Toast.makeText(TweetingsService.this, R.string.unfavorite_success, Toast.LENGTH_SHORT).show();

			} else {
				showErrorToast(R.string.unfav, result.exception, true);
			}
			super.onPostExecute(result);
		}

	}

	class DestroyFriendshipTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private long account_id;
		private long user_id;

		public DestroyFriendshipTask(final long account_id, final long user_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_id = user_id;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final User user = twitter.destroyFriendship(user_id);
					return new SingleResponse<User>(account_id, user, null);
				} catch (final TwitterException e) {
					return new SingleResponse<User>(account_id, null, e);
				}
			}
			return new SingleResponse<User>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result != null && result.data != null) {
				Toast.makeText(TweetingsService.this, R.string.unfollow_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.unfollow, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_FRIENDSHIP_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class DestroyStatusTask extends ManagedAsyncTask<Void, Void, SingleResponse<twitter4j.Status>> {

		private long account_id;

		private long status_id;

		public DestroyStatusTask(final long account_id, final long status_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected SingleResponse<twitter4j.Status> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final twitter4j.Status status = twitter.destroyStatus(status_id);
					return new SingleResponse<twitter4j.Status>(account_id, status, null);
				} catch (final TwitterException e) {
					return new SingleResponse<twitter4j.Status>(account_id, null, e);
				}
			}
			return new SingleResponse<twitter4j.Status>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<twitter4j.Status> result) {
			final Intent intent = new Intent(BROADCAST_STATUS_DESTROYED);
			if (result != null && result.data != null && result.data.getId() > 0) {
				final long status_id = result.data.getId();

				final StringBuilder where = new StringBuilder();
				where.append(Statuses.STATUS_ID + " = " + status_id);
				where.append(" OR " + Statuses.RETWEET_ID + " = " + status_id);
				for (final Uri uri : TweetStore.STATUSES_URIS) {
					mResolver.delete(uri, where.toString(), null);
				}
				intent.putExtra(INTENT_KEY_STATUS_ID, status_id);
				intent.putExtra(INTENT_KEY_SUCCEED, true);
				Toast.makeText(TweetingsService.this, R.string.delete_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.delete, result.exception, true);
			}
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}
	
	class DestroySavedSearchTask extends ManagedAsyncTask<Void, Void, SingleResponse<twitter4j.SavedSearch>> {

		private long account_id;

		private int search_id;

		public DestroySavedSearchTask(final long account_id, final int search_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.search_id = search_id;
		}

		@Override
		protected SingleResponse<twitter4j.SavedSearch> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final twitter4j.SavedSearch status = twitter.destroySavedSearch(search_id);
					return new SingleResponse<twitter4j.SavedSearch>(account_id, status, null);
				} catch (final TwitterException e) {
					return new SingleResponse<twitter4j.SavedSearch>(account_id, null, e);
				}
			}
			return new SingleResponse<twitter4j.SavedSearch>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<twitter4j.SavedSearch> result) {
			final Intent intent = new Intent(BROADCAST_SEARCH_CHANGED);
			if (result != null && result.data != null && result.data.getId() > 0) {
				Toast.makeText(TweetingsService.this, R.string.delete_search_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.delete, result.exception, true);
			}
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class CreateSavedSearchTask extends ManagedAsyncTask<Void, Void, SingleResponse<twitter4j.SavedSearch>> {

		private long account_id;

		private String query;

		public CreateSavedSearchTask(final long account_id, final String query) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.query = query;
		}

		@Override
		protected SingleResponse<twitter4j.SavedSearch> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final twitter4j.SavedSearch status = twitter.createSavedSearch(query);
					return new SingleResponse<twitter4j.SavedSearch>(account_id, status, null);
				} catch (final TwitterException e) {
					return new SingleResponse<twitter4j.SavedSearch>(account_id, null, e);
				}
			}
			return new SingleResponse<twitter4j.SavedSearch>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<twitter4j.SavedSearch> result) {
			final Intent intent = new Intent(BROADCAST_SEARCH_CHANGED);
			if (result != null && result.data != null && result.data.getId() > 0) {
				Toast.makeText(TweetingsService.this, R.string.create_search_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.save, result.exception, true);
			}
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class DestroyUserListSubscriptionTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id;
		private final int list_id;

		public DestroyUserListSubscriptionTask(final long account_id, final int list_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.list_id = list_id;
		}

		@Override
		protected SingleResponse<UserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final UserList list = twitter.destroyUserListSubscription(list_id);
					return new SingleResponse<UserList>(account_id, list, null);
				} catch (final TwitterException e) {
					return new SingleResponse<UserList>(account_id, null, e);
				}
			}
			return new SingleResponse<UserList>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<UserList> result) {
			final boolean succeed = result != null && result.data != null && result.data.getId() > 0;
			if (succeed) {
				Toast.makeText(TweetingsService.this, R.string.unfollow_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.delete, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_USER_LIST_SUBSCRIPTION_CHANGED);
			intent.putExtra(INTENT_KEY_LIST_ID, list_id);
			intent.putExtra(INTENT_KEY_SUCCEED, succeed);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class DestroyUserListTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id;
		private final int list_id;

		public DestroyUserListTask(final long account_id, final int list_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.list_id = list_id;
		}

		@Override
		protected SingleResponse<UserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					if (list_id > 0) {
						final UserList list = twitter.destroyUserList(list_id);
						return new SingleResponse<UserList>(account_id, list, null);
					}
				} catch (final TwitterException e) {
					return new SingleResponse<UserList>(account_id, null, e);
				}
			}
			return new SingleResponse<UserList>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<UserList> result) {
			final boolean succeed = result != null && result.data != null && result.data.getId() > 0;
			if (succeed) {
				Toast.makeText(TweetingsService.this, R.string.delete_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.delete, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_USER_LIST_DELETED);
			intent.putExtra(INTENT_KEY_SUCCEED, succeed);
			intent.putExtra(INTENT_KEY_LIST_ID, list_id);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class GetDailyTrendsTask extends GetTrendsTask {

		public GetDailyTrendsTask(final long account_id) {
			super(account_id);
		}

		@Override
		public ResponseList<Trends> getTrends(final Twitter twitter) throws TwitterException {
			if (twitter == null) return null;
			return twitter.getDailyTrends();
		}

		@Override
		protected void onPostExecute(final ListResponse<Trends> result) {
			mStoreDailyTrendsTaskId = mAsyncTaskManager.add(new StoreDailyTrendsTask(result), true);
			super.onPostExecute(result);

		}

	}

	abstract class GetDirectMessagesTask extends ManagedAsyncTask<Void, Void, List<ListResponse<DirectMessage>>> {

		private final long[] account_ids, max_ids, since_ids;

		public GetDirectMessagesTask(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_ids = account_ids;
			this.max_ids = max_ids;
			this.since_ids = since_ids;
		}

		public abstract ResponseList<DirectMessage> getDirectMessages(final Twitter twitter, final Paging paging)
				throws TwitterException;

		@Override
		protected List<ListResponse<DirectMessage>> doInBackground(final Void... params) {

			final List<ListResponse<DirectMessage>> result = new ArrayList<ListResponse<DirectMessage>>();

			if (account_ids == null) return result;

			final boolean max_ids_valid = max_ids != null && max_ids.length == account_ids.length;
			final boolean since_ids_valid = since_ids != null && since_ids.length == account_ids.length;

			int idx = 0;
			final int load_item_limit = mPreferences.getInt(PREFERENCE_KEY_LOAD_ITEM_LIMIT,
					PREFERENCE_DEFAULT_LOAD_ITEM_LIMIT);
			for (final long account_id : account_ids) {
				final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, true);
				if (twitter != null) {
					try {
						final Paging paging = new Paging();
						paging.setCount(load_item_limit);
						long max_id = -1;
						if (max_ids_valid && max_ids[idx] > 0) {
							max_id = max_ids[idx];
							paging.setMaxId(max_id);
						}
						long since_id = -1;
						if (since_ids_valid && since_ids[idx] > 0) {
							since_id = since_ids[idx];
							paging.setSinceId(since_id);
						}
						final ResponseList<DirectMessage> statuses = getDirectMessages(twitter, paging);

						if (statuses != null) {
							result.add(new ListResponse<DirectMessage>(account_id, max_id, since_id, statuses, null));
						}
					} catch (final TwitterException e) {
						e.printStackTrace();
					}
				}
				idx++;
			}
			return result;

		}

	}

	class GetHomeTimelineTask extends GetStatusesTask {

		private final boolean is_auto_refresh;

		public GetHomeTimelineTask(final long[] account_ids, final long[] max_ids, final long[] since_ids, final boolean is_auto_refresh) {
			super(Statuses.CONTENT_URI, account_ids, max_ids, since_ids);
			this.is_auto_refresh = is_auto_refresh;
		}

		@Override
		public ResponseList<twitter4j.Status> getStatuses(final Twitter twitter, final Paging paging) throws TwitterException {
			if (mPreferences.getBoolean(PREFERENCE_KEY_API_V1, true) == true) {
				return twitter.getHomeTimelinev1(paging);
			}
			return twitter.getHomeTimeline(paging);
		}

		@Override
		public Twitter getTwitter(final long account_id) {
			return getTwitterInstance(TweetingsService.this, account_id, true, true);
		}

		@Override
		protected void onPostExecute(final List<ListResponse<twitter4j.Status>> responses) {
			super.onPostExecute(responses);
			mStoreStatusesTaskId = mAsyncTaskManager.add(new StoreHomeTimelineTask(responses, is_auto_refresh,
					shouldSetMinId()), true);
			mGetHomeTimelineTaskId = -1;
		}

		@Override
		protected void onPreExecute() {
			mAlarmManager.cancel(mPendingRefreshHomeTimelineIntent);
			if (mPreferences.getBoolean(PREFERENCE_KEY_AUTO_REFRESH, false)) {
				final long update_interval = parseInt(mPreferences.getString(PREFERENCE_KEY_REFRESH_INTERVAL,
						"30")) * 60 * 1000;
				if (update_interval > 0) {
					mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + update_interval,
							update_interval, mPendingRefreshHomeTimelineIntent);
				}
			}
			super.onPreExecute();
		}

	}

	class GetLocalTrendsTask extends GetTrendsTask {

		private final int woeid;

		public GetLocalTrendsTask(final long account_id, final int woeid) {
			super(account_id);
			this.woeid = woeid;
		}

		@Override
		public List<Trends> getTrends(final Twitter twitter) throws TwitterException {
			final ArrayList<Trends> trends_list = new ArrayList<Trends>();
			if (twitter != null) {
				trends_list.add(twitter.getLocationTrends(woeid));
			}
			return trends_list;
		}

		@Override
		protected void onPostExecute(final ListResponse<Trends> result) {
			mStoreLocalTrendsTaskId = mAsyncTaskManager.add(new StoreLocalTrendsTask(result), true);
			super.onPostExecute(result);

		}

	}

	class GetMentionsTask extends GetStatusesTask {

		private final boolean is_auto_refresh;

		public GetMentionsTask(final long[] account_ids, final long[] max_ids, final long[] since_ids, final boolean is_auto_refresh) {
			super(Mentions.CONTENT_URI, account_ids, max_ids, since_ids);
			this.is_auto_refresh = is_auto_refresh;
		}

		@Override
		public ResponseList<twitter4j.Status> getStatuses(final Twitter twitter, final Paging paging) throws TwitterException {
			if (mPreferences.getBoolean(PREFERENCE_KEY_API_V1, true) == true) {
				return twitter.getMentionsv1(paging);
			}
			return twitter.getMentions(paging);
		}

		@Override
		public Twitter getTwitter(final long account_id) {
			return getTwitterInstance(TweetingsService.this, account_id, true, false);
		}

		@Override
		protected void onPostExecute(final List<ListResponse<twitter4j.Status>> responses) {
			super.onPostExecute(responses);
			mStoreMentionsTaskId = mAsyncTaskManager.add(new StoreMentionsTask(responses, is_auto_refresh,
					shouldSetMinId()), true);
			mGetMentionsTaskId = -1;
		}

		@Override
		protected void onPreExecute() {
			mAlarmManager.cancel(mPendingRefreshMentionsIntent);
			if (mPreferences.getBoolean(PREFERENCE_KEY_AUTO_REFRESH, false)) {
				final long update_interval = parseInt(mPreferences.getString(PREFERENCE_KEY_REFRESH_INTERVAL,
						"30")) * 60 * 1000;
				if (update_interval > 0) {
					mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
							+ update_interval, update_interval, mPendingRefreshMentionsIntent);
				}
			}
			super.onPreExecute();
		}

	}

	class GetReceivedDirectMessagesTask extends GetDirectMessagesTask {

		private final boolean is_auto_refresh;

		public GetReceivedDirectMessagesTask(final long[] account_ids, final long[] max_ids, final long[] since_ids, final boolean is_auto_refresh) {
			super(account_ids, max_ids, since_ids);
			this.is_auto_refresh = is_auto_refresh;
		}

		@Override
		public ResponseList<DirectMessage> getDirectMessages(final Twitter twitter, final Paging paging) throws TwitterException {
			if (mPreferences.getBoolean(PREFERENCE_KEY_API_V1, true) == true) {
				return twitter.getDirectMessagesv1(paging);
			}
			return twitter.getDirectMessages(paging);
		}

		@Override
		protected void onPreExecute() {
			mAlarmManager.cancel(mPendingRefreshDirectMessagesIntent);
			if (mPreferences.getBoolean(PREFERENCE_KEY_AUTO_REFRESH, false)) {
				final long update_interval = parseInt(mPreferences.getString(PREFERENCE_KEY_REFRESH_INTERVAL,
						"30")) * 60 * 1000;
				if (update_interval > 0) {
					mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
							+ update_interval, update_interval, mPendingRefreshDirectMessagesIntent);
				}
			}
			super.onPreExecute();
		}

		@Override
		protected void onPostExecute(final List<ListResponse<DirectMessage>> responses) {
			super.onPostExecute(responses);
			mStoreReceivedDirectMessagesTaskId = mAsyncTaskManager.add(new StoreReceivedDirectMessagesTask(responses,
					is_auto_refresh), true);
			mGetReceivedDirectMessagesTaskId = -1;
		}

	}

	class GetSentDirectMessagesTask extends GetDirectMessagesTask {

		public GetSentDirectMessagesTask(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			super(account_ids, max_ids, since_ids);
		}

		@Override
		public ResponseList<DirectMessage> getDirectMessages(final Twitter twitter, final Paging paging) throws TwitterException {
			if (mPreferences.getBoolean(PREFERENCE_KEY_API_V1, true) == true) {
				return twitter.getSentDirectMessagesv1(paging);
			}
			return twitter.getSentDirectMessages(paging);
		}

		@Override
		protected void onPostExecute(final List<ListResponse<DirectMessage>> responses) {
			super.onPostExecute(responses);
			mStoreSentDirectMessagesTaskId = mAsyncTaskManager.add(new StoreSentDirectMessagesTask(responses), true);
			mGetSentDirectMessagesTaskId = -1;
		}

	}

	abstract class GetStatusesTask extends ManagedAsyncTask<Void, Void, List<ListResponse<twitter4j.Status>>> {

		private final long[] account_ids, max_ids, since_ids;

		private boolean should_set_min_id, should_set_since_id;

		public GetStatusesTask(final Uri uri, final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_ids = account_ids;
			this.max_ids = max_ids;
			this.since_ids = since_ids;
		}

		public abstract ResponseList<twitter4j.Status> getStatuses(final Twitter twitter, final Paging paging)
				throws TwitterException;

		public abstract Twitter getTwitter(final long account_id);

		public boolean shouldSetMinId() {
			return should_set_min_id;
		}
		
		public boolean shouldSetSinceId() {
			return should_set_since_id;
		}
		
		@Override
		protected void onPostExecute(final List<ListResponse<twitter4j.Status>> result) {
			super.onPostExecute(result);
			for (final ListResponse<twitter4j.Status> response : result) {
				if (response.list == null) {
					showErrorToast(R.string.refresh, response.exception, true);
				}
			}
		}
		
		@Override
		protected List<ListResponse<twitter4j.Status>> doInBackground(final Void... params) {

			final List<ListResponse<twitter4j.Status>> result = new ArrayList<ListResponse<twitter4j.Status>>();

			if (account_ids == null) return result;

			final boolean max_ids_valid = max_ids != null && max_ids.length == account_ids.length;
			should_set_min_id = !max_ids_valid;
			final boolean since_ids_valid = since_ids != null && since_ids.length == account_ids.length;
			should_set_since_id = !since_ids_valid;

			int idx = 0;
			final int load_item_limit = mPreferences.getInt(PREFERENCE_KEY_LOAD_ITEM_LIMIT,
					PREFERENCE_DEFAULT_LOAD_ITEM_LIMIT);
			for (final long account_id : account_ids) {
				final Twitter twitter = getTwitter(account_id);
				if (twitter != null) {
					try {
						final Paging paging = new Paging();
						paging.setCount(load_item_limit);
						long max_id = -1;
						if (max_ids_valid && max_ids[idx] > 0) {
							max_id = max_ids[idx];
							paging.setMaxId(max_id);
						}
						long since_id = -1;
						if (since_ids_valid && since_ids[idx] > 0) {
							since_id = since_ids[idx];
							paging.setSinceId(since_id);
						}
						final ResponseList<twitter4j.Status> statuses = getStatuses(twitter, paging);

						if (statuses != null) {
							result.add(new ListResponse<twitter4j.Status>(account_id, max_id, since_id, statuses, null));
						}
					} catch (final TwitterException e) {
						e.printStackTrace();
						result.add(new ListResponse<twitter4j.Status>(account_id, -1, -1, null, e));
					}
				}
				idx++;
			}
			return result;
		}

	}

	abstract class GetTrendsTask extends ManagedAsyncTask<Void, Void, ListResponse<Trends>> {

		private final long account_id;

		public GetTrendsTask(final long account_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
		}

		public abstract List<Trends> getTrends(final Twitter twitter) throws TwitterException;

		@Override
		protected ListResponse<Trends> doInBackground(final Void... params) {
			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					return new ListResponse<Trends>(account_id, -1, -1, getTrends(twitter), null);
				} catch (final TwitterException e) {
					e.printStackTrace();
				}
			}
			return new ListResponse<Trends>(account_id, -1, -1, null, null);
		}

	}

	class GetWeeklyTrendsTask extends GetTrendsTask {

		public GetWeeklyTrendsTask(final long account_id) {
			super(account_id);
		}

		@Override
		public ResponseList<Trends> getTrends(final Twitter twitter) throws TwitterException {
			if (twitter == null) return null;
			return twitter.getWeeklyTrends();
		}

		@Override
		protected void onPostExecute(final ListResponse<Trends> result) {
			mStoreWeeklyTrendsTaskId = mAsyncTaskManager.add(new StoreWeeklyTrendsTask(result), true);
			super.onPostExecute(result);

		}

	}

	public static final class ListResponse<Data> {

		public final long account_id, max_id, since_id;
		public final List<Data> list;
		public final TwitterException exception;

		public ListResponse(final long account_id, final long max_id, final long since_id, final List<Data> responselist, TwitterException exception) {
			this.account_id = account_id;
			this.max_id = max_id;
			this.since_id = since_id;
			this.list = responselist;
			this.exception = exception;
		}
	}
	
	class ReportMultiSpamTask extends ManagedAsyncTask<Void, Void, ListResponse<Long>> {

		private long account_id;
		private long[] user_ids;

		public ReportMultiSpamTask(final long account_id, final long[] user_ids) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_ids = user_ids;
		}

		@Override
		protected ListResponse<Long> doInBackground(final Void... params) {

			final List<Long> reported_users = new ArrayList<Long>();
			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				for (final long user_id : user_ids) {
					try {
						final User user = twitter.reportSpam(user_id);
						if (user == null || user.getId() <= 0) {
							continue;
						}
						reported_users.add(user.getId());
					} catch (final TwitterException e) {
						e.printStackTrace();
					}
				}
			}
			return new ListResponse<Long>(account_id, -1, -1, reported_users, null);
		}

		@Override
		protected void onPostExecute(final ListResponse<Long> result) {
			if (result != null) {
				final String user_id_where = ListUtils.toString(result.list, ',', false);
				for (final Uri uri : Utils.STATUSES_URIS) {
					final String where = Statuses.ACCOUNT_ID + " = " + account_id + " AND " + Statuses.USER_ID
							+ " IN (" + user_id_where + ")";
					mResolver.delete(uri, where, null);
				}
				Toast.makeText(TweetingsService.this, R.string.reported_users_for_spam, Toast.LENGTH_SHORT).show();
			}
			final Intent intent = new Intent(BROADCAST_MULTI_BLOCKSTATE_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_ids);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class ReportSpamTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private long account_id;
		private long user_id;

		public ReportSpamTask(final long account_id, final long user_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_id = user_id;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final User user = twitter.reportSpam(user_id);
					return new SingleResponse<User>(account_id, user, null);
				} catch (final TwitterException e) {
					return new SingleResponse<User>(account_id, null, e);
				}
			}
			return new SingleResponse<User>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result != null && result.data != null && result.data.getId() > 0) {
				for (final Uri uri : Utils.STATUSES_URIS) {
					final String where = Statuses.ACCOUNT_ID + " = " + account_id + " AND " + Statuses.USER_ID + " = "
						 	 + user_id;
					mResolver.delete(uri, where, null);
				}
				Toast.makeText(TweetingsService.this, R.string.reported_user_for_spam, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.refresh, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_BLOCKSTATE_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class RetweetStatusTask extends ManagedAsyncTask<Void, Void, SingleResponse<twitter4j.Status>> {

		private long account_id;

		private long status_id;
		
		private final AudioManager audioManager = (AudioManager) getSystemService(Activity.AUDIO_SERVICE);

		public RetweetStatusTask(final long account_id, final long status_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected SingleResponse<twitter4j.Status> doInBackground(final Void... params) {

			if (account_id < 0) return new SingleResponse<twitter4j.Status>(account_id, null, null);

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final twitter4j.Status status = twitter.retweetStatus(status_id);
					return new SingleResponse<twitter4j.Status>(account_id, status, null);
				} catch (final TwitterException e) {
					return new SingleResponse<twitter4j.Status>(account_id, null, e);
				}
			}
			return new SingleResponse<twitter4j.Status>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<twitter4j.Status> result) {

			if (result.data != null && result.data.getId() > 0) {
				Toast.makeText(TweetingsService.this, R.string.retweet_success, Toast.LENGTH_SHORT).show();
				boolean isMuted = false;
				switch(audioManager.getRingerMode()) {
					case AudioManager.RINGER_MODE_NORMAL:
				        isMuted = false;
				        break;
				    case AudioManager.RINGER_MODE_SILENT:
				        isMuted = true;
				        break;
				    case AudioManager.RINGER_MODE_VIBRATE:
				    	isMuted = true;
				    	break;
				}
				if (mPreferences.getBoolean(PREFERENCE_KEY_SOUND_SEND, true)) {
					MediaPlayer mPlayer = MediaPlayer.create(TweetingsService.this, R.raw.whoosh);
					if (isMuted == true) {
						mPlayer.setVolume(0,0);
					}
					mPlayer.start();
				}
				final Intent intent = new Intent(BROADCAST_RETWEET_CHANGED);
				intent.putExtra(INTENT_KEY_STATUS_ID, status_id);
				intent.putExtra(INTENT_KEY_RETWEETED, true);
				sendBroadcast(intent);
			} else {
				showErrorToast(R.string.retweet, result.exception, true);
			}

			super.onPostExecute(result);
		}

	}

	class SendDirectMessageTask extends ManagedAsyncTask<Void, Void, SingleResponse<DirectMessage>> {

		private final Twitter twitter;
		private final long user_id;
		private final String screen_name;
		private final String message;
		private final long account_id;
		
		private final AudioManager audioManager = (AudioManager) getSystemService(Activity.AUDIO_SERVICE);

		public SendDirectMessageTask(final long account_id, final String screen_name, final long user_id, final String message) {
			super(TweetingsService.this, mAsyncTaskManager);
			twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			this.account_id = account_id;
			this.user_id = user_id;
			this.screen_name = screen_name;
			this.message = message;
		}

		@Override
		protected SingleResponse<DirectMessage> doInBackground(final Void... args) {
			if (twitter == null) return new SingleResponse<DirectMessage>(account_id, null, null);
			try {
				if (user_id > 0)
					return new SingleResponse<DirectMessage>(account_id, twitter.sendDirectMessage(user_id, message),
							null);
				else if (screen_name != null)
					return new SingleResponse<DirectMessage>(account_id,
							twitter.sendDirectMessage(screen_name, message), null);
			} catch (final TwitterException e) {
				return new SingleResponse<DirectMessage>(account_id, null, e);
			}
			return new SingleResponse<DirectMessage>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<DirectMessage> result) {
			super.onPostExecute(result);
			if (result == null) return;
			if (result.data != null && result.data.getId() > 0) {
				final Uri.Builder builder = DirectMessages.Outbox.CONTENT_URI.buildUpon();
				builder.appendQueryParameter(QUERY_PARAM_NOTIFY, String.valueOf(true));
				final ContentValues values = makeDirectMessageContentValues(result.data, account_id);
				getContentResolver().insert(builder.build(), values);
				boolean isMuted = false;
				switch(audioManager.getRingerMode()) {
					case AudioManager.RINGER_MODE_NORMAL:
				        isMuted = false;
				        break;
				    case AudioManager.RINGER_MODE_SILENT:
				        isMuted = true;
				        break;
				    case AudioManager.RINGER_MODE_VIBRATE:
				    	isMuted = true;
				    	break;
				}
				if (mPreferences.getBoolean(PREFERENCE_KEY_SOUND_SEND, true)) {
					MediaPlayer mPlayer = MediaPlayer.create(TweetingsService.this, R.raw.whoosh);
					if (isMuted == true) {
						mPlayer.setVolume(0,0);
					}
					mPlayer.start();
				}
				Toast.makeText(TweetingsService.this, R.string.send_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.update, result.exception, true);
			}
		}

	}

	/*
	 * By making this a static class with a WeakReference to the Service, we
	 * ensure that the Service can be GCd even when the system process still has
	 * a remote reference to the stub.
	 */
	static final class ServiceStub extends ITweetingsService.Stub {

		public void cancelShutdown() {
			mService.get().cancelShutdown();
		}
		
		final WeakReference<TweetingsService> mService;

		public ServiceStub(TweetingsService service) {

			mService = new WeakReference<TweetingsService>(service);
		}

		@Override
		public int addUserListMember(final long account_id, final int list_id, final long user_id, final String screen_name) {
			return mService.get().addUserListMember(account_id, list_id, user_id, screen_name);
		}

		@Override
		public void clearNotification(final int id) {
			mService.get().clearNotification(id);
		}

		@Override
		public int createBlock(final long account_id, final long user_id) {
			return mService.get().createBlock(account_id, user_id);
		}
		
		@Override
		public int insertStreamToHomeTimeline(final long account_id, final Intent intent) {
			return mService.get().insertStreamToHomeTimeline(account_id, intent);
		}
		
		@Override
		public int insertStreamToMentions(final long account_id, final Intent intent) {
			return mService.get().insertStreamToMentions(account_id, intent);
		}
		
		@Override
		public int insertStreamToInbox(final long account_id, final Intent intent) {
			return mService.get().insertStreamToInbox(account_id, intent);
		}


		@Override
		public int createFavorite(final long account_id, final long status_id) {
			return mService.get().createFavorite(account_id, status_id);
		}

		@Override
		public int createFriendship(final long account_id, final long user_id) {
			return mService.get().createFriendship(account_id, user_id);
		}
		
		@Override
		public String generateOAuthEchoHeader(final long account_id) {
			return mService.get().generateOAuthEchoHeader(account_id);
		}
		
		@Override
		public int createMultiBlock(final long account_id, final long[] user_ids) {
			return mService.get().createMultiBlock(account_id, user_ids);
		}

		@Override
		public int createUserList(final long account_id, final String list_name, final boolean is_public, final String description) {
			return mService.get().createUserList(account_id, list_name, is_public, description);
		}

		@Override
		public int createUserListSubscription(final long account_id, final int list_id) {
			return mService.get().createUserListSubscription(account_id, list_id);
		}

		@Override
		public int deleteUserListMember(final long account_id, final int list_id, final long user_id) {
			return mService.get().deleteUserListMember(account_id, list_id, user_id);
		}

		@Override
		public int destroyBlock(final long account_id, final long user_id) {
			return mService.get().destroyBlock(account_id, user_id);
		}

		@Override
		public int destroyDirectMessage(final long account_id, final long message_id) {
			return mService.get().destroyDirectMessage(account_id, message_id);
		}

		@Override
		public int destroyFavorite(final long account_id, final long status_id) {
			return mService.get().destroyFavorite(account_id, status_id);
		}

		@Override
		public int destroyFriendship(final long account_id, final long user_id) {
			return mService.get().destroyFriendship(account_id, user_id);
		}

		@Override
		public int destroyStatus(final long account_id, final long status_id) {
			return mService.get().destroyStatus(account_id, status_id);
		}
		
		@Override
		public int destroySavedSearch(final long account_id, final int search_id) {
			return mService.get().destroySavedSearch(account_id, search_id);
		}
		
		@Override
		public int createSavedSearch(final long account_id, final String query) {
			return mService.get().createSavedSearch(account_id, query);
		}

		@Override
		public int destroyUserList(final long account_id, final int list_id) {
			return mService.get().destroyUserList(account_id, list_id);
		}

		@Override
		public int destroyUserListSubscription(final long account_id, final int list_id) {
			return mService.get().destroyUserListSubscription(account_id, list_id);
		}

		@Override
		public int getDailyTrends(final long account_id) {
			return mService.get().getDailyTrends(account_id);
		}

		@Override
		public int getHomeTimeline(final long[] account_ids, final long[] max_ids) {
			return mService.get().getHomeTimeline(account_ids, max_ids);
		}
		
		@Override
		public int getHomeTimelineWithSinceId(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			return mService.get().getHomeTimelineWithSinceId(account_ids, max_ids, since_ids);
		}

		@Override
		public int getLocalTrends(final long account_id, final int woeid) {
			return mService.get().getLocalTrends(account_id, woeid);
		}

		@Override
		public int getMentions(final long[] account_ids, final long[] max_ids) {
			return mService.get().getMentions(account_ids, max_ids);
		}
		
		@Override
		public int getMentionsWithSinceId(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			return mService.get().getMentionsWithSinceId(account_ids, max_ids, since_ids);
		}

		@Override
		public int getReceivedDirectMessages(final long[] account_ids, final long[] max_ids) {
			return mService.get().getReceivedDirectMessages(account_ids, max_ids);
		}
		
		@Override
		public int getReceivedDirectMessagesWithSinceId(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			return mService.get().getReceivedDirectMessagesWithSinceId(account_ids, max_ids, since_ids);
		}

		@Override
		public int getSentDirectMessages(final long[] account_ids, final long[] max_ids) {
			return mService.get().getSentDirectMessages(account_ids, max_ids);
		}
		
		@Override
		public int getSentDirectMessagesWithSinceId(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			return mService.get().getSentDirectMessagesWithSinceId(account_ids, max_ids, since_ids);
		}

		@Override
		public int getWeeklyTrends(final long account_id) {
			return mService.get().getWeeklyTrends(account_id);
		}

		@Override
		public boolean hasActivatedTask() {
			return mService.get().hasActivatedTask();
		}

		@Override
		public boolean isDailyTrendsRefreshing() {
			return mService.get().isDailyTrendsRefreshing();
		}

		@Override
		public boolean isHomeTimelineRefreshing() {
			return mService.get().isHomeTimelineRefreshing();
		}

		@Override
		public boolean isLocalTrendsRefreshing() {
			return mService.get().isLocalTrendsRefreshing();
		}

		@Override
		public boolean isMentionsRefreshing() {
			return mService.get().isMentionsRefreshing();
		}

		@Override
		public boolean isReceivedDirectMessagesRefreshing() {
			return mService.get().isReceivedDirectMessagesRefreshing();
		}

		@Override
		public boolean isSentDirectMessagesRefreshing() {
			return mService.get().isSentDirectMessagesRefreshing();
		}

		@Override
		public boolean isWeeklyTrendsRefreshing() {
			return mService.get().isWeeklyTrendsRefreshing();
		}
		
		@Override
		public int refreshAll() {
			return mService.get().refreshAll();
		}
		
		@Override
		public int reportMultiSpam(final long account_id, final long[] user_ids) {
			return mService.get().reportMultiSpam(account_id, user_ids);
		}

		@Override
		public int reportSpam(final long account_id, final long user_id) {
			return mService.get().reportSpam(account_id, user_id);
		}

		@Override
		public int retweetStatus(final long account_id, final long status_id) {
			return mService.get().retweetStatus(account_id, status_id);
		}

		@Override
		public int sendDirectMessage(final long account_id, final String screen_name, final long user_id, final String message) {
			return mService.get().sendDirectMessage(account_id, screen_name, user_id, message);
		}

		@Override
		public void shutdownService() {
			mService.get().shutdownService();
		}

		@Override
		public boolean startAutoRefresh() {
			return mService.get().startAutoRefresh();
		}

		@Override
		public void stopAutoRefresh() {
			mService.get().stopAutoRefresh();
		}

		@Override
		public boolean test() {
			return mService.get().test();
		}

		@Override
		public int updateProfile(final long account_id, final String name, final String url, final String location, final String description) {
			return mService.get().updateProfile(account_id, name, url, location, description);
		}

		@Override
		public int updateProfileImage(final long account_id, final Uri image_uri, final boolean delete_image) {
			return mService.get().updateProfileImage(account_id, image_uri, delete_image);
		}
		
		@Override
		public int updateBannerImage(final long account_id, final Uri image_uri, final boolean delete_image) {
			return mService.get().updateBannerImage(account_id, image_uri, delete_image);
		}

		@Override
		public int updateStatus(final long[] account_ids, final String content, final Location location, final Uri image_uri, final long in_reply_to,
				final boolean is_possibly_sensitive, final boolean delete_image) {
			return mService.get().updateStatus(account_ids, content, location, image_uri, in_reply_to, is_possibly_sensitive, delete_image);
		}
		
		@Override
		public int scheduleStatus(final String schedule_date, final long[] account_ids, final String content, final Location location, final Uri image_uri, final long in_reply_to,
				final boolean delete_image) {
			return mService.get().scheduleStatus(schedule_date, account_ids, content, location, image_uri, in_reply_to, delete_image);
		}

		@Override
		public int bufferStatus(final long[] account_ids, final String content) {
			return mService.get().bufferStatus(account_ids, content);
		}
		
		@Override
		public int updateUserListDetails(final long account_id, final int list_id, final boolean is_public, final String name,
				final String description) {
			return mService.get().updateUserListDetails(account_id, list_id, is_public, name, description);
		}
	}

	static final class SingleResponse<Data> {
		public final Exception exception;
		public final Data data;
		public final long account_id;

		public SingleResponse(final long account_id, final Data data, final Exception exception) {
			this.exception = exception;
			this.data = data;
			this.account_id = account_id;
		}
	}

	class StoreDailyTrendsTask extends StoreTrendsTask {

		public StoreDailyTrendsTask(final ListResponse<Trends> result) {
			super(result, CachedTrends.Daily.CONTENT_URI);
		}

	}

	class StoreDirectMessagesTask extends ManagedAsyncTask<Void, Void, SingleResponse<Bundle>> {

		private final List<ListResponse<DirectMessage>> responses;
		private final Uri uri;

		public StoreDirectMessagesTask(final List<ListResponse<DirectMessage>> result, final Uri uri) {
			super(TweetingsService.this, mAsyncTaskManager);
			responses = result;
			this.uri = uri;
		}

		@Override
		protected SingleResponse<Bundle> doInBackground(final Void... args) {

			boolean succeed = false;
			final Uri query_uri = buildQueryUri(uri, false);
			int total_items_inserted = 0;
			for (final ListResponse<DirectMessage> response : responses) {
				final long account_id = response.account_id;
				final List<DirectMessage> messages = response.list;
				final Cursor cur = mResolver.query(uri, new String[0], DirectMessages.ACCOUNT_ID + " = " + account_id,
						null, null);
				boolean no_items_before = false;
				if (cur != null) {
					no_items_before = cur.getCount() <= 0;
					cur.close();
				}
				if (messages != null) {
					final List<ContentValues> values_list = new ArrayList<ContentValues>();
					final List<Long> message_ids = new ArrayList<Long>();

					final long min_id = -1;

					for (final DirectMessage message : messages) {
						if (message == null || message.getId() <= 0) {
							continue;
						}
						message_ids.add(message.getId());

						values_list.add(makeDirectMessageContentValues(message, account_id));

					}

					int rows_deleted = -1;

					// Delete all rows conflicting before new data inserted.
					{
						final StringBuilder where = new StringBuilder();
						where.append(DirectMessages.ACCOUNT_ID + " = " + account_id);
						where.append(" AND ");
						where.append(DirectMessages.MESSAGE_ID + " IN ( " + ListUtils.toString(message_ids, ',', true)
								+ " ) ");
						rows_deleted = mResolver.delete(query_uri, where.toString(), null);
					}

					// Insert previously fetched items.
					mResolver.bulkInsert(query_uri, values_list.toArray(new ContentValues[values_list.size()]));

					// No row deleted, so I will insert a gap.
					final boolean insert_gap = rows_deleted == 1 && message_ids.contains(response.max_id)
							|| rows_deleted == 0 && response.max_id == -1 && !no_items_before;
					if (insert_gap) {
						final ContentValues values = new ContentValues();
						values.put(DirectMessages.IS_GAP, 1);
						final StringBuilder where = new StringBuilder();
						where.append(DirectMessages.ACCOUNT_ID + "=" + account_id);
						where.append(" AND " + DirectMessages.MESSAGE_ID + "=" + min_id);
						mResolver.update(query_uri, values, where.toString(), null);
					}
					final int actual_items_inserted = values_list.size() - rows_deleted;
					if (actual_items_inserted > 0) {
						total_items_inserted += actual_items_inserted;
					}
				}
				succeed = true;
			}
			final Bundle bundle = new Bundle();
			bundle.putBoolean(INTENT_KEY_SUCCEED, succeed);
			bundle.putInt(INTENT_KEY_ITEMS_INSERTED, total_items_inserted);
			return new SingleResponse<Bundle>(-1, bundle, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<Bundle> response) {
			if (response != null && response.data != null && response.data.getBoolean(INTENT_KEY_SUCCEED)) {
				notifyForUpdatedUri(TweetingsService.this, uri);
			}
			super.onPostExecute(response);
		}

	}

	public class StoreHomeTimelineTask extends StoreStatusesTask {

		private final boolean is_auto_refresh;

		public StoreHomeTimelineTask(final List<ListResponse<twitter4j.Status>> result, final boolean is_auto_refresh,
				final boolean should_set_min_id) {
			super(result, Statuses.CONTENT_URI, should_set_min_id);
			this.is_auto_refresh = is_auto_refresh;
		}

		@Override
		protected void onPostExecute(final SingleResponse<Bundle> response) {
			mStoreStatusesTaskId = -1;
			final boolean succeed = response != null && response.data != null
					&& response.data.getBoolean(INTENT_KEY_SUCCEED);
			final Bundle extras = new Bundle();
			extras.putBoolean(INTENT_KEY_SUCCEED, succeed);
			if (shouldSetMinId() && getTotalItemsInserted() > 0) {
				extras.putLong(INTENT_KEY_MIN_ID,
						response != null && response.data != null ? response.data.getLong(INTENT_KEY_MIN_ID, -1) : -1);
			}
			sendBroadcast(new Intent(BROADCAST_HOME_TIMELINE_REFRESHED).putExtras(extras));
			final int items_inserted = response.data.getInt(INTENT_KEY_ITEMS_INSERTED);
			mNewStatusesCount += items_inserted;
			if (items_inserted > 0) {
				Intent intent = new Intent(BROADCAST_TABS_NEW_TWEETS);
				intent.putExtra(INTENT_KEY_UPDATE_TAB, TAB_HOME);
				sendBroadcast(intent);
			}
			if (succeed && is_auto_refresh
					&& mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_HOME_TIMELINE, false)) {
				if (items_inserted > 0) {
					final String message = getResources().getQuantityString(R.plurals.Ntweets, mNewStatusesCount,
							mNewStatusesCount);
					final Intent delete_intent = new Intent(BROADCAST_NOTIFICATION_CLEARED);
					final Bundle delete_extras = new Bundle();
					delete_extras.putInt(INTENT_KEY_NOTIFICATION_ID, NOTIFICATION_ID_HOME_TIMELINE);
					delete_intent.putExtras(delete_extras);
					final Intent content_intent = new Intent(TweetingsService.this, HomeActivity.class);
					content_intent.setAction(Intent.ACTION_MAIN);
					content_intent.addCategory(Intent.CATEGORY_LAUNCHER);
					final Bundle content_extras = new Bundle();
					content_extras.putInt(INTENT_KEY_INITIAL_TAB, HomeActivity.TAB_POSITION_HOME);
					content_intent.putExtras(content_extras);
					if (mPreferences.getBoolean(PREFERENCE_KEY_LOCAL_NOTIFICATIONS, true)) {
						mNotificationManager.notify(
							NOTIFICATION_ID_HOME_TIMELINE,
							buildNotification(getString(R.string.new_notifications), message, R.drawable.ic_stat_tweet,
									content_intent, delete_intent));
					}
				}
			}
			super.onPostExecute(response);
		}

	}

	class StoreLocalTrendsTask extends StoreTrendsTask {

		public StoreLocalTrendsTask(final ListResponse<Trends> result) {
			super(result, CachedTrends.Local.CONTENT_URI);
		}

	}

	class StoreMentionsTask extends StoreStatusesTask {

		private final boolean is_auto_refresh;

		public StoreMentionsTask(final List<ListResponse<twitter4j.Status>> result, final boolean is_auto_refresh,
				boolean should_set_min_id) {
			super(result, Mentions.CONTENT_URI, should_set_min_id);
			this.is_auto_refresh = is_auto_refresh;
		}

		@Override
		protected void onPostExecute(final SingleResponse<Bundle> response) {
			mStoreMentionsTaskId = -1;
			final boolean succeed = response != null && response.data != null
					&& response.data.getBoolean(INTENT_KEY_SUCCEED);
			final Bundle extras = new Bundle();
			extras.putBoolean(INTENT_KEY_SUCCEED, succeed);
			if (shouldSetMinId() && getTotalItemsInserted() > 0) {
				extras.putLong(INTENT_KEY_MIN_ID,
						response != null && response.data != null ? response.data.getLong(INTENT_KEY_MIN_ID, -1) : -1);
			}
			sendBroadcast(new Intent(BROADCAST_MENTIONS_REFRESHED).putExtras(extras));
			final int items_inserted = response.data.getInt(INTENT_KEY_ITEMS_INSERTED); 
			
			if (items_inserted > 0) {
				Intent intent = new Intent(BROADCAST_TABS_NEW_TWEETS);
				intent.putExtra(INTENT_KEY_UPDATE_TAB, TAB_MENTIONS);
				sendBroadcast(intent);
			}
			if (succeed && is_auto_refresh
					&& mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_MENTIONS, false)) {
				mNewMentionsCount += items_inserted;
				if (items_inserted > 0) {
					final Intent delete_intent = new Intent(BROADCAST_NOTIFICATION_CLEARED);
					final Bundle delete_extras = new Bundle();
					delete_extras.putInt(INTENT_KEY_NOTIFICATION_ID, NOTIFICATION_ID_MENTIONS);
					delete_intent.putExtras(delete_extras);
					final Intent content_intent = new Intent(TweetingsService.this, HomeActivity.class);
					content_intent.setAction(Intent.ACTION_MAIN);
					content_intent.addCategory(Intent.CATEGORY_LAUNCHER);
					final Bundle content_extras = new Bundle();
					content_extras.putInt(INTENT_KEY_INITIAL_TAB, HomeActivity.TAB_POSITION_MENTIONS);
					content_intent.putExtras(content_extras);
					final String title = getString(R.string.mentions);
					final String message = getResources().getQuantityString(R.plurals.Nmentions, mNewMentionsCount,
							mNewMentionsCount);
					final Notification notification = buildNotification(title, message, R.drawable.ic_stat_mention,
							content_intent, delete_intent);
					if (mPreferences.getBoolean(PREFERENCE_KEY_LOCAL_NOTIFICATIONS, true)) {
						mNotificationManager.notify(NOTIFICATION_ID_MENTIONS, notification);
					}
				}
			}
			super.onPostExecute(response);
		}

	}

	class StoreReceivedDirectMessagesTask extends StoreDirectMessagesTask {

		private final boolean is_auto_refresh;

		public StoreReceivedDirectMessagesTask(final List<ListResponse<DirectMessage>> result, final boolean is_auto_refresh) {
			super(result, DirectMessages.Inbox.CONTENT_URI);
			this.is_auto_refresh = is_auto_refresh;
		}

		@Override
		protected void onPostExecute(final SingleResponse<Bundle> response) {
			mStoreReceivedDirectMessagesTaskId = -1;
			final boolean succeed = response != null && response.data != null
					&& response.data.getBoolean(INTENT_KEY_SUCCEED);
			sendBroadcast(new Intent(BROADCAST_RECEIVED_DIRECT_MESSAGES_REFRESHED)
					.putExtra(INTENT_KEY_SUCCEED, succeed));
			if (succeed && is_auto_refresh
					&& mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_ENABLE_DIRECT_MESSAGES, false)) {
				final int items_inserted = response.data.getInt(INTENT_KEY_ITEMS_INSERTED);
				mNewMessagesCount += items_inserted;
				if (items_inserted > 0) {
					final String message = getResources().getQuantityString(R.plurals.Ndirect_messages,
							mNewMessagesCount, mNewMessagesCount);
					final Intent delete_intent = new Intent(BROADCAST_NOTIFICATION_CLEARED);
					final Bundle delete_extras = new Bundle();
					delete_extras.putInt(INTENT_KEY_NOTIFICATION_ID, NOTIFICATION_ID_DIRECT_MESSAGES);
					delete_intent.putExtras(delete_extras);
					final Intent content_intent = new Intent(TweetingsService.this, HomeActivity.class);
					content_intent.setAction(Intent.ACTION_MAIN);
					content_intent.addCategory(Intent.CATEGORY_LAUNCHER);
					final Bundle content_extras = new Bundle();
					content_extras.putInt(INTENT_KEY_INITIAL_TAB, HomeActivity.TAB_POSITION_MESSAGES);
					content_intent.putExtras(content_extras);
					if (mPreferences.getBoolean(PREFERENCE_KEY_LOCAL_NOTIFICATIONS, true)) {
						mNotificationManager.notify(
							NOTIFICATION_ID_DIRECT_MESSAGES,
							buildNotification(getString(R.string.new_notifications), message,
									R.drawable.ic_stat_direct_message, content_intent, delete_intent));
					}
				}
			}
			super.onPostExecute(response);
		}

	}

	class StoreSentDirectMessagesTask extends StoreDirectMessagesTask {

		public StoreSentDirectMessagesTask(final List<ListResponse<DirectMessage>> result) {
			super(result, DirectMessages.Outbox.CONTENT_URI);
		}

		@Override
		protected void onPostExecute(final SingleResponse<Bundle> response) {
			mStoreSentDirectMessagesTaskId = -1;
			final boolean succeed = response != null && response.data != null
					&& response.data.getBoolean(INTENT_KEY_SUCCEED);
			sendBroadcast(new Intent(BROADCAST_SENT_DIRECT_MESSAGES_REFRESHED).putExtra(INTENT_KEY_SUCCEED, succeed));
			super.onPostExecute(response);
		}

	}

	abstract class StoreStatusesTask extends ManagedAsyncTask<Void, Void, SingleResponse<Bundle>> {

		private final List<ListResponse<twitter4j.Status>> responses;
		private final Uri uri;
		private final boolean should_set_min_id;

		int total_items_inserted = 0;

		public StoreStatusesTask(final List<ListResponse<twitter4j.Status>> result, final Uri uri, final boolean should_set_min_id) {
			super(TweetingsService.this, mAsyncTaskManager);
			responses = result;
			this.should_set_min_id = should_set_min_id;
			this.uri = uri;
		}

		public int getTotalItemsInserted() {
			return total_items_inserted;
		}

		public boolean shouldSetMinId() {
			return should_set_min_id;
		}

		@Override
		protected SingleResponse<Bundle> doInBackground(final Void... args) {

			boolean succeed = false;
			final Uri query_uri = buildQueryUri(uri, false);
			final ArrayList<Long> newly_inserted_ids = new ArrayList<Long>();
			final long[] old_ids = getAllStatusesIds(TweetingsService.this, uri,
					mPreferences.getBoolean(PREFERENCE_KEY_ENABLE_FILTER, false));
			for (final ListResponse<twitter4j.Status> response : responses) {
				final long account_id = response.account_id;
				final List<twitter4j.Status> statuses = response.list;
				if (statuses == null || statuses.size() <= 0) {
					continue;
				}
				final ArrayList<Long> ids_in_db = Utils.getStatusIdsInDatabase(TweetingsService.this, query_uri,
						account_id);
				final boolean no_items_before = ids_in_db.size() <= 0;
				final List<ContentValues> values_list = new ArrayList<ContentValues>();
				final List<Long> status_ids = new ArrayList<Long>(), retweet_ids = new ArrayList<Long>();

				long min_id = -1;

				for (final twitter4j.Status status : statuses) {
					if (status == null) {
						continue;
					}
					final long status_id = status.getId();
					final long retweet_id = status.getRetweetedStatus() != null ? status.getRetweetedStatus().getId()
							: -1;

					status_ids.add(status_id);

					if ((retweet_id <= 0 || !retweet_ids.contains(retweet_id)) && !retweet_ids.contains(status_id)) {
						if (status_id < min_id || min_id == -1) {
							min_id = status_id;
						}
						if (retweet_id > 0) {
							retweet_ids.add(retweet_id);
						}
						values_list.add(makeStatusContentValues(status, account_id));
					}

				}

				int rows_deleted = -1;

				// Delete all rows conflicting before new data inserted.
				{

					final ArrayList<Long> account_newly_inserted = new ArrayList<Long>();
					account_newly_inserted.addAll(status_ids);
					account_newly_inserted.removeAll(ids_in_db);
					newly_inserted_ids.addAll(account_newly_inserted);
					final StringBuilder where = new StringBuilder();
					final String ids_string = ListUtils.toString(status_ids, ',', true);
					where.append(Statuses.ACCOUNT_ID + " = " + account_id);
					where.append(" AND ");
					where.append("(");
					where.append(Statuses.STATUS_ID + " IN ( " + ids_string + " ) ");
					where.append(" OR ");
					where.append(Statuses.RETWEET_ID + " IN ( " + ids_string + " ) ");
					where.append(")");
					rows_deleted = mResolver.delete(query_uri, where.toString(), null);
				}

				// Insert previously fetched items.
				mResolver.bulkInsert(query_uri, values_list.toArray(new ContentValues[values_list.size()]));

				final int actual_items_inserted = values_list.size() - rows_deleted;

				if (actual_items_inserted > 0) {
					total_items_inserted += actual_items_inserted;
				}

				int variance = 0;
				final int load_item_limit = mPreferences.getInt(PREFERENCE_KEY_LOAD_ITEM_LIMIT,
						PREFERENCE_DEFAULT_LOAD_ITEM_LIMIT);
				if (load_item_limit > 50) {
					variance = 5;
				}
				else if (load_item_limit > 100) {
					variance = 10;
				}
				
				boolean insert_gap = false;
				int tryCount = load_item_limit - variance;
				if (no_items_before) {
					insert_gap = false;
				}
				if (response.list.size() == 0) {
					insert_gap = false;
				}
				else if (load_item_limit == response.list.size()) {
					insert_gap = true;
				}
				else if (response.list.size() > tryCount) {
					insert_gap = true;
				}
				//final boolean insert_gap = rows_deleted == 1 && status_ids.contains(response.max_id)
					//	|| rows_deleted == 0 && response.max_id == -1 && !no_items_before;
				if (insert_gap) {
					final ContentValues values = new ContentValues();
					values.put(Statuses.IS_GAP, 1);
					final StringBuilder where = new StringBuilder();
					where.append(Statuses.ACCOUNT_ID + "=" + account_id);
					where.append(" AND " + Statuses.STATUS_ID + "=" + min_id);
					mResolver.update(query_uri, values, where.toString(), null);
					// Ignore gaps
					newly_inserted_ids.remove(min_id);
				}
				succeed = true;
			}
			final long[] new_ids = getAllStatusesIds(TweetingsService.this, uri,
					mPreferences.getBoolean(PREFERENCE_KEY_ENABLE_FILTER, false));
			final Bundle bundle = new Bundle();
			bundle.putBoolean(INTENT_KEY_SUCCEED, succeed);
			getAllStatusesIds(TweetingsService.this, uri, mPreferences.getBoolean(PREFERENCE_KEY_ENABLE_FILTER, false));
			bundle.putInt(INTENT_KEY_ITEMS_INSERTED, new_ids.length - old_ids.length);
			if (should_set_min_id && total_items_inserted > 0) {
				bundle.putLong(INTENT_KEY_MIN_ID, ListUtils.min(newly_inserted_ids));
			}
			return new SingleResponse<Bundle>(-1, bundle, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<Bundle> response) {
			if (response.data.getBoolean(INTENT_KEY_SUCCEED)) {
				notifyForUpdatedUri(TweetingsService.this, uri);
			}
			super.onPostExecute(response);
			mAsyncTaskManager.add(new CacheUsersTask(responses), true);
		}

	}

	class StoreTrendsTask extends ManagedAsyncTask<Void, Void, SingleResponse<Bundle>> {

		private final ListResponse<Trends> response;
		private final Uri uri;

		public StoreTrendsTask(final ListResponse<Trends> result, Uri uri) {
			super(TweetingsService.this, mAsyncTaskManager);
			response = result;
			this.uri = uri;
		}

		@Override
		protected SingleResponse<Bundle> doInBackground(final Void... args) {
			final Bundle bundle = new Bundle();
			if (response != null) {

				final Uri query_uri = buildQueryUri(uri, false);
				final List<Trends> messages = response.list;
				if (messages != null && messages.size() > 0) {
					final ContentValues[] values_array = makeTrendsContentValues(messages);
					mResolver.delete(query_uri, null, null);
					mResolver.bulkInsert(query_uri, values_array);

					bundle.putBoolean(INTENT_KEY_SUCCEED, true);
				}
			}
			return new SingleResponse<Bundle>(-1, bundle, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<Bundle> response) {
			final Intent intent = new Intent(BROADCAST_TRENDS_UPDATED);
			if (response != null && response.data != null && response.data.getBoolean(INTENT_KEY_SUCCEED)) {
				notifyForUpdatedUri(TweetingsService.this, uri);
				intent.putExtra(INTENT_KEY_SUCCEED, true);
			}
			super.onPostExecute(response);
			sendBroadcast(intent);
		}

	}

	class StoreWeeklyTrendsTask extends StoreTrendsTask {

		public StoreWeeklyTrendsTask(final ListResponse<Trends> result) {
			super(result, CachedTrends.Weekly.CONTENT_URI);
		}

	}

	class UpdateProfileImageTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private final long account_id;
		private final Uri image_uri;
		private final boolean delete_image;

		public UpdateProfileImageTask(final long account_id, final Uri image_uri, final boolean delete_image) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.image_uri = image_uri;
			this.delete_image = delete_image;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null && image_uri != null && "file".equals(image_uri.getScheme())) {
				try {
					final User user = twitter.updateProfileImage(new File(image_uri.getPath()));
					return new SingleResponse<User>(account_id, user, null);
				} catch (final TwitterException e) {
					return new SingleResponse<User>(account_id, null, e);
				}
			}
			return new SingleResponse<User>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result != null && result.data != null) {
				Toast.makeText(TweetingsService.this, R.string.profile_image_update_success, Toast.LENGTH_SHORT).show();
				if (delete_image) {
					new File(image_uri.getPath()).delete();
				}
			} else {
				showErrorToast(R.string.update, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_PROFILE_UPDATED);
			intent.putExtra(INTENT_KEY_USER_ID, account_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}
	
	class UpdateBannerImageTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private final long account_id;
		private final Uri image_uri;
		private final boolean delete_image;

		public UpdateBannerImageTask(final long account_id, final Uri image_uri, final boolean delete_image) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.image_uri = image_uri;
			this.delete_image = delete_image;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null && image_uri != null && "file".equals(image_uri.getScheme())) {
				try {
					final User user = twitter.updateBannerImage(new File(image_uri.getPath()));
					return new SingleResponse<User>(account_id, user, null);
				} catch (final TwitterException e) {
					return new SingleResponse<User>(account_id, null, e);
				}
			}
			return new SingleResponse<User>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			//if (result != null && result.data != null) {
				Toast.makeText(TweetingsService.this, R.string.profile_banner_update_success, Toast.LENGTH_SHORT).show();
				if (delete_image) {
					new File(image_uri.getPath()).delete();
				}
			//} else {
				//showErrorToast(R.string.update, result.exception, true);
			//}
			final Intent intent = new Intent(BROADCAST_PROFILE_UPDATED);
			intent.putExtra(INTENT_KEY_USER_ID, account_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class UpdateProfileTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private final long account_id;
		private final String name, url, location, description;

		public UpdateProfileTask(final long account_id, final String name, final String url, final String location, final String description) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.name = name;
			this.url = url;
			this.location = location;
			this.description = description;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final User user = twitter.updateProfile(name, url, location, description);
					return new SingleResponse<User>(account_id, user, null);
				} catch (final TwitterException e) {
					return new SingleResponse<User>(account_id, null, e);
				}
			}
			return new SingleResponse<User>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result != null && result.data != null) {
				Toast.makeText(TweetingsService.this, R.string.profile_update_success, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.update, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_PROFILE_UPDATED);
			intent.putExtra(INTENT_KEY_USER_ID, account_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}
	
	class ScheduleStatusTask extends ManagedAsyncTask<Void, Void, String> {

		private final Validator validator = new Validator();

		private String schedule_date;
		private long[] account_ids;
		private String content;
		private Location location;
		private Uri image_uri;
		private long in_reply_to;
		
		private final AudioManager audioManager = (AudioManager) getSystemService(Activity.AUDIO_SERVICE);
		
		public ScheduleStatusTask(final String schedule_date, final long[] account_ids, final String content, final Location location, final Uri image_uri, final long in_reply_to,
				final boolean delete_image) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_ids = account_ids != null ? account_ids : new long[0];
			this.content = content;
			this.location = location;
			this.image_uri = image_uri;
			this.in_reply_to = in_reply_to;
			this.schedule_date = schedule_date;
		}

		@Override
		protected String doInBackground(final Void... params) {

			if (account_ids.length == 0) return null;

			
				final String unshortened_content = content;
				final String send_on_date = schedule_date;
				final StatusUpdate status = new StatusUpdate(unshortened_content);
				final String deviceId = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
				if (location != null) {
					status.setLocation(new GeoLocation(location.getLatitude(), location.getLongitude()));
				}
				if (in_reply_to > 0) {
					status.setInReplyToStatusId(in_reply_to);
				}
				
				for (final long account_id : account_ids) {
					
					final String screen_name = getAccountUsername(TweetingsService.this, account_id);
					
					final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
					AccessToken accessToken = null;
					try {
						accessToken = twitter.getOAuthAccessToken();
					} catch (TwitterException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					
					HttpClient client = HttpClientFactory.getThreadSafeClient();
	    			HttpPost post = new HttpPost(TWEETINGS_SCHEDULER_POST_URL);
	    			
	    			if (accessToken != null) {
		    			try {
		    				List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
		    				// Get the deviceID
		    				nameValuePairs.add(new BasicNameValuePair("status", unshortened_content));
		    				if (in_reply_to > 0) {
		    					nameValuePairs.add(new BasicNameValuePair("replyto", String.valueOf(in_reply_to)));
		    				}
		    				if (location != null) {
		    					nameValuePairs.add(new BasicNameValuePair("display_coordinates", "true"));
		    					nameValuePairs.add(new BasicNameValuePair("lat", String.valueOf(location.getLatitude())));
		    					nameValuePairs.add(new BasicNameValuePair("long", String.valueOf(location.getLongitude())));
		    				}
		    				nameValuePairs.add(new BasicNameValuePair("app", "android"));
		    				
		    				TimeZone tz = TimeZone.getDefault();
		    				Date now = new Date();
		    				int offsetFromUtc = tz.getOffset(now.getTime());
		    				
		    				nameValuePairs.add(new BasicNameValuePair("time", send_on_date));
		    				nameValuePairs.add(new BasicNameValuePair("tz", String.valueOf(offsetFromUtc)));
		    				
		    				
		    				nameValuePairs.add(new BasicNameValuePair("u", screen_name));
		    				nameValuePairs.add(new BasicNameValuePair("did", deviceId));
		    				
		    				nameValuePairs.add(new BasicNameValuePair("se", accessToken.getTokenSecret()));
		    				nameValuePairs.add(new BasicNameValuePair("tk", accessToken.getToken()));
		    				
		    				post.setEntity(new UrlEncodedFormEntity(nameValuePairs));
		    				HttpResponse response = client.execute(post);
		    				BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
		    				String line = "";
		    				
		    				while ((line = rd.readLine()) != null) {
		    					return null;
		    					//Log.e("HttpResponse", line);
		    				}
		    			} catch (IOException e) {
		    				e.printStackTrace();
		    			}
	    			}
				
				}
			
			return "";
		}

		@Override
		protected void onPostExecute(final String result) {
	
			boolean isMuted = false;
			switch(audioManager.getRingerMode()) {
				case AudioManager.RINGER_MODE_NORMAL:
			        isMuted = false;
			        break;
			    case AudioManager.RINGER_MODE_SILENT:
			        isMuted = true;
			        break;
			    case AudioManager.RINGER_MODE_VIBRATE:
			    	isMuted = true;
			    	break;
			}
			if (mPreferences.getBoolean(PREFERENCE_KEY_SOUND_SEND, true)) {
				MediaPlayer mPlayer = MediaPlayer.create(TweetingsService.this, R.raw.whoosh);
				if (isMuted == true) {
					mPlayer.setVolume(0,0);
				}
				mPlayer.start();
			}
			//if (result != null) {
				Toast.makeText(TweetingsService.this, R.string.schedule_success, Toast.LENGTH_SHORT).show();
			//}
			//else {
				//Toast.makeText(TweetingsService.this, R.string.schedule_error, Toast.LENGTH_SHORT).show();
			//}
				
			
			super.onPostExecute(result);
		}
	}
	
	class BufferStatusTask extends ManagedAsyncTask<Void, Void, String> {

		private final Validator validator = new Validator();

		private long[] account_ids;
		private String content;
		private String buffer_access_token;
		
		private final AudioManager audioManager = (AudioManager) getSystemService(Activity.AUDIO_SERVICE);
		
		public BufferStatusTask(final long[] account_ids, final String content) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_ids = account_ids != null ? account_ids : new long[0];
			this.content = content;
			this.buffer_access_token = mPreferences.getString(PREFERENCE_KEY_BUFFERAPP_ACCESS_TOKEN, null);
		}

		@Override
		protected String doInBackground(final Void... params) {
			boolean oneOrMoreFailed = true;
			if (account_ids.length == 0) return null;
			if (buffer_access_token == null) return null;
			final String unshortened_content = content;
			final StatusUpdate status = new StatusUpdate(unshortened_content);
			
			String accountJson = null;
			
			HttpClient client = HttpClientFactory.getThreadSafeClient();
			try {
				HttpResponse response = client.execute(new HttpGet("https://api.bufferapp.com/1/profiles.json?access_token=" + buffer_access_token));
			    
			    BufferedReader reader = new BufferedReader(new InputStreamReader(
				response.getEntity().getContent(), "UTF-8"));
				
				String sResponse;
				StringBuilder s = new StringBuilder();
	
				while ((sResponse = reader.readLine()) != null) {
					s = s.append(sResponse);
				}
				accountJson = s.toString();
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalStateException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (accountJson != null) {
				try {
					JSONArray jArray = new JSONArray(accountJson);
					for (int i = 0 ; i < jArray.length(); i++) {
						JSONObject jObject = jArray.getJSONObject(i);
						final String service = jObject.getString("service");
						final String service_user = jObject.getString("service_username").toLowerCase();
						final String profile_id = jObject.getString("id");
						if (service != null && service.equals("twitter")) {
							for (final long account_id : account_ids) {
								final String screen_name = getAccountUsername(TweetingsService.this, account_id).toLowerCase();
								if (screen_name.equals(service_user)) {
									// We have a matching user!
									try {
										HttpPost post = new HttpPost("https://api.bufferapp.com/1/updates/create.json");
										List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
					    				nameValuePairs.add(new BasicNameValuePair("text", unshortened_content));
					    				nameValuePairs.add(new BasicNameValuePair("access_token", buffer_access_token));
					    				nameValuePairs.add(new BasicNameValuePair("profile_ids[]", profile_id));
					    				post.setEntity(new UrlEncodedFormEntity(nameValuePairs));
					    				HttpResponse response = client.execute(post);
					    				BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
					    				String line = "";
					    				String outputContent = "";
					    				
					    				while ((line = rd.readLine()) != null) {
					    					outputContent += line;
					    					//Log.e("HttpResponse", line);
					    				}
					    				
					    				if (!outputContent.equals("")) {
					    					JSONObject bufferResponse = new JSONObject(outputContent);
					    					boolean didSucceed = bufferResponse.getBoolean("success");
					    					if (didSucceed == true) {
					    						oneOrMoreFailed = false;
					    					}
					    					else {
					    						oneOrMoreFailed = true;
					    					}
					    				}
					    			} catch (IOException e) {
					    				e.printStackTrace();
					    			}
				    				
								}
							}
						}
					}
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
			else {
				return null;
			}
			if (oneOrMoreFailed == false) {
				boolean isMuted = false;
				switch(audioManager.getRingerMode()) {
					case AudioManager.RINGER_MODE_NORMAL:
				        isMuted = false;
				        break;
				    case AudioManager.RINGER_MODE_SILENT:
				        isMuted = true;
				        break;
				    case AudioManager.RINGER_MODE_VIBRATE:
				    	isMuted = true;
				    	break;
				}
				if (mPreferences.getBoolean(PREFERENCE_KEY_SOUND_SEND, true)) {
					MediaPlayer mPlayer = MediaPlayer.create(TweetingsService.this, R.raw.whoosh);
					if (isMuted == true) {
						mPlayer.setVolume(0,0);
					}
					mPlayer.start();
				}
				return "";
			}
			
			return null;
		}

		@Override
		protected void onPostExecute(final String result) {
	
			if (result == null) {
				Toast.makeText(TweetingsService.this, R.string.buffer_failed, Toast.LENGTH_SHORT).show();
			}
			else {
				boolean isMuted = false;
				switch(audioManager.getRingerMode()) {
					case AudioManager.RINGER_MODE_NORMAL:
				        isMuted = false;
				        break;
				    case AudioManager.RINGER_MODE_SILENT:
				        isMuted = true;
				        break;
				    case AudioManager.RINGER_MODE_VIBRATE:
				    	isMuted = true;
				    	break;
				}
				MediaPlayer mPlayer = MediaPlayer.create(TweetingsService.this, R.raw.whoosh);
				if (isMuted == true) {
					mPlayer.setVolume(0,0);
				}
				mPlayer.start();
				Toast.makeText(TweetingsService.this, R.string.tweet_buffered, Toast.LENGTH_SHORT).show();
			}
			super.onPostExecute(result);
		}
	}

	class UpdateStatusTask extends ManagedAsyncTask<Void, Void, List<SingleResponse<twitter4j.Status>>> {

		private final ImageUploaderInterface uploader;
		private final TweetShortenerInterface shortener;
		private final Validator validator = new Validator();

		private long[] account_ids;
		private String content;
		private Location location;
		private Uri image_uri;
		private Timer uploadTimer;
		private long in_reply_to;
		private boolean use_uploader, use_shortener, delete_image, is_possibly_sensitive;
		private Notification notification;
		private NotificationManager notificationManager;
		
		private final AudioManager audioManager = (AudioManager) getSystemService(Activity.AUDIO_SERVICE);

		public UpdateStatusTask(final long[] account_ids, final String content, final Location location, final Uri image_uri, final long in_reply_to,
				final boolean is_possibly_sensitive, final boolean delete_image) {
			super(TweetingsService.this, mAsyncTaskManager);
			final String uploader_component = mPreferences.getString(PREFERENCE_KEY_IMAGE_UPLOADER, null);
			final String shortener_component = mPreferences.getString(PREFERENCE_KEY_TWEET_SHORTENER, null);
			use_uploader = !isNullOrEmpty(uploader_component);
			uploader = use_uploader ? ImageUploaderInterface.getInstance(getApplication(), uploader_component) : null;
			use_shortener = !isNullOrEmpty(shortener_component);
			shortener = use_shortener ? TweetShortenerInterface.getInstance(getApplication(), shortener_component)
					: null;
			this.account_ids = account_ids != null ? account_ids : new long[0];
			this.content = content;
			this.location = location;
			this.image_uri = image_uri;
			this.in_reply_to = in_reply_to;
			this.delete_image = delete_image;
			this.is_possibly_sensitive = is_possibly_sensitive;
		}

		@Override
		protected List<SingleResponse<twitter4j.Status>> doInBackground(final Void... params) {

			final List<SingleResponse<twitter4j.Status>> result = new ArrayList<SingleResponse<twitter4j.Status>>();
			
			if (account_ids.length == 0) return result;

			
			try {
				if (use_shortener && shortener == null) throw new TweetShortenerNotFoundException();

				final String image_path = getImagePathFromUri(TweetingsService.this, image_uri);
				final File image_file = image_path != null ? new File(image_path) : null;
				if (uploader != null) {
					uploader.waitForService();
				}
				
				final String unshortened_content = content;
				final StatusUpdate status = new StatusUpdate(unshortened_content);
						
				if (location != null) {
					status.setLocation(new GeoLocation(location.getLatitude(), location.getLongitude()));
				}
				if (in_reply_to > 0) {
					status.setInReplyToStatusId(in_reply_to);
				}
				if (!use_uploader && image_file != null && image_file.exists()) {
					status.setMedia(image_file);
				}
				status.setPossiblySensitive(is_possibly_sensitive);
				
				for (final long account_id : account_ids) {
					final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
					
					if (!use_uploader && image_file != null && image_file.exists()) {
						CreateNotification();
						uploadTimer = new Timer();
			    		uploadTimer.schedule(new TimerTask() {			
			    			@Override
			    			public void run() {
			    				UploadProgressMethod(twitter);
			    			}
			    			
			    		}, 0, 50);
					}
					
					if (twitter != null) {
						try {
							result.add(new SingleResponse<twitter4j.Status>(account_id, twitter.updateStatus(status),
									null));
						} catch (final TwitterException e) {
							e.printStackTrace();
							result.add(new SingleResponse<twitter4j.Status>(account_id, null, e));
						}
					}
				}
			} catch (final UpdateStatusException e) {
				for (final long account_id : account_ids) {
					result.add(new SingleResponse<twitter4j.Status>(account_id, null, e));
				}
			}
			return result;
		}
		
		protected void CreateNotification() {
			// configure the notification
			Intent intent = new Intent(TweetingsService.this, HomeActivity.class);
	        final PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
			
			notification = new Notification(R.drawable.logo, getString(R.string.uploading_media), System
			                .currentTimeMillis());
			notification.flags = notification.flags | Notification.FLAG_ONGOING_EVENT;
			notification.contentView = new RemoteViews(getApplicationContext().getPackageName(),
			                                              R.layout.upload_progress);
			notification.contentIntent = pendingIntent;
			notification.contentView.setImageViewResource(R.id.status_icon, R.drawable.logo);
			notification.contentView.setTextViewText(R.id.status_text, getString(R.string.uploading_media));
			notification.contentView.setProgressBar(R.id.status_progress, 100, 0, false);
			notificationManager = (NotificationManager) getApplicationContext()
					.getSystemService(Context.NOTIFICATION_SERVICE);

	        notificationManager.notify(NOTIFICATION_ID_UPLOAD_PROGRESS, notification);
		}
		
		protected void UploadProgressMethod(final Twitter twitter) {
			if (twitter.getHttpClientWrapper() != null) {
        		HttpClientWrapper http = twitter.getHttpClientWrapper();
        		float progress = (float)http.getRequestStatus()/(float)http.getRequestLength();
        		float longPc = progress * 100;
        		int percentage = Math.round(longPc);
        		if (notification != null && notificationManager != null) {
        			notification.contentView.setProgressBar(R.id.status_progress, 100, percentage, false);

        			// inform the progress bar of updates in progress
        			notificationManager.notify(NOTIFICATION_ID_UPLOAD_PROGRESS, notification);
        		}
			};
		}

		@Override
		protected void onPostExecute(final List<SingleResponse<twitter4j.Status>> result) {

			if (uploadTimer != null) {
				uploadTimer.cancel();
			}
			NotificationManager notificationManager = (NotificationManager) getApplicationContext()
					.getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.cancelAll();
			
			boolean succeed = true;
			Exception exception = null;
			final List<Long> failed_account_ids = new ArrayList<Long>();

			for (final SingleResponse<twitter4j.Status> response : result) {
				if (response.data == null) {
					succeed = false;
					failed_account_ids.add(response.account_id);
					if (exception == null) {
						exception = response.exception;
					}
				}
			}
			if (succeed) {
				boolean isMuted = false;
				switch(audioManager.getRingerMode()) {
					case AudioManager.RINGER_MODE_NORMAL:
				        isMuted = false;
				        break;
				    case AudioManager.RINGER_MODE_SILENT:
				        isMuted = true;
				        break;
				    case AudioManager.RINGER_MODE_VIBRATE:
				    	isMuted = true;
				    	break;
				}
				if (mPreferences.getBoolean(PREFERENCE_KEY_SOUND_SEND, true)) {
					MediaPlayer mPlayer = MediaPlayer.create(TweetingsService.this, R.raw.whoosh);
					if (isMuted == true) {
						mPlayer.setVolume(0,0);
					}
					mPlayer.start();
				}
				Toast.makeText(TweetingsService.this, R.string.send_success, Toast.LENGTH_SHORT).show();
				if (image_uri != null && delete_image) {
					final String path = getImagePathFromUri(TweetingsService.this, image_uri);
					if (path != null) {
						new File(path).delete();
					}
				}
			} else {
				showErrorToast(R.string.send, exception, true);
				
				final ContentValues values = new ContentValues();
				values.put(Drafts.ACCOUNT_IDS, ListUtils.toString(failed_account_ids, ';', false));
				values.put(Drafts.IN_REPLY_TO_STATUS_ID, in_reply_to);
				values.put(Drafts.TEXT, content);
				if (mPreferences.getBoolean(PREFERENCE_KEY_AUTOMATIC_RETRY, false)) {
					values.put(Drafts.IS_QUEUED, true);
				}
				else {
					values.put(Drafts.IS_QUEUED, false);
				}
				if (image_uri != null) {
					values.put(Drafts.IS_IMAGE_ATTACHED, !delete_image);
					values.put(Drafts.IS_PHOTO_ATTACHED, delete_image);
					values.put(Drafts.IMAGE_URI, parseString(image_uri));
				}

				mResolver.insert(Drafts.CONTENT_URI, values);
				final String title = getString(R.string.tweet_not_sent);
				final String message = getString(R.string.tweet_not_sent_summary);
				final Intent intent = new Intent(INTENT_ACTION_DRAFTS);
				final Notification notification = buildNotification(title, message, R.drawable.ic_stat_tweet, intent, null);
				mNotificationManager.notify(NOTIFICATION_ID_DRAFTS, notification);
			
			}
			super.onPostExecute(result);
			
			// Add sound in here
			
			if (mPreferences.getBoolean(PREFERENCE_KEY_REFRESH_AFTER_TWEET, true)) {
				final long[] activated_ids = getActivatedAccountIds(TweetingsService.this);
				getHomeTimeline(activated_ids, null);
				if (mPreferences.getBoolean(PREFERENCE_KEY_HOME_REFRESH_MENTIONS, false)) {
					getMentions(account_ids, null);
				}
				if (mPreferences.getBoolean(PREFERENCE_KEY_HOME_REFRESH_DIRECT_MESSAGES, false)) {
					getReceivedDirectMessages(account_ids, null);
					//getSentDirectMessages(account_ids, null);
				}
			}
		}

		class ImageUploaderNotFoundException extends UpdateStatusException {
			private static final long serialVersionUID = 1041685850011544106L;

			public ImageUploaderNotFoundException() {
				super(R.string.error_message_image_uploader_not_found);
			}
		}

		class ImageUploadException extends UpdateStatusException {
			private static final long serialVersionUID = 8596614696393917525L;

			public ImageUploadException() {
				super(R.string.error_message_image_upload_failed);
			}
		}

		class StatusTooLongException extends UpdateStatusException {
			private static final long serialVersionUID = -6469920130856384219L;

			public StatusTooLongException() {
				super(R.string.error_message_status_too_long);
			}
		}

		class TweetShortenerNotFoundException extends UpdateStatusException {
			private static final long serialVersionUID = -7262474256595304566L;

			public TweetShortenerNotFoundException() {
				super(R.string.error_message_tweet_shortener_not_found);
			}
		}

		class TweetShortenException extends UpdateStatusException {
			private static final long serialVersionUID = 3075877185536740034L;

			public TweetShortenException() {
				super(R.string.error_message_tweet_shorten_failed);
			}
		}

		class UpdateStatusException extends Exception {
			private static final long serialVersionUID = -1267218921727097910L;

			public UpdateStatusException(int message) {
				super(getString(message));
			}
		}
	}

	class UpdateUserListProfileTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id;
		private final int list_id;
		private final boolean is_public;
		private final String name, description;

		public UpdateUserListProfileTask(long account_id, int list_id, boolean is_public, String name,
				String description) {
			super(TweetingsService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.name = name;
			this.list_id = list_id;
			this.is_public = is_public;
			this.description = description;
		}

		@Override
		protected SingleResponse<UserList> doInBackground(Void... params) {

			final Twitter twitter = getTwitterInstance(TweetingsService.this, account_id, false);
			if (twitter != null) {
				try {
					final UserList user = twitter.updateUserList(list_id, name, is_public, description);
					return new SingleResponse<UserList>(account_id, user, null);
				} catch (final TwitterException e) {
					return new SingleResponse<UserList>(account_id, null, e);
				}
			}
			return new SingleResponse<UserList>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(SingleResponse<UserList> result) {
			final Intent intent = new Intent(BROADCAST_USER_LIST_DETAILS_UPDATED);
			intent.putExtra(INTENT_KEY_LIST_ID, list_id);
			if (result != null && result.data != null && result.data.getId() > 0) {
				Toast.makeText(TweetingsService.this, R.string.profile_update_success, Toast.LENGTH_SHORT).show();
				intent.putExtra(INTENT_KEY_SUCCEED, true);
			} else {
				showErrorToast(R.string.update, result.exception, true);
			}
			sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

}
