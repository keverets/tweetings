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

import static com.dwdesign.tweetings.util.Utils.clearUserColor;
import static com.dwdesign.tweetings.util.Utils.findStatusInDatabases;
import static com.dwdesign.tweetings.util.Utils.formatToLongTimeString;
import static com.dwdesign.tweetings.util.Utils.getAccountColor;
import static com.dwdesign.tweetings.util.Utils.getActivatedAccountIds;
import static com.dwdesign.tweetings.util.Utils.getBiggerTwitterProfileImage;
import static com.dwdesign.tweetings.util.Utils.getImagesInStatus;
import static com.dwdesign.tweetings.util.Utils.getQuoteStatus;
import static com.dwdesign.tweetings.util.Utils.getTwitterInstance;
import static com.dwdesign.tweetings.util.Utils.getUserColor;
import static com.dwdesign.tweetings.util.Utils.getUserTypeIconRes;
import static com.dwdesign.tweetings.util.Utils.isMyActivatedAccount;
import static com.dwdesign.tweetings.util.Utils.isMyRetweet;
import static com.dwdesign.tweetings.util.Utils.isNullOrEmpty;
import static com.dwdesign.tweetings.util.Utils.openConversation;
import static com.dwdesign.tweetings.util.Utils.openUserProfile;
import static com.dwdesign.tweetings.util.Utils.openUserRetweetedStatus;
import static com.dwdesign.tweetings.util.Utils.openUserTimeline;
import static com.dwdesign.tweetings.util.Utils.parseURL;
import static com.dwdesign.tweetings.util.Utils.setMenuForStatus;
import static com.dwdesign.tweetings.util.Utils.setUserColor;
import static com.dwdesign.tweetings.util.Utils.showErrorToast;

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

import com.dwdesign.menubar.MenuBar;
import com.dwdesign.menubar.MenuBar.OnMenuItemClickListener;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.model.ImageSpec;
import com.dwdesign.tweetings.model.Panes;
import com.dwdesign.tweetings.model.ParcelableLocation;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.provider.TweetStore.Accounts;
import com.dwdesign.tweetings.provider.TweetStore.Filters;
import com.dwdesign.tweetings.util.HtmlEscapeHelper;
import com.dwdesign.tweetings.util.LazyImageLoader;
import com.dwdesign.tweetings.util.OnLinkClickHandler;
import com.dwdesign.tweetings.util.ServiceInterface;
import com.dwdesign.tweetings.util.TwidereLinkify;

import twitter4j.Relationship;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.twitter.Extractor;

public class StatusFragment extends BaseFragment implements OnClickListener, OnMenuItemClickListener, Panes.Right {

	private long mAccountId, mStatusId;
	private boolean mLoadMoreAutomatically;
	private boolean mFollowInfoDisplayed, mLocationInfoDisplayed;
	private ServiceInterface mService;
	private SharedPreferences mPreferences;
	private LazyImageLoader mProfileImageLoader;
	private TextView mNameView, mScreenNameView, mTextView, mTimeAndSourceView, mInReplyToView, mLocationView,
			mRetweetedStatusView;
	private ImageView mProfileImageView;
	private Button mFollowButton;
	private View mStatusContent, mProfileView, mFollowIndicator, mImagesPreviewContainer, mContentScroller,
		mUserColorLabel;
	private MenuBar mMenuBar;
	private ProgressBar mStatusLoadProgress, mFollowInfoProgress;
	private FollowInfoTask mFollowInfoTask;
	private ImagesPreviewFragment mImagesPreviewFragment;
	private GetStatusTask mGetStatusTask;
	private LocationInfoTask mLocationInfoTask;
	private ParcelableStatus mStatus;
	private RelativeLayout mConversationView;

	private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (BROADCAST_FRIENDSHIP_CHANGED.equals(action)) {
				if (mStatus != null && mStatus.user_id == intent.getLongExtra(INTENT_KEY_USER_ID, -1)
						&& intent.getBooleanExtra(INTENT_KEY_SUCCEED, false)) {
					showFollowInfo(true);
				}
			} else if (BROADCAST_FAVORITE_CHANGED.equals(action)) {
				final long status_id = intent.getLongExtra(INTENT_KEY_STATUS_ID, -1);
				if (status_id > 0 && status_id == mStatusId) {
					getStatus(true);
				}
			} else if (BROADCAST_RETWEET_CHANGED.equals(action)) {
				final long status_id = intent.getLongExtra(INTENT_KEY_STATUS_ID, -1);
				if (status_id > 0 && status_id == mStatusId) {
					getStatus(true);
				}
			} else if (BROADCAST_TWITLONGER_EXPANDED.equals(action)) {
				final String expanded_status_text = intent.getStringExtra(INTENT_KEY_TWITLONGER_EXPANDED_TEXT);
				Spanned expanded_text = Html.fromHtml(expanded_status_text);

				mTextView.setText(expanded_text);
				final TwidereLinkify linkify = new TwidereLinkify(mTextView);
				linkify.setOnLinkClickListener(new OnLinkClickHandler(getActivity(), mAccountId));
				linkify.addAllLinks();
				
			}
		}
	};

	@SuppressWarnings("deprecation")
	public void displayStatus(final ParcelableStatus status) {
		mStatus = null;
		mImagesPreviewFragment.clear();
		if (status == null || getActivity() == null) return;
		mStatus = status;
		
		final String buffer_authorised = mPreferences.getString(PREFERENCE_KEY_BUFFERAPP_ACCESS_TOKEN, null);
		
		mMenuBar.inflate(R.menu.menu_status);
		
		final MenuItem bufferView = mMenuBar.getMenu().findItem(MENU_ADD_TO_BUFFER);
		if (bufferView != null) {
			if (buffer_authorised != null && !buffer_authorised.equals("")) {
				bufferView.setVisible(true);
			}
			else {
				bufferView.setVisible(false);
			}
		}
		setMenuForStatus(getActivity(), mMenuBar.getMenu(), status);
		mMenuBar.show();

		final boolean is_multiple_account_enabled = getActivatedAccountIds(getActivity()).length > 1;

		updateUserColor();
		
		mContentScroller.setBackgroundResource(is_multiple_account_enabled ? R.drawable.ic_label_account_nopadding : 0);
		if (is_multiple_account_enabled) {
			final Drawable d = mContentScroller.getBackground();
			if (d != null) {
				d.mutate().setColorFilter(getAccountColor(getActivity(), status.account_id), PorterDuff.Mode.MULTIPLY);
				mContentScroller.invalidate();
			}
		}

		mNameView.setText(status.name);
		mScreenNameView.setText("@" + status.screen_name);
		mScreenNameView.setCompoundDrawablesWithIntrinsicBounds(
				getUserTypeIconRes(status.is_verified, status.is_protected), 0, 0, 0);
		mTextView.setText(status.text);
		final TwidereLinkify linkify = new TwidereLinkify(mTextView);
		linkify.setOnLinkClickListener(new OnLinkClickHandler(getActivity(), mAccountId));
		linkify.addAllLinks();
		final boolean is_reply = status.in_reply_to_status_id > 0;
		final String time = formatToLongTimeString(getActivity(), status.status_timestamp);
		final String strTime = "<a href=\"https://twitter.com/" + status.screen_name + "/status/" + String.valueOf(status.status_id) + "\">" + time + "</a>";
		final String source_html = status.source;
		if (!isNullOrEmpty(time) && !isNullOrEmpty(source_html)) {
			mTimeAndSourceView.setText(Html.fromHtml(getString(R.string.time_source, strTime, source_html)));
		} else if (isNullOrEmpty(time) && !isNullOrEmpty(source_html)) {
			mTimeAndSourceView.setText(Html.fromHtml(getString(R.string.source, source_html)));
		} else if (!isNullOrEmpty(time) && isNullOrEmpty(source_html)) {
			mTimeAndSourceView.setText(time);
		}
		mTimeAndSourceView.setMovementMethod(LinkMovementMethod.getInstance());
		mInReplyToView.setVisibility(is_reply ? View.VISIBLE : View.GONE);
		mConversationView.setVisibility(is_reply ? View.VISIBLE : View.GONE);
		if (is_reply) {
			mInReplyToView.setText(getString(R.string.in_reply_to, status.in_reply_to_screen_name));
			
			Display display = getActivity().getWindowManager().getDefaultDisplay(); 
			int width = display.getWidth();  // deprecated
			int height = display.getHeight();  // deprecated
			
			int heightOfConversation = (height / 2) - 48 - 44;
			ViewGroup.LayoutParams params = mConversationView.getLayoutParams();
			params.height = heightOfConversation;
			mConversationView.setLayoutParams(params);
			
			FragmentManager fragmentManager=getFragmentManager();
			FragmentTransaction ft=null;

			ft = fragmentManager.beginTransaction();

			final Fragment fragment = new ConversationFragment();
			final Bundle args = new Bundle();
			args.putLong(INTENT_KEY_ACCOUNT_ID, mAccountId);
			args.putLong(INTENT_KEY_STATUS_ID, status.status_id);
			fragment.setArguments(args);
			
			ft.replace(R.id.conversation, fragment, getString(R.string.view_conversation));

			ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
			ft.commit();

		}

		final boolean hires_profile_image = getResources().getBoolean(R.bool.hires_profile_image);

		mProfileImageLoader.displayImage(
				parseURL(hires_profile_image ? getBiggerTwitterProfileImage(status.profile_image_url_string)
						: status.profile_image_url_string), mProfileImageView);
		final List<ImageSpec> images = getImagesInStatus(status.text_html);
		mImagesPreviewContainer.setVisibility(images.size() > 0 ? View.VISIBLE : View.GONE);
		mImagesPreviewFragment.addAll(images);
		mImagesPreviewFragment.update();
		if (mLoadMoreAutomatically == true) {
			mImagesPreviewFragment.show();
		}
		mRetweetedStatusView.setVisibility(status.is_protected ? View.GONE : View.VISIBLE);
		if (status.retweet_id > 0) {
			final boolean display_name = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_NAME, false);
			final String retweeted_by = display_name ? status.retweeted_by_name : status.retweeted_by_screen_name;
			mRetweetedStatusView.setText(status.retweet_count > 1 ? getString(R.string.retweeted_by_with_count,
					retweeted_by, status.retweet_count - 1) : getString(R.string.retweeted_by, retweeted_by));
			mRetweetedStatusView.setVisibility(View.VISIBLE);
		} else {
			mRetweetedStatusView.setVisibility(View.GONE);
			 mRetweetedStatusView.setText(R.string.users_retweeted_this);
		}
		mLocationView.setVisibility(ParcelableLocation.isValidLocation(status.location) ? View.VISIBLE : View.GONE);

		if (mLoadMoreAutomatically) {
			showFollowInfo(true);
			showLocationInfo(true);
		} else {
			mFollowIndicator.setVisibility(View.GONE);
		}
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		final TweetingsApplication application = getApplication();
		mService = application.getServiceInterface();
	 	mProfileImageLoader = application.getProfileImageLoader();
	 	mImagesPreviewFragment = (ImagesPreviewFragment) Fragment.instantiate(getActivity(),
	 	    ImagesPreviewFragment.class.getName());
		super.onActivityCreated(savedInstanceState);
		setRetainInstance(true);
		mLoadMoreAutomatically = mPreferences.getBoolean(PREFERENCE_KEY_LOAD_MORE_AUTOMATICALLY, true);
		final Bundle bundle = getArguments();
		if (bundle != null) {
			mAccountId = bundle.getLong(INTENT_KEY_ACCOUNT_ID);
			mStatusId = bundle.getLong(INTENT_KEY_STATUS_ID);
			bundle.setClassLoader(ParcelableStatus.class.getClassLoader());
			mStatus = bundle.getParcelable(INTENT_KEY_STATUS);
		}
		mInReplyToView.setOnClickListener(this);
		mFollowButton.setOnClickListener(this);
		mProfileView.setOnClickListener(this);
		mLocationView.setOnClickListener(this);
		mRetweetedStatusView.setOnClickListener(this);
		mMenuBar.setOnMenuItemClickListener(this);
		final FragmentManager fm = getFragmentManager();
		final FragmentTransaction ft = fm.beginTransaction();
		ft.replace(R.id.images_preview, mImagesPreviewFragment);
		ft.commit();
		//if (mStatus != null) {
			//displayStatus(mStatus);
		//} else {
			//getStatus(false);
		//}

	}
	
	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
		if (intent == null || mStatus == null) return;
		switch (requestCode) {
			case REQUEST_SET_COLOR: {
				if (resultCode == Activity.RESULT_OK) if (intent != null && intent.getExtras() != null) {
					final int color = intent.getIntExtra(Accounts.USER_COLOR, Color.TRANSPARENT);
					setUserColor(getActivity(), mStatus.user_id, color);
					updateUserColor();
				}
				break;
			}
		}

	}

	@Override
	public void onClick(final View view) {
		if (mStatus == null) return;
		switch (view.getId()) {
			case R.id.profile: {
				openUserProfile(getActivity(), mStatus.account_id, mStatus.user_id, null);
				break;
			}
			case R.id.follow: {
				mService.createFriendship(mAccountId, mStatus.user_id);
				break;
			}
			case R.id.in_reply_to: {
				openConversation(getActivity(), mStatus.account_id, mStatus.status_id);
				break;
			}
			case R.id.location_view: {
				if (mStatus.location == null) return;
				final ParcelableLocation location = mStatus.location;
				if (location == null || !location.isValid()) return;
				final Uri.Builder builder = new Uri.Builder();
				builder.scheme(SCHEME_TWEETINGS);
				builder.authority(AUTHORITY_MAP);
				builder.appendQueryParameter(QUERY_PARAM_LAT, String.valueOf(location.latitude));
				builder.appendQueryParameter(QUERY_PARAM_LNG, String.valueOf(location.longitude));
				startActivity(new Intent(Intent.ACTION_VIEW, builder.build()));
				break;
			}
			case R.id.retweet_view: {
				openUserRetweetedStatus(getActivity(), mStatus.account_id, mStatus.retweet_id > 0 ? mStatus.retweet_id
						: mStatus.status_id);
				break;
			}
		}

	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.view_status, container, false);
		mStatusContent = view.findViewById(R.id.content);
		mStatusLoadProgress = (ProgressBar) view.findViewById(R.id.status_load_progress);
		mUserColorLabel = view.findViewById(R.id.user_color_label);
		mContentScroller = view.findViewById(R.id.content_scroller);
		mImagesPreviewContainer = view.findViewById(R.id.images_preview);
		mLocationView = (TextView) view.findViewById(R.id.location_view);
		mRetweetedStatusView = (TextView) view.findViewById(R.id.retweet_view);
		mNameView = (TextView) view.findViewById(R.id.name);
		mScreenNameView = (TextView) view.findViewById(R.id.screen_name);
		mTextView = (TextView) view.findViewById(R.id.text);
		mProfileImageView = (ImageView) view.findViewById(R.id.profile_image);
		mTimeAndSourceView = (TextView) view.findViewById(R.id.time_source);
		mInReplyToView = (TextView) view.findViewById(R.id.in_reply_to);
		mFollowButton = (Button) view.findViewById(R.id.follow);
		mFollowIndicator = view.findViewById(R.id.follow_indicator);
		mFollowInfoProgress = (ProgressBar) view.findViewById(R.id.follow_info_progress);
		mProfileView = view.findViewById(R.id.profile);
		mMenuBar = (MenuBar) view.findViewById(R.id.menu_bar);
		mConversationView = (RelativeLayout) view.findViewById(R.id.conversation);
		return view;
	}

	@Override
	public void onDestroyView() {
		mStatus = null;
		mAccountId = -1;
		mStatusId = -1;
		if (mGetStatusTask != null) {
			mGetStatusTask.cancel(true);
		}
		if (mFollowInfoTask != null) {
			mFollowInfoTask.cancel(true);
		}
		if (mLocationInfoTask != null) {
			mLocationInfoTask.cancel(true);
		}
		super.onDestroyView();
	}

	@SuppressLint({ "NewApi", "NewApi", "NewApi" })
	@Override
	public boolean onMenuItemClick(final MenuItem item) {
		if (mStatus == null) return false;
		final String text_plain = mStatus.text_plain;
		final String screen_name = mStatus.screen_name;
		final String name = mStatus.name;
		switch (item.getItemId()) {
			case MENU_SHARE: {
				final Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, "@" + mStatus.screen_name + ": " + text_plain);
				startActivity(Intent.createChooser(intent, getString(R.string.share)));
				break;
			}
			case MENU_RETWEET: {
				if (isMyRetweet(mStatus)) {
					mService.destroyStatus(mAccountId, mStatus.retweet_id);
				} else {
					final long id_to_retweet = mStatus.is_retweet && mStatus.retweet_id > 0 ? mStatus.retweet_id
							: mStatus.status_id;
					mService.retweetStatus(mAccountId, id_to_retweet);
				}
				break;
			}
			case MENU_TRANSLATE: {
				translate(mStatus);
				break;
			}
			case MENU_QUOTE: {
				final Intent intent = new Intent(INTENT_ACTION_COMPOSE);
				final Bundle bundle = new Bundle();
				bundle.putLong(INTENT_KEY_ACCOUNT_ID, mAccountId);
				bundle.putLong(INTENT_KEY_IN_REPLY_TO_ID, mStatusId);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_SCREEN_NAME, screen_name);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_NAME, name);
				bundle.putBoolean(INTENT_KEY_IS_QUOTE, true);
				bundle.putString(INTENT_KEY_TEXT, getQuoteStatus(getActivity(), screen_name, text_plain));
				intent.putExtras(bundle);
				startActivity(intent);
				break;
			}
			case MENU_ADD_TO_BUFFER: {
				final Intent intent = new Intent(INTENT_ACTION_COMPOSE);
				final Bundle bundle = new Bundle();
				bundle.putLong(INTENT_KEY_ACCOUNT_ID, mAccountId);
				bundle.putBoolean(INTENT_KEY_IS_BUFFER, true);
				bundle.putString(INTENT_KEY_TEXT, getQuoteStatus(getActivity(), screen_name, text_plain));
				intent.putExtras(bundle);
				startActivity(intent);
				break;
			}
			case MENU_REPLY: {
				final Intent intent = new Intent(INTENT_ACTION_COMPOSE);
				final Bundle bundle = new Bundle();
				final List<String> mentions = new Extractor().extractMentionedScreennames(text_plain);
				mentions.remove(screen_name);
				mentions.add(0, screen_name);
				bundle.putStringArray(INTENT_KEY_MENTIONS, mentions.toArray(new String[mentions.size()]));
				bundle.putLong(INTENT_KEY_ACCOUNT_ID, mAccountId);
				bundle.putLong(INTENT_KEY_IN_REPLY_TO_ID, mStatusId);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_SCREEN_NAME, screen_name);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_NAME, name);
				intent.putExtras(bundle);
				startActivity(intent);
				break;
			}
			case MENU_FAV: {
				if (mStatus.is_favorite) {
					mService.destroyFavorite(mAccountId, mStatusId);
				} else {
					mService.createFavorite(mAccountId, mStatusId);
				}
				break;
			}
			case MENU_COPY_CLIPBOARD: {
				final String textToCopy = "@" + mStatus.screen_name + ": " + mStatus.text_plain;
				int sdk = android.os.Build.VERSION.SDK_INT;
				if(sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
				    android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
				    clipboard.setText(textToCopy);
				} else {
				    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); 
				    android.content.ClipData clip = android.content.ClipData.newPlainText("Status", textToCopy);
				    clipboard.setPrimaryClip(clip);
				}
				Toast.makeText(getActivity(), R.string.text_copied, Toast.LENGTH_SHORT).show();
				break;
			}
			case MENU_DELETE: {
				mService.destroyStatus(mAccountId, mStatusId);
				break;
			}
			case MENU_EXTENSIONS: {
				final Intent intent = new Intent(INTENT_ACTION_EXTENSION_OPEN_STATUS);
				final Bundle extras = new Bundle();
				extras.putParcelable(INTENT_KEY_STATUS, mStatus);
				intent.putExtras(extras);
				startActivity(Intent.createChooser(intent, getString(R.string.open_with_extensions)));
				break;
			}
			case MENU_MUTE_SOURCE: {
				final String source = HtmlEscapeHelper.unescape(mStatus.source);
				if (source == null) return false;
		 		final Uri uri = Filters.Sources.CONTENT_URI;
		 		final ContentValues values = new ContentValues();
			 	final SharedPreferences.Editor editor = getSharedPreferences(SHARED_PREFERENCES_NAME,
			 			Context.MODE_PRIVATE).edit();
			 	final ContentResolver resolver = getContentResolver();
		 	    values.put(Filters.TEXT, source);
		 	    resolver.delete(uri, Filters.TEXT + " = '" + source + "'", null);
		 	    resolver.insert(uri, values);
		 	    editor.putBoolean(PREFERENCE_KEY_ENABLE_FILTER, true).commit();
		 	    Toast.makeText(getActivity(), getString(R.string.source_muted, source), Toast.LENGTH_SHORT).show();
		 	    break;
			}
			case MENU_SET_COLOR: {
				final Intent intent = new Intent(INTENT_ACTION_SET_COLOR);
				startActivityForResult(intent, REQUEST_SET_COLOR);
				break;
			}
			case MENU_CLEAR_COLOR: {
				clearUserColor(getActivity(), mStatus.user_id);
				updateUserColor();
				break;
			}
			case MENU_RECENT_TWEETS: {
				openUserTimeline(getActivity(), mAccountId, mStatus.user_id, mStatus.screen_name);
				break;
			}
			case MENU_FIND_RETWEETS: {
				openUserRetweetedStatus(getActivity(), mStatus.account_id, mStatus.retweet_id > 0 ? mStatus.retweet_id
						: mStatus.status_id);
				break;
			}
			default:
				return false;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onStart() {
		super.onStart();
		final IntentFilter filter = new IntentFilter();
		filter.addAction(BROADCAST_FRIENDSHIP_CHANGED);
		filter.addAction(BROADCAST_FAVORITE_CHANGED);
		filter.addAction(BROADCAST_RETWEET_CHANGED);
		filter.addAction(BROADCAST_TWITLONGER_EXPANDED);
		registerReceiver(mStatusReceiver, filter);
		
		if (mStatus != null) {
			displayStatus(mStatus);
		}
		else {
			getStatus(false);
		}
		updateUserColor();
	}

	@Override
	public void onStop() {
		unregisterReceiver(mStatusReceiver);
		super.onStop();
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
			
			Toast.makeText(getActivity(), finalString, Toast.LENGTH_LONG).show();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	}
	
	private void getStatus(final boolean omit_intent_extra) {
		if (mGetStatusTask != null) {
			mGetStatusTask.cancel(true);
		}
		mGetStatusTask = new GetStatusTask(omit_intent_extra);
		mGetStatusTask.execute();

	}

	private void showFollowInfo(final boolean force) {
		if (mFollowInfoDisplayed && !force) return;
		if (mFollowInfoTask != null) {
			mFollowInfoTask.cancel(true);
		}
		mFollowInfoTask = new FollowInfoTask();
		mFollowInfoTask.execute();
	}

	private void showLocationInfo(final boolean force) {
		if (mLocationInfoDisplayed && !force) return;
		if (mLocationInfoTask != null) {
			mLocationInfoTask.cancel(true);
		}
		mLocationInfoTask = new LocationInfoTask();
		mLocationInfoTask.execute();
	}
	
	private void updateUserColor() {
		if (mUserColorLabel != null && mStatus != null) {
			final Drawable d = mUserColorLabel.getBackground();
			if (d != null) {
				d.mutate().setColorFilter(getUserColor(getActivity(), mStatus.user_id), Mode.MULTIPLY);
				mUserColorLabel.invalidate();
			}
		}
	}

	class FollowInfoTask extends AsyncTask<Void, Void, Response<Boolean>> {

		@Override
		protected Response<Boolean> doInBackground(Void... params) {
			return isAllFollowing();
		}

		@Override
		protected void onPostExecute(Response<Boolean> result) {
			if (getActivity() == null) return;
			if (result.exception == null) {
				mFollowIndicator.setVisibility(result.value == null || result.value ? View.GONE : View.VISIBLE);
				if (result.value != null) {
					mFollowButton.setVisibility(result.value ? View.GONE : View.VISIBLE);
					mFollowInfoDisplayed = true;
				}
			}
			mFollowInfoProgress.setVisibility(View.GONE);
			super.onPostExecute(result);
			mFollowInfoTask = null;
		}

		@Override
		protected void onPreExecute() {
			if (getActivity() == null) return;
			mFollowIndicator.setVisibility(View.VISIBLE);
			mFollowButton.setVisibility(View.GONE);
			mFollowInfoProgress.setVisibility(View.VISIBLE);
			super.onPreExecute();
		}

		private Response<Boolean> isAllFollowing() {
			if (mStatus == null) return new Response<Boolean>(null, null);
			if (isMyActivatedAccount(getActivity(), mStatus.user_id)) return new Response<Boolean>(true, null);
			final Twitter twitter = getTwitterInstance(getActivity(), mStatus.account_id, false);
			if (twitter == null) return new Response<Boolean>(null, null);
			try {
				final Relationship result = twitter.showFriendship(mStatus.account_id, mStatus.user_id);
				if (!result.isSourceFollowingTarget()) return new Response<Boolean>(false, null);
			} catch (final TwitterException e) {
				return new Response<Boolean>(null, e);
			}
			return new Response<Boolean>(null, null);
		}
	}

	class GetStatusTask extends AsyncTask<Void, Void, Response<ParcelableStatus>> {

		private final boolean omit_intent_extra;

		public GetStatusTask(boolean omit_intent_extra) {
			this.omit_intent_extra = omit_intent_extra;
		}

		@Override
		protected Response<ParcelableStatus> doInBackground(Void... params) {
			ParcelableStatus status = null;
			if (!omit_intent_extra) {
				status = getArguments().getParcelable(INTENT_KEY_STATUS);
				if (status != null) return new Response<ParcelableStatus>(status, null);
			}
			status = findStatusInDatabases(getActivity(), mAccountId, mStatusId);
			if (status != null) return new Response<ParcelableStatus>(status, null);

			final Twitter twitter = getTwitterInstance(getActivity(), mAccountId, false);
			try {
				return new Response<ParcelableStatus>(new ParcelableStatus(twitter.showStatus(mStatusId), mAccountId,
						false), null);
			} catch (final TwitterException e) {
				return new Response<ParcelableStatus>(null, e);
			}
		}

		@Override
		protected void onPostExecute(Response<ParcelableStatus> result) {
			if (getActivity() == null) return;
			if (result.value == null) {
				showErrorToast(getActivity(), null, result.exception, true);
			} else {
				displayStatus(result.value);
				mStatusLoadProgress.setVisibility(View.GONE);
				mStatusContent.setVisibility(View.VISIBLE);
				mStatusContent.setEnabled(true);
			}
			setProgressBarIndeterminateVisibility(false);
			super.onPostExecute(result);
		}

		@Override
		protected void onPreExecute() {
			if (getActivity() == null) return;
			mStatusLoadProgress.setVisibility(View.VISIBLE);
			mStatusContent.setVisibility(View.INVISIBLE);
			mStatusContent.setEnabled(false);
			setProgressBarIndeterminateVisibility(true);
			super.onPreExecute();
		}

	}

	class LocationInfoTask extends AsyncTask<Void, Void, String> {

		@Override
		protected String doInBackground(Void... params) {
			if (getActivity() == null || mStatus == null) return null;
			final ParcelableLocation location = mStatus.location;
			if (location == null) return null;
			try {
				final Geocoder coder = new Geocoder(getActivity());
				final List<Address> addresses = coder.getFromLocation(location.latitude, location.longitude, 1);
				if (addresses.size() == 1) {
					final Address address = addresses.get(0);
					final StringBuilder builder = new StringBuilder();
					final int max_idx = address.getMaxAddressLineIndex();
					for (int i = 0; i < max_idx; i++) {
						builder.append(address.getAddressLine(i));
						if (i != max_idx - 1) {
							builder.append(", ");
						}
					}
					return builder.toString();

				}
			} catch (final IOException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			if (result != null) {
				mLocationView.setText(result);
				mLocationInfoDisplayed = true;
			} else {
				mLocationView.setText(R.string.view_map);
				mLocationInfoDisplayed = false;
			}
			super.onPostExecute(result);
		}

	}

	class Response<T> {
		public final T value;
		public final TwitterException exception;

		public Response(T value, TwitterException exception) {
			this.value = value;
			this.exception = exception;
		}
	}

}
