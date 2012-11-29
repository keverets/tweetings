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

import static com.dwdesign.tweetings.util.Utils.findStatusInDatabases;
import static com.dwdesign.tweetings.util.Utils.getAccountUsername;
import static com.dwdesign.tweetings.util.Utils.getQuoteStatus;
import static com.dwdesign.tweetings.util.Utils.getTwitterInstance;
import static com.dwdesign.tweetings.util.Utils.showErrorToast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.HttpClientFactory;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.activity.ComposeActivity;
import com.dwdesign.tweetings.adapter.ParcelableStatusesAdapter;
import com.dwdesign.tweetings.loader.DummyParcelableStatusesLoader;
import com.dwdesign.tweetings.model.ParcelableStatus;
import com.dwdesign.tweetings.service.TweetingsService;
import com.dwdesign.tweetings.util.NoDuplicatesArrayList;
import com.dwdesign.tweetings.util.ServiceInterface;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

public class ConversationFragment extends ParcelableStatusesListFragment implements Constants {

	private static final int ADD_STATUS = 1;
	private static final long INVALID_ID = -1;

	private ShowConversationTask mShowConversationTask;
	private StatusHandler mStatusHandler;
	private ParcelableStatusesAdapter mAdapter;
	public long account_id;
	public String screen_name;
	public boolean isShare = false;


	@Override
	public boolean isLoaderUsed() {
		return false;
	}
	
	@Override
	public void scrollToStatusId(long statusId) {
		
	}
	
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}
	
	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case MENU_SHARE: {
				isShare = true;
				shareConversation(false);
				break;
			}
			case MENU_TWEET_CONVERSATION: {
				isShare = false;
				shareConversation(true);
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
    public void onCreateOptionsMenu (Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_conversation, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public Loader<List<ParcelableStatus>> newLoaderInstance(final Bundle args) {
		final long account_id = args != null ? args.getLong(INTENT_KEY_ACCOUNT_ID, -1) : -1;
		return new DummyParcelableStatusesLoader(getActivity(), account_id, getData());
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setPullToRefreshEnabled(false);
		mAdapter = getListAdapter();
		mAdapter.setGapDisallowed(true);
		Bundle bundle = getArguments();
		if (bundle == null) {
			bundle = new Bundle();
		}
		account_id = bundle.getLong(INTENT_KEY_ACCOUNT_ID, INVALID_ID);
		final long status_id = bundle.getLong(INTENT_KEY_STATUS_ID, INVALID_ID);

		if (mShowConversationTask != null && !mShowConversationTask.isCancelled()) {
			mShowConversationTask.cancel(true);
		}
		mStatusHandler = new StatusHandler(mAdapter, account_id);
		mShowConversationTask = new ShowConversationTask(mStatusHandler, account_id, status_id);

		if (account_id != INVALID_ID && status_id != INVALID_ID) {
			
			mShowConversationTask.execute();
		}
	}

	@Override
	public void onDataLoaded(final Loader<List<ParcelableStatus>> loader, final ParcelableStatusesAdapter adapter) {

	}

	@Override
	public void onDestroyView() {
		setProgressBarIndeterminateVisibility(false);
		super.onDestroyView();
	}

	class ShowConversationTask extends AsyncTask<Void, Void, TwitterException> {

		private final long mAccountId, mStatusId;
		private final StatusHandler mHandler;

		public ShowConversationTask(final StatusHandler handler, final long account_id, final long status_id) {
			mHandler = handler;
			mAccountId = account_id;
			mStatusId = status_id;
		}

		@Override
		protected TwitterException doInBackground(final Void... params) {
			final Twitter twitter = getTwitterInstance(getActivity(), mAccountId, true);
			if (twitter == null) return null;
			try {
				ParcelableStatus p_status = findStatusInDatabases(getActivity(), mAccountId, mStatusId);
				twitter4j.Status status = null;
				if (p_status == null) {
					status = twitter.showStatus(mStatusId);
					if (status == null) return null;
					p_status = new ParcelableStatus(status, mAccountId, false);
				}
				mHandler.sendMessage(mHandler.obtainMessage(ADD_STATUS, p_status));
				long in_reply_to_id = p_status.in_reply_to_status_id;
				while (in_reply_to_id != -1) {
					p_status = findStatusInDatabases(getActivity(), mAccountId, in_reply_to_id);
					if (p_status == null) {
						status = twitter.showStatus(in_reply_to_id);
						if (status == null) {
							break;
						}
						p_status = new ParcelableStatus(status, mAccountId, false);
					}
					if (p_status.status_id <= 0) {
						break;
					}
					mHandler.sendMessage(mHandler.obtainMessage(ADD_STATUS, p_status));
					in_reply_to_id = p_status.in_reply_to_status_id;
				}
			} catch (final TwitterException e) {
				return e;
			}
			return null;
		}

		@Override
		protected void onPostExecute(final TwitterException result) {
			if (result != null) {
				showErrorToast(getActivity(), null, result, true);
			}
			setProgressBarIndeterminateVisibility(false);
			super.onPostExecute(result);
		}

		@Override
		protected void onPreExecute() {
			setProgressBarIndeterminateVisibility(true);
			super.onPreExecute();
		}

	}

	static class StatusHandler extends Handler {

		private final ParcelableStatusesAdapter mAdapter;

		public StatusHandler(final ParcelableStatusesAdapter adapter, long account_id) {
			mAdapter = adapter;
		}

		@Override
		public void handleMessage(final Message msg) {
			switch (msg.what) {
				case ADD_STATUS:
					final Object obj = msg.obj;
					if (obj instanceof ParcelableStatus) {
						mAdapter.add((ParcelableStatus) obj);
					}
					break;
			}
			super.handleMessage(msg);
		}
	}

	protected void shareConversation(boolean isCompose) {
		GenerateConversationLinkTask mTask = new GenerateConversationLinkTask();
		mTask.execute();
	}
	
	private class GenerateConversationLinkTask extends AsyncTask<String, Void, String> {
		private ProgressDialog dialog;
		
		
        // can use UI thread here
        protected void onPreExecute() {
        	dialog = new ProgressDialog(ConversationFragment.this.getActivity());
        	dialog.setMessage(getString(R.string.storify_generate));
        	dialog.setIndeterminate(true);
        	dialog.setCancelable(false);
        	dialog.show();

        }
   
        // automatically done on worker thread (separate from UI thread)
        protected String doInBackground(final String... args) {
        	HttpClient client = HttpClientFactory.getThreadSafeClient();
        	final String screen_name = getAccountUsername(ConversationFragment.this.getActivity(), account_id);
			
        	String title = "Conversation with";
            String slug = "conversation-with";
            String thumbnail = null;
            String statusId = null;
            
            String signingUrl = TWITTER_VERIFY_CREDENTIALS_JSON;
    		ServiceInterface mService = getServiceInterface();
    		String oAuthEchoHeader = mService.generateOAuthEchoHeader(account_id);
    		
    		NoDuplicatesArrayList<ParcelableStatus> statuses = mAdapter.getStatuses();
    		
            ArrayList<String> userArray = new ArrayList<String>();
            ArrayList<String> elementArray = new ArrayList<String>();
            
            for (ParcelableStatus status : statuses) {
            	if (statusId == null) {
            		statusId = String.valueOf(status.status_id);
            		thumbnail = "http://api.twitter.com/1/users/profile_image?screen_name=" + screen_name + "&size=bigger";
            	}
            	boolean inArray = false;
            	for (String user : userArray) {
            		if (user.equals(status.screen_name)) {
            			inArray = true;
            		}
            	}
            	if (inArray == false) {
            		title = title + " " + status.screen_name;
            		slug = slug + "-" + status.screen_name.toLowerCase();
            		userArray.add(status.screen_name);
            	}
            	elementArray.add("https://twitter.com/" + status.screen_name + "/status/" + String.valueOf(status.status_id));
    		}
            slug = slug + "-" + statusId;
            
            JSONObject jObject = new JSONObject();
            try {
				jObject.put("title", title);
				jObject.put("slug", slug);
				jObject.put("elements", new JSONArray(elementArray));
            } catch (JSONException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
            String jsonString = jObject.toString();

            try {
				HttpPost post = new HttpPost("http://api.storify.com/v1/stories/" + screen_name + "/create");
				post.addHeader("X-Verify-Credentials-Authorization", oAuthEchoHeader);
				post.addHeader("X-Auth-Service-Provider", TWITTER_VERIFY_CREDENTIALS_JSON);
			
				List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
				nameValuePairs.add(new BasicNameValuePair("publish", "true"));
				nameValuePairs.add(new BasicNameValuePair("api_key", STORIFY_API_KEY));
				nameValuePairs.add(new BasicNameValuePair("story", jsonString));
				post.setEntity(new UrlEncodedFormEntity(nameValuePairs));
				HttpResponse response = client.execute(post);
				
				BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
				String line = "";
				String outputContent = "";
				
				while ((line = rd.readLine()) != null) {
					outputContent += line;
				}
				
				if (!outputContent.equals("")) {
					JSONObject bufferResponse = new JSONObject(outputContent);
					JSONObject content = bufferResponse.getJSONObject("content");
					if (content != null) {
						String url = content.getString("permalink");
						if (url != null) {
							return url;
						}
					}
				}
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalStateException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	return null;
        }
   
        // can use UI thread here
        protected void onPostExecute(final String result) {
        	if (dialog.isShowing()) {
        		dialog.dismiss();
        	}
        	if (result == null) {
        		Toast.makeText(ConversationFragment.this.getActivity(), R.string.storify_failed, Toast.LENGTH_SHORT).show();
        	}
        	else {
        		ConversationFragment.this.linkGenerationComplete(result);
        	}
        }
     }
	
	public void linkGenerationComplete(String url) {
		if (isShare == false) {
			final Intent intent = new Intent(INTENT_ACTION_COMPOSE);
			final Bundle bundle = new Bundle();
			bundle.putLong(INTENT_KEY_ACCOUNT_ID, account_id);
			bundle.putString(INTENT_KEY_TEXT, url);
			intent.putExtras(bundle);
			startActivity(intent);
		}
		else {
			final Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_TEXT, url);
			startActivity(Intent.createChooser(intent, getString(R.string.share)));
		}
	}
}