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

import java.io.File;

import com.dwdesign.gallery3d.ui.GLRoot;
import com.dwdesign.gallery3d.ui.GLRootView;
import com.dwdesign.gallery3d.ui.GLView;
import com.dwdesign.gallery3d.ui.PhotoView;
import com.dwdesign.gallery3d.ui.SynchronizedHandler;
import com.dwdesign.gallery3d.util.GalleryUtils;
import com.dwdesign.gallery3d.util.ThreadPool;
import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.loader.GLImageLoader;
import com.dwdesign.tweetings.loader.ImageLoader;
import com.dwdesign.tweetings.util.SaveImageTask;
import com.dwdesign.tweetings.util.Utils;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;

public final class ImageViewerGLActivity extends FragmentActivity implements Constants, View.OnClickListener,
		PhotoView.Listener, ImageLoader.DownloadListener, LoaderManager.LoaderCallbacks<ImageLoader.Result> {

	private final GLView mRootPane = new GLView() {
		@Override
		protected void onLayout(final boolean changed, final int left, final int top, final int right, final int bottom) {
			mPhotoView.layout(0, 0, right - left, bottom - top);
		}
	};

	private GLRootView mGLRootView;
	private ProgressBar mProgress;
	private View mControlButtons;
	private ImageButton mRefreshStopSaveButton;

	protected static final int FLAG_HIDE_ACTION_BAR = 1;
	protected static final int FLAG_HIDE_STATUS_BAR = 2;

	protected int mFlags;

	private GLView mContentPane;

	private static final int MSG_HIDE_BARS = 1;
	private static final int MSG_ON_FULL_SCREEN_CHANGED = 4;
	private static final int MSG_UPDATE_ACTION_BAR = 5;
	private static final int MSG_UNFREEZE_GLROOT = 6;
	private static final int MSG_WANT_BARS = 7;
	private static final int MSG_REFRESH_BOTTOM_CONTROLS = 8;
	private static final int HIDE_BARS_TIMEOUT = 3500;
	private static final int UNFREEZE_GLROOT_TIMEOUT = 250;

	private PhotoView mPhotoView;
	private PhotoView.ITileImageAdapter mAdapter;
	private Handler mHandler;

	private boolean mShowBars = true;
	private volatile boolean mActionBarAllowed = true;
	private boolean mIsMenuVisible;

	private long mContentLength;
	private boolean mLoaderInitialized;

	private ThreadPool mThreadPool;

	private boolean mImageLoaded;

	private File mImageFile;

	public GLRoot getGLRoot() {
		return mGLRootView;
	}

	public ThreadPool getThreadPool() {
		if (mThreadPool != null) return mThreadPool;
		return mThreadPool = new ThreadPool();
	}

	public void hideControls() {
		mControlButtons.setVisibility(View.GONE);
	}

	public void hideProgress() {
		mProgress.setVisibility(View.GONE);
		mProgress.setProgress(0);
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
	public void onClick(final View view) {
		final Uri uri = getIntent().getData();
		switch (view.getId()) {
			case R.id.close: {
				onBackPressed();
				break;
			}
			case R.id.refresh_stop_save: {
				final LoaderManager lm = getSupportLoaderManager();
				if (!mImageLoaded && !lm.hasRunningLoaders()) {
					loadImage();
				} else if (!mImageLoaded && lm.hasRunningLoaders()) {
					stopLoading();
				} else if (mImageLoaded) {
					new SaveImageTask(this, mImageFile).execute();
				}
				break;
			}
			case R.id.share: {
				if (uri == null) {
					break;
				}
				final Intent intent = new Intent(Intent.ACTION_SEND);
				if (mImageFile != null && mImageFile.exists()) {
					intent.setType("image/*");
					intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(mImageFile));
				} else {
					intent.setType("text/plain");
					intent.putExtra(Intent.EXTRA_TEXT, uri.toString());
				}
				startActivity(Intent.createChooser(intent, getString(R.string.share)));
				break;
			}
			case R.id.open_in_browser: {
				if (uri == null) {
					break;
				}
				final String scheme = uri.getScheme();
				if ("http".equals(scheme) || "https".equals(scheme)) {
					final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
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

	@Override
	public void onContentChanged() {
		super.onContentChanged();
		mGLRootView = (GLRootView) findViewById(R.id.gl_root_view);
		mProgress = (ProgressBar) findViewById(R.id.progress);
		mControlButtons = findViewById(R.id.control_buttons);
		mRefreshStopSaveButton = (ImageButton) findViewById(R.id.refresh_stop_save);
	}

	@Override
	public Loader<ImageLoader.Result> onCreateLoader(final int id, final Bundle args) {
		mProgress.setVisibility(View.VISIBLE);
		mProgress.setIndeterminate(true);
		mRefreshStopSaveButton.setImageResource(R.drawable.ic_menu_stop);
		final Uri uri = args != null ? (Uri) args.getParcelable(INTENT_KEY_URI) : null;
		return new GLImageLoader(this, this, uri);
	}

	@Override
	public void onCurrentImageUpdated() {
		mGLRootView.unfreeze();
	}

	@Override
	public void onDownloadError(final Throwable t) {
		mContentLength = 0;
	}

	@Override
	public void onDownloadFinished() {
		mContentLength = 0;
	}

	@Override
	public void onDownloadStart(final long total) {
		mContentLength = total;
		mProgress.setIndeterminate(false);
		mProgress.setMax(100);
	}

	@Override
	public void onLoaderReset(final Loader<ImageLoader.Result> loader) {

	}

	@Override
	public void onLoadFinished(final Loader<ImageLoader.Result> loader, final ImageLoader.Result data) {
		if (data instanceof GLImageLoader.GLImageResult) {
			final GLImageLoader.GLImageResult data_gl = (GLImageLoader.GLImageResult) data;
			mAdapter.setData(data_gl.decoder, data_gl.bitmap, data_gl.orientation);
			mImageFile = data.file;
			mImageLoaded = true;
			mRefreshStopSaveButton.setImageResource(R.drawable.ic_menu_save);
		} else {
			mImageFile = null;
			mImageLoaded = false;
			mRefreshStopSaveButton.setImageResource(R.drawable.ic_menu_refresh);
			if (data != null) {
				Utils.showErrorToast(this, null, data.exception, true);
			}
		}
		mProgress.setVisibility(View.GONE);
		mProgress.setProgress(0);
	}

	@Override
	public void onPictureCenter() {
		mPhotoView.setWantPictureCenterCallbacks(false);
	}

	@Override
	public void onProgressUpdate(final long downloaded) {
		if (mContentLength == 0) {
			mProgress.setIndeterminate(true);
			return;
		}
		mProgress.setIndeterminate(false);
		mProgress.setProgress((int) (downloaded * 100 / mContentLength));
	}

	@Override
	public void onSingleTapUp(final int x, final int y) {
		toggleBars();
	}

	public void showControls() {
		mControlButtons.setVisibility(View.VISIBLE);
	}

	public void showProgress() {
		mProgress.setVisibility(View.VISIBLE);
		mProgress.setIndeterminate(true);
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.image_viewer_gl);
		mHandler = new MyHandler(this);
		mPhotoView = new PhotoView(this);
		mPhotoView.setListener(this);
		mRootPane.addComponent(mPhotoView);
		mAdapter = new PhotoViewAdapter(mPhotoView);
		mPhotoView.setModel(mAdapter);
		if (savedInstanceState == null) {
			loadImage();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mGLRootView.lockRenderThread();
		try {
			// Remove all pending messages.
			mHandler.removeCallbacksAndMessages(null);
		} finally {
			mGLRootView.unlockRenderThread();
		}
	}

	@Override
	protected void onNewIntent(final Intent intent) {
		setIntent(intent);
		loadImage();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mGLRootView.onPause();
		mGLRootView.lockRenderThread();
		try {
			mGLRootView.unfreeze();
			mHandler.removeMessages(MSG_UNFREEZE_GLROOT);

			if (mAdapter != null) {
				mAdapter.recycleScreenNail();
			}
			mPhotoView.pause();
			mHandler.removeMessages(MSG_HIDE_BARS);
			mHandler.removeMessages(MSG_REFRESH_BOTTOM_CONTROLS);
		} finally {
			mGLRootView.unlockRenderThread();
		}

	}

	@Override
	protected void onResume() {
		super.onResume();
		mGLRootView.lockRenderThread();
		try {
			if (mAdapter == null) {
				finish();
				return;
			}
			mGLRootView.freeze();
			setContentPane(mRootPane);

			mPhotoView.resume();
			if (!mShowBars) {
				hideControls();
			}
			mHandler.sendEmptyMessageDelayed(MSG_UNFREEZE_GLROOT, UNFREEZE_GLROOT_TIMEOUT);
		} finally {
			mGLRootView.unlockRenderThread();
		}
		mGLRootView.onResume();
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

	protected void setContentPane(final GLView content) {
		mContentPane = content;
		mContentPane.setBackgroundColor(GalleryUtils.intColorToFloatARGBArray(Color.BLACK));
		mGLRootView.setContentPane(mContentPane);
	}

	private boolean canShowBars() {
		// No bars if it's not allowed.
		if (!mActionBarAllowed) return false;
		return true;
	}

	private void hideBars() {
		if (!mShowBars) return;
		mShowBars = false;
		hideControls();
		mHandler.removeMessages(MSG_HIDE_BARS);
	}

	private void loadImage() {
		getSupportLoaderManager().destroyLoader(0);
		final Uri uri = getIntent().getData();
		if (uri == null) {
			finish();
			return;
		}
		final Bundle args = new Bundle();
		args.putParcelable(INTENT_KEY_URI, uri);
		if (!mLoaderInitialized) {
			getSupportLoaderManager().initLoader(0, args, this);
			mLoaderInitialized = true;
		} else {
			getSupportLoaderManager().restartLoader(0, args, this);
		}
	}

	private void refreshHidingMessage() {
		mHandler.removeMessages(MSG_HIDE_BARS);
		if (!mIsMenuVisible) {
			mHandler.sendEmptyMessageDelayed(MSG_HIDE_BARS, HIDE_BARS_TIMEOUT);
		}
	}

	private void showBars() {
		if (mShowBars) return;
		mShowBars = true;
		showControls();
		refreshHidingMessage();
	}

	private void stopLoading() {
		getSupportLoaderManager().destroyLoader(0);
		if (!mImageLoaded) {
			mRefreshStopSaveButton.setImageResource(R.drawable.ic_menu_refresh);
			mProgress.setVisibility(View.GONE);
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

	private void wantBars() {
		if (canShowBars()) {
			showBars();
		}
	}

	private static class MyHandler extends SynchronizedHandler {
		ImageViewerGLActivity activity;

		private MyHandler(final ImageViewerGLActivity activity) {
			super(activity.getGLRoot());
			this.activity = activity;
		}

		@Override
		public void handleMessage(final Message message) {
			switch (message.what) {
				case MSG_HIDE_BARS: {
					activity.hideBars();
					break;
				}
				case MSG_REFRESH_BOTTOM_CONTROLS: {
					break;
				}
				case MSG_ON_FULL_SCREEN_CHANGED: {
					break;
				}
				case MSG_UPDATE_ACTION_BAR: {
					activity.updateBars();
					break;
				}
				case MSG_WANT_BARS: {
					activity.wantBars();
					break;
				}
				case MSG_UNFREEZE_GLROOT: {
					mGLRoot.unfreeze();
					break;
				}
			}
		}
	}

}