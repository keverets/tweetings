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

package com.dwdesign.tweetings.util;

import static com.dwdesign.tweetings.util.Utils.copyStream;
import static com.dwdesign.tweetings.util.Utils.getBestCacheDir;
import static com.dwdesign.tweetings.util.Utils.getImageLoaderHttpClient;
import static com.dwdesign.tweetings.util.Utils.getRedirectedHttpResponse;
import static com.dwdesign.tweetings.util.Utils.resizeBitmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import com.dwdesign.tweetings.BuildConfig;
import com.dwdesign.tweetings.Constants;

import twitter4j.TwitterException;
import twitter4j.internal.http.HttpClientWrapper;
import twitter4j.internal.http.HttpResponse;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;

/**
 * Lazy image loader for {@link ListView} and {@link GridView} etc.</br> </br>
 * Inspired by <a href="https://github.com/thest1/LazyList">LazyList</a>, this
 * class has extra features like image loading/caching image to
 * /mnt/sdcard/Android/data/[package name]/cache features.</br> </br> Requires
 * Android 2.2, you can modify {@link Context#getExternalCacheDir()} to other to
 * support Android 2.1 and below.
 * 
 * @author mariotaku
 * 
 */
public class LazyImageLoader implements Constants {

	private static final String LOGTAG = LazyImageLoader.class.getSimpleName();
	private static final int DELAY_BEFORE_PURGE = 40000;

	private final ArrayList<String> mBlacklist;
	private final MemoryCache mMemoryCache;
	private final Context mContext;
	private final FileCache mFileCache;
	private final Map<ImageView, String> mImageViews = Collections
			.synchronizedMap(new WeakHashMap<ImageView, String>());
	private final ExecutorService mExecutor;
	private final int mFallbackRes;
	private final int mRequiredWidth, mRequiredHeight;
	private final Handler mPurgeHandler;
	private final MemoryPurger mPurger;
	final ThreadPoolExecutor cacheExecutor;
	int crossFadeMillis = 0;

	private HttpClientWrapper mClient;
	
	private Handler handler;

	public LazyImageLoader(final Context context, final String cache_dir_name, final int fallback_image_res,
			final int required_width, final int required_height, final int mem_cache_capacity) {
		mContext = context;
		mMemoryCache = new MemoryCache(mem_cache_capacity);
		mFileCache = new FileCache(context, cache_dir_name);
		mExecutor = Executors.newFixedThreadPool(8, new LowerPriorityThreadFactory());
		mFallbackRes = fallback_image_res;
		mPurgeHandler = new Handler();
		mPurger = new MemoryPurger(this);
		mBlacklist = new ArrayList<String>();
		mRequiredWidth = required_width % 2 == 0 ? required_width : required_width + 1;
		mRequiredHeight = required_height % 2 == 0 ? required_height : required_height + 1;
		cacheExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
		handler = new Handler(Looper.getMainLooper());
		reloadConnectivitySettings();
	}

	/**
	 * Cancels any downloads, shuts down the executor pool, and then purges the
	 * caches.
	 */
	public void cancel() {

		// We could also terminate it immediately,
		// but that may lead to synchronization issues.
		if (!mExecutor.isShutdown()) {
			mExecutor.shutdown();
		}

		stopPurgeTimer();

		clearMemoryCache();
	}

	public void clearFileCache() {
		mFileCache.clear();
	}

	public void clearMemoryCache() {
		mMemoryCache.clear();
		mBlacklist.clear();
		System.gc();
	}
	
	public void displayImage(final URL url, final ImageView view) {
		this.displayImage(url.toString(), view);
	}

	public void displayImage(final String url, final ImageView view) {
		if (view == null) return;
		if (url == null) {
			view.setImageResource(mFallbackRes);
			return;
		}
		mImageViews.put(view, url);
		long submitted = System.nanoTime();
		Runnable r = new ReadFromCacheRunnable(this, view, url,
				submitted);
		cacheExecutor.remove(r);
		cacheExecutor.execute(r);
		/*final Bitmap bitmap = mMemoryCache.get(url);
		if (bitmap != null) {
			view.setImageBitmap(bitmap);
			resetPurgeTimer();
		} else if (!mBlacklist.contains(url)) {
			queuePhoto(url, view);
			view.setImageResource(mFallbackRes);
			resetPurgeTimer();
		} else {
			view.setImageResource(mFallbackRes);
		}*/
	}
	
	// Used to display bitmap in the UI thread
		class BitmapDisplayer implements Runnable {

			Bitmap bitmap;
			ImageToLoad imagetoload;

			public BitmapDisplayer(final Bitmap b, final ImageToLoad p) {
				bitmap = b;
				imagetoload = p;
			}

			@Override
			public final void run() {
				if (imageViewReused(imagetoload)) return;
				if (bitmap != null) {
					imagetoload.view.setImageBitmap(bitmap);
				} else {
					imagetoload.view.setImageResource(mFallbackRes);
				}
			}
		}
	
	static class SetBitmapRunnable extends ImageViewRunnable {

		private final Bitmap bitmap;
		private final int crossFadeMillis;

		public SetBitmapRunnable(LazyImageLoader imageFetcher,
				ImageView imageView, Bitmap bitmap, int crossFadeMillis) {
			super(imageFetcher, imageView);
			this.bitmap = bitmap;
			this.crossFadeMillis = crossFadeMillis;
		}

		@Override
		public void run() {
			if (crossFadeMillis > 0) {
				Drawable prevDrawable = imageView.getDrawable();
				if (prevDrawable == null) {
					prevDrawable = new ColorDrawable(Color.TRANSPARENT);
				}
				Drawable nextDrawable = new BitmapDrawable(
						imageView.getResources(), bitmap);
				TransitionDrawable transitionDrawable = new TransitionDrawable(
						new Drawable[] { prevDrawable, nextDrawable });
				imageView.setImageDrawable(transitionDrawable);
				transitionDrawable.startTransition(crossFadeMillis);
			} else {
				imageView.setImageBitmap(bitmap);
			}
		}

	}
	
	static abstract class ImageViewRunnable implements Runnable {

		protected final LazyImageLoader imageFetcher;
		protected final ImageView imageView;

		public ImageViewRunnable(LazyImageLoader imageFetcher, ImageView imageView) {
			this.imageFetcher = imageFetcher;
			this.imageView = imageView;
		}

		@Override
		public boolean equals(Object o) {
			boolean eq = false;
			if (this == o) {
				eq = true;
			} else if (o instanceof ImageViewRunnable) {
				eq = imageView.equals(((ImageViewRunnable) o).imageView);
			}
			return eq;
		}

		@Override
		public int hashCode() {
			return imageView.hashCode();
		}
	}
	
	private void runOnUiThread(Runnable r) {
		boolean success = handler.post(r);
		// a hack
		while (!success) {
			handler = new Handler(Looper.getMainLooper());
			success = handler.post(r);
		}
	}

	static class ReadFromCacheRunnable extends ImageViewRunnable {

		protected final String imgUrl;
		protected final long submitted;

		public ReadFromCacheRunnable(LazyImageLoader imageFetcher,
				ImageView imageView, String imgUrl, long submitted) {
			super(imageFetcher, imageView);
			this.imgUrl = imgUrl;
			this.submitted = submitted;
		}

		@Override
		public void run() {
			final Bitmap bitmap = imageFetcher.mMemoryCache.get(imgUrl);
			if (bitmap != null) {
				//imageView.setImageBitmap(bitmap);
				SetBitmapRunnable r = new SetBitmapRunnable(imageFetcher,
						imageView, bitmap, imageFetcher.crossFadeMillis);
				imageFetcher.runOnUiThread(r);
				imageFetcher.resetPurgeTimer();
			} else if (!imageFetcher.mBlacklist.contains(imgUrl)) {
				imageFetcher.queuePhoto(imgUrl, imageView);
				try {
					Bitmap bm = BitmapFactory.decodeResource(imageFetcher.mContext.getResources(), imageFetcher.mFallbackRes);
					SetBitmapRunnable r = new SetBitmapRunnable(imageFetcher,
							imageView, bm, imageFetcher.crossFadeMillis);
					imageFetcher.runOnUiThread(r);
				}
				catch (NotFoundException e) {
					
				}
				//imageView.setImageResource(imageFetcher.mFallbackRes);
				imageFetcher.resetPurgeTimer();
			} else {
				try {
					Bitmap bm = BitmapFactory.decodeResource(imageFetcher.mContext.getResources(), imageFetcher.mFallbackRes);
					SetBitmapRunnable r = new SetBitmapRunnable(imageFetcher,
							imageView, bm, imageFetcher.crossFadeMillis);
					imageFetcher.runOnUiThread(r);
				}
				catch (NotFoundException e) {
				
				}
				//imageView.setImageResource(imageFetcher.mFallbackRes);
			}
		}
	}

	
	public File getCachedImageFile(final String url) {
		if (mFileCache == null) return null;
		final File f = mFileCache.getFile(url);
		if (ImageValidator.checkImageValidity(f))
			return f;
		else {
			queuePhoto(url);
		}
		return null;
	}

	public void queuePhoto(final String url) {
		queuePhoto(url, null);
	}

	public void reloadConnectivitySettings() {
		mClient = getImageLoaderHttpClient(mContext);
	}

	/**
	 * Stops the cache purger from running until it is reset again.
	 */
	public void stopPurgeTimer() {
		mPurgeHandler.removeCallbacks(mPurger);
	}

	/**
	 * The file to decode.
	 * 
	 * @return The resized and resampled bitmap, if can not be decoded it
	 *         returns null.
	 */
	private Bitmap decodeFile(final File file, final String url) {
		if (file == null || !file.exists()) return null;
		final BitmapFactory.Options options = new BitmapFactory.Options();

		options.inJustDecodeBounds = true;
		options.outWidth = 0;
		options.outHeight = 0;
		options.inSampleSize = 1;

		final String filePath = file.getAbsolutePath();
		BitmapFactory.decodeFile(filePath, options);

		if (options.outWidth > 0 && options.outHeight > 0) {
			// Now see how much we need to scale it down.
			int widthFactor = (options.outWidth + mRequiredWidth - 1) / mRequiredWidth;
			final int heightFactor = (options.outHeight + mRequiredHeight - 1) / mRequiredHeight;
			widthFactor = Math.max(widthFactor, heightFactor);
			widthFactor = Math.max(widthFactor, 1);
			// Now turn it into a power of two.
			if (widthFactor > 1) {
				if ((widthFactor & widthFactor - 1) != 0) {
					while ((widthFactor & widthFactor - 1) != 0) {
						widthFactor &= widthFactor - 1;
					}

					widthFactor <<= 1;
				}
			}
			options.inSampleSize = widthFactor;
			options.inJustDecodeBounds = false;
			final Bitmap bitmap = resizeBitmap(BitmapFactory.decodeFile(filePath, options), mRequiredWidth,
					mRequiredHeight);
			if (bitmap != null) return bitmap;
		} else {
			if (file.isFile() && file.length() == 0) {
				file.delete();
			}
			// Must not be a bitmap, so we add it to the blacklist.
			if (!mBlacklist.contains(url)) {
				mBlacklist.add(url);
			}
		}
		return null;
	}

	private void queuePhoto(final String url, final ImageView imageview) {
		final ImageToLoad p = new ImageToLoad(url, imageview);
		mExecutor.submit(new ImageLoader(p));
	}

	/**
	 * Purges the cache every (DELAY_BEFORE_PURGE) milliseconds.
	 * 
	 * @see DELAY_BEFORE_PURGE
	 */
	private void resetPurgeTimer() {
		mPurgeHandler.removeCallbacks(mPurger);
		mPurgeHandler.postDelayed(mPurger, DELAY_BEFORE_PURGE);
	} // Both hard and soft caches are purged after 40 seconds idling.

	boolean imageViewReused(final ImageToLoad imagetoload) {
		final Object tag = mImageViews.get(imagetoload.view);
		if (tag == null || !tag.equals(imagetoload.source)) return true;
		return false;
	}

		static class FileCache {

		private final String mCacheDirName;

		private File mCacheDir;
		private final Context mContext;

		public FileCache(final Context context, final String cache_dir_name) {
			mContext = context;
			mCacheDirName = cache_dir_name;
			init();
		}

		public void clear() {
			if (mCacheDir == null) return;
			final File[] files = mCacheDir.listFiles();
			if (files == null) return;
			for (final File f : files) {
				f.delete();
			}
		}

		public File getFile(final String url) {
			if (mCacheDir == null) return null;
			final String filename = getFilename(url);
			if (filename == null) return null;
			final File file = new File(mCacheDir, filename);
			return file;
		}

		public void init() {
			/* Find the dir to save cached images. */
			mCacheDir = getBestCacheDir(mContext, mCacheDirName);
			if (mCacheDir != null && !mCacheDir.exists()) {
				mCacheDir.mkdirs();
			}
		}

		private String getFilename(final String url) {
			if (url == null) return null;
			return url.replaceFirst("https?:\\/\\/", "").replaceAll("[^\\w\\d]", "_");
		}

	}

	class ImageLoader implements Runnable {
		private final ImageToLoad imagetoload;

		public ImageLoader(final ImageToLoad imagetoload) {
			this.imagetoload = imagetoload;
		}

		public Bitmap getBitmap(final String url) {
			if (url == null) return null;
			final File cache_file = mFileCache.getFile(url);

			// from SD cache
			final Bitmap cached_bitmap = decodeFile(cache_file, url);
			if (cached_bitmap != null) return cached_bitmap;

			// from web
			try {
				final HttpResponse resp = getRedirectedHttpResponse(mClient, url);

				if (resp != null && resp.getStatusCode() == 200) {
					final InputStream is = resp.asStream();
					final OutputStream os = new FileOutputStream(cache_file);
					copyStream(is, os);
					os.flush();
					os.close();
					final Bitmap bitmap = decodeFile(cache_file, url);
					if (bitmap == null) {
						// The file is corrupted, so we remove it from cache.
						if (cache_file.isFile() && cache_file.length() == 0) {
							cache_file.delete();
						}
					} else
						return bitmap;
				}
			} catch (final FileNotFoundException e) {
				// Storage state may changed, so call FileCache.init() again.
				Log.w(LOGTAG, e);
				mFileCache.init();
			} catch (final IOException e) {
				Log.w(LOGTAG, e);
			} catch (final TwitterException e) {
				Log.w(LOGTAG, e);
			}
			return null;
		}

		@Override
		public void run() {
			if (imageViewReused(imagetoload) || imagetoload.source == null) return;
			final Bitmap bmp = getBitmap(imagetoload.source);
			mMemoryCache.put(imagetoload.source, bmp);
			if (imageViewReused(imagetoload)) return;
			final BitmapDisplayer bd = new BitmapDisplayer(bmp, imagetoload);
			final Activity a = (Activity) imagetoload.view.getContext();
			a.runOnUiThread(bd);
		}
	}

	static class ImageToLoad {
		public final String source;
		public final ImageView view;

		public ImageToLoad(final String source, final ImageView imageview) {
			this.source = source;
			view = imageview;
		}
	}

	static class LowerPriorityThreadFactory implements ThreadFactory {

		@Override
		public Thread newThread(final Runnable r) {
			final Thread t = new Thread(r);
			t.setPriority(3);
			return t;
		}

	}

	static class MemoryCache {

		private final Map<String, SoftReference<Bitmap>> mSoftCache;
		private final LinkedHashMap<String, Bitmap> mHardCache;

		public MemoryCache(final int max_capacity) {
			mSoftCache = new HashMap<String, SoftReference<Bitmap>>(max_capacity / 2);
			mHardCache = new HardBitmapCache(mSoftCache, max_capacity / 2);
		}

		public void clear() {
			try {
				mHardCache.clear();
				mSoftCache.clear();
			} catch (final Exception e) {
				Log.e(LOGTAG, "Unknown exception", e);
			}
		}

		public Bitmap get(final String key) {
			if (key == null) return null;
			try {
				synchronized (mHardCache) {
					final Bitmap bitmap = mHardCache.get(key);
					if (bitmap != null && key != null) {
						// Put bitmap on top of cache so it's purged last.
						mHardCache.remove(key);
						mHardCache.put(key, bitmap);
						return bitmap;
					}
				}
				final Reference<Bitmap> bitmapRef = mSoftCache.get(key);
				if (bitmapRef != null) {
					final Bitmap bitmap = bitmapRef.get();
					if (bitmap != null)
						return bitmap;
					else {
						// Must have been collected by the Garbage Collector
						// so we remove the bucket from the cache.
						mSoftCache.remove(key);
					}
				}
			} catch (final Exception e) {
				Log.e(LOGTAG, "Unknown exception", e);
			}
			// Could not locate the bitmap in any of the caches, so we return
			// null.
			return null;

		}

		public void put(final String key, final Bitmap bitmap) {
			if (key == null || bitmap == null) return;
			try {
				mHardCache.put(key, bitmap);
			} catch (final Exception e) {
				Log.e(LOGTAG, "Unknown exception", e);
			}
		}

		static class HardBitmapCache extends LinkedHashMap<String, Bitmap> {

			private static final long serialVersionUID = 1347795807259717646L;
			private final Map<String, SoftReference<Bitmap>> soft_cache;
			private final int capacity;

			HardBitmapCache(final Map<String, SoftReference<Bitmap>> soft_cache, final int capacity) {
				super(capacity);
				this.soft_cache = soft_cache;
				this.capacity = capacity;
			}

			@Override
			protected boolean removeEldestEntry(final LinkedHashMap.Entry<String, Bitmap> eldest) {
				// Moves the last used item in the hard cache to the soft cache.
				if (size() > capacity) {
					soft_cache.put(eldest.getKey(), new SoftReference<Bitmap>(eldest.getValue()));
					return true;
				} else
					return false;
			}
		}
	}

	static class MemoryPurger implements Runnable {

		private final LazyImageLoader loader;

		MemoryPurger(final LazyImageLoader loader) {
			this.loader = loader;
		}

		@Override
		public void run() {
			loader.clearMemoryCache();
		}
	}

}