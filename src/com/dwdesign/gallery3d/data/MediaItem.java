/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.dwdesign.gallery3d.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

import com.dwdesign.gallery3d.app.IGalleryApplication;
import com.dwdesign.gallery3d.common.ApiHelper;
import com.dwdesign.gallery3d.common.BitmapUtils;
import com.dwdesign.gallery3d.common.Utils;
import com.dwdesign.gallery3d.util.ThreadPool.CancelListener;
import com.dwdesign.gallery3d.util.ThreadPool.Job;
import com.dwdesign.gallery3d.util.ThreadPool.JobContext;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

// MediaItem represents an image or a video item.
@SuppressLint("NewApi")
public class MediaItem extends MediaObject {
	// NOTE: These type numbers are stored in the image cache, so it should not
	// not be changed without resetting the cache.
	public static final int TYPE_THUMBNAIL = 1;

	public static final String MIME_TYPE_JPEG = "image/jpeg";

	private static int sThumbnailTargetSize = 640;
	private static final BitmapPool sThumbPool = ApiHelper.HAS_REUSING_BITMAP_IN_BITMAP_FACTORY ? new BitmapPool(4)
			: null;

	private static final String TAG = "UriImage";
	

	private static final int STATE_INIT = 0;

	private static final int STATE_DOWNLOADING = 1;

	private static final int STATE_DOWNLOADED = 2;

	private static final int STATE_ERROR = -1;

	private final Uri mUri;

	private final String mContentType;

	private DownloadCache.Entry mCacheEntry;


	private ParcelFileDescriptor mFileDescriptor;
	private int mState = STATE_INIT;
	private int mRotation;
	private final IGalleryApplication mApplication;

	public MediaItem(final IGalleryApplication application, final Path path, final Uri uri, final String contentType) {
		super(path, nextVersionNumber());
		mUri = uri;
		mApplication = Utils.checkNotNull(application);
		mContentType = contentType;
	}

	@Override
	public Uri getContentUri() {
		return mUri;
	}

	public Uri getFilePath() {
		File file = mCacheEntry.cacheFile;
		if (file != null) {
			return Uri.fromFile(file);
		}
		return null;
	}

	// The rotation of the full-resolution image. By default, it returns the
	// value of
	// getRotation().
	public int getFullImageRotation() {
		return getRotation();
	}

	@Override
	public int getMediaType() {
		return MEDIA_TYPE_IMAGE;
	}

	public String getMimeType() {
		return mContentType;
	}

	public int getRotation() {
		return mRotation;
	}

	@Override
	public int getSupportedOperations() {
		return SUPPORT_FULL_IMAGE;
	}

	public Job<Bitmap> requestImage(final int type) {
		return new BitmapJob(type);
	}

	public Job<BitmapRegionDecoder> requestLargeImage() {
		return new RegionDecoderJob();
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			if (mFileDescriptor != null) {
				Utils.closeSilently(mFileDescriptor);
			}
		} finally {
			super.finalize();
		}
	}

	private void openFileOrDownloadTempFile(final JobContext jc) {
		final int state = openOrDownloadInner(jc);
		synchronized (this) {
			mState = state;
			if (mState != STATE_DOWNLOADED) {
				if (mFileDescriptor != null) {
					Utils.closeSilently(mFileDescriptor);
					mFileDescriptor = null;
				}
			}
			notifyAll();
		}
	}

	private int openOrDownloadInner(final JobContext jc) {
		final String scheme = mUri.getScheme();
		if (ContentResolver.SCHEME_CONTENT.equals(scheme) || ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)
				|| ContentResolver.SCHEME_FILE.equals(scheme)) {
			try {
				if (MIME_TYPE_JPEG.equalsIgnoreCase(mContentType)) {
					final InputStream is = mApplication.getContentResolver().openInputStream(mUri);
					mRotation = Exif.getOrientation(is);
					Utils.closeSilently(is);
				}
				mFileDescriptor = mApplication.getContentResolver().openFileDescriptor(mUri, "r");
				if (jc.isCancelled()) return STATE_INIT;
				return STATE_DOWNLOADED;
			} catch (final FileNotFoundException e) {
				Log.w(TAG, "fail to open: " + mUri, e);
				return STATE_ERROR;
			}
		} else {
			try {
				final URL url = new URI(mUri.toString()).toURL();
				mCacheEntry = mApplication.getDownloadCache().download(jc, url);
				if (jc.isCancelled()) return STATE_INIT;
				if (mCacheEntry == null) {
					Log.w(TAG, "download failed " + url);
					return STATE_ERROR;
				}
				if (MIME_TYPE_JPEG.equalsIgnoreCase(mContentType)) {
					final InputStream is = new FileInputStream(mCacheEntry.cacheFile);
					BitmapFactory.decodeStream(is);
					mRotation = Exif.getOrientation(is);
					Utils.closeSilently(is);
				}
				mFileDescriptor = ParcelFileDescriptor.open(mCacheEntry.cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
				return STATE_DOWNLOADED;
			} catch (final Throwable t) {
				Log.w(TAG, "download error", t);
				return STATE_ERROR;
			}
		}
	}

	private boolean prepareInputFile(final JobContext jc) {
		jc.setCancelListener(new CancelListener() {
			@Override
			public void onCancel() {
				synchronized (this) {
					notifyAll();
				}
			}
		});

		while (true) {
			synchronized (this) {
				if (jc.isCancelled()) return false;
				if (mState == STATE_INIT) {
					mState = STATE_DOWNLOADING;
					// Then leave the synchronized block and continue.
				} else if (mState == STATE_ERROR)
					return false;
				else if (mState == STATE_DOWNLOADED)
					return true;
				else /* if (mState == STATE_DOWNLOADING) */{
					try {
						wait();
					} catch (final InterruptedException ex) {
						// ignored.
					}
					continue;
				}
			}
			// This is only reached for STATE_INIT->STATE_DOWNLOADING
			openFileOrDownloadTempFile(jc);
		}
	}

	public static BitmapPool getThumbPool() {
		return sThumbPool;
	}

	public static void setThumbnailSizes(final int size, final int microSize) {
		sThumbnailTargetSize = size;
	}

	private static int getTargetSize(final int type) {
		switch (type) {
			case TYPE_THUMBNAIL:
				return sThumbnailTargetSize;
			default:
				throw new RuntimeException("should only request thumb/microthumb from cache");
		}
	}

	private class BitmapJob implements Job<Bitmap> {
		private final int mType;

		protected BitmapJob(final int type) {
			mType = type;
		}

		@Override
		public Bitmap run(final JobContext jc) {
			if (!prepareInputFile(jc)) return null;
			final int targetSize = MediaItem.getTargetSize(mType);
			final Options options = new Options();
			options.inPreferredConfig = Config.ARGB_8888;
			Bitmap bitmap = DecodeUtils.decodeThumbnail(jc, mFileDescriptor.getFileDescriptor(), options, targetSize,
					mType);
			
			if (jc.isCancelled() || bitmap == null) return null;

			bitmap = BitmapUtils.resizeDownBySideLength(bitmap, targetSize, true);
			return bitmap;
		}
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
	private class RegionDecoderJob implements Job<BitmapRegionDecoder> {
		@Override
		public BitmapRegionDecoder run(final JobContext jc) {
			if (!prepareInputFile(jc)) return null;
			final BitmapRegionDecoder decoder = DecodeUtils.createBitmapRegionDecoder(jc,
					mFileDescriptor.getFileDescriptor(), false);
			return decoder;
		}
	}
}
