/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dwdesign.gallery3d.app;

import com.dwdesign.gallery3d.data.BitmapPool;
import com.dwdesign.gallery3d.data.DataManager;
import com.dwdesign.gallery3d.data.MediaItem;
import com.dwdesign.gallery3d.data.MediaObject;
import com.dwdesign.gallery3d.data.Path;
import com.dwdesign.gallery3d.ui.GLRoot;
import com.dwdesign.gallery3d.ui.GLRootView;
import com.dwdesign.gallery3d.ui.GLView;
import com.dwdesign.gallery3d.ui.PhotoView;
import com.dwdesign.gallery3d.ui.SynchronizedHandler;
import com.dwdesign.gallery3d.util.GalleryUtils;
import com.dwdesign.gallery3d.util.ThreadPool;
import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.util.SimpleGestureFilter;
import com.dwdesign.tweetings.util.SimpleGestureFilter.SimpleGestureListener;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public final class ImageViewerGLActivity extends FragmentActivity implements Constants, View.OnClickListener,
		GalleryContext, PhotoView.Listener, OrientationManager.Listener, SimpleGestureListener {

	private static final String TAG = "Gallery";
	
	private SimpleGestureFilter detector;

	private GLRootView mGLRootView;

	private View mProgress;
	private View mControlButtons;

	private OrientationManager mOrientationManager;

	private AlertDialog mAlertDialog = null;

	// private boolean mDisableToggleStatusBar;

	private final BroadcastReceiver mMountReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			if (getExternalCacheDir() != null) {
				onStorageReady();
			}
		}
	};

	protected static final int FLAG_HIDE_ACTION_BAR = 1;
	protected static final int FLAG_HIDE_STATUS_BAR = 2;
	protected static final int FLAG_SCREEN_ON_WHEN_PLUGGED = 4;
	protected static final int FLAG_SCREEN_ON_ALWAYS = 8;
	protected static final int FLAG_ALLOW_LOCK_WHILE_SCREEN_ON = 16;
	protected static final int FLAG_SHOW_WHEN_LOCKED = 32;

	protected Bundle mData;
	protected int mFlags;

	protected ResultEntry mReceivedResults;

	protected ResultEntry mResult;
	private boolean mPlugged = false;

	boolean mIsFinishing = false;

	private GLView mContentPane;
	protected float[] mBackgroundColor;
	BroadcastReceiver mPowerIntentReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
				final boolean plugged = 0 != intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);

				if (plugged != mPlugged) {
					mPlugged = plugged;
					setScreenFlags();
				}
			}
		}
	};

	private static final int MSG_HIDE_BARS = 1;

	private static final int MSG_ON_FULL_SCREEN_CHANGED = 4;

	private static final int MSG_UPDATE_ACTION_BAR = 5;

	private static final int MSG_UNFREEZE_GLROOT = 6;

	private static final int MSG_WANT_BARS = 7;

	private static final int MSG_REFRESH_BOTTOM_CONTROLS = 8;

	private static final int MSG_ON_CAMERA_CENTER = 9;

	private static final int MSG_ON_PICTURE_CENTER = 10;

	private static final int MSG_REFRESH_IMAGE = 11;

	private static final int MSG_UPDATE_PHOTO_UI = 12;

	private static final int MSG_UPDATE_PROGRESS = 13;

	private static final int MSG_UPDATE_DEFERRED = 14;

	private static final int MSG_UPDATE_SHARE_URI = 15;

	private static final int HIDE_BARS_TIMEOUT = 3500;

	private static final int UNFREEZE_GLROOT_TIMEOUT = 250;

	public static final String KEY_MEDIA_ITEM_PATH = "media-item-path";

	public static final String KEY_INDEX_HINT = "index-hint";
	public static final String KEY_APP_BRIDGE = "app-bridge";
	public static final String KEY_TREAT_BACK_AS_UP = "treat-back-as-up";
	public static final String KEY_START_IN_FILMSTRIP = "start-in-filmstrip";
	public static final String KEY_RETURN_INDEX_HINT = "return-index-hint";
	public static final String KEY_SHOW_WHEN_LOCKED = "show_when_locked";
	private PhotoView mPhotoView;
	private Model mModel;
	private boolean mShowDetails;
	private int mCurrentIndex = 0;
	private Handler mHandler;
	private boolean mShowBars = true;
	private volatile boolean mActionBarAllowed = true;

	private boolean mIsMenuVisible;
	private MediaItem mCurrentPhoto = null;

	private final long mDeferUpdateUntil = Long.MAX_VALUE;
	private final GLView mRootPane = new GLView() {
		@Override
		protected void onLayout(final boolean changed, final int left, final int top, final int right, final int bottom) {
			mPhotoView.layout(0, 0, right - left, bottom - top);
		}
	};

	@Override
	public Context getAndroidContext() {
		return this;
	}

	public Bundle getData() {
		return mData;
	}

	@Override
	public DataManager getDataManager() {
		return ((IGalleryApplication) getApplication()).getDataManager();
	}

	public GLRoot getGLRoot() {
		return mGLRootView;
	}

	public OrientationManager getOrientationManager() {
		return mOrientationManager;
	}

	@Override
	public ThreadPool getThreadPool() {
		return ((IGalleryApplication) getApplication()).getThreadPool();
	}

	public void hideControls() {
		mControlButtons.setVisibility(View.GONE);
	}

	public void hideProgress() {
		mProgress.setVisibility(View.GONE);
	}

	@Override
	public boolean isFinishing() {
		return mIsFinishing;
	}

	@Override
	public void onActionBarAllowed(final boolean allowed) {
		mActionBarAllowed = allowed;
		mHandler.sendEmptyMessage(MSG_UPDATE_ACTION_BAR);
	}

	@Override
	public void onActionBarWanted() {
		mHandler.sendEmptyMessage(MSG_WANT_BARS);
	}

	@Override
	public void onClick(final View v) {
		switch (v.getId()) {
			case R.id.close: {
				onBackPressed();
				break;
			}
			case R.id.refresh_stop_save: {
				final Uri contentUri = mCurrentPhoto.getFilePath();
				saveImage(contentUri);
				/*final MediaItem photo = mCurrentPhoto;
				mCurrentPhoto = null;
				updateCurrentPhoto(photo);*/
				break;
			}
			case R.id.share: {
				final Uri contentUri = mCurrentPhoto.getFilePath();
				if (contentUri != null) {
					final Intent intent = new Intent(Intent.ACTION_SEND);
					intent.setType("image/*");
					intent.putExtra(Intent.EXTRA_STREAM, contentUri);
					startActivity(Intent.createChooser(intent, getString(R.string.share)));
				}
				//final Intent intent = createShareIntent(mCurrentPhoto);
				//startActivity(Intent.createChooser(intent, getString(R.string.share)));
				break;
			}
			case R.id.open_in_browser: {
				final Uri contentUri = mCurrentPhoto.getContentUri();
				
				final String scheme = contentUri.getScheme();
				if ("http".equals(scheme) || "https".equals(scheme)) {
					final Intent intent = new Intent(Intent.ACTION_VIEW, contentUri);
					intent.addCategory(Intent.CATEGORY_BROWSABLE);
					try {
						startActivity(intent);
					} catch (final ActivityNotFoundException e) {
						// Ignore.
					}
				}
				break;
			}
		}
	}
	
	private void saveImage(Uri uri) {
		if (uri == null) return;
		final Uri contentUri = mCurrentPhoto.getContentUri();
		String file_name = contentUri.getLastPathSegment();
		final BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(uri.getPath(), o);
		final String mime_type = o.outMimeType;
		String file_name_with_suffix = null;
		
		if (file_name.matches("(.*/)*.+\\.(png|jpg|gif|bmp|jpeg|PNG|JPG|JPEG|GIF|BMP)$")) {
			file_name_with_suffix = file_name;
		} else {
			if (mime_type == null) return;
			if (mime_type.startsWith("image/") && !"image/*".equals(mime_type)) {
				String mime_type_fixed = mime_type.substring(6);
				if (mime_type_fixed.equals("jpeg")) {
					mime_type_fixed = "jpg";
				}
				file_name_with_suffix = file_name + "." + mime_type_fixed;
			}
		}
		
		final Intent intent = new Intent(INTENT_ACTION_SAVE_FILE);
		intent.setPackage(getPackageName());
		intent.putExtra(INTENT_KEY_FILE_SOURCE, uri.getPath());
		intent.putExtra(INTENT_KEY_FILENAME, file_name_with_suffix);
		startActivity(intent);
	
	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();
		mGLRootView = (GLRootView) findViewById(R.id.gl_root_view);
		mProgress = findViewById(R.id.progress);
		mControlButtons = findViewById(R.id.control_buttons);
	}

	@Override
	public void onCurrentImageUpdated() {
		getGLRoot().unfreeze();
	}

	@Override
	public void onOrientationCompensationChanged() {
		getGLRoot().requestLayoutContentPane();
	}

	@Override
	public void onPictureCenter() {
		mPhotoView.setWantPictureCenterCallbacks(false);
		mHandler.removeMessages(MSG_ON_CAMERA_CENTER);
		mHandler.removeMessages(MSG_ON_PICTURE_CENTER);
		mHandler.sendEmptyMessage(MSG_ON_PICTURE_CENTER);
	}

	// //////////////////////////////////////////////////////////////////////////
	// Callbacks from PhotoView
	// //////////////////////////////////////////////////////////////////////////
	@Override
	public void onSingleTapUp(final int x, final int y) {

		final MediaItem item = mModel.getMediaItem();
		if (item == null) // item is not ready or it is camera preview, ignore
			return;

		toggleBars();
	}

	public void showControls() {
		mControlButtons.setVisibility(View.VISIBLE);
	}

	public void showProgress() {
		mProgress.setVisibility(View.VISIBLE);
	}

	public void state_onCreate(final Bundle data, final Bundle restoreState) {
		mBackgroundColor = GalleryUtils.intColorToFloatARGBArray(Color.BLACK);

		mPhotoView = new PhotoView(this);
		mPhotoView.setListener(this);
		mRootPane.addComponent(mPhotoView);
		mOrientationManager = getOrientationManager();
		mOrientationManager.addListener(this);
		getGLRoot().setOrientationSource(mOrientationManager);
		
		mHandler = new SynchronizedHandler(getGLRoot()) {
			@Override
			public void handleMessage(final Message message) {
				switch (message.what) {
					case MSG_HIDE_BARS: {
						hideBars();
						break;
					}
					case MSG_REFRESH_BOTTOM_CONTROLS: {
						break;
					}
					case MSG_ON_FULL_SCREEN_CHANGED: {
						break;
					}
					case MSG_UPDATE_ACTION_BAR: {
						updateBars();
						break;
					}
					case MSG_WANT_BARS: {
						wantBars();
						break;
					}
					case MSG_UNFREEZE_GLROOT: {
						getGLRoot().unfreeze();
						break;
					}
					case MSG_UPDATE_DEFERRED: {
						final long nextUpdate = mDeferUpdateUntil - SystemClock.uptimeMillis();
						if (nextUpdate <= 0) {
							updateUIForCurrentPhoto();
						} else {
							mHandler.sendEmptyMessageDelayed(MSG_UPDATE_DEFERRED, nextUpdate);
						}
						break;
					}
					case MSG_ON_CAMERA_CENTER: {
						boolean stayedOnCamera = false;
						stayedOnCamera = true;

						if (stayedOnCamera) {
							if (null == null) {
								/*
								 * We got here by swiping from photo 1 to the
								 * placeholder, so make it be the thing that is
								 * in focus when the user presses back from the
								 * camera app
								 */
							} else {
								updateBars();
								updateCurrentPhoto(mModel.getMediaItem());
							}
						}
						break;
					}
					case MSG_ON_PICTURE_CENTER: {
						break;
					}
					case MSG_REFRESH_IMAGE: {
						final MediaItem photo = mCurrentPhoto;
						mCurrentPhoto = null;
						updateCurrentPhoto(photo);
						break;
					}
					case MSG_UPDATE_PHOTO_UI: {
						updateUIForCurrentPhoto();
						break;
					}
					case MSG_UPDATE_PROGRESS: {
						updateProgressBar();
						break;
					}
					case MSG_UPDATE_SHARE_URI: {
						if (mCurrentPhoto == message.obj) {
							mCurrentPhoto.getContentUri();
							createShareIntent(mCurrentPhoto);
						}
						break;
					}
					default:
						throw new AssertionError(message.what);
				}
			}
		};

		final String itemPathString = data.getString(KEY_MEDIA_ITEM_PATH);
		final Path itemPath = itemPathString != null ? Path.fromString(data.getString(KEY_MEDIA_ITEM_PATH)) : null;
		
		mCurrentIndex = data.getInt(KEY_INDEX_HINT, 0);
		// Get default media set by the URI
		final MediaItem mediaItem = (MediaItem) getDataManager().getMediaObject(itemPath);
		mModel = new SinglePhotoDataAdapter(this, mPhotoView, mediaItem);
		mPhotoView.setModel(mModel);
		updateCurrentPhoto(mediaItem);

	}

	public void state_onPause() {
		if (0 != (mFlags & FLAG_SCREEN_ON_WHEN_PLUGGED)) {
			((Activity) this).unregisterReceiver(mPowerIntentReceiver);
		}

		getGLRoot().unfreeze();
		mHandler.removeMessages(MSG_UNFREEZE_GLROOT);

		// Hide the detail dialog on exit
		if (mShowDetails) {
			hideDetails();
		}
		if (mModel != null) {
			mModel.pause();
		}
		mPhotoView.pause();
		mHandler.removeMessages(MSG_HIDE_BARS);
		mHandler.removeMessages(MSG_REFRESH_BOTTOM_CONTROLS);
	}

	protected void clearStateResult() {
	}

	protected float[] getBackgroundColor() {
		return mBackgroundColor;
	}

	protected int getBackgroundColorId() {
		return android.R.color.transparent;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mOrientationManager = new OrientationManager(this);
		detector = new SimpleGestureFilter(this, this);

		setContentView(R.layout.image_viewer_gl);

		if (savedInstanceState != null) {
		} else {
			initializeByIntent();
		}
	}
	
	@Override 
    public boolean dispatchTouchEvent(MotionEvent me){ 
		this.detector.onTouchEvent(me);
		return super.dispatchTouchEvent(me); 
    }
	
	@Override
	public void onDoubleTap() { 
	}
	
	@Override
	public void onSwipe(int direction) {
		  switch (direction) {
		  	case SimpleGestureFilter.SWIPE_RIGHT:
		  		break;
		  	case SimpleGestureFilter.SWIPE_LEFT:
		        break;
		  	case SimpleGestureFilter.SWIPE_DOWN:
		  		onBackPressed();
		        break;
		  	case SimpleGestureFilter.SWIPE_UP:
		  		break;
		                                           
		  }
	 }

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mGLRootView.lockRenderThread();
		try {
			state_onDestroy();
		} finally {
			mGLRootView.unlockRenderThread();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		mOrientationManager.pause();
		mGLRootView.onPause();
		mGLRootView.lockRenderThread();
		try {
			state_onPause();
		} finally {
			mGLRootView.unlockRenderThread();
		}
		clearBitmapPool(MediaItem.getThumbPool());

	}

	@Override
	protected void onResume() {
		super.onResume();
		mGLRootView.lockRenderThread();
		try {
			resume();
		} finally {
			mGLRootView.unlockRenderThread();
		}
		mGLRootView.onResume();
		mOrientationManager.resume();
	}

	@Override
	protected void onSaveInstanceState(final Bundle outState) {
		mGLRootView.lockRenderThread();
		try {
			super.onSaveInstanceState(outState);
		} finally {
			mGLRootView.unlockRenderThread();
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (mAlertDialog != null) {
			unregisterReceiver(mMountReceiver);
			mAlertDialog.dismiss();
			mAlertDialog = null;
		}
	}

	protected void onStorageReady() {
		if (mAlertDialog != null) {
			mAlertDialog.dismiss();
			mAlertDialog = null;
			unregisterReceiver(mMountReceiver);
		}
	}

	protected void setContentPane(final GLView content) {
		mContentPane = content;
		mContentPane.setBackgroundColor(getBackgroundColor());
		final GLRoot root = getGLRoot();
		root.setContentPane(mContentPane);
	}

	protected void setStateResult(final int resultCode, final Intent data) {
		if (mResult == null) return;
		mResult.resultCode = resultCode;
		mResult.resultData = data;
	}

	protected void state_onBackPressed() {

	}

	protected void state_onDestroy() {
		mOrientationManager.removeListener(this);
		getGLRoot().setOrientationSource(null);

		// Remove all pending messages.
		mHandler.removeCallbacksAndMessages(null);
	}

	protected void state_onResume() {

		if (mModel == null) {
			finish();
			return;
		}

		getGLRoot().freeze();
		setContentPane(mRootPane);

		mModel.resume();
		mPhotoView.resume();
		if (!mShowBars) {
			hideControls();
		}
		mHandler.sendEmptyMessageDelayed(MSG_UNFREEZE_GLROOT, UNFREEZE_GLROOT_TIMEOUT);
	}

	private boolean canShowBars() {
		// No bars if we are showing camera preview.
		if (null != null && mCurrentIndex == 0) return false;

		// No bars if it's not allowed.
		if (!mActionBarAllowed) return false;

		return true;
	}

	private String getContentType(final Intent intent) {
		final String type = intent.getType();
		if (type != null) return type;

		final Uri uri = intent.getData();
		try {
			return getContentResolver().getType(uri);
		} catch (final Throwable t) {
			Log.w(TAG, "get type fail", t);
			return null;
		}
	}

	private void hideBars() {
		if (!mShowBars) return;
		mShowBars = false;
		hideControls();
		mHandler.removeMessages(MSG_HIDE_BARS);
	}

	private void hideDetails() {
		mShowDetails = false;
	}

	private void initializeByIntent() {
		final Intent intent = getIntent();
		final String action = intent.getAction();

		if (INTENT_ACTION_VIEW_IMAGE.equals(action)) {
			startViewAction(intent);
		} else {
			finish();
		}
	}

	private void refreshHidingMessage() {
		mHandler.removeMessages(MSG_HIDE_BARS);
		if (!mIsMenuVisible) {
			mHandler.sendEmptyMessageDelayed(MSG_HIDE_BARS, HIDE_BARS_TIMEOUT);
		}
	}

	private void setScreenFlags() {
		final Window win = getWindow();
		final WindowManager.LayoutParams params = win.getAttributes();
		if (0 != (mFlags & FLAG_SCREEN_ON_ALWAYS) || mPlugged && 0 != (mFlags & FLAG_SCREEN_ON_WHEN_PLUGGED)) {
			params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
		} else {
			params.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
		}
		if (0 != (mFlags & FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)) {
			params.flags |= WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;
		} else {
			params.flags &= ~WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;
		}
		if (0 != (mFlags & FLAG_SHOW_WHEN_LOCKED)) {
			params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
		} else {
			params.flags &= ~WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
		}
		win.setAttributes(params);
	}

	private void showBars() {
		if (mShowBars) return;
		mShowBars = true;
		showControls();
		mOrientationManager.unlockOrientation();
		refreshHidingMessage();
	}

	private void startViewAction(final Intent intent) {
		final Bundle data = new Bundle();
		final DataManager dm = getDataManager();
		final Uri uri = intent.getData();
		final String contentType = getContentType(intent);
		if (contentType == null) {
			// Toast.makeText(this, R.string.no_such_item,
			// Toast.LENGTH_LONG).show();
			// finish();
			// return;
		}
		if (uri == null) {
			finish();
		} else {
			final Path itemPath = dm.findPathByUri(uri, contentType);
			data.putString(KEY_MEDIA_ITEM_PATH, itemPath.toString());
			mData = data;
			state_onCreate(data, null);
		}
	}

	private void toggleBars() {
		if (mShowBars) {
			hideBars();
		} else {
			if (canShowBars()) {
				showBars();
			}
		}
	}

	private void updateBars() {
		if (!canShowBars()) {
			hideBars();
		}
	}

	private void updateCurrentPhoto(final MediaItem photo) {
		if (mCurrentPhoto == photo) return;
		mCurrentPhoto = photo;
		updateUIForCurrentPhoto();
	}

	private void updateMenuOperations() {
	}

	private void updateProgressBar() {
	}

	private void updateUIForCurrentPhoto() {
		if (mCurrentPhoto == null) return;

		// If by swiping or deletion the user ends up on an action item
		// and zoomed in, zoom out so that the context of the action is
		// more clear
		if ((mCurrentPhoto.getSupportedOperations() & MediaObject.SUPPORT_ACTION) != 0) {
			mPhotoView.setWantPictureCenterCallbacks(true);
		}

		updateMenuOperations();
		if (mShowDetails) {
		}
		updateProgressBar();
	}

	// ////////////////////////////////////////////////////////////////////////
	// AppBridge.Server interface
	// ////////////////////////////////////////////////////////////////////////

	private void wantBars() {
		if (canShowBars()) {
			showBars();
		}
	}

	void initialize(final ImageViewerGLActivity activity, final Bundle data) {
		mData = data;
	}

	void onLoadFinished() {
		hideProgress();
	}

	void onLoadStart() {
		showProgress();
	}

	// should only be called by StateManager
	void resume() {
		final ImageViewerGLActivity activity = this;

		//activity.invalidateOptionsMenu();

		setScreenFlags();

		final ResultEntry entry = mReceivedResults;
		if (entry != null) {
			mReceivedResults = null;
		}

		if (0 != (mFlags & FLAG_SCREEN_ON_WHEN_PLUGGED)) {
			// we need to know whether the device is plugged in to do this
			// correctly
			final IntentFilter filter = new IntentFilter();
			filter.addAction(Intent.ACTION_BATTERY_CHANGED);
			activity.registerReceiver(mPowerIntentReceiver, filter);
		}

		state_onResume();

	}

	private static void clearBitmapPool(final BitmapPool pool) {
		if (pool != null) {
			pool.clear();
		}
	}

	// TODO better mimetype
	private static Intent createShareIntent(final MediaObject mediaObject) {
		return new Intent(Intent.ACTION_SEND).setType("image/*")
				.putExtra(Intent.EXTRA_STREAM, mediaObject.getContentUri())
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
	}

	public static interface Model extends PhotoView.Model {

		public void pause();

		public void resume();

	}

	protected static class ResultEntry {
		public int requestCode;
		public int resultCode = Activity.RESULT_CANCELED;
		public Intent resultData;
	}
}
