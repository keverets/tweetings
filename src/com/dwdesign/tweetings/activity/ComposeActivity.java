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

import static android.os.Environment.getExternalStorageDirectory;
import static android.os.Environment.getExternalStorageState;
import static com.dwdesign.tweetings.util.Utils.getAccountColors;
import static com.dwdesign.tweetings.util.Utils.getAccountUsername;
import static com.dwdesign.tweetings.util.Utils.getActivatedAccountIds;
import static com.dwdesign.tweetings.util.Utils.getImagePathFromUri;
import static com.dwdesign.tweetings.util.Utils.getImageUploadStatus;
import static com.dwdesign.tweetings.util.Utils.getShareStatus;
import static com.dwdesign.tweetings.util.Utils.isNullOrEmpty;
import static com.dwdesign.tweetings.util.Utils.parseString;
import static com.dwdesign.tweetings.util.Utils.showErrorToast;

import static com.dwdesign.tweetings.util.Utils.getTwitterAccessToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;
import org.w3c.dom.Document;

import com.aviary.android.feather.FeatherActivity;
import com.aviary.android.feather.library.utils.StringUtils;
import com.dwdesign.actionbarcompat.ActionBar;
import com.dwdesign.menubar.MenuBar;
import com.dwdesign.menubar.MenuBar.OnMenuItemClickListener;
import com.dwdesign.popupmenu.PopupMenu;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.provider.TweetStore.Drafts;
import com.dwdesign.tweetings.util.ArrayUtils;
import com.dwdesign.tweetings.service.TweetingsService;
import com.dwdesign.tweetings.util.GetExternalCacheDirAccessor;
import com.dwdesign.tweetings.util.ServiceInterface;
import com.dwdesign.tweetings.view.ColorView;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.twitter.Validator;
import com.dwdesign.tweetings.fragment.BaseDialogFragment;
import twitter4j.TwitterException;
import twitter4j.auth.AccessToken;
import twitter4j.auth.OAuthAuthorization;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.internal.http.HttpParameter;
import twitter4j.media.ImageUpload;
import twitter4j.media.ImageUploadFactory;
import twitter4j.media.MediaProvider;

public class ComposeActivity extends BaseActivity implements TextWatcher, LocationListener, OnMenuItemClickListener,
		OnClickListener, OnLongClickListener, PopupMenu.OnMenuItemClickListener, OnEditorActionListener {

	private static final String FAKE_IMAGE_LINK = "https://www.example.com/fake_image.jpg";
	private static final String INTENT_KEY_CONTENT_MODIFIED = "content_modified";
	private static final String INTENT_KEY_IS_NAVIGATE_UP = "is_navigate_up";
	
	private ServiceInterface mService;
	private LocationManager mLocationManager;
	private SharedPreferences mPreferences;
	private Location mRecentLocation;
	private ContentResolver mResolver;
	private final Validator mValidator = new Validator();
	private AttachedImageThumbnailTask mAttachedImageThumbnailTask;
	
	private ActionBar mActionBar;
	private PopupMenu mPopupMenu;

	private static final int THUMBNAIL_SIZE = 36;

	private static final int ACTION_REQUEST_FEATHER = 100;
	
	private ColorView mColorIndicator;
	private EditText mEditText;
	private TextView mTextCount;
	private ImageView mImageThumbnailPreview;
	private MenuBar mMenuBar;
	private boolean mIsImageAttached, mIsPhotoAttached;
	private long[] mAccountIds;
	private String mText;
	private Uri mImageUri;
	private long mInReplyToStatusId = -1;
	private String mInReplyToScreenName, mInReplyToName;
	private boolean mIsQuote, mUploadUseExtension, mIsBuffer, mContentModified;
	private String mUploadProvider;
	private ProgressDialog progressDialog;
	private ArrayList<String> tweetParts;
	private String mScheduleDate;

	private DialogFragment mUnsavedTweetDialogFragment;
	
	@Override
	public void afterTextChanged(final Editable s) {

	}

	@Override
	public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {

	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {

		switch (requestCode) {
			case REQUEST_SCHEDULE_DATE: {
				if (resultCode == Activity.RESULT_OK) {
					Bundle bundle = intent.getExtras();
					mScheduleDate = bundle.getString(INTENT_KEY_SCHEDULE_DATE_TIME);
				}
				else {
					if (mScheduleDate != null) {
						mScheduleDate = null;
					}
				}
				setMenu(mMenuBar.getMenu());
				break;
			}
			case REQUEST_TAKE_PHOTO: {
				if (resultCode == Activity.RESULT_OK) {
					final File file = new File(mImageUri.getPath());
					if (file.exists()) {
						mIsImageAttached = false;
						mIsPhotoAttached = true;
						mImageThumbnailPreview.setVisibility(View.VISIBLE);
						reloadAttachedImageThumbnail(file);
					} else {
						mIsPhotoAttached = false;
					}
					setMenu(mMenuBar.getMenu());
				}
				break;
			}
			case REQUEST_PICK_IMAGE: {
				if (resultCode == Activity.RESULT_OK) {
					final Uri uri = intent.getData();
					final File file = uri == null ? null : new File(getImagePathFromUri(this, uri));
					if (file != null && file.exists()) {
						mImageUri = Uri.fromFile(file);
						mIsPhotoAttached = false;
						mIsImageAttached = true;
						mImageThumbnailPreview.setVisibility(View.VISIBLE);
						reloadAttachedImageThumbnail(file);
					} else {
						mIsImageAttached = false;
					}
					setMenu(mMenuBar.getMenu());
				}
				break;
			}
			case REQUEST_SELECT_ACCOUNT: {
				if (resultCode == Activity.RESULT_OK) {
					final Bundle bundle = intent.getExtras();
					if (bundle == null) {
						break;
					}
					final long[] account_ids = bundle.getLongArray(INTENT_KEY_IDS);
					if (account_ids != null) {
						mAccountIds = account_ids;
						if (mInReplyToStatusId <= 0) {
							final SharedPreferences.Editor editor = mPreferences.edit();
							editor.putString(PREFERENCE_KEY_COMPOSE_ACCOUNTS, ArrayUtils.toString(mAccountIds, ',', false));
							editor.commit();
						}
						mColorIndicator.setColor(getAccountColors(this, account_ids));
					}
				}
				break;
			}
			case REQUEST_EDIT_IMAGE: {
				if (resultCode == Activity.RESULT_OK) {
					final Uri uri = intent.getData();
					final File file = uri == null ? null : new File(getImagePathFromUri(this, uri));
					if (file != null && file.exists()) {
						mImageUri = Uri.fromFile(file);
						reloadAttachedImageThumbnail(file);
					} else {
						break;
					}
					setMenu(mMenuBar.getMenu());
				}
				break;
			}
			case REQUEST_EXTENSION_COMPOSE: {
				if (resultCode == Activity.RESULT_OK) {
					final Bundle extras = intent.getExtras();
					if (extras == null) {
						break;
					}
					final String text = extras.getString(INTENT_KEY_TEXT);
					final String append = extras.getString(INTENT_KEY_APPEND_TEXT);
					if (text != null) {
						mEditText.setText(text);
						mText = parseString(mEditText.getText());
					} else if (append != null) {
						mEditText.append(append);
						mText = parseString(mEditText.getText());
					}
				}
				break;
			}
			 case ACTION_REQUEST_FEATHER:
				 if( resultCode == RESULT_OK ) {
					 final Uri uri = intent.getData();
						final File file = uri == null ? null : new File(getImagePathFromUri(this, uri));
						if (file != null && file.exists()) {
							mImageUri = Uri.fromFile(file);
							reloadAttachedImageThumbnail(file);
						} else {
							break;
						}
						setMenu(mMenuBar.getMenu());
				 }
	                break;
		}

	}

	@Override
	public void onBackPressed() {
		final String text = mEditText != null ? parseString(mEditText.getText()) : null;
		if (mContentModified && !isNullOrEmpty(text)) {
			mUnsavedTweetDialogFragment = (DialogFragment) Fragment.instantiate(this,
			UnsavedTweetDialogFragment.class.getName());
			final Bundle args = new Bundle();
			args.putBoolean(INTENT_KEY_IS_NAVIGATE_UP, false);
			mUnsavedTweetDialogFragment.setArguments(args);
			mUnsavedTweetDialogFragment.show(getSupportFragmentManager(), "unsaved_tweet");
			return;
		}
		super.onBackPressed();
	}
	
	@Override
	public void onClick(final View view) {
		switch (view.getId()) {
			case R.id.image_thumbnail_preview: {
				if (mPopupMenu != null) {
					mPopupMenu.dismiss();
				}
				mPopupMenu = PopupMenu.getInstance(this, view);
				mPopupMenu.inflate(R.menu.action_attached_image);
				mPopupMenu.setOnMenuItemClickListener(this);
				mPopupMenu.show();
				break;
			}
		}

	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();
		mColorIndicator = (ColorView) findViewById(R.id.account_colors);
		mEditText = (EditText) findViewById(R.id.edit_text);
		mTextCount = (TextView) findViewById(R.id.text_count);
		mImageThumbnailPreview = (ImageView) findViewById(R.id.image_thumbnail_preview);
		mMenuBar = (MenuBar) findViewById(R.id.menu_bar);
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mService = getTweetingsApplication().getServiceInterface();
		mResolver = getContentResolver();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.compose);
		mActionBar = getSupportActionBar();
		mActionBar.setDisplayHomeAsUpEnabled(true);
		
		
		mUploadProvider = mPreferences.getString(PREFERENCE_KEY_IMAGE_UPLOADER, null);
		
		final Bundle bundle = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
		final long account_id = bundle != null ? bundle.getLong(INTENT_KEY_ACCOUNT_ID) : -1;
		mAccountIds = bundle != null ? bundle.getLongArray(INTENT_KEY_IDS) : null;
		mInReplyToStatusId = bundle != null ? bundle.getLong(INTENT_KEY_IN_REPLY_TO_ID) : -1;
		mInReplyToScreenName = bundle != null ? bundle.getString(INTENT_KEY_IN_REPLY_TO_SCREEN_NAME) : null;
		mInReplyToName = bundle != null ? bundle.getString(INTENT_KEY_IN_REPLY_TO_NAME) : null;
		mIsImageAttached = bundle != null ? bundle.getBoolean(INTENT_KEY_IS_IMAGE_ATTACHED) : false;
		mIsPhotoAttached = bundle != null ? bundle.getBoolean(INTENT_KEY_IS_PHOTO_ATTACHED) : false;
		mImageUri = bundle != null ? (Uri) bundle.getParcelable(INTENT_KEY_IMAGE_URI) : null;
		final String[] mentions = bundle != null ? bundle.getStringArray(INTENT_KEY_MENTIONS) : null;
		final String account_username = getAccountUsername(this, account_id);
		int text_selection_start = -1;
		mIsBuffer = bundle != null ? bundle.getBoolean(INTENT_KEY_IS_BUFFER, false): false;
		
		if (mInReplyToStatusId > 0) {
			if (bundle != null && bundle.getString(INTENT_KEY_TEXT) != null
					&& (mentions == null || mentions.length < 1)) {
				mText = bundle.getString(INTENT_KEY_TEXT);
			} else if (mentions != null) {
				final StringBuilder builder = new StringBuilder();
				for (final String mention : mentions) {
					if (mentions.length == 1 && mentions[0].equalsIgnoreCase(account_username)) {
						builder.append('@' + account_username + ' ');
					} else if (!mention.equalsIgnoreCase(account_username)) {
						builder.append('@' + mention + ' ');
					}
				}
				mText = builder.toString();
				text_selection_start = mText.indexOf(' ') + 1;
			}

			mIsQuote = bundle != null ? bundle.getBoolean(INTENT_KEY_IS_QUOTE, false) : false;
			
			final boolean display_name = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_NAME, false);
			final String name = display_name ? mInReplyToName : mInReplyToScreenName;
			if (name != null) {
				setTitle(getString(mIsQuote ? R.string.quote_user : R.string.reply_to, name));
			}
			if (mAccountIds == null || mAccountIds.length == 0) {
				mAccountIds = new long[] { account_id };
			}
		} else {
			if (mentions != null) {
				final StringBuilder builder = new StringBuilder();
				for (final String mention : mentions) {
					if (mentions.length == 1 && mentions[0].equalsIgnoreCase(account_username)) {
						builder.append('@' + account_username + ' ');
					} else if (!mention.equalsIgnoreCase(account_username)) {
						builder.append('@' + mention + ' ');
					}
				}
				mText = builder.toString();
			}
			if (mAccountIds == null || mAccountIds.length == 0) {
				final long[] ids_in_prefs = ArrayUtils.fromString(
						mPreferences.getString(PREFERENCE_KEY_COMPOSE_ACCOUNTS, null), ',');
				final long[] activated_ids = getActivatedAccountIds(this);
				final long[] intersection = ArrayUtils.intersection(ids_in_prefs, activated_ids);
				mAccountIds = intersection.length > 0 ? intersection : activated_ids;
			}
			final String action = getIntent().getAction();
			if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
				setTitle(R.string.share);
				final Bundle extras = getIntent().getExtras();
				if (extras != null) {
					if (mText == null) {
						final CharSequence extra_subject = extras.getCharSequence(Intent.EXTRA_SUBJECT);
						final CharSequence extra_text = extras.getCharSequence(Intent.EXTRA_TEXT);
						mText = getShareStatus(this, parseString(extra_subject), parseString(extra_text));
					} else {
						mText = bundle.getString(INTENT_KEY_TEXT);
					}
					if (mImageUri == null) {
						final Uri extra_stream = extras.getParcelable(Intent.EXTRA_STREAM);
						final String content_type = getIntent().getType();
						if (extra_stream != null && content_type != null && content_type.startsWith("image/")) {
							final String real_path = getImagePathFromUri(this, extra_stream);
							final File file = real_path != null ? new File(real_path) : null;
							if (file != null && file.exists()) {
								mImageUri = Uri.fromFile(file);
								mIsImageAttached = true;
								mIsPhotoAttached = false;
							} else {
								mImageUri = null;
								mIsImageAttached = false;
							}
						}
					}
				}
			} else if (bundle != null) {

				if (bundle.getString(INTENT_KEY_TEXT) != null) {
					mText = bundle.getString(INTENT_KEY_TEXT);
				}
			}
		}

		final File image_file = mImageUri != null && "file".equals(mImageUri.getScheme()) ? new File(
				mImageUri.getPath()) : null;
		final boolean image_file_valid = image_file != null && image_file.exists();
		mImageThumbnailPreview.setVisibility(image_file_valid ? View.VISIBLE : View.GONE);
		if (image_file_valid) {
			reloadAttachedImageThumbnail(image_file);
		}

		mImageThumbnailPreview.setOnClickListener(this);
		mImageThumbnailPreview.setOnLongClickListener(this);
		mMenuBar.setOnMenuItemClickListener(this);
		mMenuBar.inflate(R.menu.menu_compose);
		setMenu(mMenuBar.getMenu());
		mMenuBar.show();
		if (mPreferences.getBoolean(PREFERENCE_KEY_QUICK_SEND, false)) {
			mEditText.setRawInputType(InputType.TYPE_CLASS_TEXT); 
			mEditText.setImeOptions(EditorInfo.IME_ACTION_GO);
			mEditText.setOnEditorActionListener(this);
		}
		mEditText.addTextChangedListener(this);
		if (mText != null) {
			mEditText.setText(mText);
			if (mIsQuote) {
				mEditText.setSelection(0);
			} else if (text_selection_start != -1 && text_selection_start < mEditText.length()
					&& mEditText.length() > 0) {
				mEditText.setSelection(text_selection_start, mEditText.length() - 1);
			} else if (mEditText.length() > 0) {
				mEditText.setSelection(mEditText.length());
			}
		}
		invalidateSupportOptionsMenu();
		mMenuBar.invalidate();
		if (mColorIndicator != null) {
			mColorIndicator.setOrientation(ColorView.VERTICAL);
			mColorIndicator.setColor(getAccountColors(this, mAccountIds));
		}
		mContentModified = savedInstanceState != null ? savedInstanceState.getBoolean(
				INTENT_KEY_CONTENT_MODIFIED) : false;
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.menu_compose_actionbar, menu);
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onEditorAction(final TextView view, final int actionId, final KeyEvent event) {
		if (event == null) return false;
		switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_ENTER: {
				if (event.getAction() == KeyEvent.ACTION_UP) {
					send();
				}
				return true;
			}
		}
		return false;
	 }

	/** Sets the mRecentLocation object to the current location of the device **/
	@Override
	public void onLocationChanged(final Location location) {
		mRecentLocation = location;
	}

	@Override
	public boolean onLongClick(final View view) {
		switch (view.getId()) {
			case R.id.image_thumbnail_preview: {
				onClick(view);
				return true;
			}
		}
		return false;
	}
	
	public void removeAttachments() {
		if (mIsImageAttached && !mIsPhotoAttached) {
			mImageUri = null;

		} else if (mIsPhotoAttached && !mIsImageAttached) {
			final File image_file = mImageUri != null && "file".equals(mImageUri.getScheme()) ? new File(
					mImageUri.getPath()) : null;
			if (image_file != null) {
				image_file.delete();
			}
			mImageUri = null;
		}
		mIsPhotoAttached = false;
		mIsImageAttached = false;
		reloadAttachedImageThumbnail(null);
		setMenu(mMenuBar.getMenu());
	}

	@Override
	public boolean onMenuItemClick(final MenuItem item) {
		switch (item.getItemId()) {
			case MENU_TAKE_PHOTO: {
				if (item.getTitle().equals(getString(R.string.remove_photo))) {
					if (mImageUri == null) return false;
					removeAttachments();
				}
				else {
					takePhoto();
				}
				break;
			}
			case MENU_ADD_IMAGE: {
				if (item.getTitle().equals(getString(R.string.remove_image))) {
					if (mImageUri == null) return false;
					removeAttachments();
				}
				else {
					pickImage();
				}
				break;
			}
			case MENU_SHORTEN_LINKS: {
				String s = parseString(mEditText.getText());
				new UrlShortenerTask().execute(s);
				break;
			}
			case MENU_ADD_TO_BUFFER: {
				if (mIsBuffer) {
					mIsBuffer = false;
				}
				else {
					mIsBuffer = true;
				}
				setMenu(mMenuBar.getMenu());
				break;
			}
			case MENU_SCHEDULE_TWEET: {
				if (mScheduleDate != null) {
					mScheduleDate = null;
					setMenu(mMenuBar.getMenu());
				}
				else {
					Intent intent = new Intent(INTENT_ACTION_SCHEDULE_TWEET);
					if (mScheduleDate != null) {
						Bundle bundle = new Bundle();
						bundle.putString(INTENT_KEY_SCHEDULE_DATE_TIME, mScheduleDate);
						intent.putExtras(bundle);
					}
					startActivityForResult(intent, REQUEST_SCHEDULE_DATE);
				}
				break;
			}
			case MENU_ADD_LOCATION: {
				final boolean attach_location = mPreferences.getBoolean(PREFERENCE_KEY_ATTACH_LOCATION, false);
				if (!attach_location) {
					getLocation();
				}
				mPreferences.edit().putBoolean(PREFERENCE_KEY_ATTACH_LOCATION, !attach_location).commit();
				setMenu(mMenuBar.getMenu());
				break;
			}
			case MENU_DRAFTS: {
				startActivity(new Intent(INTENT_ACTION_DRAFTS));
				break;
			}
			case MENU_DELETE: {
				if (mImageUri == null) return false;
				removeAttachments();
				break;
			}
			case MENU_EDIT: {
				if (mImageUri == null) return false;
				/*final Intent intent = new Intent(INTENT_ACTION_EXTENSION_EDIT_IMAGE);
				intent.setData(mImageUri);
				startActivityForResult(Intent.createChooser(intent, getString(R.string.open_with_extensions)),
						REQUEST_EDIT_IMAGE);*/
				// Create the intent needed to start feather
				Intent newIntent = new Intent( this, FeatherActivity.class );
				// set the source image uri
				newIntent.setData( mImageUri );
				// pass the required api key ( http://developers.aviary.com/ )
				newIntent.putExtra( "API_KEY", AVIARY_SDK_API_KEY );
				// pass the uri of the destination image file (optional)
				// This will be the same uri you will receive in the onActivityResult
				//newIntent.putExtra( "output", mImageUri);
				// format of the destination image (optional)
				newIntent.putExtra( "output-format", Bitmap.CompressFormat.JPEG.name() );
				// output format quality (optional)
				newIntent.putExtra( "output-quality", 85 );
				// you can force feather to display only a certain tools
				// newIntent.putExtra( "tools-list", new String[]{"ADJUST", "BRIGHTNESS" } );

				// enable fast rendering preview
				newIntent.putExtra( "effect-enable-fast-preview", true );

				// limit the image size
				// You can pass the current display size as max image size because after
				// the execution of Aviary you can save the HI-RES image so you don't need a big
				// image for the preview
				// newIntent.putExtra( "max-image-size", 800 );

				// you want to hide the exit alert dialog shown when back is pressed
				// without saving image first
				// newIntent.putExtra( "hide-exit-unsave-confirmation", true );

				// ..and start feather
				startActivityForResult( newIntent, ACTION_REQUEST_FEATHER );
				break;
			}
			case MENU_VIEW: {
				if (mImageUri != null) {
					final Intent intent = new Intent(INTENT_ACTION_VIEW_IMAGE, mImageUri);
					startActivity(intent);
				}
				break;
			}
		}
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case MENU_HOME: {
				final String text = mEditText != null ? parseString(mEditText.getText()) : null;
				 if (mContentModified && !isNullOrEmpty(text)) {
					 mUnsavedTweetDialogFragment = (DialogFragment) Fragment.instantiate(this,
							 UnsavedTweetDialogFragment.class.getName());
					 final Bundle args = new Bundle();
					 args.putBoolean(INTENT_KEY_IS_NAVIGATE_UP, true);
					 mUnsavedTweetDialogFragment.setArguments(args);
					 mUnsavedTweetDialogFragment.show(getSupportFragmentManager(), "unsaved_tweet");  
				 } else {
					 NavUtils.navigateUpFromSameTask(this);
				 }
				break;
			}
			case MENU_SEND: {
				send();
				break;
			}
			case MENU_SELECT_ACCOUNT: {
				final Intent intent = new Intent(INTENT_ACTION_SELECT_ACCOUNT);
				final Bundle bundle = new Bundle();
				bundle.putBoolean(INTENT_KEY_ACTIVATED_ONLY, true);
				bundle.putLongArray(INTENT_KEY_IDS, mAccountIds);
				intent.putExtras(bundle);
				startActivityForResult(intent, REQUEST_SELECT_ACCOUNT);
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}
	
	protected void send() {
		final String text_orig = mEditText != null ? parseString(mEditText.getText()) : null;
		final String atext = mIsPhotoAttached || mIsImageAttached ? mUploadUseExtension ? getImageUploadStatus(this,
				FAKE_IMAGE_LINK, text_orig) : text_orig + " " + FAKE_IMAGE_LINK : text_orig;
		final int count = mValidator.getTweetLength(atext);
		if (!isNullOrEmpty(mUploadProvider) && (mIsPhotoAttached || mIsImageAttached)) {
			postMedia();
		}
		else if (count > 140) {
			if (mScheduleDate != null) {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.schedule_tweet));
				builder.setMessage(getString(R.string.schedule_tweet_too_long));
				builder.setCancelable(true);
				AlertDialog alert = builder.create();
				alert.show();
			}
			else {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.tweet_too_long));
				builder.setMessage(getString(R.string.confirm_twitlonger));
				builder.setCancelable(true);
				builder.setPositiveButton(getString(R.string.send_to_twitlonger),
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int id) {
							postTwitlonger();
						}
					});
				builder.setNeutralButton(getString(R.string.split_tweets), new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int id) {
							splitTweets();
						}
					});
				builder.setNegativeButton(getString(R.string.cancel),
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int id) {
							//actuallyPost();
						}
					});
				//
				AlertDialog alert = builder.create();
				alert.show();
			}
			
		}
		else {
			if (mScheduleDate != null && isNullOrEmpty(mUploadProvider) && (mIsPhotoAttached || mIsImageAttached)) {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.schedule_tweet));
				builder.setMessage(getString(R.string.schedule_tweet_pic_twitter));
				builder.setCancelable(true);
				AlertDialog alert = builder.create();
				alert.show();
			}
			else if (mScheduleDate != null && mIsBuffer == true) {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.schedule_tweet));
				builder.setMessage(getString(R.string.schedule_tweet_buffer));
				builder.setCancelable(true);
				AlertDialog alert = builder.create();
				alert.show();
			}
			else if (mIsBuffer == true && isNullOrEmpty(mUploadProvider) && (mIsPhotoAttached || mIsImageAttached)) {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.add_to_buffer));
				builder.setMessage(getString(R.string.buffer_pic_twitter));
				builder.setCancelable(true);
				AlertDialog alert = builder.create();
				alert.show();
			}
			else {
				actuallyPost();
			}
		}
	}
	
	protected void splitTweets() {
		String text = mEditText != null ? parseString(mEditText.getText()) : null;
		String[] chunks = text.split("\\s+");
		String replyTo = null;
		if (chunks.length >= 1) {
			final String firstWord = chunks[0];
			final char firstCharacter = firstWord.charAt(0);
			if (Character.toString(firstCharacter).equals("@")) {
				replyTo = firstWord;
			}
		}
		String currentPart = "";
		tweetParts = new ArrayList<String>();
		for (final String word : chunks) {
			int checkLength = 120;
			if (tweetParts.size() > 1 && replyTo != null) {
				checkLength = 120 - replyTo.length() + 1;
			}
			if (currentPart.length() + word.length() + 1 > checkLength) {
				if (tweetParts.size() >= 1 && replyTo != null) {
					tweetParts.add(replyTo + " " + currentPart);
				}
				else {
					tweetParts.add(currentPart);
				}
				currentPart = "";
			}
			if (currentPart.equals("")) {
				currentPart = word;
			}
			else {
				currentPart = currentPart + " " + word;
			}
			
		}
		if (!currentPart.equals("")) {
			if (tweetParts.size() >= 1 && replyTo != null) {
				tweetParts.add(replyTo + " " + currentPart);
			}
			else {
				tweetParts.add(currentPart);
			}
		}
		if (tweetParts.size() >= 1) {
			for (int i=tweetParts.size(); i>0; i--) {
				String part = tweetParts.get(i-1);
				String postString = part + " (" + getString(R.string.split_part) + " " + Integer.toString(i) + ")"; 
				final boolean attach_location = mPreferences.getBoolean(PREFERENCE_KEY_ATTACH_LOCATION, false);
				mService.updateStatus(mAccountIds, postString, attach_location ? mRecentLocation : null, mImageUri,
					mInReplyToStatusId, mIsPhotoAttached && !mIsImageAttached);			
			}
			setResult(Activity.RESULT_OK);
			finish();
		}
	}
	
	protected void actuallyPost() {
		final String text = mEditText != null ? parseString(mEditText.getText()) : null;
		final boolean attach_location = mPreferences.getBoolean(PREFERENCE_KEY_ATTACH_LOCATION, false);
		if (mScheduleDate != null) {
			mService.scheduleStatus(mScheduleDate, mAccountIds, text, attach_location ? mRecentLocation : null, mImageUri,
					mInReplyToStatusId, mIsPhotoAttached && !mIsImageAttached);
		}
		else if (mIsBuffer == true) {
			mService.bufferStatus(mAccountIds, text);
		}
		else {
			mService.updateStatus(mAccountIds, text, attach_location ? mRecentLocation : null, mImageUri,
					mInReplyToStatusId, mIsPhotoAttached && !mIsImageAttached);
		}
		setResult(Activity.RESULT_OK);
		finish();
	}
	
	protected void postMedia() {
		final String image_path = getImagePathFromUri(this, mImageUri);
		//final File image_file = image_path != null ? new File(image_path) : null;
		new UploadMediaTask().execute(image_path);
		
	}
	
	private class UploadMediaTask extends AsyncTask<String, Void, String> {
		private ProgressDialog dialog;
		
		
        // can use UI thread here
        protected void onPreExecute() {
        	dialog = new ProgressDialog(ComposeActivity.this);
        	dialog.setMessage(getString(R.string.uploading_media));
        	dialog.setIndeterminate(true);
        	dialog.setCancelable(false);
        	dialog.show();

        }
   
        // automatically done on worker thread (separate from UI thread)
        protected String doInBackground(final String... args) {
          String url = ComposeActivity.this.uploadImage(args[0]);
          return url;
        }
   
        // can use UI thread here
        protected void onPostExecute(final String result) {
        	//if (this.dialog.isShowing()) {
                //this.dialog.dismiss();
            //}
        	if (dialog.isShowing()) {
        		dialog.dismiss();
        	}
        	if (result == null) {
        		Dialog superSimpleDlg = new Dialog(ComposeActivity.this);
                superSimpleDlg.setTitle(R.string.error_upload);
                superSimpleDlg.show();
        	}
        	else {
        		
        		ComposeActivity.this.uploadComplete(result);
        		ComposeActivity.this.send();
        	}
        }
     }
	
	public void uploadComplete(String text) {
		String oldText = parseString(mEditText.getText());
		mEditText.setText(oldText + " " + text);
		
		mImageUri = null;
		reloadAttachedImageThumbnail(null);
		mIsPhotoAttached = false;
		mIsImageAttached = false;
	}
	
	private String uploadImage(String path) {
		String url = "";
		long accountId = 0;
		if (mAccountIds != null && mAccountIds.length > 0) {
			accountId = mAccountIds[0];
		}
		final AccessToken accessToken = getTwitterAccessToken(this, accountId);
		
		String consumer_key = mPreferences.getString(PREFERENCE_KEY_CONSUMER_KEY, CONSUMER_KEY);
		String consumer_secret = mPreferences.getString(PREFERENCE_KEY_CONSUMER_SECRET, CONSUMER_SECRET);
		if (isNullOrEmpty(consumer_key) || isNullOrEmpty(consumer_secret)) {
			consumer_key = CONSUMER_KEY;
			consumer_secret = CONSUMER_SECRET;
		}
		
		if (mUploadProvider.equals("twitpic")) {
			try {
				ConfigurationBuilder cb = new ConfigurationBuilder();
				cb.setMediaProviderAPIKey(TWITPIC_API_KEY);
				cb.setOAuthConsumerKey(consumer_key);
				cb.setOAuthConsumerSecret(consumer_secret);
				cb.setOAuthAccessToken(accessToken.getToken());
				cb.setOAuthAccessTokenSecret(accessToken.getTokenSecret());
				Configuration conf = cb.build();
				ImageUpload upload = new ImageUploadFactory(conf).getInstance(MediaProvider.TWITPIC);
				 url = upload.upload(new File(path));
			} catch (TwitterException te) {
	            
	        }
		}
		else if (mUploadProvider.equals("yfrog")) {
			try {
				ConfigurationBuilder cb = new ConfigurationBuilder();
				cb.setMediaProviderAPIKey(YFROG_API_KEY);
				cb.setOAuthConsumerKey(consumer_key);
				cb.setOAuthConsumerSecret(consumer_secret);
				cb.setOAuthAccessToken(accessToken.getToken());
				cb.setOAuthAccessTokenSecret(accessToken.getTokenSecret());
				Configuration conf = cb.build();
				ImageUpload upload = new ImageUploadFactory(conf).getInstance(MediaProvider.YFROG);
				 url = upload.upload(new File(path));
			} catch (TwitterException te) {
	            
	        }
		}
		else if (mUploadProvider.equals("imgly")) {
			try {
				ConfigurationBuilder cb = new ConfigurationBuilder();
				cb.setOAuthConsumerKey(consumer_key);
				cb.setOAuthConsumerSecret(consumer_secret);
				cb.setOAuthAccessToken(accessToken.getToken());
				cb.setOAuthAccessTokenSecret(accessToken.getTokenSecret());
				Configuration conf = cb.build();
				ImageUpload upload = new ImageUploadFactory(conf).getInstance(MediaProvider.IMG_LY);
				 url = upload.upload(new File(path));
			} catch (TwitterException te) {
	            
	        }
		}
		else if (mUploadProvider.equals("lockerz")) {
			try {
				ConfigurationBuilder cb = new ConfigurationBuilder();
				cb.setMediaProviderAPIKey(PLIXI_API_KEY);
				cb.setOAuthConsumerKey(consumer_key);
				cb.setOAuthConsumerSecret(consumer_secret);
				cb.setOAuthAccessToken(accessToken.getToken());
				cb.setOAuthAccessTokenSecret(accessToken.getTokenSecret());
				Configuration conf = cb.build();
				ImageUpload upload = new ImageUploadFactory(conf).getInstance(MediaProvider.LOCKERZ);
				 url = upload.upload(new File(path));
			} catch (TwitterException te) {
	            
	        }
		}
		else if (mUploadProvider.equals("moby")) {
			try {
				ConfigurationBuilder cb = new ConfigurationBuilder();
				cb.setMediaProviderAPIKey(MOBY_API_KEY);
				cb.setOAuthConsumerKey(consumer_key);
				cb.setOAuthConsumerSecret(consumer_secret);
				cb.setOAuthAccessToken(accessToken.getToken());
				cb.setOAuthAccessTokenSecret(accessToken.getTokenSecret());
				Configuration conf = cb.build();
				ImageUpload upload = new ImageUploadFactory(conf).getInstance(MediaProvider.MOBYPICTURE);
				 url = upload.upload(new File(path));
			} catch (TwitterException te) {
	            
	        }
		}
		return url;
		
	}
	
	private class UrlShortenerTask extends AsyncTask<String, Void, String> {
		private ProgressDialog dialog;
   
        // can use UI thread here
        protected void onPreExecute() {
        	dialog = new ProgressDialog(ComposeActivity.this);
        	dialog.setMessage(getString(R.string.shortening));
        	dialog.setIndeterminate(true);
        	dialog.setCancelable(false);
        	dialog.show();
        }
   
        // automatically done on worker thread (separate from UI thread)
        protected String doInBackground(final String... args) {
          String text = ComposeActivity.this.doShrink(args[0]);
          return text;
        }
   
        // can use UI thread here
        protected void onPostExecute(final String result) {
        	if (dialog.isShowing()) {
                dialog.dismiss();
            }
        	if (result == null) {
        		Dialog superSimpleDlg = new Dialog(ComposeActivity.this);
                superSimpleDlg.setTitle(R.string.error_shorten);
                superSimpleDlg.show();
        	}
        	else {
        		ComposeActivity.this.urlShortenerComplete(result);
        	}
        }
     }
	
	public void urlShortenerComplete(String text) {
		//String s = parseString(mEditText.getText());
		mEditText.setText(text);
	}
	
	private final void gatherLinks(ArrayList<Hyperlink> links,
            Spannable s, Pattern pattern)
	{
	// Matcher matching the pattern
		Matcher m = pattern.matcher(s);
		
		while (m.find())
		{
			int start = m.start();
			int end = m.end();
			
			/*
			*  Hyperlink is basically used like a structure for storing the information about
			*  where the link was found.
			*/
			Hyperlink spec = new Hyperlink();
			
			spec.textSpan = s.subSequence(start, end);
			spec.span = new InternalURLSpan(spec.textSpan.toString());
			spec.start = start;
			spec.end = end;
			
			links.add(spec);
		}
	}
	
	
	public class InternalURLSpan extends ClickableSpan
	
	{
	    private String clickedSpan;
	
	    public InternalURLSpan (String clickedString)
	    {
	        clickedSpan = clickedString;
	    }
	
	    @Override
	    public void onClick(View textView)
	    {
	       
	    }
	}
	
	/*
	 * Class for storing the information about the Link Location
	 */
	
	class Hyperlink
	{
	    CharSequence textSpan;
	    InternalURLSpan span;
	    int start;
	    int end;
	
	}
	
	private String doShrink(String text) {
		ArrayList<Hyperlink> listOfLinks = new ArrayList<Hyperlink>(); 
		
		SpannableString linkableText = new SpannableString(text);
		Pattern hyperLinksPattern = Pattern.compile("([Hh][tT][tT][pP][sS]?:\\/\\/[^ ,'\">\\]\\)]*[^\\. ,'\">\\]\\)])");
		gatherLinks(listOfLinks, linkableText, hyperLinksPattern);
		
		for (int i = 0; i<listOfLinks.size(); i++) {
			Hyperlink link = listOfLinks.get(i);
			final String provider = mPreferences.getString(PREFERENCE_KEY_URL_SHORTENER, "tinyurl");
			
			if (!link.textSpan.toString().toLowerCase().contains("http://tl.gd") &&
				!link.textSpan.toString().toLowerCase().contains("http://bit.ly") &&
				!link.textSpan.toString().toLowerCase().contains("http://t.co") &&
				!link.textSpan.toString().toLowerCase().contains("http://tinyurl.com") &&
				!link.textSpan.toString().toLowerCase().contains("http://is.gd") &&
				!link.textSpan.toString().toLowerCase().contains("http://tr.im") &&
				!link.textSpan.toString().toLowerCase().contains("http://twitpic.com") &&
				!link.textSpan.toString().toLowerCase().contains("http://yfrog.com") &&
				!link.textSpan.toString().toLowerCase().contains("http://pic.gd") &&
				!link.textSpan.toString().toLowerCase().contains("http://lockerz.com") &&
				!link.textSpan.toString().toLowerCase().contains("http://twitgoo.com") &&
				!link.textSpan.toString().toLowerCase().contains("http://twitrpix.com") &&
				!link.textSpan.toString().toLowerCase().contains("http://img.ly") &&
				!link.textSpan.toString().toLowerCase().contains("http://j.mp") &&
				!link.textSpan.toString().toLowerCase().contains("http://cli.cs")
					) {
				try {
					String finalUrl = "";
					
					if (provider.equals("googl")) {
						HttpClient httpClient = new DefaultHttpClient();
						HttpPost postRequest = new HttpPost("https://www.googleapis.com/urlshortener/v1/url");
						postRequest.addHeader("Content-Type", "application/json");
						
						StringEntity reqEntity = new StringEntity("{\"longUrl\": \"" + link.textSpan.toString() + "\"}", HTTP.UTF_8);
						postRequest.setEntity(reqEntity);
						
						HttpResponse response = httpClient.execute(postRequest);
						
						BufferedReader reader = new BufferedReader(new InputStreamReader(
						response.getEntity().getContent(), "UTF-8"));
						String sResponse;
						StringBuilder s = new StringBuilder();
		
						while ((sResponse = reader.readLine()) != null) {
							s = s.append(sResponse);
						}
						
						JSONObject jObject = new JSONObject(s.toString());
						
						text = text.replace(link.textSpan.toString(), jObject.getString("id"));
						
	
					}
					else {
						if (provider.equals("tinyurl")) {
							finalUrl = "http://tinyurl.com/api-create.php?url=" + URLEncoder.encode(link.textSpan.toString()); 
						}
						else if (provider.equals("isgd")) {
							finalUrl = "http://is.gd/api.php?longurl=" + URLEncoder.encode(link.textSpan.toString()); 
						}
						else if (provider.equals("bitly")) {
							finalUrl = "http://api.bitly.com/v3/shorten?format=txt&login=" + BITLY_USER + "&apiKey=" + BITLY_API_KEY + "&longUrl=" + URLEncoder.encode(link.textSpan.toString()); 
						}
						else if (provider.equals("jmp")) {
							finalUrl = "http://api.bitly.com/v3/shorten?domain=j.mp&format=txt&login=" + BITLY_USER + "&apiKey=" + BITLY_API_KEY + "&longUrl=" + URLEncoder.encode(link.textSpan.toString()); 
						}
						else if (provider.equals("vgd")) {
							finalUrl = "http://v.gd/create.php?format=simple&url=" + URLEncoder.encode(link.textSpan.toString()); 
						}
						else if (provider.equals("trim")) {
							finalUrl = "http://api.tr.im/v1/trim_simple?url=" + URLEncoder.encode(link.textSpan.toString()); 
						}
						else if (provider.equals("googl")) {
							finalUrl = "http://v.gd/create.php?format=simple&url=" + URLEncoder.encode(link.textSpan.toString()); 
						}
					    HttpClient httpclient = new DefaultHttpClient();
					    HttpResponse response = httpclient.execute(new HttpGet(finalUrl));
					    
					    BufferedReader reader = new BufferedReader(new InputStreamReader(
						response.getEntity().getContent(), "UTF-8"));
						String sResponse;
						StringBuilder s = new StringBuilder();
		
						while ((sResponse = reader.readLine()) != null) {
							s = s.append(sResponse);
						}
						text = text.replace(link.textSpan.toString(), s.toString());
						
						
					}
				    
				    
				   
				  } catch (Exception e) {
					  Toast.makeText(getApplicationContext(), "Network exception" + e.getMessage(), Toast.LENGTH_SHORT).show();
				  }

			}
				
			
		}
		
		return text;
		
	}
	
	private class TwitlongerTask extends AsyncTask<String, Void, String> {
        //private final ProgressDialog dialog = new ProgressDialog(ComposeActivity.this);
   
		private ProgressDialog dialog;
		
		
        // can use UI thread here
        protected void onPreExecute() {
        	dialog = new ProgressDialog(ComposeActivity.this);
        	dialog.setMessage(getString(R.string.twitlongering));
        	dialog.setIndeterminate(true);
        	dialog.setCancelable(false);
        	dialog.show();

        }
   
        // automatically done on worker thread (separate from UI thread)
        protected String doInBackground(final String... args) {
          String text = ComposeActivity.this.uploadTwitlonger(args[0]);
          return text;
        }
   
        // can use UI thread here
        protected void onPostExecute(final String result) {
        	if (dialog.isShowing()) {
                dialog.dismiss();
            }
        	if (result == null) {
        		Dialog superSimpleDlg = new Dialog(ComposeActivity.this);
                superSimpleDlg.setTitle(R.string.error_upload_twitlonger);
                superSimpleDlg.show();
        	}
        	else {
        		ComposeActivity.this.twitlongerComplete(result);
        		ComposeActivity.this.actuallyPost();
        	}
        }
     }
	
	private String uploadTwitlonger(String text) {
		String finalUrl = "http://www.twitlonger.com/api_post/";
		
		final String atext = parseString(mEditText.getText());
		String screen_name = null;
		if (mAccountIds != null && mAccountIds.length > 0) {
			screen_name = getAccountUsername(this, mAccountIds[0]);
		}
		
		  try {
		    HttpClient httpclient = new DefaultHttpClient();
		    HttpPost postRequest = new HttpPost(finalUrl);
			MultipartEntity reqEntity = new MultipartEntity(
					HttpMultipartMode.BROWSER_COMPATIBLE);
			
			
			reqEntity.addPart("username", new StringBody(screen_name));
			reqEntity.addPart("application", new StringBody(TWIT_LONGER_USER));
			reqEntity.addPart("api_key", new StringBody(TWIT_LONGER_API_KEY));
			reqEntity.addPart("message", new StringBody(atext, Charset.forName("UTF-8")));
			postRequest.setEntity(reqEntity);
			
			HttpResponse response = httpclient.execute(postRequest);
		    
		    DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
			domFactory.setNamespaceAware(true); // never forget this!
			DocumentBuilder builder = domFactory.newDocumentBuilder();
			Document doc = builder.parse(response.getEntity().getContent());
			
			XPathFactory factory = XPathFactory.newInstance();
			XPath xpath = factory.newXPath();
			XPathExpression expr;
			expr = xpath.compile("//twitlonger/post/content/text()");
			
			Object result = expr.evaluate(doc, XPathConstants.STRING);
			Log.d("ComposeActivity.uploadTwitlonger","path: " + atext + " " + result.toString());

			
			return result.toString();
		  } catch (Exception e) {
			 
			  //Toast.makeText(getApplicationContext(), "Network exception" + e.getMessage(), Toast.LENGTH_SHORT).show();
		  }
		return null;
	}
	
	protected void postTwitlonger() {
		final String text = mEditText != null ? parseString(mEditText.getText()) : null;
		
		new TwitlongerTask().execute(text);
	}
	
	public void twitlongerComplete(String text) {
		mEditText.setText(text);
	}
	

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu) {
		final String text_orig = mEditText != null ? parseString(mEditText.getText()) : null;
		final String text = mIsPhotoAttached || mIsImageAttached ? mUploadUseExtension ? getImageUploadStatus(this,
				FAKE_IMAGE_LINK, text_orig) : text_orig + " " + FAKE_IMAGE_LINK : text_orig;
		if (mTextCount != null) {
			final int count = mValidator.getTweetLength(text);
			final float hue = count < 140 ? count >= 130 ? 5 * (140 - count) : 50 : 0;
			final float[] hsv = new float[] { hue, 1.0f, 1.0f };
			mTextCount.setTextColor(count >= 130 ? Color.HSVToColor(0x80, hsv) : 0x80808080);
			mTextCount.setText(parseString(140-count));
		}
		final MenuItem sendItem = menu.findItem(MENU_SEND);
		sendItem.setEnabled(text_orig.length() > 0);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onProviderDisabled(final String provider) {
	}

	@Override
	public void onProviderEnabled(final String provider) {
	}

	@Override
	public void onSaveInstanceState(final Bundle outState) {
		mText = parseString(mEditText.getText());
		outState.putLongArray(INTENT_KEY_IDS, mAccountIds);
		outState.putString(INTENT_KEY_TEXT, mText);
		outState.putLong(INTENT_KEY_IN_REPLY_TO_ID, mInReplyToStatusId);
		outState.putString(INTENT_KEY_IN_REPLY_TO_NAME, mInReplyToName);
		outState.putString(INTENT_KEY_IN_REPLY_TO_SCREEN_NAME, mInReplyToScreenName);
		outState.putBoolean(INTENT_KEY_IS_QUOTE, mIsQuote);
		outState.putBoolean(INTENT_KEY_IS_IMAGE_ATTACHED, mIsImageAttached);
		outState.putBoolean(INTENT_KEY_IS_PHOTO_ATTACHED, mIsPhotoAttached);
		outState.putParcelable(INTENT_KEY_IMAGE_URI, mImageUri);
		outState.putBoolean(INTENT_KEY_CONTENT_MODIFIED, mContentModified);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onStart() {
		super.onStart();
		final String component = mPreferences.getString(PREFERENCE_KEY_IMAGE_UPLOADER, null);
		mUploadUseExtension = !isNullOrEmpty(component);
		if (mMenuBar != null) {
			setMenu(mMenuBar.getMenu());
		}
	}

	@Override
	public void onStatusChanged(final String provider, final int status, final Bundle extras) {

	}

	@Override
	public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
		invalidateSupportOptionsMenu();
		mContentModified = true;
	}
	
	public void saveToDrafts() {
		final String text = mEditText != null ? parseString(mEditText.getText()) : null;
	 	final ContentValues values = new ContentValues();
	 	values.put(Drafts.TEXT, text);
	 	values.put(Drafts.ACCOUNT_IDS, ArrayUtils.toString(mAccountIds, ',', false));
	 	values.put(Drafts.IN_REPLY_TO_STATUS_ID, mInReplyToStatusId);
	 	values.put(Drafts.IN_REPLY_TO_NAME, mInReplyToName);
	 	values.put(Drafts.IN_REPLY_TO_SCREEN_NAME, mInReplyToScreenName);
	 	values.put(Drafts.IS_QUOTE, mIsQuote ? 1 : 0);
	 	values.put(Drafts.IS_QUEUED, false);
	 	if (mImageUri != null) {
	 		values.put(Drafts.IS_IMAGE_ATTACHED, mIsImageAttached);
	 		values.put(Drafts.IS_PHOTO_ATTACHED, mIsPhotoAttached);
	 		values.put(Drafts.IMAGE_URI, parseString(mImageUri));
	 	 }
	 	 mResolver.insert(Drafts.CONTENT_URI, values);
	}

	@Override
	protected void onStop() {
		if (mPopupMenu != null) {
			mPopupMenu.dismiss();
		}
		super.onStop();
	}

	/**
	 * The Location Manager manages location providers. This code searches for
	 * the best provider of data (GPS, WiFi/cell phone tower lookup, some other
	 * mechanism) and finds the last known location.
	 **/
	private boolean getLocation() {
		final Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		final String provider = mLocationManager.getBestProvider(criteria, true);

		if (provider != null) {
			mRecentLocation = mLocationManager.getLastKnownLocation(provider);
		} else {
			Toast.makeText(this, R.string.cannot_get_location, Toast.LENGTH_SHORT).show();
		}
		return provider != null;
	}

	private void pickImage() {
		final Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		try {
			startActivityForResult(i, REQUEST_PICK_IMAGE);
		} catch (final ActivityNotFoundException e) {
			showErrorToast(this, null, e, false);
		}
	}

	private void reloadAttachedImageThumbnail(final File file) {
		if (mAttachedImageThumbnailTask != null && mAttachedImageThumbnailTask.getStatus() == AsyncTask.Status.RUNNING) {
			mAttachedImageThumbnailTask.cancel(true);
		}
		mAttachedImageThumbnailTask = new AttachedImageThumbnailTask(file);
		mAttachedImageThumbnailTask.execute();
	}

	private void setMenu(final Menu menu) {
		final int activated_color = getResources().getColor(R.color.holo_blue_bright);
		final MenuItem itemAddImage = menu.findItem(MENU_ADD_IMAGE);
		final MenuItem itemLibrary = menu.findItem(MENU_LIBRARY_MENU);
		final Drawable iconAddImage = itemAddImage.getIcon().mutate();
		final Drawable iconLibrary = itemLibrary.getIcon().mutate();
		boolean menuSelected = false;
		if (mIsImageAttached && !mIsPhotoAttached) {
			iconAddImage.setColorFilter(activated_color, Mode.MULTIPLY);
			itemAddImage.setTitle(R.string.remove_image);
			menuSelected = true;
		} else {
			iconAddImage.clearColorFilter();
			itemAddImage.setTitle(R.string.add_image);
		}
		final MenuItem itemTakePhoto = menu.findItem(MENU_TAKE_PHOTO);
		final Drawable iconTakePhoto = itemTakePhoto.getIcon().mutate();
		if (!mIsImageAttached && mIsPhotoAttached) {
			iconTakePhoto.setColorFilter(activated_color, Mode.MULTIPLY);
			itemTakePhoto.setTitle(R.string.remove_photo);
			menuSelected = true;
		} else {
			iconTakePhoto.clearColorFilter();
			itemTakePhoto.setTitle(R.string.take_photo);
		}
		if (menuSelected == true) {
			iconLibrary.setColorFilter(activated_color, Mode.MULTIPLY);
		}
		else {
			iconLibrary.clearColorFilter();
		}
		final MenuItem itemAttachLocation = menu.findItem(MENU_ADD_LOCATION);
		final Drawable iconAttachLocation = itemAttachLocation.getIcon().mutate();
		final boolean attach_location = mPreferences.getBoolean(PREFERENCE_KEY_ATTACH_LOCATION, false);
		if (attach_location && getLocation()) {
			iconAttachLocation.setColorFilter(activated_color, Mode.MULTIPLY);
			itemAttachLocation.setTitle(R.string.remove_location);
		} else {
			mPreferences.edit().putBoolean(PREFERENCE_KEY_ATTACH_LOCATION, false).commit();
			iconAttachLocation.clearColorFilter();
			itemAttachLocation.setTitle(R.string.add_location);
		}
		final MenuItem itemMore = menu.findItem(R.id.more_submenu);

		boolean moreHighlighted = false;
		if (itemMore != null) {
			final MenuItem itemDrafts = menu.findItem(R.id.drafts);
			final Drawable iconDrafts = itemDrafts.getIcon().mutate();
			
			final Cursor drafts_cur = getContentResolver().query(Drafts.CONTENT_URI, new String[0], null, null, null);
			if (drafts_cur.getCount() > 0) {
				iconDrafts.setColorFilter(activated_color, Mode.MULTIPLY);
				moreHighlighted = true;
			} else {
				moreHighlighted = false;
				iconDrafts.clearColorFilter();
			}
			drafts_cur.close();
		}
		if (mScheduleDate != null) {
			final MenuItem itemSchedule = menu.findItem(R.id.schedule_tweet);
			final Drawable iconSchedule = itemSchedule.getIcon().mutate();
			iconSchedule.setColorFilter(activated_color, Mode.MULTIPLY);
			moreHighlighted = true;
			itemSchedule.setTitle(getString(R.string.schedule_clear));
		}
		else {
			final MenuItem itemSchedule = menu.findItem(R.id.schedule_tweet);
			final Drawable iconSchedule = itemSchedule.getIcon().mutate();
			iconSchedule.clearColorFilter();
			itemSchedule.setTitle(getString(R.string.schedule_tweet));
		}
		
		
		final String consumer_key = mPreferences.getString(PREFERENCE_KEY_CONSUMER_KEY, null);
		final String consumer_secret = mPreferences.getString(PREFERENCE_KEY_CONSUMER_SECRET, null);
		
		if (!isNullOrEmpty(consumer_key) && !isNullOrEmpty(consumer_secret)) {
			final MenuItem itemSchedule = menu.findItem(R.id.schedule_tweet);
			
			itemSchedule.setVisible(false);
		}
		
		final MenuItem bufferItem = menu.findItem(MENU_ADD_TO_BUFFER);
		if (bufferItem != null) {
			final String buffer_authorised = mPreferences.getString(PREFERENCE_KEY_BUFFERAPP_ACCESS_TOKEN, null);
			final Drawable iconBuffer = bufferItem.getIcon().mutate();
			if (buffer_authorised != null && !buffer_authorised.equals("")) {
				bufferItem.setVisible(true);
				if (mIsBuffer) {
					iconBuffer.setColorFilter(activated_color, Mode.MULTIPLY);
					moreHighlighted = true;
				}
				else {
					iconBuffer.clearColorFilter();
				}
			}
			else {
				bufferItem.setVisible(false);
				mIsBuffer = false;
			}
		}
		if (itemMore != null) {
			final Drawable iconMore = itemMore.getIcon().mutate();
			if (moreHighlighted == true) {
				iconMore.setColorFilter(activated_color, Mode.MULTIPLY);
			} else {
				iconMore.clearColorFilter();
			}
		}
		
		invalidateSupportOptionsMenu();
	}

	private void takePhoto() {
		final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		if (getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			final File cache_dir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? GetExternalCacheDirAccessor
					.getExternalCacheDir(this) : new File(getExternalStorageDirectory().getPath() + "/Android/data/"
					+ getPackageName() + "/cache/");
			final File file = new File(cache_dir, "tmp_photo_" + System.currentTimeMillis() + ".jpg");
			mImageUri = Uri.fromFile(file);
			intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, mImageUri);
			try {
				startActivityForResult(intent, REQUEST_TAKE_PHOTO);
			} catch (final ActivityNotFoundException e) {
				showErrorToast(this, null, e, false);
			}
		}
	}
	
	public static class UnsavedTweetDialogFragment extends BaseDialogFragment implements DialogInterface.OnClickListener {

		private boolean mIsNavigateUp;
		
		@Override
	 	public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			final Bundle args = getArguments();
			if (args != null) {
				mIsNavigateUp = args.getBoolean(INTENT_KEY_IS_NAVIGATE_UP);
			}
	 	}
		
		@Override
		public void onClick(final DialogInterface dialog, final int which) {
			final FragmentActivity activity = getActivity();
			switch (which) {
				case DialogInterface.BUTTON_POSITIVE: {
					if (activity instanceof ComposeActivity) {
						((ComposeActivity) activity).saveToDrafts();
						if (mIsNavigateUp) {
							NavUtils.navigateUpFromSameTask(activity);  
						} else {
							activity.finish();
						}
					}
					break;
				}
				case DialogInterface.BUTTON_NEGATIVE: {
					if (mIsNavigateUp) {
						NavUtils.navigateUpFromSameTask(activity);  
					} else {
						activity.finish();
					}
					break;
				}
			}

		}

		@Override
		public Dialog onCreateDialog(final Bundle savedInstanceState) {
			final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage(R.string.unsaved_tweet);
			builder.setPositiveButton(R.string.save, this);
			builder.setNeutralButton(android.R.string.cancel, null);
			builder.setNegativeButton(R.string.discard, this);
			return builder.create();
		}

	}


	class AttachedImageThumbnailTask extends AsyncTask<Void, Void, Bitmap> {

		private final File file;

		public AttachedImageThumbnailTask(final File file) {
			this.file = file;
		}

		@Override
		protected Bitmap doInBackground(final Void... args) {
			if (file != null && file.exists()) {
				final int thumbnail_size_px = (int) (THUMBNAIL_SIZE * getResources().getDisplayMetrics().density);
				final BitmapFactory.Options o = new BitmapFactory.Options();
				o.inJustDecodeBounds = true;
				BitmapFactory.decodeFile(file.getPath(), o);
				final int tmp_width = o.outWidth;
				final int tmp_height = o.outHeight;
				if (tmp_width == 0 || tmp_height == 0) return null;
				final BitmapFactory.Options o2 = new BitmapFactory.Options();
				o2.inSampleSize = Math.round(Math.max(tmp_width, tmp_height) / thumbnail_size_px);
				return BitmapFactory.decodeFile(file.getPath(), o2);
			}
			return null;
		}

		@Override
		protected void onPostExecute(final Bitmap result) {
			mImageThumbnailPreview.setVisibility(result != null ? View.VISIBLE : View.GONE);
			mImageThumbnailPreview.setImageBitmap(result);
			super.onPostExecute(result);
		}

	}
}
