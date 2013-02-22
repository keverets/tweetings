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

import static android.os.Environment.getExternalStorageDirectory;
import static android.os.Environment.getExternalStorageState;
import static com.dwdesign.tweetings.util.Utils.clearUserColor;
import static com.dwdesign.tweetings.util.Utils.formatToLongTimeString;
import static com.dwdesign.tweetings.util.Utils.getAccountColor;
import static com.dwdesign.tweetings.util.Utils.getActivatedAccountIds;
import static com.dwdesign.tweetings.util.Utils.getAllAvailableImage;
import static com.dwdesign.tweetings.util.Utils.getBiggerTwitterProfileImage;
import static com.dwdesign.tweetings.util.Utils.getImagePathFromUri;
import static com.dwdesign.tweetings.util.Utils.getOriginalTwitterProfileImage;
import static com.dwdesign.tweetings.util.Utils.getTimestampFromDate;
import static com.dwdesign.tweetings.util.Utils.getTwitterInstance;
import static com.dwdesign.tweetings.util.Utils.getUserColor;
import static com.dwdesign.tweetings.util.Utils.getUserTypeIconRes;
import static com.dwdesign.tweetings.util.Utils.isMyAccount;
import static com.dwdesign.tweetings.util.Utils.isMyActivatedAccount;
import static com.dwdesign.tweetings.util.Utils.isNullOrEmpty;
import static com.dwdesign.tweetings.util.Utils.openIncomingFriendships;
import static com.dwdesign.tweetings.util.Utils.makeCachedUserContentValues;
import static com.dwdesign.tweetings.util.Utils.openImage;
import static com.dwdesign.tweetings.util.Utils.openSavedSearches;
import static com.dwdesign.tweetings.util.Utils.openTweetSearch;
import static com.dwdesign.tweetings.util.Utils.openUserBlocks;
import static com.dwdesign.tweetings.util.Utils.openUserFavorites;
import static com.dwdesign.tweetings.util.Utils.openRetweetsOfMe;
import static com.dwdesign.tweetings.util.Utils.openUserFollowers;
import static com.dwdesign.tweetings.util.Utils.openUserFriends;
import static com.dwdesign.tweetings.util.Utils.openUserListTypes;
import static com.dwdesign.tweetings.util.Utils.openUserMentions;
import static com.dwdesign.tweetings.util.Utils.openUserProfile;
import static com.dwdesign.tweetings.util.Utils.openUserTimeline;
import static com.dwdesign.tweetings.util.Utils.parseString;
import static com.dwdesign.tweetings.util.Utils.parseURL;
import static com.dwdesign.tweetings.util.Utils.setUserColor;
import static com.dwdesign.tweetings.util.Utils.getAccountScreenName;
import static com.dwdesign.tweetings.util.Utils.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import com.dwdesign.popupmenu.PopupMenu;
import com.dwdesign.popupmenu.PopupMenu.OnMenuItemClickListener;
import com.dwdesign.tweetings.HttpClientFactory;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.adapter.ListActionAdapter;
import com.dwdesign.tweetings.fragment.StatusFragment.FollowInfoTask;
import com.dwdesign.tweetings.fragment.StatusFragment.Response;
import com.dwdesign.tweetings.model.ImageSpec;
import com.dwdesign.tweetings.model.ListAction;
import com.dwdesign.tweetings.model.Panes;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.model.ParcelableUser;
import com.dwdesign.tweetings.provider.TweetStore.Accounts;
import com.dwdesign.tweetings.provider.TweetStore.CachedUsers;
import com.dwdesign.tweetings.provider.TweetStore.Filters;
import com.dwdesign.tweetings.util.GetExternalCacheDirAccessor;
import com.dwdesign.tweetings.util.ImageLoaderWrapper;
import com.dwdesign.tweetings.util.ServiceInterface;
import com.dwdesign.tweetings.util.TwidereLinkify;
import com.dwdesign.tweetings.util.TwidereLinkify.OnLinkClickListener;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import twitter4j.Paging;
import twitter4j.Relationship;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.DialogFragment;
import android.text.InputFilter;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class UserProfileFragment extends BaseListFragment implements OnClickListener, OnLongClickListener,
		OnItemClickListener, OnItemLongClickListener, OnMenuItemClickListener, OnLinkClickListener, Panes.Right {

	private ImageLoaderWrapper mLazyImageLoader;

	private ImageView mProfileImageView, mProfileBackgroundView;
	private GetFriendshipTask mGetFriendshipTask;
	private UpdateFriendshipTask mUpdateFriendshipTask;
	private View mFollowContainer, mMoreOptionsContainer;
	private TextView mNameView, mScreenNameView, mDescriptionView, mLocationView, mURLView, mCreatedAtView,
			mTweetCount, mFollowersCount, mFriendsCount, mFollowedYouIndicator, mErrorMessageView;
	private View mNameContainer, mProfileImageContainer, mDescriptionContainer, mLocationContainer, mURLContainer,
		mTweetsContainer, mFollowersContainer, mFriendsContainer, mProfileNameContainer;
	private ProgressBar mFollowProgress, mMoreOptionsProgress;
	private Button mFollowButton, mMoreOptionsButton, mRetryButton;
	private ListActionAdapter mAdapter;
	private boolean mDisplaySensitiveContents;

	private ListView mListView;
	private UserInfoTask mUserInfoTask;
	private View mHeaderView;
	private long mAccountId;
	private Relationship mFriendship;
	private final DialogFragment mDialogFragment = new EditTextDialogFragment();
	private Uri mImageUri;
	private User mUser = null;
	
	private boolean tracking = false;

	private static final int TYPE_NAME = 1;

	private static final int TYPE_URL = 2;

	private static final int TYPE_LOCATION = 3;

	private static final int TYPE_DESCRIPTION = 4;

	private long mUserId;

	private String mScreenName;

	private ServiceInterface mService;

	private PopupMenu mPopupMenu;
	private TrackingTask mTrackingTask;
	
	
	private Gallery mRecentPhotosGallery;
	private MediaTimelineTask mMediaTimelineTask;
	private ArrayList<ParcelableStatus> mMediaStatuses;

	private SharedPreferences mPreferences;
	private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			if (mUser == null) return;
			final String action = intent.getAction();
			if (BROADCAST_FRIENDSHIP_CHANGED.equals(action)) {
				if (intent.getLongExtra(INTENT_KEY_USER_ID, -1) == mUser.getId()
						&& intent.getBooleanExtra(INTENT_KEY_SUCCEED, false)) {
					getFriendship();
				}
			}
			if (BROADCAST_BLOCKSTATE_CHANGED.equals(action)) {
				if (intent.getLongExtra(INTENT_KEY_USER_ID, -1) == mUser.getId()
						&& intent.getBooleanExtra(INTENT_KEY_SUCCEED, false)) {
					getFriendship();
				}
			}
			if (BROADCAST_PROFILE_UPDATED.equals(action)) {
				if (intent.getLongExtra(INTENT_KEY_USER_ID, -1) == mUser.getId()
						&& intent.getBooleanExtra(INTENT_KEY_SUCCEED, false)) {
					reloadUserInfo();
				}
			}
		}
	};

	private View mListContainer, mErrorRetryContainer;

	public void changeUser(final long account_id, final User user) {
		mFriendship = null;
		mUserId = -1;
		mAccountId = -1;
		if (user == null || user.getId() <= 0 || getActivity() == null
				|| !isMyActivatedAccount(getActivity(), account_id)) return;
		if (mUserInfoTask != null && mUserInfoTask.getStatus() == AsyncTask.Status.RUNNING) {
			mUserInfoTask.cancel(true);
		}
		final boolean is_my_activated_account = isMyActivatedAccount(getActivity(), user.getId());
		mUserInfoTask = null;
		mErrorRetryContainer.setVisibility(View.GONE);
		mAccountId = account_id;
		mUserId = user.getId();
		mScreenName = user.getScreenName();
		
		updateUserColor();
		final boolean is_multiple_account_enabled = getActivatedAccountIds(getActivity()).length > 1;

		mListView.setBackgroundResource(is_multiple_account_enabled ? R.drawable.ic_label_account_nopadding : 0);
		if (is_multiple_account_enabled) {
			final Drawable d = mListView.getBackground();
			if (d != null) {
				d.mutate().setColorFilter(getAccountColor(getActivity(), account_id), PorterDuff.Mode.MULTIPLY);
				mListView.invalidate();
			}
		}

		mNameView.setText(user.getName());
		mScreenNameView.setText("@" + user.getScreenName());
		mScreenNameView.setCompoundDrawablesWithIntrinsicBounds(
				getUserTypeIconRes(user.isVerified(), user.isProtected()), 0, 0, 0);
		final String description = user.getDescription();
		mDescriptionContainer.setVisibility(is_my_activated_account || !isNullOrEmpty(description) ? View.VISIBLE
				: View.GONE);
		mDescriptionContainer.setOnLongClickListener(this);
		mDescriptionView.setText(description);
		final TwidereLinkify linkify = new TwidereLinkify(mDescriptionView);
		linkify.setOnLinkClickListener(this);
		linkify.addAllLinks();
		mDescriptionView.setMovementMethod(LinkMovementMethod.getInstance());
		final String location = user.getLocation();
		mLocationContainer
				.setVisibility(is_my_activated_account || !isNullOrEmpty(location) ? View.VISIBLE : View.GONE);
		mLocationContainer.setOnLongClickListener(this);
		mLocationView.setText(location);
		final String url = user.getURL() != null ? user.getURL().toString() : null;
		mURLContainer.setVisibility(is_my_activated_account || !isNullOrEmpty(url) ? View.VISIBLE : View.GONE);
		mURLContainer.setOnLongClickListener(this);
		mURLView.setText(url);
		mCreatedAtView.setText(formatToLongTimeString(getActivity(), getTimestampFromDate(user.getCreatedAt())));
		mTweetCount.setText(String.valueOf(user.getStatusesCount()));
		mFollowersCount.setText(String.valueOf(user.getFollowersCount()));
		mFriendsCount.setText(String.valueOf(user.getFriendsCount()));
		// final boolean display_profile_image =
		// mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_PROFILE_IMAGE, true);
		// mProfileImageView.setVisibility(display_profile_image ? View.VISIBLE
		// : View.GONE);
		// if (display_profile_image) {
		final String profile_image_url_string = parseString(user.getProfileImageURL());
		final boolean hires_profile_image = getResources().getBoolean(R.bool.hires_profile_image);
		mLazyImageLoader.displayProfileImage(mProfileImageView, 
				hires_profile_image ? getBiggerTwitterProfileImage(profile_image_url_string)
						: profile_image_url_string);
		// }
		
		String profile_banner_url_string = parseString(user.getProfileBannerImageUrl());
		if (profile_banner_url_string != null) {
			final int def_width = getResources().getDisplayMetrics().widthPixels;
			profile_banner_url_string = profile_banner_url_string + "/" + getBestBannerType(def_width);
		}
		final String banner_url = profile_banner_url_string;
		if (mProfileBackgroundView != null) {
			mProfileBackgroundView.setScaleType(ImageView.ScaleType.CENTER_CROP);
			if (banner_url != null) {
				mLazyImageLoader.displayPreviewImage(mProfileBackgroundView, 
						banner_url);
			}
			else {
				final Drawable d = getResources().getDrawable(R.drawable.linen);
				mProfileBackgroundView.setImageDrawable(d);
			}
		}
		
		
		mUser = user;
		if (isMyAccount(getActivity(), user.getId())) {
			final ContentResolver resolver = getContentResolver();
			final ContentValues values = new ContentValues();
			final URL profile_image_url = user.getProfileImageURL();
			if (profile_image_url != null) {
				values.put(Accounts.PROFILE_IMAGE_URL, profile_image_url.toString());
			}
			values.put(Accounts.USERNAME, user.getScreenName());
			final String where = Accounts.USER_ID + " = " + user.getId() + " AND 1 = 1";
			resolver.update(Accounts.CONTENT_URI, values, where, null);
		}
		mAdapter.add(new UserRecentPhotosAction());
		mAdapter.add(new FavoritesAction());
		mAdapter.add(new UserMentionsAction());
		mAdapter.add(new UserListTypesAction());
		if (user.getId() == mAccountId) {
			mAdapter.add(new MyTweetsRetweetedAction());
			mAdapter.add(new SavedSearchesAction());
			boolean nativeMapSupported = true;
			try {
				Class.forName("com.google.android.maps.MapActivity");
				Class.forName("com.google.android.maps.MapView");
			} catch (final ClassNotFoundException e) {
				nativeMapSupported = false;
			}
			if (nativeMapSupported) {
				mAdapter.add(new UserNearbyAction());
			}
			if (user.isProtected()) {
				mAdapter.add(new IncomingFriendshipsAction());
			}
			mAdapter.add(new UserBlocksAction());
		}
		mAdapter.notifyDataSetChanged();
		
		if (mRecentPhotosGallery != null) {
			mRecentPhotosGallery.setVisibility(View.GONE);
			mRecentPhotosGallery.setAdapter(new ImageAdapter(this.getActivity()));
			mRecentPhotosGallery.setOnItemClickListener(new OnItemClickListener() {

				public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
					ParcelableStatus pStatus = mMediaStatuses.get(position);
	            	final ImageSpec spec = getAllAvailableImage(pStatus.image_orig_url_string);
	            	if (spec != null) {
	            		openImage(UserProfileFragment.this.getActivity(), Uri.parse(spec.full_image_link), pStatus.is_possibly_sensitive);
					}
				}

			});
			
			mMediaTimelineTask = new MediaTimelineTask(this.getActivity(), mAccountId, mUser.getScreenName());
	        if (mMediaTimelineTask != null) {
	        	mMediaTimelineTask.execute();
			}
		}
		
		getFriendship();
		checkPushTracked();
	}
	
	private static String getBestBannerType(final int width) {
		if (width <= 320)
			return "mobile";
	 	else if (width <= 520)
	 		return "web";
	 	else if (width <= 626)
	 		return "ipad";
	 	else if (width <= 640)
	 		return "mobile_retina";
	 	else if (width <= 1040)
	 		return "web_retina";
	 	else
	 		return "ipad_retina";
	}
	
	public static Bitmap createAlphaGradientBitmap(Bitmap orig) {
		final int width = orig.getWidth(), height = orig.getHeight();
	 	final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
	 	final Canvas canvas = new Canvas(bitmap);  
	 	final Paint paint = new Paint();
	 	final LinearGradient shader = new LinearGradient(width / 2, 0, width / 2, height, 0xffffffff, 0x00ffffff, Shader.TileMode.CLAMP);
	 	paint.setShader(shader);
	 	paint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
	 	canvas.drawBitmap(orig,0, 0, null);
	 	canvas.drawRect(0, 0, width, height, paint);
	 	return bitmap;
	 }
	
	public class ImageAdapter extends BaseAdapter {
		/** The parent context */
		private Context myContext;
		
		public ImageAdapter(final Context c) {
			this.myContext = c;
		}

		// inherited abstract methods - must be implemented
		// Returns count of images, and individual IDs
		public int getCount() {
			if (mMediaStatuses == null) {
        		return 0;
        	}
            return mMediaStatuses.size();
		}

		public Object getItem(final int position) {
			return position;
		}

		public long getItemId(final int position) {
			return position;
		}
		
		/** Returns the size (0.0f to 1.0f) of the views
         * depending on the 'offset' to the center. */
        public float getScale(final boolean focused, final int offset) {
                /* Formula: 1 / (2 ^ offset) */
            return Math.max(0, 1.0f / (float)Math.pow(2, Math.abs(offset)));
        }
		
		// Returns a new ImageView to be displayed,
		public View getView(final int position, final View convertView, 
				final ViewGroup parent) {

			ImageView i;
            if (convertView == null) { 
            	i = new ImageView(UserProfileFragment.this.getActivity());
                i.setScaleType(ImageView.ScaleType.CENTER_CROP);
                i.setLayoutParams(new Gallery.LayoutParams(100, 100));
           
            }
            else {
            	i = (ImageView) convertView;
            }
            if (mMediaStatuses != null && mMediaStatuses.size() >= 1) {
            	ParcelableStatus pStatus = mMediaStatuses.get(position);
            	if (pStatus.image_preview_url_string != null) {
            		if (pStatus.is_possibly_sensitive && !mDisplaySensitiveContents) {
            			i.setImageResource(R.drawable.image_preview_nsfw);
    				} else {
    					UrlImageViewHelper.setUrlDrawable(i, pStatus.image_preview_url_string);
    				}
            	}
            	//i.setImageURI(Uri.parse(pStatus.image_preview_url_string));
            }
            return i;
		}
	}

	public void getUserInfo(final long account_id, final long user_id, final String screen_name) {
		mAccountId = account_id;
		mUserId = user_id;
		mScreenName = screen_name;
		if (mUserInfoTask != null && mUserInfoTask.getStatus() == AsyncTask.Status.RUNNING) {
			mUserInfoTask.cancel(true);
		}
		mUserInfoTask = null;
		if (!isMyActivatedAccount(getActivity(), mAccountId)) {
			mListContainer.setVisibility(View.GONE);
			mErrorRetryContainer.setVisibility(View.GONE);
			return;
		}

		if (user_id != -1) {
			mUserInfoTask = new UserInfoTask(getActivity(), account_id, user_id);
		} else if (screen_name != null) {
			mUserInfoTask = new UserInfoTask(getActivity(), account_id, screen_name);
		} else {
			mListContainer.setVisibility(View.GONE);
			mErrorRetryContainer.setVisibility(View.GONE);
			return;
		}

		if (mUserInfoTask != null) {
			mUserInfoTask.execute();
		}
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		mService = getApplication().getServiceInterface();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mDisplaySensitiveContents = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_SENSITIVE_CONTENTS, false);
		
		super.onActivityCreated(savedInstanceState);
		final Bundle args = getArguments();
		long account_id = -1, user_id = -1;
		String screen_name = null;
		if (args != null) {
			account_id = args.getLong(INTENT_KEY_ACCOUNT_ID, -1);
			user_id = args.getLong(INTENT_KEY_USER_ID, -1);
			screen_name = args.getString(INTENT_KEY_SCREEN_NAME);
		}
		mLazyImageLoader = getApplication().getImageLoaderWrapper();
		mAdapter = new ListActionAdapter(getActivity());
		mProfileImageContainer.setOnClickListener(this);
		mProfileImageContainer.setOnLongClickListener(this);
		mNameContainer.setOnClickListener(this);
		mNameContainer.setOnLongClickListener(this);
		mFollowButton.setOnClickListener(this);
		mTweetsContainer.setOnClickListener(this);
		mFollowersContainer.setOnClickListener(this);
		mFriendsContainer.setOnClickListener(this);
		mRetryButton.setOnClickListener(this);
		mMoreOptionsButton.setOnClickListener(this);
		setListAdapter(null);
		mListView = getListView();
		mListView.addHeaderView(mHeaderView, null, false);
		mListView.setOnItemClickListener(this);
		mListView.setOnItemLongClickListener(this);
		setListAdapter(mAdapter);
		getUserInfo(account_id, user_id, screen_name);

	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
		if (intent == null) return;
		switch (requestCode) {
			case REQUEST_TAKE_PHOTO: {
				if (resultCode == Activity.RESULT_OK) {
					final String path = mImageUri.getPath();
					final File file = path != null ? new File(path) : null;
					if (file != null && file.exists()) {
						mService.updateProfileImage(mUser.getId(), mImageUri, true);
					}
				}
				break;
			}
			case REQUEST_BANNER_TAKE_PHOTO: {
				if (resultCode == Activity.RESULT_OK) {
					final String path = mImageUri.getPath();
					final File file = path != null ? new File(path) : null;
					if (file != null && file.exists()) {
						mService.updateBannerImage(mUser.getId(), mImageUri, true);
					}
				}
				break;
			}
			case REQUEST_PICK_IMAGE: {
				if (resultCode == Activity.RESULT_OK && intent != null) {
					final Uri uri = intent.getData();
					final String image_path = getImagePathFromUri(getActivity(), uri);
					final File file = image_path != null ? new File(image_path) : null;
					if (file != null && file.exists()) {
						mService.updateProfileImage(mUser.getId(), Uri.fromFile(file), false);
					}
				}
				break;
			}
			case REQUEST_BANNER_PICK_IMAGE: {
				if (resultCode == Activity.RESULT_OK && intent != null) {
					final Uri uri = intent.getData();
					final String image_path = getImagePathFromUri(getActivity(), uri);
					final File file = image_path != null ? new File(image_path) : null;
					if (file != null && file.exists()) {
						mService.updateBannerImage(mUser.getId(), Uri.fromFile(file), false);
					}
				}
				break;
			}
			case REQUEST_SET_COLOR: {
				if (resultCode == Activity.RESULT_OK && intent != null) {
					final int color = intent.getIntExtra(Accounts.USER_COLOR, Color.TRANSPARENT);
					setUserColor(getActivity(), mUserId, color);
					updateUserColor();
				}
				break;
			}
		}

	}

	@Override
	public void onClick(final View view) {
		switch (view.getId()) {
			case R.id.follow: {
				if (mUser != null && mAccountId != mUser.getId()) {
					mFollowProgress.setVisibility(View.VISIBLE);
					mFollowButton.setVisibility(View.GONE);
					if (mFriendship.isSourceFollowingTarget()) {
						mService.destroyFriendship(mAccountId, mUser.getId());
					} else {
						mService.createFriendship(mAccountId, mUser.getId());
					}
				}
				break;
			}
			case R.id.retry: {
				reloadUserInfo();
				break;
			}
			case R.id.name_container: {
				if (mUser != null) {
				}
				break;
			}
			case R.id.profile_image_container: {
				final String profile_image_url_string = getOriginalTwitterProfileImage(parseString(mUser.getProfileImageURL()));
				if (profile_image_url_string == null) return;
				final Uri uri = Uri.parse(profile_image_url_string);
				openImage(getActivity(), uri, false);
				break;
			}
			case R.id.tweets_container: {
				if (mUser == null) return;
				openUserTimeline(getActivity(), mAccountId, mUser.getId(), mUser.getScreenName());
				break;
			}
			case R.id.followers_container: {
				if (mUser == null) return;
				openUserFollowers(getActivity(), mAccountId, mUser.getId(), mUser.getScreenName());
				break;
			}
			case R.id.friends_container: {
				if (mUser == null) return;
				openUserFriends(getActivity(), mAccountId, mUser.getId(), mUser.getScreenName());
				break;
			}
			case R.id.more_options: {
				if (mUser == null || mFriendship == null) return;
				if (!isMyActivatedAccount(getActivity(), mUser.getId())) {
					mPopupMenu = PopupMenu.getInstance(getActivity(), view);
					mPopupMenu.inflate(R.menu.action_user_profile);
					final Menu menu = mPopupMenu.getMenu();
					final MenuItem blockItem = menu.findItem(MENU_BLOCK);
					final MenuItem trackItem = menu.findItem(MENU_TRACKING);
					if (blockItem != null) {
						final Drawable blockIcon = blockItem.getIcon();
						if (mFriendship.isSourceBlockingTarget()) {
							blockItem.setTitle(R.string.unblock);
							blockIcon.mutate().setColorFilter(getResources().getColor(R.color.holo_blue_bright),
									PorterDuff.Mode.MULTIPLY);
						} else {
							blockItem.setTitle(R.string.block);
							blockIcon.clearColorFilter();
						}
					}
					if (trackItem != null) {
						if (mPreferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_PUSH_NOTIFICATIONS, false) == false) {
							trackItem.setVisible(false);
						}
						else {
							if (tracking == true) {
								trackItem.setTitle(R.string.untrack_user);
							}
							else {
								trackItem.setTitle(R.string.track_user);
							}
						}
					}
					final MenuItem sendDirectMessageItem = menu.findItem(MENU_SEND_DIRECT_MESSAGE);
					if (sendDirectMessageItem != null) {
						sendDirectMessageItem.setVisible(mFriendship.isTargetFollowingSource());
					}
					final MenuItem wantRetweetsItem = menu.findItem(MENU_WANT_RETWEETS);
					if (mFriendship.wantRetweets() == true) {
						wantRetweetsItem.setTitle(R.string.disable_retweet);
					}
					else {
						wantRetweetsItem.setTitle(R.string.enable_retweet);
					}
					mPopupMenu.setOnMenuItemClickListener(this);
					mPopupMenu.show();
				}
				break;
			}
		}

	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		mHeaderView = inflater.inflate(R.layout.user_profile_header, null, false);
		mNameContainer = mHeaderView.findViewById(R.id.name_container);
		mNameView = (TextView) mHeaderView.findViewById(R.id.name);
		mScreenNameView = (TextView) mHeaderView.findViewById(R.id.screen_name);
		mDescriptionView = (TextView) mHeaderView.findViewById(R.id.description);
		mLocationView = (TextView) mHeaderView.findViewById(R.id.location);
		mURLView = (TextView) mHeaderView.findViewById(R.id.url);
		mCreatedAtView = (TextView) mHeaderView.findViewById(R.id.created_at);
		mTweetsContainer = mHeaderView.findViewById(R.id.tweets_container);
		mTweetCount = (TextView) mHeaderView.findViewById(R.id.tweet_count);
		mFollowersContainer = mHeaderView.findViewById(R.id.followers_container);
		mFollowersCount = (TextView) mHeaderView.findViewById(R.id.followers_count);
		mFriendsContainer = mHeaderView.findViewById(R.id.friends_container);
		mFriendsCount = (TextView) mHeaderView.findViewById(R.id.friends_count);
		mProfileNameContainer = mHeaderView.findViewById(R.id.profile_name_container);
		mProfileImageView = (ImageView) mHeaderView.findViewById(R.id.profile_image);
		mProfileBackgroundView = (ImageView) mHeaderView.findViewById(R.id.profile_background_image);
		mProfileImageContainer = mHeaderView.findViewById(R.id.profile_image_container);
		mDescriptionContainer = mHeaderView.findViewById(R.id.description_container);
		mLocationContainer = mHeaderView.findViewById(R.id.location_container);
		mURLContainer = mHeaderView.findViewById(R.id.url_container);
		mFollowContainer = mHeaderView.findViewById(R.id.follow_container);
		mFollowButton = (Button) mHeaderView.findViewById(R.id.follow);
		mFollowProgress = (ProgressBar) mHeaderView.findViewById(R.id.follow_progress);
		mMoreOptionsContainer = mHeaderView.findViewById(R.id.more_options_container);
		mMoreOptionsButton = (Button) mHeaderView.findViewById(R.id.more_options);
		mMoreOptionsProgress = (ProgressBar) mHeaderView.findViewById(R.id.more_options_progress);
		mFollowedYouIndicator = (TextView) mHeaderView.findViewById(R.id.followed_you_indicator);
		mRecentPhotosGallery = (Gallery) mHeaderView.findViewById(R.id.recent_photos_gallery);
		mListContainer = super.onCreateView(inflater, container, savedInstanceState);
		final View container_view = inflater.inflate(R.layout.list_with_error_message, null);
		((FrameLayout) container_view.findViewById(R.id.list_container)).addView(mListContainer);
		mErrorRetryContainer = container_view.findViewById(R.id.error_retry_container);
		mRetryButton = (Button) container_view.findViewById(R.id.retry);
		mErrorMessageView = (TextView) container_view.findViewById(R.id.error_message);
		return container_view;
	}

	@Override
	public void onDestroyView() {
		mUser = null;
		mFriendship = null;
		mMediaStatuses = null;
		if (mGetFriendshipTask != null) {
			mGetFriendshipTask.cancel(true);
		}
		if (mUpdateFriendshipTask != null) {
			mUpdateFriendshipTask.cancel(true);
		}
		if (mUserInfoTask != null) {
			mUserInfoTask.cancel(true);
		}
		if (mMediaTimelineTask != null) {
			mMediaTimelineTask.cancel(true);
		}
		if (mTrackingTask != null) {
			mTrackingTask.cancel(true);
		}
		super.onDestroyView();
	}

	@Override
	public void onItemClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		final ListAction action = mAdapter.findItem(id);
		if (action != null) {
			action.onClick();
		}
	}

	@Override
	public boolean onItemLongClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		final ListAction action = mAdapter.findItem(id);
		if (action != null) return action.onLongClick();
		return false;
	}

	@Override
	public void onLinkClick(final String link, final int type) {
		if (mUser == null) return;
		switch (type) {
			case TwidereLinkify.LINK_TYPE_MENTION_LIST: {
				openUserProfile(getActivity(), mAccountId, -1, link);
				break;
			}
			case TwidereLinkify.LINK_TYPE_HASHTAG: {
				openTweetSearch(getActivity(), mAccountId, link);
				break;
			}
			case TwidereLinkify.LINK_TYPE_LINK_WITH_IMAGE_EXTENSION: {
				openImage(getActivity(), Uri.parse(link), false);
				break;
			}
			case TwidereLinkify.LINK_TYPE_LINK: {
				final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
				startActivity(intent);
				break;
			}
			case TwidereLinkify.LINK_TYPE_LIST: {
				final String[] mention_list = link.split("\\/");
				if (mention_list == null || mention_list.length != 2) {
					break;
				}
				break;
			}
		}
	}

	@Override
	public boolean onLongClick(final View view) {
		if (mUser == null) return false;
		final boolean is_my_activated_account = isMyActivatedAccount(getActivity(), mUser.getId());
		if (!is_my_activated_account) return false;
		switch (view.getId()) {
			case R.id.profile_image_container: {
				mPopupMenu = PopupMenu.getInstance(getActivity(), view);
				mPopupMenu.inflate(R.menu.action_profile_image);
				mPopupMenu.setOnMenuItemClickListener(this);
				mPopupMenu.show();
				return true;
			}
			case R.id.name_container: {
				final Bundle args = new Bundle();
				args.putLong(INTENT_KEY_ACCOUNT_ID, mUser.getId());
				args.putString(INTENT_KEY_TEXT, mUser.getName());
				args.putString(INTENT_KEY_TITLE, getString(R.string.name));
				args.putInt(INTENT_KEY_TYPE, TYPE_NAME);
				mDialogFragment.setArguments(args);
				mDialogFragment.show(getFragmentManager(), "edit_name");
				return true;
			}
			case R.id.description_container: {
				final Bundle args = new Bundle();
				args.putLong(INTENT_KEY_ACCOUNT_ID, mUser.getId());
				args.putString(INTENT_KEY_TEXT, mUser.getDescription());
				args.putString(INTENT_KEY_TITLE, getString(R.string.description));
				args.putInt(INTENT_KEY_TYPE, TYPE_DESCRIPTION);
				mDialogFragment.setArguments(args);
				mDialogFragment.show(getFragmentManager(), "edit_description");
				return true;
			}
			case R.id.location_container: {
				final Bundle args = new Bundle();
				args.putLong(INTENT_KEY_ACCOUNT_ID, mUser.getId());
				args.putString(INTENT_KEY_TEXT, mUser.getLocation());
				args.putString(INTENT_KEY_TITLE, getString(R.string.location));
				args.putInt(INTENT_KEY_TYPE, TYPE_LOCATION);
				mDialogFragment.setArguments(args);
				mDialogFragment.show(getFragmentManager(), "edit_location");
				return true;
			}
			case R.id.url_container: {
				final URL url = mUser.getURL();
				final Bundle args = new Bundle();
				args.putLong(INTENT_KEY_ACCOUNT_ID, mUser.getId());
				args.putString(INTENT_KEY_TEXT, url != null ? url.toString() : null);
				args.putString(INTENT_KEY_TITLE, getString(R.string.url));
				args.putInt(INTENT_KEY_TYPE, TYPE_URL);
				mDialogFragment.setArguments(args);
				mDialogFragment.show(getFragmentManager(), "edit_url");
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onMenuItemClick(final MenuItem item) {
		if (mUser == null || mService == null) return false;
		switch (item.getItemId()) {
			case MENU_TAKE_PHOTO: {
				takePhoto();
				break;
			}
			case MENU_ADD_IMAGE: {
				pickImage();
				break;
			}
			case MENU_BANNER_TAKE_PHOTO: {
				takeBannerPhoto();
				break;
			}
			case MENU_BANNER_ADD_IMAGE: {
				pickBannerImage();
				break;
			}
			case MENU_TRACKING: {
				UpdateTrackingTask task = new UpdateTrackingTask(!tracking);
				task.execute();
				break;
			}
			case MENU_BLOCK: {
				if (mService == null || mFriendship == null) {
					break;
				}
				if (mFriendship.isSourceBlockingTarget()) {
					mService.destroyBlock(mAccountId, mUser.getId());
				} else {
					mService.createBlock(mAccountId, mUser.getId());
				}
				break;
			}
			case MENU_REPORT_SPAM: {
				mService.reportSpam(mAccountId, mUser.getId());
				break;
			}
			case MENU_MUTE_USER: {
				final String screen_name = mUser.getScreenName();
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
			case MENU_MENTION: {
				final Intent intent = new Intent(INTENT_ACTION_COMPOSE);
				final Bundle bundle = new Bundle();
				final String name = mUser.getName();
				final String screen_name = mUser.getScreenName();
				bundle.putLong(INTENT_KEY_ACCOUNT_ID, mAccountId);
				bundle.putString(INTENT_KEY_TEXT, "@" + screen_name + " ");
				bundle.putString(INTENT_KEY_IN_REPLY_TO_SCREEN_NAME, screen_name);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_NAME, name);
				intent.putExtras(bundle);
				startActivity(intent);
				break;
			}
			case MENU_SEND_DIRECT_MESSAGE: {
				final Uri.Builder builder = new Uri.Builder();
				builder.scheme(SCHEME_TWEETINGS);
				builder.authority(AUTHORITY_DIRECT_MESSAGES_CONVERSATION);
				builder.appendQueryParameter(QUERY_PARAM_ACCOUNT_ID, String.valueOf(mAccountId));
				builder.appendQueryParameter(QUERY_PARAM_CONVERSATION_ID, String.valueOf(mUser.getId()));
				startActivity(new Intent(Intent.ACTION_VIEW, builder.build()));
				break;
			}
			case MENU_VIEW_ON_TWITTER: {
				Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://twitter.com/" + mUser.getScreenName()));
				startActivity(browserIntent);
				break;
			}
			case MENU_WANT_RETWEETS: {
				updateFriendship();
				break;
			}
			case MENU_EXTENSIONS: {
				final Intent intent = new Intent(INTENT_ACTION_EXTENSION_OPEN_USER);
				final Bundle extras = new Bundle();
				extras.putParcelable(INTENT_KEY_USER, new ParcelableUser(mUser, mAccountId));
				intent.putExtras(extras);
				startActivity(Intent.createChooser(intent, getString(R.string.open_with_extensions)));
				break;
			}
			case MENU_SET_COLOR: {
				final Intent intent = new Intent(INTENT_ACTION_SET_COLOR);
				startActivityForResult(intent, REQUEST_SET_COLOR);
				break;
			}
			case MENU_CLEAR_COLOR: {
				clearUserColor(getActivity(), mUserId);
				updateUserColor();
				break;
			}
		}
		return true;
	}

	@Override
	public void onStart() {
		super.onStart();
		final IntentFilter filter = new IntentFilter(BROADCAST_FRIENDSHIP_CHANGED);
		filter.addAction(BROADCAST_BLOCKSTATE_CHANGED);
		filter.addAction(BROADCAST_PROFILE_UPDATED);
		registerReceiver(mStatusReceiver, filter);
		updateUserColor();
	}

	@Override
	public void onStop() {
		unregisterReceiver(mStatusReceiver);
		super.onStop();
	}
	
	private void updateFriendship() {
		if (mUpdateFriendshipTask != null) {
			mUpdateFriendshipTask.cancel(true);
		}
		mUpdateFriendshipTask = new UpdateFriendshipTask();
		mUpdateFriendshipTask.execute();
	}
	
	private void checkPushTracked() {
		if (!mUser.isProtected()) {
			
		}
	}
	
	class UpdateTrackingTask extends AsyncTask<Void, Void, Boolean> {
		
		private boolean setTracking = false;

		private UpdateTrackingTask(final boolean tracking) {
			this.setTracking = tracking;
		}
		
		@Override
		protected Boolean doInBackground(Void... params) {
			return updateTracking();
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (getActivity() == null) return;
			tracking = result;
			if (tracking == true) {
				Toast.makeText(getActivity(), R.string.track_start, Toast.LENGTH_LONG).show();
				
			}
			super.onPostExecute(result);
		}

		@Override
		protected void onPreExecute() {
			if (getActivity() == null) return;
			super.onPreExecute();
		}

		private Boolean updateTracking() {
			if (mUser == null) return false;
			final Twitter twitter = getTwitterInstance(getActivity(), mAccountId, false);
			if (twitter == null) return false;
			String trackType = "add";
			if (setTracking == false) {
				trackType = "remove";
			}
			try {
				HttpClient client = HttpClientFactory.getThreadSafeClient();
				final String accountScreenName = getAccountScreenName(getActivity().getApplicationContext(), mAccountId);
				final String finalUrl = TWEETINGS_TRACKING_URL +"?u=" + accountScreenName + "&m=" + trackType + "&t=" + mUser.getScreenName() + "&ti=" + String.valueOf(mUser.getId());
				HttpResponse response = client.execute(new HttpGet(finalUrl));
				
				BufferedReader reader = new BufferedReader(new InputStreamReader(
				response.getEntity().getContent(), "UTF-8"));
				String sResponse;
				StringBuilder s = new StringBuilder();
	
				while ((sResponse = reader.readLine()) != null) {
					s = s.append(sResponse);
				}
				
				
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		    
			return !setTracking;
		}
	}
	
	class TrackingTask extends AsyncTask<Void, Void, Boolean> {

		@Override
		protected Boolean doInBackground(Void... params) {
			return isTracking();
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (getActivity() == null) return;
			tracking = result;
			super.onPostExecute(result);
		}

		@Override
		protected void onPreExecute() {
			if (getActivity() == null) return;
			super.onPreExecute();
		}

		private Boolean isTracking() {
			boolean isTracking = false;
			if (mUser == null) return false;
			final Twitter twitter = getTwitterInstance(getActivity(), mAccountId, false);
			if (twitter == null) return false;
			try {
				HttpClient client = HttpClientFactory.getThreadSafeClient();
				final String accountScreenName = getAccountScreenName(getActivity().getApplicationContext(), mAccountId);
				final String finalUrl = TWEETINGS_TRACKING_URL +"?u=" + accountScreenName + "&m=view&t=" + mUser.getScreenName();
				HttpResponse response = client.execute(new HttpGet(finalUrl));
				
				BufferedReader reader = new BufferedReader(new InputStreamReader(
				response.getEntity().getContent(), "UTF-8"));
				String sResponse;
				StringBuilder s = new StringBuilder();
	
				while ((sResponse = reader.readLine()) != null) {
					s = s.append(sResponse);
				}
				if (s.toString().equals("1")) {
					isTracking = true;
				}
			
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		    
			return isTracking;
		}
	}

	private void getFriendship() {
		if (mGetFriendshipTask != null) {
			mGetFriendshipTask.cancel(true);
		}
		mGetFriendshipTask = new GetFriendshipTask();
		mGetFriendshipTask.execute();
	}

	private void pickImage() {
		final Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		intent.setType("image/*");
		startActivityForResult(intent, REQUEST_PICK_IMAGE);
	}
	
	private void pickBannerImage() {
		final Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		intent.setType("image/*");
		startActivityForResult(intent, REQUEST_BANNER_PICK_IMAGE);
	}

	private void reloadUserInfo() {
		getUserInfo(mAccountId, mUserId, mScreenName);
	}

	private void takePhoto() {
		if (getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			final File cache_dir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? GetExternalCacheDirAccessor
					.getExternalCacheDir(getActivity()) : new File(getExternalStorageDirectory().getPath()
					+ "/Android/data/" + getActivity().getPackageName() + "/cache/");
			final File file = new File(cache_dir, "tmp_photo_" + System.currentTimeMillis() + ".jpg");
			mImageUri = Uri.fromFile(file);
			final Intent intent = createTakePhotoIntent(mImageUri);
			startActivityForResult(intent, REQUEST_TAKE_PHOTO);
		}
	}
	
	private void takeBannerPhoto() {
		final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		if (getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			final File cache_dir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? GetExternalCacheDirAccessor
					.getExternalCacheDir(getActivity()) : new File(getExternalStorageDirectory().getPath()
					+ "/Android/data/" + getActivity().getPackageName() + "/cache/");
			final File file = new File(cache_dir, "tmp_photo_" + System.currentTimeMillis() + ".jpg");
			mImageUri = Uri.fromFile(file);
			intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, mImageUri);
			startActivityForResult(intent, REQUEST_BANNER_TAKE_PHOTO);
		}
	}
	
	private void updateUserColor() {
		if (mProfileNameContainer != null) {
			final Drawable d = mProfileNameContainer.getBackground();
			if (d != null) {
				d.mutate().setColorFilter(getUserColor(getActivity(), mUserId), Mode.MULTIPLY);
				mProfileNameContainer.invalidate();
			}
		}
	}

	public static class EditTextDialogFragment extends BaseDialogFragment implements DialogInterface.OnClickListener {
		private EditText mEditText;
		private String mText;
		private int mType;
		private String mTitle;
		private long mAccountId;
		private ServiceInterface mService;

		@Override
		public void onClick(final DialogInterface dialog, final int which) {
			switch (which) {
				case DialogInterface.BUTTON_POSITIVE: {
					mText = mEditText.getText().toString();
					switch (mType) {
						case TYPE_NAME: {
							mService.updateProfile(mAccountId, mText, null, null, null);
							break;
						}
						case TYPE_URL: {
							mService.updateProfile(mAccountId, null, mText, null, null);
							break;
						}
						case TYPE_LOCATION: {
							mService.updateProfile(mAccountId, null, null, mText, null);
							break;
						}
						case TYPE_DESCRIPTION: {
							mService.updateProfile(mAccountId, null, null, null, mText);
							break;
						}
					}
					break;
				}
			}

		}

		@Override
		public Dialog onCreateDialog(final Bundle savedInstanceState) {
			mService = getApplication().getServiceInterface();
			final Bundle bundle = savedInstanceState == null ? getArguments() : savedInstanceState;
			mAccountId = bundle != null ? bundle.getLong(INTENT_KEY_ACCOUNT_ID, -1) : -1;
			mText = bundle != null ? bundle.getString(INTENT_KEY_TEXT) : null;
			mType = bundle != null ? bundle.getInt(INTENT_KEY_TYPE, -1) : -1;
			mTitle = bundle != null ? bundle.getString(INTENT_KEY_TITLE) : null;
			final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			final View view = LayoutInflater.from(getActivity()).inflate(R.layout.edittext_default_style, null);
			builder.setView(view);
			mEditText = (EditText) view.findViewById(R.id.edit_text);
			if (mText != null) {
				mEditText.setText(mText);
			}
			int limit = 140;
			switch (mType) {
				case TYPE_NAME: {
					limit = 20;
					break;
				}
				case TYPE_URL: {
					limit = 100;
					break;
				}
				case TYPE_LOCATION: {
					limit = 30;
					break;
				}
				case TYPE_DESCRIPTION: {
					limit = 160;
					break;
				}
			}
			mEditText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(limit) });
			builder.setTitle(mTitle);
			builder.setView(view);
			builder.setPositiveButton(android.R.string.ok, this);
			builder.setNegativeButton(android.R.string.cancel, this);
			return builder.create();
		}

		@Override
		public void onSaveInstanceState(final Bundle outState) {
			outState.putLong(INTENT_KEY_ACCOUNT_ID, mAccountId);
			outState.putString(INTENT_KEY_TEXT, mText);
			outState.putInt(INTENT_KEY_TYPE, mType);
			outState.putString(INTENT_KEY_TITLE, mTitle);
			super.onSaveInstanceState(outState);
		}

	}

	class FavoritesAction extends ListAction {

		@Override
		public long getId() {
			return 2;
		}
		
		@Override
		public String getName() {
			return getString(R.string.favorites);
		}

		@Override
		public String getSummary() {
			if (mUser == null) return null;
			return String.valueOf(mUser.getFavouritesCount());
		}

		@Override
		public void onClick() {
			if (mUser == null) return;
			openUserFavorites(getActivity(), mAccountId, mUser.getId(), mUser.getScreenName());
		}

	}
	
	class MediaTimelineTask extends AsyncTask<Void, Void, ResponseList<twitter4j.Status>> {

		private final Twitter twitter;
		private final String screen_name;

		private MediaTimelineTask(final Context context, final long account_id, final String screen_name) {
			twitter = getTwitterInstance(context, account_id, true);
			this.screen_name = screen_name;
		}

		@Override
		protected ResponseList<twitter4j.Status> doInBackground(final Void... args) {
			try {
				final Paging paging = new Paging();
				final int load_item_limit = 20;
				paging.setCount(load_item_limit);
				return twitter.getUserMediaTimeline(screen_name, paging);
			} catch (final TwitterException e) {
				return null;
			}
		}

		@Override
		protected void onCancelled() {
			setProgressBarIndeterminateVisibility(false);
			super.onCancelled();
		}

		@Override
		protected void onPostExecute(final ResponseList<twitter4j.Status> result) {
			if (result == null) return;
			mMediaStatuses = new ArrayList<ParcelableStatus>();
			for (twitter4j.Status status : result) {
				ParcelableStatus pStatus = new ParcelableStatus(status, mAccountId, false);
            	if (pStatus != null && pStatus.image_preview_url_string != null) {
            		mMediaStatuses.add(pStatus);
            	}
			}
			setProgressBarIndeterminateVisibility(false);
			if (mMediaStatuses.size() >= 1) {
				mRecentPhotosGallery.setVisibility(View.VISIBLE);
			}
			else {
				mRecentPhotosGallery.setVisibility(View.GONE);
			}
			((BaseAdapter) mRecentPhotosGallery.getAdapter()).notifyDataSetChanged(); 
			if (mMediaStatuses.size() >= 3) {
				mRecentPhotosGallery.setSelection(3, true);
			}
			super.onPostExecute(result);
		}

		@Override
		protected void onPreExecute() {
			setProgressBarIndeterminateVisibility(true);
			super.onPreExecute();
		}

	}
    
	
	class UpdateFriendshipTask extends AsyncTask<Void, Void, Response<Relationship>> {

		private final boolean is_my_activated_account;

		UpdateFriendshipTask() {
			is_my_activated_account = isMyActivatedAccount(getActivity(), mUser.getId());
		}

		@Override
		protected Response<Relationship> doInBackground(final Void... params) {
			return updateFriendship();
		}

		@Override
		protected void onPostExecute(final Response<Relationship> result) {
			mFriendship = null;
			if (result.exception == null) {
				mFollowContainer.setVisibility(result.value == null ? View.GONE : View.VISIBLE);
				if (!is_my_activated_account) {
					mMoreOptionsContainer.setVisibility(result.value == null ? View.GONE : View.VISIBLE);
				}
				if (result.value != null) {
					mFriendship = result.value;
					mFollowButton.setVisibility(View.VISIBLE);
					//mFollowBackContainer.setVisibility(View.VISIBLE);
					mFollowedYouIndicator.setVisibility(View.VISIBLE);
					if (mFriendship.isTargetFollowingSource()) {
						mFollowedYouIndicator.setText(getString(R.string.following_back, mFriendship.getTargetUserScreenName()));
					}
					else {
						if (mFriendship.getSourceUserScreenName().equalsIgnoreCase(mFriendship.getTargetUserScreenName())) {
							mFollowedYouIndicator.setText(getString(R.string.not_following_back_you, mFriendship.getTargetUserScreenName()));
						}
						else {
							mFollowedYouIndicator.setText(getString(R.string.not_following_back, mFriendship.getTargetUserScreenName()));
						}
					}
					mFollowButton.setText(mFriendship.isSourceFollowingTarget() ? R.string.unfollow : R.string.follow);
					if (!is_my_activated_account) {
						mMoreOptionsButton.setVisibility(View.VISIBLE);
					//	mFollowedYouIndicator.setVisibility(result.value.isSourceFollowedByTarget() ? View.VISIBLE
					//			: View.GONE);
					}
					final ContentResolver resolver = getContentResolver();
					final String where = CachedUsers.USER_ID + " = " + mUserId;
					resolver.delete(CachedUsers.CONTENT_URI, where, null);
					//I bet you don't want to see blocked user in your auto complete list.
					if (!mFriendship.isSourceBlockingTarget()) {
						final ContentValues cached_values = makeCachedUserContentValues(mUser);  
						if (cached_values != null) {
							resolver.insert(CachedUsers.CONTENT_URI, cached_values);
						}
					}
				}
			}
			mFollowProgress.setVisibility(View.GONE);
			mMoreOptionsProgress.setVisibility(View.GONE);
			super.onPostExecute(result);
			mUpdateFriendshipTask = null;
		}

		@Override
		protected void onPreExecute() {
			mFollowedYouIndicator.setVisibility(View.GONE);
			mFollowContainer.setVisibility(is_my_activated_account ? View.GONE : View.VISIBLE);
			mFollowButton.setVisibility(View.GONE);
			mFollowProgress.setVisibility(View.VISIBLE);
			mMoreOptionsContainer.setVisibility(is_my_activated_account ? View.GONE : View.VISIBLE);
			mMoreOptionsButton.setVisibility(View.GONE);
			mMoreOptionsProgress.setVisibility(View.VISIBLE);
			super.onPreExecute();
		}

		private Response<Relationship> updateFriendship() {
			if (mUser == null) return new Response<Relationship>(null, null);
			if (mAccountId == mUser.getId()) return new Response<Relationship>(null, null);
			final Twitter twitter = getTwitterInstance(getActivity(), mAccountId, false);
			try {
				boolean wantRetweets = true;
				if (mFriendship.wantRetweets() == true) {
					wantRetweets = false;
				}
				final Relationship result = twitter.updateFriendship(mUser.getId(), wantRetweets);
				return new Response<Relationship>(result, null);
			} catch (final TwitterException e) {
				return new Response<Relationship>(null, e);
			}
		}
	}

	class GetFriendshipTask extends AsyncTask<Void, Void, Response<Relationship>> {

		private final boolean is_my_activated_account;

		GetFriendshipTask() {
			is_my_activated_account = isMyActivatedAccount(getActivity(), mUser.getId());
		}

		@Override
		protected Response<Relationship> doInBackground(final Void... params) {
			return getFriendship();
		}

		@Override
		protected void onPostExecute(final Response<Relationship> result) {
			mFriendship = null;
			if (result.exception == null) {
				mFollowContainer.setVisibility(result.value == null ? View.GONE : View.VISIBLE);
				if (!is_my_activated_account) {
					mMoreOptionsContainer.setVisibility(result.value == null ? View.GONE : View.VISIBLE);
				}
				if (result.value != null) {
					mFriendship = result.value;
					mFollowButton.setVisibility(View.VISIBLE);
					//mFollowBackContainer.setVisibility(View.VISIBLE);
					mFollowedYouIndicator.setVisibility(View.VISIBLE);
					if (mFriendship.isTargetFollowingSource()) {
						mFollowedYouIndicator.setText(getString(R.string.following_back, mFriendship.getTargetUserScreenName()));
					}
					else {
						if (mFriendship.getSourceUserScreenName().equalsIgnoreCase(mFriendship.getTargetUserScreenName())) {
							mFollowedYouIndicator.setText(getString(R.string.not_following_back_you, mFriendship.getTargetUserScreenName()));
						}
						else {
							mFollowedYouIndicator.setText(getString(R.string.not_following_back, mFriendship.getTargetUserScreenName()));
						}
					}
					mFollowButton.setText(mFriendship.isSourceFollowingTarget() ? R.string.unfollow : R.string.follow);
					if (!is_my_activated_account) {
						mMoreOptionsButton.setVisibility(View.VISIBLE);
					//	mFollowedYouIndicator.setVisibility(result.value.isSourceFollowedByTarget() ? View.VISIBLE
					//			: View.GONE);
					}
				}
			}
			mFollowProgress.setVisibility(View.GONE);
			mMoreOptionsProgress.setVisibility(View.GONE);
			super.onPostExecute(result);
			mGetFriendshipTask = null;
		}

		@Override
		protected void onPreExecute() {
			mFollowedYouIndicator.setVisibility(View.GONE);
			mFollowContainer.setVisibility(is_my_activated_account ? View.GONE : View.VISIBLE);
			mFollowButton.setVisibility(View.GONE);
			mFollowProgress.setVisibility(View.VISIBLE);
			mMoreOptionsContainer.setVisibility(is_my_activated_account ? View.GONE : View.VISIBLE);
			mMoreOptionsButton.setVisibility(View.GONE);
			mMoreOptionsProgress.setVisibility(View.VISIBLE);
			super.onPreExecute();
		}

		private Response<Relationship> getFriendship() {
			if (mUser == null) return new Response<Relationship>(null, null);
			if (mAccountId == mUser.getId()) return new Response<Relationship>(null, null);
			final Twitter twitter = getTwitterInstance(getActivity(), mAccountId, false);
			try {
				final Relationship result = twitter.showFriendship(mAccountId, mUser.getId());
				return new Response<Relationship>(result, null);
			} catch (final TwitterException e) {
				return new Response<Relationship>(null, e);
			}
		}
	}

	class Response<T> {
		public final T value;
		public final TwitterException exception;

		public Response(final T value, final TwitterException exception) {
			this.value = value;
			this.exception = exception;
		}
	}
	
	class IncomingFriendshipsAction extends ListAction {
		
		@Override
		public long getId() {
			return 6;
		}
		
		@Override
		public String getName() {
			return getString(R.string.incoming_friendships);
		}

		@Override
		public void onClick() {
			if (mUser == null) return;
			openIncomingFriendships(getActivity(), mAccountId);
		}
		
	}

	class SavedSearchesAction extends ListAction {
		
		@Override
		public long getId() {
			return 5;
		}
		
		@Override
		public String getName() {
			return getString(R.string.saved_searches);
		}

		@Override
		public void onClick() {
			if (mUser == null) return;
			openSavedSearches(getActivity(), mAccountId);
		}

	}
	
	class MyTweetsRetweetedAction extends ListAction {
		
		@Override
		public long getId() {
			return 8;
		}
		
		@Override
		public String getName() {
			return getString(R.string.retweets_of_me);
		}

		@Override
		public void onClick() {
			if (mUser == null) return;
			openRetweetsOfMe(getActivity(), mAccountId);
		}

	}

	class UserBlocksAction extends ListAction {

		@Override
		public long getId() {
			return 7;
		}
		
		@Override
		public String getName() {
			return getString(R.string.blocked_users);
		}

		@Override
		public void onClick() {
			if (mUser == null) return;
			openUserBlocks(getActivity(), mAccountId);
		}

	}

	
	class UserInfoTask extends AsyncTask<Void, Void, Response<User>> {

		private final Twitter twitter;
		private final long user_id;
		private final String screen_name;

		private UserInfoTask(final Context context, final long account_id, final long user_id, final String screen_name) {
			twitter = getTwitterInstance(context, account_id, true);
			this.user_id = user_id;
			this.screen_name = screen_name;
		}

		UserInfoTask(final Context context, final long account_id, final long user_id) {
			this(context, account_id, user_id, null);
		}

		UserInfoTask(final Context context, final long account_id, final String screen_name) {
			this(context, account_id, -1, screen_name);
		}

		@Override
		protected Response<User> doInBackground(final Void... args) {
			if (twitter == null) return new Response<User>(null, null);
			try {
				if (user_id != -1) {
					if (mPreferences.getBoolean(PREFERENCE_KEY_API_V1, true) == true) {
						return new Response<User>(twitter.showUserv1(user_id), null);
					}
					return new Response<User>(twitter.showUser(user_id), null);
				}
				else if (screen_name != null) return new Response<User>(twitter.showUser(screen_name), null);
			} catch (final TwitterException e) {
				return new Response<User>(null, e);
			}
			return new Response<User>(null, null);
		}

		@Override
		protected void onCancelled() {
			setProgressBarIndeterminateVisibility(false);
			super.onCancelled();
		}

		@Override
		protected void onPostExecute(final Response<User> result) {
			if (result == null) return;
			if (getActivity() == null) return;
			if (result.value != null && result.value.getId() > 0) {
				final User user = result.value;
				final ContentResolver resolver = getContentResolver();
				setListShown(true);
				changeUser(mAccountId, user);
				mTrackingTask = new TrackingTask();
				mTrackingTask.execute();
				mErrorRetryContainer.setVisibility(View.GONE);
				if (isMyAccount(getActivity(), user.getId())) {
					final ContentValues values = new ContentValues();
					final URL profile_image_url = user.getProfileImageURL();
					if (profile_image_url != null) {
						values.put(Accounts.PROFILE_IMAGE_URL, profile_image_url.toString());
					}
					values.put(Accounts.USERNAME, user.getScreenName());
					final String where = Accounts.USER_ID + " = " + user.getId() + " AND 1 = 1";
					resolver.update(Accounts.CONTENT_URI, values, where, null);
				}
			} else {
				if (result.exception != null) {
					//result.exception.printStackTrace();
					Toast.makeText(getActivity(), result.exception.getMessage(), Toast.LENGTH_LONG).show();
				}
				mListContainer.setVisibility(View.GONE);
				mErrorRetryContainer.setVisibility(View.VISIBLE);
			}
			setProgressBarIndeterminateVisibility(false);
			super.onPostExecute(result);
		}

		@Override
		protected void onPreExecute() {
			mListContainer.setVisibility(View.VISIBLE);
			mErrorRetryContainer.setVisibility(View.GONE);
			setListShown(false);
			setProgressBarIndeterminateVisibility(true);
			super.onPreExecute();
		}

	}

	class UserListTypesAction extends ListAction {

		@Override
		public long getId() {
			return 4;
		}
		
		@Override
		public String getName() {
			return getString(R.string.user_list);
		}

		@Override
		public void onClick() {
			if (mUser == null) return;
			openUserListTypes(getActivity(), mAccountId, mUser.getId(), mUser.getScreenName());
		}

	}
	
	class UserNearbyAction extends ListAction {
		
		@Override
		public long getId() {
			return 9;
		}
		
		@Override
		public String getName() {
			return getString(R.string.nearby_tweets);
		}
		
		@Override
		public void onClick() {
			
			final Uri.Builder builder = new Uri.Builder();
			builder.scheme(SCHEME_TWEETINGS);
			builder.authority(AUTHORITY_NEARBY);
			startActivity(new Intent(Intent.ACTION_VIEW, builder.build()));
		}
	}
	
	class UserRecentPhotosAction extends ListAction {
		
		@Override
		public long getId() {
			return 1;
		}
		
		@Override
		public String getName() {
			return getString(R.string.recent_photos);
		}
		
		@Override
		public void onClick() {
			if (mUser == null) return;
			Intent intent = new Intent(INTENT_ACTION_GALLERY);
			intent.putExtra("screen_name", mUser.getScreenName());
			intent.putExtra("account_id", mAccountId);
			startActivity(intent);
		}
	}

	class UserMentionsAction extends ListAction {
		
		@Override
		public long getId() {
			return 3;
		}
		
		@Override
		public String getName() {
			return getString(R.string.user_mentions);
		}
		
		@Override
		public void onClick() {
			if (mUser == null) return;
			openUserMentions(getActivity(), mAccountId, mUser.getScreenName());
		}
	}
}
