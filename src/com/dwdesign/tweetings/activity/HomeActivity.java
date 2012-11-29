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

import static com.dwdesign.tweetings.util.Utils.cleanDatabasesByItemLimit;
import static com.dwdesign.tweetings.util.Utils.getNewestMessageIdsFromDatabase;
import static com.dwdesign.tweetings.util.Utils.getNewestStatusIdsFromDatabase;
import static com.dwdesign.tweetings.util.Utils.getAccountIds;
import static com.dwdesign.tweetings.util.Utils.getActivatedAccountIds;
import static com.dwdesign.tweetings.util.Utils.getTabs;
import static com.dwdesign.tweetings.util.Utils.openDirectMessagesConversation;
import static com.dwdesign.tweetings.util.Utils.haveNetworkConnection;
import static com.dwdesign.tweetings.service.TweetingsService.ListResponse;

import twitter4j.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.dwdesign.actionbarcompat.ActionBar;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.adapter.TabsAdapter;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.fragment.AccountsFragment;
import com.dwdesign.tweetings.fragment.DirectMessagesFragment;
import com.dwdesign.tweetings.fragment.HomeTimelineFragment;
import com.dwdesign.tweetings.fragment.MentionsFragment;
import com.dwdesign.tweetings.model.DraftItem;
import com.dwdesign.tweetings.model.TabSpec;
import com.dwdesign.tweetings.provider.TweetStore;
import com.dwdesign.tweetings.provider.TweetStore.Accounts;
import com.dwdesign.tweetings.provider.TweetStore.DirectMessages.Inbox;
import com.dwdesign.tweetings.provider.TweetStore.DirectMessages;
import com.dwdesign.tweetings.provider.TweetStore.Drafts;
import com.dwdesign.tweetings.provider.TweetStore.Mentions;
import com.dwdesign.tweetings.provider.TweetStore.Statuses;
import com.dwdesign.tweetings.service.TweetingsService;
import com.dwdesign.tweetings.service.TweetingsService.StoreHomeTimelineTask;
import com.dwdesign.tweetings.util.ArrayUtils;
import com.dwdesign.tweetings.util.AsyncTaskManager;
import com.dwdesign.tweetings.util.ConnectivityReceiver;
import com.dwdesign.tweetings.util.ServiceInterface;
import com.dwdesign.tweetings.util.SetHomeButtonEnabledAccessor;
import com.dwdesign.tweetings.view.ExtendedViewPager;
import com.dwdesign.tweetings.view.TabPageIndicator;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Notification.Builder;
import android.app.backup.BackupManager;
import android.app.backup.RestoreObserver;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManagerTrojan;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

@TargetApi(16)
public class HomeActivity extends MultiSelectActivity implements OnClickListener, OnPageChangeListener {

	private SharedPreferences mPreferences;
	private ServiceInterface mService;
	private TweetingsApplication mApplication;
	
	private ActionBar mActionBar;
	private TabsAdapter mAdapter;
	
	private ExtendedViewPager mViewPager;
	private ImageButton mComposeButton;
	private TabPageIndicator mIndicator;
	private ProgressBar mProgress;
	
	private Cursor mCursor;
	
	private boolean mProgressBarIndeterminateVisible = false;
	
	private boolean mIsNavigateToDefaultAccount = false;
	private boolean mDisplayAppIcon;
	private boolean mPushNotifications = false;
	
	public static final int TAB_POSITION_HOME = 0;
	
	public static final int TAB_POSITION_MENTIONS = 1;
	public static final int TAB_POSITION_MESSAGES = 2;
	private final ArrayList<TabSpec> mCustomTabs = new ArrayList<TabSpec>();
	
	protected boolean refresh_on_start;
	protected Bundle bundle;
	
	protected int initial_tab;
	
	protected BackupManager backupManager;
	
	protected TwitterStream twitterStream;
	protected static long[] friendsList;
	
	private boolean mShowHomeTab, mShowMentionsTab, mShowMessagesTab, mShowAccountsTab;

	public static String BROADCAST_STATUS_RECEIVED = "com.dwdesign.tweetings.broadcast.STATUS_RECEIVED";

	private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			
			final String action = intent.getAction();
			if (BROADCAST_REFRESHSTATE_CHANGED.equals(action)) {
				setSupportProgressBarIndeterminateVisibility(mProgressBarIndeterminateVisible);
			} else if (WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION.equals(action)) {
				if (intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {
					// We have a wifi connection
					connectToStream();
				}
				else {
					// Lost Wifi connection
					closeStream();
				}
			} else if (BROADCAST_NETWORK_STATE_CHANGED.equals(action)) {
				if (haveNetworkConnection(HomeActivity.this)) {
					checkAndSendFailedTweets();
				}
			}
		}

	};

	public boolean checkDefaultAccountSet() {
		boolean result = true;
		final long[] activated_ids = getActivatedAccountIds(this);
		final long default_account_id = mPreferences.getLong(PREFERENCE_KEY_DEFAULT_ACCOUNT_ID, -1);
		if (default_account_id == -1 || !ArrayUtils.contains(activated_ids, default_account_id)) {
			if (activated_ids.length == 1) {
				mPreferences.edit().putLong(PREFERENCE_KEY_DEFAULT_ACCOUNT_ID, activated_ids[0]).commit();
				mIndicator.setPagingEnabled(true);
				mIsNavigateToDefaultAccount = false;
			} else if (activated_ids.length > 1) {
				final int count = mAdapter.getCount();
				if (count > 0) {
					mViewPager.setCurrentItem(count - 1, false);
				}
				mIndicator.setPagingEnabled(false);
				if (!mIsNavigateToDefaultAccount) {
					Toast.makeText(this, R.string.set_default_account_hint, Toast.LENGTH_LONG).show();
				}
				mIsNavigateToDefaultAccount = true;
				result = false;
			}
		} else {
			mIndicator.setPagingEnabled(true);
			mIsNavigateToDefaultAccount = false;
		}
		return result;
	}
	
	@Override
	public void onBackPressed() {
		final FragmentManager fm = getSupportFragmentManager();
		if (fm.getBackStackEntryCount() == 0 && 
				!mPreferences.getBoolean(PREFERENCE_KEY_STOP_SERVICE_AFTER_CLOSED, false) &&
				mPreferences.getBoolean(PREFERENCE_KEY_KEEP_IN_BACKGROUND, false)) {
			moveTaskToBack(true);
			return;
		}
		super.onBackPressed();
	}
	
	@Override
	public void onBackStackChanged() {
		super.onBackStackChanged();
		if (!isDualPaneMode()) return;
		final FragmentManager fm = getSupportFragmentManager();
		final Fragment left_pane_fragment = fm.findFragmentById(PANE_LEFT);
		final boolean left_pane_used = left_pane_fragment != null && left_pane_fragment.isAdded();
		setPagingEnabled(!left_pane_used);
		final int count = fm.getBackStackEntryCount();
		if (count == 0) {
			bringLeftPaneToFront();
		}
	}

	@Override
	public void onClick(final View v) {
		switch (v.getId()) {
			case R.id.compose:
			case R.id.button_compose:
				if (mViewPager == null) return;
				final int position = mViewPager.getCurrentItem();
				if (position == mAdapter.getCount() - 1) {
					startActivity(new Intent(INTENT_ACTION_TWITTER_LOGIN));
				} else {
					switch (position) {
						case TAB_POSITION_MESSAGES:
							openDirectMessagesConversation(this, -1, -1);
							break;
						default:
							startActivity(new Intent(INTENT_ACTION_COMPOSE));
					}
				}
				break;
		}
	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();
		mViewPager = (ExtendedViewPager) findViewById(R.id.pager);
		mComposeButton = (ImageButton) findViewById(R.id.button_compose);
	}
	
	public void refreshOnResume() {
		mService.refreshAll();
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		mApplication = getTweetingsApplication();
		mService = mApplication.getServiceInterface();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		super.onCreate(savedInstanceState);
		sendBroadcast(new Intent(BROADCAST_APPLICATION_LAUNCHED));
		final Resources res = getResources();
		mDisplayAppIcon = res.getBoolean(R.bool.home_display_icon);
		final long[] account_ids = getAccountIds(this);
		if (account_ids.length <= 0) {
			startActivity(new Intent(INTENT_ACTION_TWITTER_LOGIN));
			finish();
			return;
		}
		refresh_on_start = mPreferences.getBoolean(PREFERENCE_KEY_REFRESH_ON_START, false);
		bundle = getIntent().getExtras();
		initial_tab = -1;
		mActionBar = getSupportActionBar();
		mActionBar.setCustomView(R.layout.base_tabs);
		mActionBar.setDisplayShowTitleEnabled(false);
		mActionBar.setDisplayShowCustomEnabled(true);
		mActionBar.setDisplayShowHomeEnabled(mDisplayAppIcon);
		if (mDisplayAppIcon && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			SetHomeButtonEnabledAccessor.setHomeButtonEnabled(this, true);
		}
		final View view = mActionBar.getCustomView();
		
		mProgress = (ProgressBar) view.findViewById(android.R.id.progress);
		mIndicator = (TabPageIndicator) view.findViewById(android.R.id.tabs);
		boolean tab_display_label = res.getBoolean(R.bool.tab_display_label);
		boolean tab_hide_label_always = mPreferences.getBoolean(PREFERENCE_KEY_HIDE_TAB_LABEL, false);
		boolean tab_display_label_always = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_TAB_LABEL, false);
		if (tab_display_label_always == true) {
			tab_display_label = true;
		} else if (tab_hide_label_always == true) {
			tab_display_label = false;
		}
		mAdapter = new TabsAdapter(this, getSupportFragmentManager(), mIndicator);
		mAdapter.setDisplayLabel(tab_display_label);
		mShowHomeTab = mPreferences.getBoolean(PREFERENCE_KEY_SHOW_HOME_TAB, true);
	 	mShowMentionsTab = mPreferences.getBoolean(PREFERENCE_KEY_SHOW_MENTIONS_TAB, true);
	 	mShowMessagesTab = mPreferences.getBoolean(PREFERENCE_KEY_SHOW_MESSAGES_TAB, true);
	 	mShowAccountsTab = mPreferences.getBoolean(PREFERENCE_KEY_SHOW_ACCOUNTS_TAB, true);
		initTabs(getTabs(this));
		mViewPager.setAdapter(mAdapter);
		mViewPager.setOffscreenPageLimit(3);
		mIndicator.setViewPager(mViewPager);
		mIndicator.setOnPageChangeListener(this);
		getSupportFragmentManager().addOnBackStackChangedListener(this);

		final boolean remember_position = mPreferences.getBoolean(PREFERENCE_KEY_REMEMBER_POSITION, true);
		final long[] activated_ids = getActivatedAccountIds(this);
		if (activated_ids.length <= 0) {
			startActivityForResult(new Intent(INTENT_ACTION_SELECT_ACCOUNT), REQUEST_SELECT_ACCOUNT);
		} else if (checkDefaultAccountSet() && (remember_position || initial_tab >= 0)) {
			final int position = initial_tab >= 0 ? initial_tab : mPreferences.getInt(
					PREFERENCE_KEY_SAVED_TAB_POSITION, TAB_POSITION_HOME);
			if (position >= 0 || position < mViewPager.getChildCount()) {
				mViewPager.setCurrentItem(position);
			}
		}
		if (refresh_on_start && savedInstanceState == null) {
			mService.getHomeTimelineWithSinceId(activated_ids, null, getNewestStatusIdsFromDatabase(this, Statuses.CONTENT_URI));
			mService.getMentionsWithSinceId(account_ids, null, getNewestStatusIdsFromDatabase(this, Mentions.CONTENT_URI));
			mService.getReceivedDirectMessagesWithSinceId(activated_ids, null, getNewestMessageIdsFromDatabase(this, Inbox.CONTENT_URI));
			//mService.getReceivedDirectMessages(activated_ids, null);
			
			//mService.getSentDirectMessages(account_ids, null);
		}
		
		if (!mPreferences.getBoolean(PREFERENCE_KEY_FOLLOW_DIALOG, false)) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(getString(R.string.follow_dialog_title));
			builder.setMessage(getString(R.string.follow_dialog));
			builder.setCancelable(true);
			builder.setPositiveButton(getString(R.string.follow),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						mPreferences.edit().putBoolean(PREFERENCE_KEY_FOLLOW_DIALOG, true).commit();
						final long[] activated_ids = getActivatedAccountIds(mApplication);
						for (final long account_id : activated_ids) {
							mService.createFriendship(account_id, 41573166);
						}
					}
				});
			builder.setNegativeButton(getString(R.string.cancel),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						mPreferences.edit().putBoolean(PREFERENCE_KEY_FOLLOW_DIALOG, true).commit();
					}
				});
			//
			AlertDialog alert = builder.create();
			alert.show();
		}
		
		mPushNotifications = mPreferences.getBoolean(PREFERENCE_KEY_PUSH_NOTIFICATIONS, false);
		backupManager = new BackupManager(getApplicationContext());
		
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.menu_home, menu);
		return super.onCreateOptionsMenu(menu);
	}

		@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case MENU_HOME: {
				final FragmentManager fm = getSupportFragmentManager();
				if (isDualPaneMode() && !FragmentManagerTrojan.isStateSaved(fm)) {
					final int count = fm.getBackStackEntryCount();
					for (int i = 0; i < count; i++) {
						fm.popBackStackImmediate();
					}
					setSupportProgressBarIndeterminateVisibility(false);
				}
				break;
			}
			case MENU_COMPOSE: {
				if (mComposeButton != null) {
					onClick(mComposeButton);
				}
				break;
			}
			case MENU_SEARCH: {
				onSearchRequested();
				break;
			}
			case MENU_SELECT_ACCOUNT: {
				startActivityForResult(new Intent(INTENT_ACTION_SELECT_ACCOUNT), REQUEST_SELECT_ACCOUNT);
				break;
			}
			case MENU_SETTINGS: {
				startActivity(new Intent(INTENT_ACTION_SETTINGS));
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels) {

	}

	@Override
	public void onPageScrollStateChanged(final int state) {

	}

	@Override
	public void onPageSelected(final int position) {
		switch (position) {
			case TAB_POSITION_HOME: {
				mService.clearNotification(NOTIFICATION_ID_HOME_TIMELINE);
				break;
			}
			case TAB_POSITION_MENTIONS: {
				mService.clearNotification(NOTIFICATION_ID_MENTIONS);
				break;
			}
			case TAB_POSITION_MESSAGES: {
				mService.clearNotification(NOTIFICATION_ID_DIRECT_MESSAGES);
				break;
			}
		}
		invalidateSupportOptionsMenu();
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu) {
		final boolean bottom_actions = mPreferences.getBoolean(PREFERENCE_KEY_COMPOSE_BUTTON, false);
		final boolean leftside_compose_button = mPreferences.getBoolean(PREFERENCE_KEY_LEFTSIDE_COMPOSE_BUTTON, false);
		int icon = R.drawable.ic_menu_tweet, title = R.string.compose;
		if (mViewPager != null && mAdapter != null) {
			final int position = mViewPager.getCurrentItem();

			if (position == mAdapter.getCount() - 1) {
				icon = R.drawable.ic_menu_add;
				title = R.string.add_account;
			} else {
				title = R.string.compose;
				switch (position) {
					case TAB_POSITION_MESSAGES:
						icon = R.drawable.ic_menu_compose;
						break;
					default:
						icon = R.drawable.ic_menu_tweet;
				}
			}
		}
		final MenuItem composeItem = menu.findItem(MENU_COMPOSE);
		if (composeItem != null) {
			composeItem.setIcon(icon);
			composeItem.setTitle(title);
			composeItem.setVisible(!bottom_actions);
		}
		if (mComposeButton != null) {
			mComposeButton.setImageResource(icon);
			mComposeButton.setVisibility(bottom_actions ? View.VISIBLE : View.GONE);
			if (bottom_actions) {
				final FrameLayout.LayoutParams compose_lp = (FrameLayout.LayoutParams) mComposeButton.getLayoutParams();
				compose_lp.gravity = Gravity.BOTTOM | (leftside_compose_button ? Gravity.LEFT : Gravity.RIGHT);
				mComposeButton.setLayoutParams(compose_lp);
			}
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onResume() {
		super.onResume();
		invalidateSupportOptionsMenu();
		connectToStream();
		
		NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancelAll();
		if (isPushChanged() && mPushNotifications) {
			mApplication.registerPush();
		}
		
		
	}
	
	private boolean isPushChanged() {
		final boolean push_notifications = mPreferences.getBoolean(PREFERENCE_KEY_PUSH_NOTIFICATIONS, false);
		return mPushNotifications != push_notifications;
	}
	
	public void checkAndSendFailedTweets() {
		if (mPreferences.getBoolean(PREFERENCE_KEY_AUTOMATIC_RETRY, false)) {
			ContentResolver mResolver = getContentResolver();
			
			final String[] cols = Drafts.COLUMNS;
			final Cursor cur = getContentResolver().query(Drafts.CONTENT_URI, cols, null, null, null);
			if (cur != null) {
				cur.moveToFirst();
				int i = 0;
				while (!cur.isAfterLast()) {
					final DraftItem draft = new DraftItem(cur, i);
					
					if (draft.is_queued) {
						final Uri image_uri = draft.media_uri == null ? null : Uri.parse(draft.media_uri);
						
						mService.updateStatus(draft.account_ids, draft.text, null, image_uri,
								draft.in_reply_to_status_id, draft.is_photo_attached && !draft.is_image_attached);
						
						mResolver.delete(Drafts.CONTENT_URI, Drafts._ID + " = " + draft._id, null);
					}
					
					i++;
					cur.moveToNext();
				}
				cur.close();
			}
		}
	}
	
	public void connectToStream() {
		if (mPreferences.getBoolean(PREFERENCE_KEY_STREAMING_ENABLED, false) == true) {
			if (mService != null && twitterStream != null) {
				try {
					// user() method internally creates a thread which manipulates TwitterStream and calls these adequate listener methods continuously.
					ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
					if (cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI) != null) {
						if (cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
							twitterStream.user();
						}
					}
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public void closeStream() {
		if (mPreferences.getBoolean(PREFERENCE_KEY_STREAMING_ENABLED, false) == true) {
			if (mService != null && twitterStream != null) {
				try {
			        twitterStream.shutdown();
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		closeStream();
	}

	@Override
	public void setSupportProgressBarIndeterminateVisibility(final boolean visible) {
		mProgressBarIndeterminateVisible = visible;
		mProgress.setVisibility(visible || mService.hasActivatedTask() ? View.VISIBLE : View.INVISIBLE);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
	 	 final ContentResolver resolver = getContentResolver();
	 	 ContentValues values;
	 	 switch (requestCode) {
	 	   case REQUEST_SELECT_ACCOUNT: {
	 	     if (resultCode == RESULT_OK) {
	 	       if (intent == null || intent.getExtras() == null) {
	 	         break;
	 	       }
	 	       final Bundle bundle = intent.getExtras();
	 	       if (bundle == null) {
	 	         break;
	 	       }
	 	       final long[] account_ids = bundle.getLongArray(INTENT_KEY_IDS);
	 	       if (account_ids != null) {
	 	         values = new ContentValues();
	 	         values.put(Accounts.IS_ACTIVATED, 0);
	 	         resolver.update(Accounts.CONTENT_URI, values, null, null);
	 	         values = new ContentValues();
	 	         values.put(Accounts.IS_ACTIVATED, 1);
	 	         for (final long account_id : account_ids) {
	 	           final String where = Accounts.USER_ID + " = " + account_id;
	 	           resolver.update(Accounts.CONTENT_URI, values, where, null);
	 	         }
	 	       }
	 	       checkDefaultAccountSet();
	 	     } else if (resultCode == RESULT_CANCELED) {
	 	       if (getActivatedAccountIds(this).length <= 0) {
	 	         finish();
	 	       } else {
	 	         checkDefaultAccountSet();
	 	       }
	 	     }
	 	     break;
	 	   }
	 	 }
	 	 super.onActivityResult(requestCode, resultCode, intent);	
	   }
	
	protected void onDefaultAccountSet() {
		mIsNavigateToDefaultAccount = false;
	}
	
	@Override
	protected void onDestroy() {
		// Delete unused items in databases.
		cleanDatabasesByItemLimit(this);
		sendBroadcast(new Intent(BROADCAST_APPLICATION_QUITTED));
		super.onDestroy();
		if (mPreferences.getBoolean(PREFERENCE_KEY_STOP_SERVICE_AFTER_CLOSED, false)) {
			// What the f**k are you think about? Stop service causes tweetings
			// slow and unstable!
			// Well, all right... If you still want to enable this option, I
			// take no responsibility for any problems occurred.
			mService.shutdownService();
		}
	}
	
	@Override
	protected void onNewIntent(final Intent intent) {
		final Bundle bundle = intent.getExtras();
		if (bundle != null) {
			final long[] refreshed_ids = bundle.getLongArray(INTENT_KEY_IDS);
			final int initial_tab = bundle.getInt(INTENT_KEY_INITIAL_TAB, -1);
			if (initial_tab != -1 && mViewPager != null) {
				switch (initial_tab) {
					case TAB_POSITION_HOME: {
						mService.clearNotification(NOTIFICATION_ID_HOME_TIMELINE);
					 	break;
					}
					case TAB_POSITION_MENTIONS: {
						mService.clearNotification(NOTIFICATION_ID_MENTIONS);
					 	break;
					}
					case TAB_POSITION_MESSAGES: {
						mService.clearNotification(NOTIFICATION_ID_DIRECT_MESSAGES);
					 	break;
					}
				}
				if (initial_tab >= 0 || initial_tab < mViewPager.getChildCount()) {
					mViewPager.setCurrentItem(initial_tab);
				}
			}
			if (refreshed_ids != null) {
				mService.getHomeTimelineWithSinceId(refreshed_ids, null, getNewestStatusIdsFromDatabase(this, Statuses.CONTENT_URI));
				mService.getMentionsWithSinceId(refreshed_ids, null, getNewestStatusIdsFromDatabase(this, Mentions.CONTENT_URI));
				mService.getReceivedDirectMessagesWithSinceId(refreshed_ids, null, getNewestMessageIdsFromDatabase(this, Inbox.CONTENT_URI));
				//mService.getReceivedDirectMessages(refreshed_ids, null);
			}
			
		}
		super.onNewIntent(intent);
	}

	@Override
	protected void onStart() {
		super.onStart();
		setSupportProgressBarIndeterminateVisibility(mProgressBarIndeterminateVisible);
		final IntentFilter filter = new IntentFilter(BROADCAST_REFRESHSTATE_CHANGED);
		filter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		filter.addAction(BROADCAST_NETWORK_STATE_CHANGED);
		registerReceiver(mStateReceiver, filter);
		final boolean show_home_tab = mPreferences.getBoolean(PREFERENCE_KEY_SHOW_HOME_TAB, true);
	 	final boolean show_mentions_tab = mPreferences.getBoolean(PREFERENCE_KEY_SHOW_MENTIONS_TAB, true);
	 	final boolean show_messages_tab = mPreferences.getBoolean(PREFERENCE_KEY_SHOW_MESSAGES_TAB, true);
	 	final boolean show_accounts_tab = mPreferences.getBoolean(PREFERENCE_KEY_SHOW_ACCOUNTS_TAB, true);

		final List<TabSpec> tabs = getTabs(this);
		if (isTabsChanged(tabs) || show_home_tab != mShowHomeTab || show_mentions_tab != mShowMentionsTab
			 || show_messages_tab != mShowMessagesTab || show_accounts_tab != mShowAccountsTab) {
			restart();
		}
		if (mPreferences.getBoolean(PREFERENCE_KEY_STREAMING_ENABLED, false) == true) {
			
			if (mService != null) {
				try {
					Twitter twitter = com.dwdesign.tweetings.util.Utils.getDefaultTwitterInstance(getApplicationContext(), true);
					twitterStream = new TwitterStreamFactory().getInstance(twitter.getAuthorization());
			        twitterStream.addListener(listener);
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
		}
		if (refresh_on_start) {
			refreshOnResume();
		}
	}

	@Override
	protected void onStop() {
		unregisterReceiver(mStateReceiver);
		mPreferences.edit().putInt(PREFERENCE_KEY_SAVED_TAB_POSITION, mViewPager.getCurrentItem()).commit();
		super.onStop();
	}

	protected void setPagingEnabled(final boolean enabled) {
		if (mIndicator != null) {
			mIndicator.setPagingEnabled(enabled);
			mIndicator.setEnabled(enabled);
		}
	}
	
	private void initTabs(final Collection<? extends TabSpec> tabs) {
		mCustomTabs.clear();
		mCustomTabs.addAll(tabs);
		mAdapter.clear();
		if (mShowHomeTab) {
			mAdapter.addTab(HomeTimelineFragment.class, null, getString(R.string.home), R.drawable.ic_tab_home,
				TAB_POSITION_HOME);
		}
		if (mShowMentionsTab) {
			mAdapter.addTab(MentionsFragment.class, null, getString(R.string.mentions), R.drawable.ic_tab_mention,
				TAB_POSITION_MENTIONS);
		}
		if (mShowMessagesTab) {
			mAdapter.addTab(DirectMessagesFragment.class, null, getString(R.string.direct_messages),
				R.drawable.ic_tab_message, TAB_POSITION_MESSAGES);
		}
		mAdapter.addTabs(tabs);
		if (mShowAccountsTab) {
			mAdapter.addTab(AccountsFragment.class, null, getString(R.string.accounts), R.drawable.ic_tab_accounts,
				mAdapter.getCount());
		}

	}

	private boolean isTabsChanged(final List<TabSpec> tabs) {
		if (mCustomTabs.size() == 0 && tabs == null) return false;
		if (mCustomTabs.size() != tabs.size()) return true;
		final int size = mCustomTabs.size();
		for (int i = 0; i < size; i++) {
			if (!mCustomTabs.get(i).equals(tabs.get(i))) return true;
		}
		return false;
	}
	
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
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
	int getDualPaneLayoutRes() {
		return R.layout.home_dual_pane;
	 }
	 
	@Override
	int getNormalLayoutRes() {
		return R.layout.home;
	 }
	
	UserStreamListener listener = new UserStreamListener() {
		
        @Override
        public void onStatus(Status status) {
            //System.out.println("onStatus @" + status.getUser().getScreenName() + " - " + status.getText());
        	final long account_id = com.dwdesign.tweetings.util.Utils.getDefaultAccountId(HomeActivity.this);
        	final String screen_name = com.dwdesign.tweetings.util.Utils.getAccountUsername(HomeActivity.this, account_id).toLowerCase();
            boolean followingUser = false;
            for (long friendId : friendsList) {
            	// Is the user in the list of users someone follows
            	if (friendId == status.getUser().getId()) {
            		followingUser = true;
            		break;
            	}
            }
            // Is the tweet the user's own
            if (status.getUser().getId() == account_id) {
            	followingUser = true;
            }
            
            // Delivery to home timeline
            if (followingUser == true) {
            	// We have a home timeline tweet
            	Intent broadcast = new Intent();
				broadcast.putExtra("status", status);
				broadcast.setAction(BROADCAST_STREAM_STATUS_RECEIVED);
				
				HomeActivity.this.mService.insertStreamToHomeTimeline(account_id, broadcast);
            }
            
            // Check for mentions
            if (status.getText().toLowerCase().contains("@" + screen_name)) {
            	// We've found @screenName but now we need to check it's not @screenNameSomethingElse
            	/*int position = status.getText().toLowerCase().indexOf("@" + screen_name);
            	int tweetLength = status.getText().length();
            	boolean deliverToMentions = false;
            	if (position > 0) { 
            		// +1 for the @ symbol
            		int nextCharacterPosition = position + screen_name.length() + 1;
            		// +1 as position starts at 0
            		if (nextCharacterPosition + 1 >= tweetLength) {
            			// OK we are at the end of the tweet, that's fine
            			deliverToMentions = true;
            		}
            		else {
            			String charAtPosition = status.getText().toLowerCase().substring(nextCharacterPosition, nextCharacterPosition + 1);
            			if (charAtPosition.equals(" ") || charAtPosition.equals("\n") || charAtPosition.equals("\t") || charAtPosition.equals(".") || charAtPosition.equals(",") || charAtPosition.equals("?")
            					|| charAtPosition.equals("!") || charAtPosition.equals(";") || charAtPosition.equals(":")) {
            				deliverToMentions = true;
            			}
            		}
            		
            	}
            	if (deliverToMentions == true) {*/
            		Intent broadcast = new Intent();
    				broadcast.putExtra("status", status);
    				broadcast.setAction(BROADCAST_STREAM_STATUS_RECEIVED);
    				
    				HomeActivity.this.mService.insertStreamToMentions(account_id, broadcast);
            	//}
            }
            
        }

        @Override
        public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
        	final Intent intent = new Intent(BROADCAST_STATUS_DESTROYED);
			final long status_id = statusDeletionNotice.getStatusId();
			
			final StringBuilder where = new StringBuilder();
			where.append(Statuses.STATUS_ID + " = " + status_id);
			where.append(" OR " + Statuses.RETWEET_ID + " = " + status_id);
			for (final Uri uri : TweetStore.STATUSES_URIS) {
				getContentResolver().delete(uri, where.toString(), null);
			}
			intent.putExtra(INTENT_KEY_STATUS_ID, status_id);
			intent.putExtra(INTENT_KEY_SUCCEED, true);
			
			sendBroadcast(intent);
        }

        @Override
        public void onDeletionNotice(long directMessageId, long userId) {
        	final String where = DirectMessages.MESSAGE_ID + " = " + directMessageId;
        	
        	getContentResolver().delete(DirectMessages.Inbox.CONTENT_URI, where, null);
        	getContentResolver().delete(DirectMessages.Outbox.CONTENT_URI, where, null);
        }

        @Override
        public void onTrackLimitationNotice(int numberOfLimitedStatuses) {

        }

        @Override
        public void onScrubGeo(long userId, long upToStatusId) {
        }

        @Override
        public void onStallWarning(StallWarning warning) {
        }

        @Override
        public void onFriendList(long[] friendIds) {
        	friendsList = friendIds;
        }

        public void onFavorite(User source, User target, Status favoritedStatus) {
        }

        public void onUnfavorite(User source, User target, Status unfavoritedStatus) {
        }

        public void onFollow(User source, User followedUser) {
        	long[] toAppend = { source.getId() };
        	long[] tmp = new long[friendsList.length + toAppend.length];
        	System.arraycopy(friendsList, 0, tmp, 0, friendsList.length);
        	System.arraycopy(toAppend, 0, tmp, friendsList.length, toAppend.length);
        	
        	friendsList = tmp;
        }

        public void onRetweet(User source, User target, Status retweetedStatus) {
        }

        public void onDirectMessage(DirectMessage directMessage) {
        	Intent broadcast = new Intent();
			broadcast.putExtra("status", directMessage);
			broadcast.setAction(BROADCAST_STREAM_STATUS_RECEIVED);
			final long account_id = com.dwdesign.tweetings.util.Utils.getDefaultAccountId(HomeActivity.this);
			HomeActivity.this.mService.insertStreamToInbox(account_id, broadcast);
        }

        public void onUserListMemberAddition(User addedMember, User listOwner, UserList list) {
        }

        public void onUserListMemberDeletion(User deletedMember, User listOwner, UserList list) {
        }

        public void onUserListSubscription(User subscriber, User listOwner, UserList list) {
        }

        public void onUserListUnsubscription(User subscriber, User listOwner, UserList list) {
        }

        public void onUserListCreation(User listOwner, UserList list) {
        }

        public void onUserListUpdate(User listOwner, UserList list) {
        }

        public void onUserListDeletion(User listOwner, UserList list) {
        }

        public void onUserProfileUpdate(User updatedUser) {
        }

        public void onBlock(User source, User blockedUser) {
        }

        public void onUnblock(User source, User unblockedUser) {
        }

        public void onException(Exception ex) {
            ex.printStackTrace();
            System.out.println("onException:" + ex.getMessage());
        }
    };
	
}
