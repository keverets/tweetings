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
import static com.dwdesign.tweetings.util.Utils.copyStream;
import static com.dwdesign.tweetings.util.Utils.getProxy;
import static com.dwdesign.tweetings.util.Utils.parseURL;
import static com.dwdesign.tweetings.util.Utils.setIgnoreSSLError;
import static com.dwdesign.tweetings.util.Utils.showErrorToast;
import static com.dwdesign.tweetings.util.SimpleGestureFilter.SimpleGestureListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.util.GetExternalCacheDirAccessor;
import com.dwdesign.tweetings.view.ImageViewer;
import com.dwdesign.tweetings.util.SimpleGestureFilter;
import com.dwdesign.tweetings.util.BitmapDecodeHelper;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

public class ImageViewerActivity extends FragmentActivity implements Constants, OnClickListener, SimpleGestureListener {

	private SimpleGestureFilter detector;
	private ImageViewer mImageView;
	private ImageLoader mImageLoader;
	private View mProgress;
	private ImageButton mRefreshStopSaveButton;
	private boolean mImageLoading, mImageLoaded;
	private File mImageFile;

	private final Handler mErrorHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			showErrorToast(ImageViewerActivity.this, null, msg.obj, true);
		}
	};

	@Override
	public void onClick(final View view) {
		final Uri uri = getIntent().getData();
		switch (view.getId()) {
			case R.id.close: {
				onBackPressed();
				break;
			}
			case R.id.refresh_stop_save: {
				if (!mImageLoaded && !mImageLoading) {
					loadImage();
				} else if (!mImageLoaded && mImageLoading) {
					stopLoading();
				} else if (mImageLoaded) {
					saveImage();
				}
				break;
			}
			case R.id.share: {
				if (uri == null) {
					break;
				}
				final Intent intent = new Intent(Intent.ACTION_SEND);
				final String scheme = uri.getScheme();
				if ("file".equals(scheme)) {
					intent.setType("image/*");
					intent.putExtra(Intent.EXTRA_STREAM, uri);
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
		mImageView = (ImageViewer) findViewById(R.id.image_viewer);
		mRefreshStopSaveButton = (ImageButton) findViewById(R.id.refresh_stop_save);
		mProgress = findViewById(R.id.progress);
	}

	@Override
	protected void onCreate(final Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.image_viewer);
		detector = new SimpleGestureFilter(this,this);
		loadImage();
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
	protected void onDestroy() {
		super.onDestroy();
		mImageView.recycle();
		if (mImageLoader != null && !mImageLoader.isCancelled()) {
			mImageLoader.cancel();
		}
	}
	
	@Override
	public void onSwipe(int direction) {
		  String str = "";
		  
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
	protected void onNewIntent(final Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		loadImage();
	}

	private void loadImage() {
		if (mImageLoader != null && mImageLoader.getStatus() == Status.RUNNING) {
			mImageLoader.cancel();
		}
		final Uri uri = getIntent().getData();
		if (uri == null) {
			finish();
			return;
		}
		mImageView.setBitmap(null);
		mImageLoader = new ImageLoader(uri, mImageView);
		mImageLoader.execute();
	}

	private void saveImage() {
		if (mImageFile != null && mImageFile.exists()) {
			final Uri uri = getIntent().getData();
			if (uri == null) return;
			final String file_name = uri.getLastPathSegment();
			final BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(mImageFile.getPath(), o);
			final String mime_type = o.outMimeType;
			String file_name_with_suffix = null;
			if (file_name.matches("(.*/)*.+\\.(png|jpg|gif|bmp|jpeg|PNG|JPG|JPEG|GIF|BMP)$")) {
				file_name_with_suffix = file_name;
			} else {
				if (mime_type == null) return;
				if (mime_type.startsWith("image/") && !"image/*".equals(mime_type)) {
					file_name_with_suffix = file_name + "." + mime_type.substring(5);
				}
			}
			final Intent intent = new Intent(INTENT_ACTION_SAVE_FILE);
			intent.setPackage(getPackageName());
			intent.putExtra(INTENT_KEY_FILE_SOURCE, mImageFile.getPath());
			intent.putExtra(INTENT_KEY_FILENAME, file_name_with_suffix);
			startActivity(intent);
		}
	}

	private void stopLoading() {
		if (mImageLoader != null) {
			mImageLoader.cancel();
		}
	}

	class ImageLoader extends AsyncTask<Void, Void, Bitmap> {

		private static final String CACHE_DIR_NAME = "cached_images";

		private final Uri uri;
		private final ImageViewer image_view;
		private File mCacheDir;

		public ImageLoader(final Uri uri, final ImageViewer image_view) {
			this.uri = uri;
			this.image_view = image_view;
			init();
		}

		protected void cancel() {
			mImageLoading = false;
			cancel(true);
			if (!mImageLoaded) {
				mRefreshStopSaveButton.setImageResource(R.drawable.ic_menu_refresh);
				image_view.setBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.refresh_image));
				mProgress.setVisibility(View.GONE);
			}
		}

		@Override
		protected Bitmap doInBackground(final Void... args) {

			if (uri == null) return null;
			final String scheme = uri.getScheme();
			if ("http".equals(scheme) || "https".equals(scheme)) {
				final URL url = parseURL(uri.toString());
				if (url == null) return null;
				if (mCacheDir == null || !mCacheDir.exists()) {
					init();
				}
				final File cache_file = new File(mCacheDir, getURLFilename(url));

				// from SD cache
				final Bitmap cached_bitmap = decodeFile(cache_file);
				if (cached_bitmap != null) return cached_bitmap;

				String response_msg = null;
				int response_code = -1;
				// from web
				try {
					Bitmap bitmap = null;
					final HttpURLConnection conn = (HttpURLConnection) url
							.openConnection(getProxy(ImageViewerActivity.this));
					setIgnoreSSLError(conn);
					conn.setConnectTimeout(30000);
					conn.setReadTimeout(30000);
					conn.setInstanceFollowRedirects(true);
					response_msg = conn.getResponseMessage();
					response_code = conn.getResponseCode();
					final InputStream is = conn.getInputStream();
					final OutputStream os = new FileOutputStream(cache_file);
					copyStream(is, os);
					os.close();
					bitmap = decodeFile(cache_file);
					if (bitmap == null) {
						if (response_code > 0) {
							final Message msg = new Message();
							if (response_code == 200) {
								msg.obj = getString(R.string.invalid_image);
							} else {
								msg.obj = response_msg != null ? response_code + ": " + response_msg : response_code;
							}
							mErrorHandler.sendMessage(msg);
						}
						// The file is corrupted, so we remove it from cache.
						if (cache_file.isFile()) {
							cache_file.delete();
						}
					}
					return bitmap;
				} catch (final FileNotFoundException e) {
					init();
				} catch (final IOException e) {
					final Message msg = new Message();
					msg.obj = e;
					mErrorHandler.sendMessage(msg);
				}
				if (response_code > 0) {
					final Message msg = new Message();
					if (response_code == 200) {
						msg.obj = getString(R.string.invalid_image);
					} else {
						msg.obj = response_msg != null ? response_code + ": " + response_msg : response_code;
					}
					mErrorHandler.sendMessage(msg);
				}
			} else if ("file".equals(scheme)) return decodeFile(new File(uri.getPath()));
			return null;
		}

		@Override
		protected void onPostExecute(final Bitmap result) {
			mImageLoading = false;
			if (image_view != null) {
				if (result != null) {
					image_view.setBitmap(result);
					mImageLoaded = true;
					mRefreshStopSaveButton.setImageResource(R.drawable.ic_menu_save);
				} else {
					image_view.setBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.broken_image));
					mImageLoaded = false;
					mRefreshStopSaveButton.setImageResource(R.drawable.ic_menu_refresh);
				}
			}
			mProgress.setVisibility(View.GONE);
			super.onPostExecute(result);
		}

		@Override
		protected void onPreExecute() {
			mImageLoading = true;
			mProgress.setVisibility(View.VISIBLE);
			mRefreshStopSaveButton.setImageResource(R.drawable.ic_menu_stop);
			super.onPreExecute();
		}

		private Bitmap decodeFile(final File f) {
			if (f == null) return null;
			final BitmapFactory.Options o = new BitmapFactory.Options();
			o.inSampleSize = 1;
			Bitmap bitmap = null;
			while (bitmap == null) {
				try {
					final BitmapFactory.Options o2 = new BitmapFactory.Options();
					o2.inSampleSize = o.inSampleSize;
					bitmap = BitmapDecodeHelper.decode(f.getPath(), o2);
				} catch (final OutOfMemoryError e) {
					o.inSampleSize++;
					continue;
				}
				if (bitmap == null) {
					break;
				} else {
					mImageFile = f;
				}
				return bitmap;
			}
			mImageFile = null;
			return null;
		}

		private String getURLFilename(final URL url) {
			if (url == null) return null;
			return url.toString().replaceFirst("https?:\\/\\/", "").replaceAll("[^a-zA-Z0-9]", "_");
		}

		private void init() {
			/* Find the dir to save cached images. */
			if (getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
				mCacheDir = new File(
						Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? GetExternalCacheDirAccessor.getExternalCacheDir(ImageViewerActivity.this)
								: new File(getExternalStorageDirectory().getPath() + "/Android/data/"
										+ getPackageName() + "/cache/"), CACHE_DIR_NAME);
			} else {
				mCacheDir = new File(getCacheDir(), CACHE_DIR_NAME);
			}
			if (mCacheDir != null && !mCacheDir.exists()) {
				mCacheDir.mkdirs();
			}
		}
	}
}
