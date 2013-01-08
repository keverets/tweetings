package com.dwdesign.tweetings.activity;

import static com.dwdesign.tweetings.Constants.INTENT_ACTION_VIEW_IMAGE;
import static com.dwdesign.tweetings.util.Utils.getActivatedAccountIds;
import static com.dwdesign.tweetings.util.Utils.getAllAvailableImage;
import static com.dwdesign.tweetings.util.Utils.getOriginalTwitterProfileImage;
import static com.dwdesign.tweetings.util.Utils.getPreviewImage;
import static com.dwdesign.tweetings.util.Utils.getTwitterInstance;
import static com.dwdesign.tweetings.util.Utils.isMyAccount;
import static com.dwdesign.tweetings.util.Utils.getDefaultAccountId;
import static com.dwdesign.tweetings.util.Utils.parseString;
import static com.dwdesign.tweetings.util.Utils.parseURL;
import static com.dwdesign.tweetings.util.Utils.openImage;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.adapter.ParcelableStatusesAdapter;
import com.dwdesign.tweetings.fragment.UserProfileFragment;
import com.dwdesign.tweetings.loader.UserTimelineLoader;
import com.dwdesign.tweetings.model.ImageSpec;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.model.PreviewImage;
import com.dwdesign.tweetings.provider.TweetStore.Accounts;
import com.dwdesign.tweetings.provider.TweetStore.Drafts;

import com.dwdesign.popupmenu.PopupMenu;
import com.dwdesign.popupmenu.PopupMenu.OnMenuItemClickListener;

import com.dwdesign.tweetings.loader.UserMediaTimelineLoader;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.util.DisplayMetrics;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

public class GalleryActivity extends BaseActivity implements Constants {
	
	//private ServiceInterface mService;
	//private SharedPreferences mPreferences;
	
	private MediaTimelineTask mMediaTimelineTask;
	private ArrayList<ParcelableStatus> mStatuses;
	private GridView gridview; 
	private long account_id;
	private boolean mDisplaySensitiveContents;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	//mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		//mService = getTweetingsApplication().getServiceInterface();
		
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gallery);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        Intent intent = getIntent();
        final String screen_name = intent.getStringExtra("screen_name");
        account_id = intent.getLongExtra("account_id", -1);
        if (account_id == -1) {
        	account_id = getDefaultAccountId(this);
        }
        
        gridview = (GridView) findViewById(R.id.gridview);
        gridview.setAdapter(new ImageAdapter(this));
        
        SharedPreferences mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mDisplaySensitiveContents = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_SENSITIVE_CONTENTS, false);
		
        
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        int height = displaymetrics.heightPixels;
        int wwidth = displaymetrics.widthPixels;
        
        float cellWidth = wwidth/220;
        double cellWidthd = Math.floor(cellWidth);
        int colWidth = (int)cellWidthd;
        gridview.setNumColumns(colWidth);
        gridview.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            	ParcelableStatus pStatus = mStatuses.get(position);
            	final ImageSpec spec = getAllAvailableImage(pStatus.image_orig_url_string);
            	if (spec != null) {
            		openImage(GalleryActivity.this, Uri.parse(spec.full_image_link), pStatus.is_possibly_sensitive);
				}
            }
        });
        mMediaTimelineTask = new MediaTimelineTask(this, account_id, screen_name);
        if (mMediaTimelineTask != null) {
        	mMediaTimelineTask.execute();
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
    
    @Override
	public void onDestroy() {
		mStatuses = null;
		
		if (mMediaTimelineTask != null) {
			mMediaTimelineTask.cancel(true);
		}
		super.onDestroy();
	}
    
    class MediaTimelineTask extends AsyncTask<Void, Void, ResponseList<twitter4j.Status>> {

		private final Twitter twitter;
		private final String screen_name;

		private MediaTimelineTask(Context context, long account_id, String screen_name) {
			twitter = getTwitterInstance(context, account_id, true);
			this.screen_name = screen_name;
		}

		@Override
		protected ResponseList<twitter4j.Status> doInBackground(Void... args) {
			try {
				final Paging paging = new Paging();
				final int load_item_limit = 100;
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
		protected void onPostExecute(ResponseList<twitter4j.Status> result) {
			if (result == null) return;
			mStatuses = new ArrayList<ParcelableStatus>();
			for (twitter4j.Status status : result) {
				ParcelableStatus pStatus = new ParcelableStatus(status, account_id, false);
            	if (pStatus != null && pStatus.image_preview_url_string != null) {
            		mStatuses.add(pStatus);
            	}
			}
			setProgressBarIndeterminateVisibility(false);
			gridview.invalidateViews();
			super.onPostExecute(result);
		}

		@Override
		protected void onPreExecute() {
			setProgressBarIndeterminateVisibility(true);
			super.onPreExecute();
		}

	}
    
    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_HOME: {
				onBackPressed();
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}
    
    @Override
	public void onBackPressed() {
		super.onBackPressed();
	}
 
    private boolean isAllItemsLoaded = false;

	public boolean isListLoadFinished() {
		return isAllItemsLoaded;
	}
    
    public class ImageAdapter extends BaseAdapter {
 
        public ImageAdapter(Context c) {
            mContext = c;
        }
 
        public int getCount() {
        	if (mStatuses == null) {
        		return 0;
        	}
            return mStatuses.size();
        }
 
        public Object getItem(int position) {
            return position;
        }
 
        public long getItemId(int position) {
            return position;
        }
 
        public View getView(int position, View convertView, ViewGroup parent) {
 
            ImageView i;
            if (convertView == null) { 
            	i = new ImageView(mContext);
                i.setScaleType(ImageView.ScaleType.CENTER_CROP);
                i.setLayoutParams(new GridView.LayoutParams(180, 180));
           
            }
            else {
            	i = (ImageView) convertView;
            }
            if (mStatuses != null && mStatuses.size() >= 1) {
            	ParcelableStatus pStatus = mStatuses.get(position);
            	if (pStatus.is_possibly_sensitive && !mDisplaySensitiveContents) {
        			i.setImageResource(R.drawable.image_preview_nsfw);
				} else {
					UrlImageViewHelper.setUrlDrawable(i, pStatus.image_preview_url_string);
				}
            	//i.setImageURI(Uri.parse(pStatus.image_preview_url_string));
            }
            return i;
        }
 
        private Context mContext;
 
    }
}