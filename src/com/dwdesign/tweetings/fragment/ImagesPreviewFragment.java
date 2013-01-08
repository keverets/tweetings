package com.dwdesign.tweetings.fragment;

import static com.dwdesign.tweetings.util.Utils.parseURL;
import static com.dwdesign.tweetings.util.Utils.openImage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.model.ImageSpec;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.util.LazyImageLoader;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.content.Context;

public class ImagesPreviewFragment extends BaseFragment implements OnItemClickListener, OnClickListener,
		OnTouchListener {

	private static final long TICKER_DURATION = 5000L;

	private Gallery mGallery;
	private ImagesAdapter mAdapter;
	private View mLoadImagesIndicator;
	private Handler mHandler;
	private Runnable mTicker;
	private ParcelableStatus mStatus;
	private static boolean isSensitive;
	private static boolean mDisplaySensitiveContents;

	private volatile boolean mBusy, mTickerStopped;
	
	public void setStatus(ParcelableStatus status) {
		mStatus = status;
		if (status != null) {
			isSensitive = status.is_possibly_sensitive;
		}
	}

	private final List<ImageSpec> mData = new ArrayList<ImageSpec>();
	
	public boolean addAll(final Collection<? extends ImageSpec> images) {
	 	 mData.clear();
	 	 return images != null && mData.addAll(images);
	}
	
	public void clear() {
		mData.clear();
		update();
	 	if (mLoadImagesIndicator != null) {
	 		mLoadImagesIndicator.setVisibility(View.VISIBLE);
	 	}
	 	if (mGallery != null) {
	 		mGallery.setVisibility(View.GONE);
	 	}
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mAdapter = new ImagesAdapter(getActivity());
		mGallery.setAdapter(mAdapter);
		mGallery.setOnItemClickListener(this);
		mLoadImagesIndicator.setOnClickListener(this);
		mDisplaySensitiveContents = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).getBoolean(PREFERENCE_KEY_DISPLAY_SENSITIVE_CONTENTS, false);
	}

	@Override
	public void onClick(final View v) {
		switch (v.getId()) {
			case R.id.load_images: {
				show();
				break;
			}
		}
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.images_preview, null, false);
		mGallery = (Gallery) view.findViewById(R.id.preview_gallery);
		mGallery.setOnTouchListener(this);
		mLoadImagesIndicator = view.findViewById(R.id.load_images);
		return view;
	}

	@Override
	public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
		final ImageSpec spec = mAdapter.getItem(position);
		if (spec == null) return;
		if (mStatus != null) {
			openImage(getActivity(), Uri.parse(spec.full_image_link), mStatus.is_possibly_sensitive);
		}
		else {
			openImage(getActivity(), Uri.parse(spec.full_image_link), false);
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
				if (mGallery != null && !mBusy) {
					mAdapter.notifyDataSetChanged();
				}
				final long now = SystemClock.uptimeMillis();
				final long next = now + TICKER_DURATION - now % TICKER_DURATION;
				mHandler.postAtTime(mTicker, next);
			}
		};
		mTicker.run();
	}

	@Override
	public void onStop() {
		mTickerStopped = true;
		super.onStop();
	}

	@Override
	public boolean onTouch(final View view, final MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				mBusy = true;
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				mBusy = false;
				break;
		}
		return false;
	}

	public void show() {
		if (mAdapter == null) return;
		update();
		mLoadImagesIndicator.setVisibility(View.GONE);
		mGallery.setVisibility(View.VISIBLE);
	}
	
	public void update() {
		if (mAdapter == null) return;
		mAdapter.clear();
		mAdapter.addAll(mData);
	}

	static class ImagesAdapter extends BaseAdapter {

		private final List<ImageSpec> mImages = new ArrayList<ImageSpec>();
		private final LazyImageLoader mImageLoader;
		private final LayoutInflater mInflater;

		public ImagesAdapter(final Context context) {
			mImageLoader = TweetingsApplication.getInstance(context).getPreviewImageLoader();
			mInflater = LayoutInflater.from(context);
		}

		public boolean addAll(final Collection<? extends ImageSpec> images) {
			final boolean ret = images != null && mImages.addAll(images);
			notifyDataSetChanged();
			return ret;
		}

		public void clear() {
			mImages.clear();
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return mImages.size();
		}

		@Override
		public ImageSpec getItem(final int position) {
			return mImages.get(position);
		}

		@Override
		public long getItemId(final int position) {
			final ImageSpec spec = getItem(position);
			return spec != null ? spec.hashCode() : 0;
		}

		@Override
		public View getView(final int position, final View convertView, final ViewGroup parent) {
			final View view = convertView != null ? convertView : mInflater.inflate(R.layout.images_preview_item, null);
			final ImageView image = (ImageView) view.findViewById(R.id.image);
			final ImageSpec spec = getItem(position);
			if (spec != null && spec.preview_image_link != null) {
				if (isSensitive && !mDisplaySensitiveContents) {
					image.setImageResource(R.drawable.image_preview_nsfw);
				} else {
					mImageLoader.displayImage(spec != null ? parseURL(spec.preview_image_link) : null, image);
				}
			}
			else {
				image.setVisibility(View.GONE);
				
			}
			return view;
		}
	}
}
