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

package com.dwdesign.tweetings.fragment;

import static com.dwdesign.tweetings.util.Utils.buildQueryUri;
import static com.dwdesign.tweetings.util.Utils.getActivatedAccountIds;
import static com.dwdesign.tweetings.util.Utils.getQuoteStatus;
import static com.dwdesign.tweetings.util.Utils.isMyRetweet;
import static com.dwdesign.tweetings.util.Utils.openConversation;
import static com.dwdesign.tweetings.util.Utils.openStatus;
import static com.dwdesign.tweetings.util.Utils.setMenuForStatus;
import static com.dwdesign.tweetings.util.Utils.getDefaultAccountId;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;

import java.util.List;
import java.util.Locale;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import com.dwdesign.popupmenu.PopupMenu;
import com.dwdesign.popupmenu.PopupMenu.OnMenuItemClickListener;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.activity.ComposeActivity;
import com.dwdesign.tweetings.activity.HomeActivity;
import com.dwdesign.tweetings.adapter.CursorStatusesAdapter;
import com.dwdesign.tweetings.model.Panes;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.model.StatusViewHolder;
import com.dwdesign.tweetings.provider.TweetStore.Filters;
import com.dwdesign.tweetings.provider.TweetStore.Statuses;
import com.dwdesign.tweetings.util.AsyncTaskManager;
import com.dwdesign.tweetings.util.NoDuplicatesLinkedList;
import com.dwdesign.tweetings.util.ServiceInterface;
import com.dwdesign.tweetings.util.StatusesAdapterInterface;
import com.dwdesign.tweetings.util.ClipboardUtils;

import android.content.BroadcastReceiver;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.os.SystemClock;
import android.os.StrictMode.ThreadPolicy;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.text.Html;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Toast;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;

import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.twitter.Extractor;
import android.view.Menu;

abstract class BaseStatusesListFragment<Data> extends PullToRefreshListFragment implements LoaderCallbacks<Data>,
		OnScrollListener, OnItemClickListener, OnItemLongClickListener, OnMenuItemClickListener, Panes.Left {

	private static final long TICKER_DURATION = 5000L;
	
	private ServiceInterface mService;
	private TweetingsApplication mApplication;
	protected SharedPreferences mPreferences;
	private AsyncTaskManager mAsyncTaskManager;

	private Handler mHandler;
	private Runnable mTicker;
	
	protected ListView mListView;

	private StatusesAdapterInterface mAdapter;
	private PopupMenu mPopupMenu;

	protected Data mData;
	private ParcelableStatus mSelectedStatus;

	private boolean mLoadMoreAutomatically;
	
	protected long gapStatusId = -1;

	private volatile boolean mBusy, mTickerStopped, mReachedBottom, mActivityFirstCreated,
			mNotReachedBottomBefore = true;

	private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (BROADCAST_MULTI_SELECT_STATE_CHANGED.equals(action)) {
				mAdapter.setMultiSelectEnabled(mApplication.isMultiSelectActive());
			} else if (BROADCAST_MULTI_SELECT_ITEM_CHANGED.equals(action)) {
				mAdapter.notifyDataSetChanged();
			}
		}

	};
	
	protected String syncPositionReceived;
	protected String syncPosition;
	protected String scrollStatusId;

	public AsyncTaskManager getAsyncTaskManager() {
		return mAsyncTaskManager;
	}

	public final Data getData() {
		return mData;
	}

	public abstract long[] getLastStatusIds();
	
	public abstract long[] getNewestStatusIds();
	
	public abstract void scrollToStatusId(long statusId);

	@Override
	public abstract StatusesAdapterInterface getListAdapter();

	public ParcelableStatus getSelectedStatus() {
		return mSelectedStatus;
	}

	public SharedPreferences getSharedPreferences() {
		return mPreferences;
	}

	public abstract int getStatuses(long[] account_ids, long[] max_ids, long[] since_ids);

	public boolean isActivityFirstCreated() {
		return mActivityFirstCreated;
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mApplication = getApplication();
		mAsyncTaskManager = getAsyncTaskManager();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mService = getServiceInterface();
		mAdapter = getListAdapter();
		setListAdapter(mAdapter);
		mListView = getListView();
		mListView.setOnScrollListener(this);
		mListView.setOnItemClickListener(this);
		mListView.setOnItemLongClickListener(this);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			mListView.setCacheColorHint(0);
		}
		setMode(Mode.BOTH);
		getLoaderManager().initLoader(0, getArguments(), this);
		setListShown(false);
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mActivityFirstCreated = true;
		// Tell the framework to try to keep this fragment around
		// during a configuration change.
		setRetainInstance(true);
	}
	
	@Override
	public abstract Loader<Data> onCreateLoader(int id, Bundle args);

	@Override
	public void onItemClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		mSelectedStatus = null;
		final Object tag = view.getTag();
		if (tag instanceof StatusViewHolder) {
			final boolean click_to_open_menu = mPreferences.getBoolean(PREFERENCE_KEY_CLICK_TO_OPEN_MENU, false);
			final ParcelableStatus status = mSelectedStatus = getListAdapter().findStatus(id);
			if (status == null) return;
			final StatusViewHolder holder = (StatusViewHolder) tag;
			if (holder.show_as_gap) {
				try {
					if (position + 2 < mAdapter.getCount()) {
						final long itemId = mAdapter.findItemIdByPosition(position+1);
						gapStatusId = itemId;
					}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				if (gapStatusId > 0) {
					getStatuses(new long[] { status.account_id }, new long[] { status.status_id }, new long[] { gapStatusId });
				}
				else {
					getStatuses(new long[] { status.account_id }, new long[] { status.status_id }, null);
				}
			} else {
				gapStatusId = -1;
				 if (mApplication.isMultiSelectActive()) {
					 final NoDuplicatesLinkedList<Object> list = mApplication.getSelectedItems();
					 if (!list.contains(status)) {
						 list.add(status);
					 } else {
						 list.remove(status);
					 }
					 return;
				 }
				 if (click_to_open_menu) {
					 openMenu(view, status);
				 } else {
					 openStatus(getActivity(), status);
				 }
			}
		}
	}
	
	@Override
	public boolean onItemLongClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		mSelectedStatus = null;
		final Object tag = view.getTag();
		if (tag instanceof StatusViewHolder) {
			final boolean click_to_open_menu = mPreferences.getBoolean(PREFERENCE_KEY_CLICK_TO_OPEN_MENU, false);
			final StatusViewHolder holder = (StatusViewHolder) tag;
			if (holder.show_as_gap) return false;
			final ParcelableStatus status = mSelectedStatus = getListAdapter().getStatus(position - mListView.getHeaderViewsCount());
			if (mApplication.isMultiSelectActive()) {
				final NoDuplicatesLinkedList<Object> list = mApplication.getSelectedItems();
				if (!list.contains(mSelectedStatus)) {
					list.add(mSelectedStatus);
				} else {
					list.remove(mSelectedStatus);
				}
				return true;
			}
			if (click_to_open_menu) {
				if (!mApplication.isMultiSelectActive()) {
					mApplication.startMultiSelect();
				}
				final NoDuplicatesLinkedList<Object> list = mApplication.getSelectedItems();
				if (!list.contains(status)) {
					list.add(status);
				}
			} else {
				openMenu(view, status);
			}

			return true;
		}
		return false;
	}
	
	public void openMenu(View view, ParcelableStatus status) {
		mPopupMenu = PopupMenu.getInstance(getActivity(), view);
		mPopupMenu.inflate(R.menu.action_status);
		final int activated_color = getResources().getColor(R.color.holo_blue_bright);
		final boolean click_to_open_menu = mPreferences.getBoolean(PREFERENCE_KEY_CLICK_TO_OPEN_MENU, false);
		final String buffer_authorised = mPreferences.getString(PREFERENCE_KEY_BUFFERAPP_ACCESS_TOKEN, null);
		final Menu menu = mPopupMenu.getMenu();
		final MenuItem itemView = menu.findItem(MENU_VIEW);
		if (itemView != null) {
			itemView.setVisible(click_to_open_menu);
		}
		final MenuItem direct_retweet = menu.findItem(MENU_RETWEET);
		if (direct_retweet != null) {
			final Drawable icon = direct_retweet.getIcon().mutate();
		 	direct_retweet.setVisible((!status.is_protected && status.account_id != status.user_id) || isMyRetweet(status));
		 	if (isMyRetweet(status)) {
		 		icon.setColorFilter(activated_color, PorterDuff.Mode.MULTIPLY);
		 		direct_retweet.setTitle(R.string.cancel_retweet);
		 	} else {
		 		icon.clearColorFilter();
		 		direct_retweet.setTitle(R.string.retweet);
		 	}
		}
		final MenuItem bufferView = menu.findItem(MENU_ADD_TO_BUFFER);
		if (bufferView != null) {
			if (buffer_authorised != null && !buffer_authorised.equals("")) {
				bufferView.setVisible(true);
			}
			else {
				bufferView.setVisible(false);
			}
		}
		setMenuForStatus(getActivity(), menu, status);
		mPopupMenu.setOnMenuItemClickListener(this);
		mPopupMenu.show();
	}

	@Override
	public void onLoaderReset(final Loader<Data> loader) {
		mData = null;
	}

	@Override
	public void onLoadFinished(final Loader<Data> loader, final Data data) {
		mData = data;
		mAdapter.setShowAccountColor(getActivatedAccountIds(getActivity()).length > 1);
		setListShown(true);
	}
	
	protected void translate(final ParcelableStatus status) {
		ThreadPolicy tp = ThreadPolicy.LAX;
    	StrictMode.setThreadPolicy(tp);
		
		String language = Locale.getDefault().getLanguage();
		String url = "http://api.microsofttranslator.com/v2/Http.svc/Translate?contentType=" + URLEncoder.encode("text/plain") + "&appId=" + BING_TRANSLATE_API_KEY + "&from=&to=" + language + "&text=";
		url = url + URLEncoder.encode(status.text_plain);
		try {
			HttpClient httpClient = new DefaultHttpClient();
			HttpResponse response = httpClient.execute(new HttpGet(url));
			    
			BufferedReader reader = new BufferedReader(new InputStreamReader(
			response.getEntity().getContent(), "UTF-8"));
			String sResponse;
			StringBuilder s = new StringBuilder();
	
			while ((sResponse = reader.readLine()) != null) {
				s = s.append(sResponse);
			}
			String finalString = s.toString();
			finalString = finalString.replace("<string xmlns=\"http://schemas.microsoft.com/2003/10/Serialization/\">", "");
			finalString = finalString.replace("</string>", "");
			
			AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());
			builder.setTitle(getString(R.string.translate));
			builder.setMessage(finalString);
			builder.setCancelable(true);
			builder.setPositiveButton(getString(R.string.ok),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
			
			AlertDialog alert = builder.create();
			alert.show();
			
			//Toast.makeText(getActivity(), finalString, Toast.LENGTH_LONG).show();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	}

	@Override
	public boolean onMenuItemClick(final MenuItem item) {
		final ParcelableStatus status = mSelectedStatus;
		if (status == null) return false;
		final long account_id = getDefaultAccountId(mApplication);
		switch (item.getItemId()) {
			case MENU_VIEW: {
				openStatus(getActivity(), status);
				break;
			}
			case MENU_SHARE: {
				final Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, "@" + status.screen_name + ": " + status.text_plain);
				startActivity(Intent.createChooser(intent, getString(R.string.share)));
				break;
			}
			case MENU_TRANSLATE: {
				translate(status);
				break;
			}
			case MENU_RETWEET: {
				if (isMyRetweet(status)) {
					mService.destroyStatus(status.account_id, status.status_id);
				} else {
					final long id_to_retweet = mSelectedStatus.is_retweet && mSelectedStatus.retweet_id > 0 ? mSelectedStatus.retweet_id
							: mSelectedStatus.status_id;
					mService.retweetStatus(status.account_id, id_to_retweet);
				}
				break;
			}
			case MENU_QUOTE: {
				final Intent intent = new Intent(INTENT_ACTION_COMPOSE);
				final Bundle bundle = new Bundle();
				bundle.putLong(INTENT_KEY_ACCOUNT_ID, status.account_id);
				bundle.putBoolean(INTENT_KEY_IS_QUOTE, true);
				bundle.putString(INTENT_KEY_TEXT, getQuoteStatus(getActivity(), status.screen_name, status.text_plain));
				intent.putExtras(bundle);
				startActivity(intent);
				break;
			}
			case MENU_QUOTE_REPLY: {
				final Intent intent = new Intent(INTENT_ACTION_COMPOSE);
				final Bundle bundle = new Bundle();
				bundle.putLong(INTENT_KEY_ACCOUNT_ID, status.account_id);
				bundle.putLong(INTENT_KEY_IN_REPLY_TO_ID, status.status_id);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_SCREEN_NAME, status.screen_name);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_NAME, status.name);
				bundle.putBoolean(INTENT_KEY_IS_QUOTE, true);
				bundle.putString(INTENT_KEY_TEXT, getQuoteStatus(getActivity(), status.screen_name, status.text_plain));
				intent.putExtras(bundle);
				startActivity(intent);
				break;
			}
			case MENU_ADD_TO_BUFFER: {
				final Intent intent = new Intent(INTENT_ACTION_COMPOSE);
				final Bundle bundle = new Bundle();
				bundle.putLong(INTENT_KEY_ACCOUNT_ID, status.account_id);
				bundle.putBoolean(INTENT_KEY_IS_BUFFER, true);
				bundle.putString(INTENT_KEY_TEXT, getQuoteStatus(getActivity(), status.screen_name, status.text_plain));
				intent.putExtras(bundle);
				startActivity(intent);
				break;
			}
			case MENU_REPLY: {
				final Intent intent = new Intent(INTENT_ACTION_COMPOSE);
				final Bundle bundle = new Bundle();
				final List<String> mentions = new Extractor().extractMentionedScreennames(status.text_plain);
				mentions.remove(status.screen_name);
				mentions.add(0, status.screen_name);
				bundle.putStringArray(INTENT_KEY_MENTIONS, mentions.toArray(new String[mentions.size()]));
				bundle.putLong(INTENT_KEY_ACCOUNT_ID, status.account_id);
				bundle.putLong(INTENT_KEY_IN_REPLY_TO_ID, status.status_id);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_TWEET, status.text_plain);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_SCREEN_NAME, status.screen_name);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_NAME, status.name);
				intent.putExtras(bundle);
				startActivity(intent);
				break;
			}
			case MENU_FAV: {
				if (mSelectedStatus.is_favorite) {
					mService.destroyFavorite(status.account_id, status.status_id);
				} else {
					 mService.createFavorite(status.account_id, status.status_id);
				}
				break;
			}
			case MENU_CONVERSATION: {
				openConversation(getActivity(), status.account_id, status.status_id);
				break;
			}
			case MENU_DELETE: {
				mService.destroyStatus(status.account_id, status.status_id);
				break;
			}
			case MENU_EXTENSIONS: {
				final Intent intent = new Intent(INTENT_ACTION_EXTENSION_OPEN_STATUS);
				final Bundle extras = new Bundle();
				extras.putParcelable(INTENT_KEY_STATUS, status);
				intent.putExtras(extras);
				startActivity(Intent.createChooser(intent, getString(R.string.open_with_extensions)));
				break;
			}
			case MENU_MULTI_SELECT: {
				if (!mApplication.isMultiSelectActive()) {
					mApplication.startMultiSelect();
				}
				final NoDuplicatesLinkedList<Object> list = mApplication.getSelectedItems();
				if (!list.contains(status)) {
					list.add(status);
				}
				break;
			}
			case MENU_BLOCK: {
				mService.createBlock(account_id, status.user_id);
				break;
			}
			case MENU_REPORT_SPAM: {
				mService.reportSpam(account_id, status.user_id);
				break;
			}
			case MENU_MUTE_USER: {
				final String screen_name = status.screen_name;
				final Uri uri = Filters.Users.CONTENT_URI;
				final ContentValues values = new ContentValues();
				final SharedPreferences.Editor editor = getSharedPreferences(SHARED_PREFERENCES_NAME,
						Context.MODE_PRIVATE).edit();
				final ContentResolver resolver = getContentResolver();
				values.put(Filters.Users.TEXT, screen_name);
				resolver.delete(uri, Filters.Users.TEXT + " = '" + screen_name + "'", null);
				resolver.insert(uri, values);
				editor.putBoolean(PREFERENCE_KEY_ENABLE_FILTER, true).commit();
				Toast.makeText(getActivity(), R.string.user_muted, Toast.LENGTH_SHORT).show();
				break;
			}
			case MENU_MAKE_GAP: {
				Uri uri = Statuses.CONTENT_URI;
				final Uri query_uri = buildQueryUri(uri, false);
				ContentResolver mResolver = getContentResolver();
				final ContentValues values = new ContentValues();
				values.put(Statuses.IS_GAP, 1);
				final StringBuilder where = new StringBuilder();
				where.append(Statuses.ACCOUNT_ID + "=" + account_id);
				where.append(" AND " + Statuses.STATUS_ID + "=" + status.status_id);
				mResolver.update(query_uri, values, where.toString(), null);
				getActivity().sendBroadcast(new Intent(BROADCAST_FILTERS_UPDATED).putExtra(INTENT_KEY_SUCCEED, true));
				break;
			}
			case MENU_COPY: {
				final CharSequence text = Html.fromHtml(status.text_html);
				if (ClipboardUtils.setText(getActivity(), text)) {
					Toast.makeText(getActivity(), R.string.text_copied, Toast.LENGTH_SHORT).show();
				}
				break;
			}
		}
		return true;
	}

	public abstract void onPostStart();

	@Override
	public abstract void onPullDownToRefresh();

	@Override
	public void onResume() {
		super.onResume();
		mLoadMoreAutomatically = mPreferences.getBoolean(PREFERENCE_KEY_LOAD_MORE_AUTOMATICALLY, false);
		final float text_size = mPreferences.getFloat(PREFERENCE_KEY_TEXT_SIZE, PREFERENCE_DEFAULT_TEXT_SIZE);
		final boolean display_profile_image = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_PROFILE_IMAGE, true);
		final String inline_image_preview_display_option = mPreferences.getString(PREFERENCE_KEY_INLINE_IMAGE_PREVIEW_DISPLAY_OPTION, INLINE_IMAGE_PREVIEW_DISPLAY_OPTION_SMALL);
		final boolean display_name = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_NAME, false);
		final boolean display_name_both = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_NAME_BOTH, true);
		final boolean show_absolute_time = mPreferences.getBoolean(PREFERENCE_KEY_SHOW_ABSOLUTE_TIME, false);
		final boolean display_sensitive_contents = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_SENSITIVE_CONTENTS, false);
		final boolean fast_processing = mPreferences.getBoolean(PREFERENCE_KEY_FAST_LIST_PROCESSING, false);
		final boolean show_links = mPreferences.getBoolean(PREFERENCE_KEY_SHOW_LINKS, true);
		final boolean show_fast_scroll = mPreferences.getBoolean(PREFERENCE_FAST_SCROLL, false);
		mAdapter.setMultiSelectEnabled(mApplication.isMultiSelectActive());
		mAdapter.setDisplayProfileImage(display_profile_image);
		mAdapter.setInlineImagePreviewDisplayOption(inline_image_preview_display_option);
		mAdapter.setShowLinks(show_links);
		mAdapter.setFastProcessingEnabled(fast_processing);
		mAdapter.setDisplayName(display_name);
		mAdapter.setDisplayNameBoth(display_name_both);
		mAdapter.setDisplaySensitiveContents(display_sensitive_contents);
		mAdapter.setTextSize(text_size);
		mAdapter.setShowAbsoluteTime(show_absolute_time);
		mListView.setFastScrollEnabled(show_fast_scroll);
	}
	
	@Override
	public void onScroll(final AbsListView view, final int firstVisibleItem, final int visibleItemCount, final int totalItemCount) {
		final boolean reached = firstVisibleItem + visibleItemCount >= totalItemCount
				&& totalItemCount >= visibleItemCount;

		if (mReachedBottom != reached) {
			mReachedBottom = reached;
			if (mReachedBottom && mNotReachedBottomBefore) {
				mNotReachedBottomBefore = false;
				return;
			}
			if (mLoadMoreAutomatically && mReachedBottom && getListAdapter().getCount() > visibleItemCount) {
				if (!isRefreshing()) {
					onPullUpToRefresh();
				}
			}
		}

	}

	@Override
	public void onScrollStateChanged(final AbsListView view, final int scrollState) {
		switch (scrollState) {
			case SCROLL_STATE_FLING:
			case SCROLL_STATE_TOUCH_SCROLL:
				mBusy = true;
				break;
			case SCROLL_STATE_IDLE:
				mBusy = false;
				break;
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		mTickerStopped = false;
		mHandler = new Handler();

		mTicker = new Runnable() {

			@Override
			public void run() {
				if (mTickerStopped) return;
				if (mListView != null && !mBusy) {
					getListAdapter().notifyDataSetChanged();
				}
				final long now = SystemClock.uptimeMillis();
				final long next = now + TICKER_DURATION - now % TICKER_DURATION;
				mHandler.postAtTime(mTicker, next);
			}
		};
		mTicker.run();
		
		final IntentFilter filter = new IntentFilter();
		filter.addAction(BROADCAST_MULTI_SELECT_STATE_CHANGED);
		filter.addAction(BROADCAST_MULTI_SELECT_ITEM_CHANGED);
		registerReceiver(mStateReceiver, filter);
		
		onPostStart();
	}

	@Override
	public void onStop() {
		mTickerStopped = true;
		mActivityFirstCreated = false;
		if (mPopupMenu != null) {
			mPopupMenu.dismiss();
		}
		unregisterReceiver(mStateReceiver);
		super.onStop();
	}
}
