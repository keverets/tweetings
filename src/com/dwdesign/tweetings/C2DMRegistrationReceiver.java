package com.dwdesign.tweetings;

import static com.dwdesign.tweetings.appwidget.util.Utils.getRoundedCornerBitmap;
import static com.dwdesign.tweetings.util.Utils.getBiggerTwitterProfileImage;
import static com.dwdesign.tweetings.util.Utils.parseURL;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import com.dwdesign.tweetings.activity.ComposeActivity;
import com.dwdesign.tweetings.activity.HomeActivity;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.provider.TweetStore.Accounts;
import com.dwdesign.tweetings.service.TweetingsService;
import com.twitter.Extractor;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings.Secure;
import android.util.Log;

public class C2DMRegistrationReceiver extends BroadcastReceiver implements Constants {


	Context mContext;
	
	@Override
	public void onReceive(Context context, Intent intent) {
		mContext = context;
		String action = intent.getAction();
		if ("com.google.android.c2dm.intent.REGISTRATION".equals(action)) {
			final String registrationId = intent
					.getStringExtra("registration_id");
			//String error = intent.getStringExtra("error");

			String deviceId = Secure.getString(context.getContentResolver(),
					Secure.ANDROID_ID);
			sendRegistrationIdToServer(context, deviceId, registrationId);
			
		}
		if ("com.google.android.c2dm.intent.RECEIVE".equals(action)) {
			String screenName = intent.getExtras().getString("screenname");
			String message = intent.getExtras().getString("message");
			String type = intent.getExtras().getString("type");
			String followerId = intent.getExtras().getString("followerId");
			String statusId = intent.getExtras().getString("statusId");
			String accountId = intent.getExtras().getString("accountId");
			String followerName = intent.getExtras().getString("followerName");
			String profileImageUrl = intent.getExtras().getString("profileImageUrl");
			if (screenName == null) {
				screenName = "Tweetings";
			}
			if (message == null) {
				message = "Tweet received";
			}
			String ticker = message;

			
			createNotification(context, ticker, screenName, message, type, accountId, statusId, followerId, followerName, profileImageUrl);
		}
	}
	
	@SuppressWarnings("deprecation")
	public void createNotification(Context context, String ticker, String screenName, String message, String type, String accountId, String statusId, String followerId, String followerName, String profile_image_url) {
		
		SharedPreferences preferences = context.getSharedPreferences(com.dwdesign.tweetings.Constants.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		
		Boolean displayNotification = true;
		if (type.equals("fav") && preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_ENABLE_FAV, false) == false) {
			displayNotification = false;
		}
		else if (type.equals("rt") && preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_ENABLE_RT, false) == false) {
			displayNotification = false;
		}
		else if (type.equals("follow") && preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_ENABLE_FOLLOWS, false) == false) {
			displayNotification = false;
		}
		else if (type.equals("lists") && preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_ENABLE_LISTS, false) == false) {
			displayNotification = false;
		}
		else if (type.equals("dm") && preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_ENABLE_DIRECT_MESSAGES, false) == false) {
			displayNotification = false;
		}
		else if (type.equals("mention") && preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_ENABLE_MENTIONS, false) == false) {
			displayNotification = false;
		}
		else if (type.equals("track")) {
			displayNotification = true;
		}
		
		if (preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_PUSH_NOTIFICATIONS, false) == false) {
			displayNotification = false;
		}
		
		if (displayNotification == true) {
			Intent intent;
			PendingIntent pending_intent;
			if (type.equals("follow")) {
				final Uri.Builder uri_builder = new Uri.Builder();
				uri_builder.scheme(com.dwdesign.tweetings.Constants.SCHEME_TWEETINGS);
				
				uri_builder.authority(com.dwdesign.tweetings.Constants.AUTHORITY_USER);
				uri_builder.appendQueryParameter(com.dwdesign.tweetings.Constants.QUERY_PARAM_ACCOUNT_ID, accountId);
				uri_builder.appendQueryParameter(com.dwdesign.tweetings.Constants.QUERY_PARAM_USER_ID, followerId);
				uri_builder.appendQueryParameter(com.dwdesign.tweetings.Constants.QUERY_PARAM_SCREEN_NAME, followerName);
				intent = new Intent(Intent.ACTION_VIEW, uri_builder.build());
				pending_intent = PendingIntent.getActivity(context, 0, intent,
						Intent.FLAG_ACTIVITY_NEW_TASK);
			}
			else if (type.equals("mention")) {
				final Intent content_intent = new Intent(context, HomeActivity.class);
				content_intent.setAction(Intent.ACTION_MAIN);
				content_intent.addCategory(Intent.CATEGORY_LAUNCHER);
				final Bundle content_extras = new Bundle();
				content_extras.putInt(INTENT_KEY_INITIAL_TAB, HomeActivity.TAB_POSITION_MENTIONS);
				content_intent.putExtras(content_extras);
				pending_intent = PendingIntent.getActivity(context, 0, content_intent,
						PendingIntent.FLAG_CANCEL_CURRENT);
			}
			else if (type.equals("dm")) {
				final Intent content_intent = new Intent(context, HomeActivity.class);
				content_intent.setAction(Intent.ACTION_MAIN);
				content_intent.addCategory(Intent.CATEGORY_LAUNCHER);
				final Bundle content_extras = new Bundle();
				content_extras.putInt(INTENT_KEY_INITIAL_TAB, HomeActivity.TAB_POSITION_MESSAGES);
				content_intent.putExtras(content_extras);
				pending_intent = PendingIntent.getActivity(context, 0, content_intent,
						PendingIntent.FLAG_CANCEL_CURRENT);
			}
			else {
				intent = new Intent(context, HomeActivity.class);
				pending_intent = PendingIntent.getActivity(context, 0, intent,
					PendingIntent.FLAG_UPDATE_CURRENT);
			}
			
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				NotificationManager notificationManager = (NotificationManager) context
						.getSystemService(Context.NOTIFICATION_SERVICE);
				if (preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_GROUP_NOTIFICATIONS, false) == true) {
					notificationManager.cancelAll();
				}
				
				Builder builder = new Notification.Builder(context)
									.setContentTitle(screenName).setTicker(ticker);
				
				if (profile_image_url != null) {
					TweetingsApplication application = TweetingsApplication.getInstance(context);
					/*final File cache_dir = getTweetingsCacheDir();
					final String file_name = getFilename(context.getResources().getBoolean(R.bool.hires_profile_image) ? getBiggerTwitterProfileImage(profile_image_url)
							: profile_image_url);
					final File profile_image_file = cache_dir != null && cache_dir.isDirectory() && file_name != null ? new File(
							cache_dir, file_name) : null;
					final Bitmap profile_image = profile_image_file != null && profile_image_file.isFile() ? BitmapFactory
							.decodeFile(profile_image_file.getPath()) : null;*/
					try {
						URL final_url = parseURL(getBiggerTwitterProfileImage(profile_image_url));
						
						File profile_image_file = application.mProfileImageLoader.getCachedImageFile(String.valueOf(final_url));
						final Bitmap profile_image = profile_image_file != null && profile_image_file.isFile() ? BitmapFactory
								.decodeFile(profile_image_file.getPath()) : null;
						if (profile_image != null) {
							builder.setLargeIcon(getRoundedCornerBitmap(context.getResources(), profile_image));
						} 
					}
					catch (Exception e) {
						
					}
				}
				
				builder.setSmallIcon(R.drawable.ic_launcher).setContentText(message);
				
				if (type.equals("mention")) {
					final Intent reply_intent = new Intent(context, ComposeActivity.class);
					final Bundle bundle = new Bundle();
					final List<String> mentions = new Extractor().extractMentionedScreennames(message);
					mentions.remove(followerName);
					mentions.add(0, followerName);
					bundle.putStringArray(INTENT_KEY_MENTIONS, mentions.toArray(new String[mentions.size()]));
					bundle.putLong(INTENT_KEY_ACCOUNT_ID, Long.valueOf(accountId));
					bundle.putLong(INTENT_KEY_IN_REPLY_TO_ID, Long.valueOf(statusId));
					bundle.putString(INTENT_KEY_IN_REPLY_TO_SCREEN_NAME, followerName);
					bundle.putString(INTENT_KEY_IN_REPLY_TO_NAME, followerName);
					reply_intent.putExtras(bundle);
					PendingIntent pending_reply_intent = PendingIntent.getActivity(context, 0, reply_intent,
							PendingIntent.FLAG_CANCEL_CURRENT);
					builder.addAction(R.drawable.ic_menu_reply, context.getString(R.string.reply), pending_reply_intent);
					
					final Uri.Builder abuilder = new Uri.Builder();
					abuilder.scheme(SCHEME_TWEETINGS);
					abuilder.authority(AUTHORITY_CONVERSATION);
					abuilder.appendQueryParameter(QUERY_PARAM_ACCOUNT_ID, accountId);
					abuilder.appendQueryParameter(QUERY_PARAM_STATUS_ID, statusId);
					Intent profileIntent = new Intent(Intent.ACTION_VIEW, abuilder.build());
					PendingIntent pending_profile_intent = PendingIntent.getActivity(context, 0, profileIntent,
							PendingIntent.FLAG_CANCEL_CURRENT);
					builder.addAction(R.drawable.ic_menu_conversation, context.getString(R.string.view_conversation), pending_profile_intent);
					
				}
				if (type.equals("rt")) {
					final Uri.Builder abuilder = new Uri.Builder();
					abuilder.scheme(SCHEME_TWEETINGS);
					abuilder.authority(AUTHORITY_USERS_RETWEETED_STATUS);
					abuilder.appendQueryParameter(QUERY_PARAM_ACCOUNT_ID, accountId);
					abuilder.appendQueryParameter(QUERY_PARAM_STATUS_ID, statusId);
					Intent profileIntent = new Intent(Intent.ACTION_VIEW, abuilder.build());
					PendingIntent pending_profile_intent = PendingIntent.getActivity(context, 0, profileIntent,
							PendingIntent.FLAG_CANCEL_CURRENT);
					builder.addAction(R.drawable.ic_menu_retweet, context.getString(R.string.who_retweeted), pending_profile_intent);
					
				}
				if (type.equals("mention") || type.equals("rt") || type.equals("fav")) {
					final Uri.Builder abuilder = new Uri.Builder();
					abuilder.scheme(SCHEME_TWEETINGS);
					abuilder.authority(AUTHORITY_USER);
					abuilder.appendQueryParameter(QUERY_PARAM_ACCOUNT_ID, String.valueOf(accountId));
					abuilder.appendQueryParameter(QUERY_PARAM_SCREEN_NAME, followerName);
					Intent profileIntent = new Intent(Intent.ACTION_VIEW, abuilder.build());
					PendingIntent pending_profile_intent = PendingIntent.getActivity(context, 0, profileIntent,
							PendingIntent.FLAG_CANCEL_CURRENT);
					builder.addAction(R.drawable.ic_menu_accounts, context.getString(R.string.view_user_profile), pending_profile_intent);
					
				}
				
				
				Notification notification = new Notification.BigTextStyle(builder)
									.bigText(message).build();
				notification.flags |= Notification.FLAG_AUTO_CANCEL;
				if (preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_PUSH_SOUND, true)) {
					notification.sound = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.notify);
				}
				if (preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_HAVE_VIBRATION, true)) {
					notification.defaults |= Notification.DEFAULT_VIBRATE;
					
			    }
				if (preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_HAVE_LIGHTS, true)) {
			    	//notification.defaults |= Notification.DEFAULT_LIGHTS;
					final int color_def = mContext.getResources().getColor(R.color.holo_blue_dark);
					final int color = preferences.getInt(PREFERENCE_KEY_NOTIFICATION_LIGHT_COLOR, color_def);
					builder.setLights(color, 2000, 1000);
					
					notification.ledARGB = color;
					notification.ledOnMS = 2000;
				    notification.ledOffMS = 1000;
				    notification.flags |= Notification.FLAG_SHOW_LIGHTS;
			    }
				notification.number += 1;
				notification.contentIntent = pending_intent;
				
				if (preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_GROUP_NOTIFICATIONS, false) == false) {	
					long notificationId = (System.currentTimeMillis() / 1000L);
					notificationManager.notify(String.valueOf(notificationId), Integer.parseInt(String.valueOf(notificationId)), notification);
				}
				else {
					notificationManager.notify(0, notification);
					
				}
			}
			else {
				NotificationManager notificationManager = (NotificationManager) context
						.getSystemService(Context.NOTIFICATION_SERVICE);
				Notification notification = new Notification(R.drawable.ic_launcher,
						ticker, System.currentTimeMillis());
				
				
			
				if (preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_HAVE_SOUND, false)) {
					notification.sound = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.notify);
				}
				if (preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_HAVE_VIBRATION, false)) {
					notification.defaults |= Notification.DEFAULT_VIBRATE;
					
			    }
				if (preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_HAVE_LIGHTS, false)) {
			    	//notification.defaults |= Notification.DEFAULT_LIGHTS;
					final int color_def = mContext.getResources().getColor(R.color.holo_blue_dark);
					final int color = preferences.getInt(PREFERENCE_KEY_NOTIFICATION_LIGHT_COLOR, color_def);
					
					notification.ledARGB = color;
					notification.ledOnMS = 2000;
				    notification.ledOffMS = 1000;
				    notification.flags |= Notification.FLAG_SHOW_LIGHTS;
			    }
				
				// Hide the notification after its selected
				notification.flags |= Notification.FLAG_AUTO_CANCEL;
				notification.number += 1;
				notification.setLatestEventInfo(context, screenName,
						message, pending_intent);
				
				if (preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_GROUP_NOTIFICATIONS, false) == false) {	
					long notificationId = (System.currentTimeMillis() / 1000L);
					notificationManager.notify(String.valueOf(notificationId), Integer.parseInt(String.valueOf(notificationId)), notification);
				}
				else {
					notificationManager.notify(0, notification);
					
				}
				
				/*PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		        // Check if the screen itself is off or not. If it is then this code below will FORCEFULLY wake up the device even if it is put into sleep mode by user
		        if(pm.isScreenOn() == false)
		        {
		            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "MyLock");
		            wl.acquire(15000);
		            PowerManager.WakeLock wl_cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MyCpuLock");
		            wl_cpu.acquire(15000);
		        }*/
				
			}	
		}
	}
	
	public static long[] getActivatedAccountIds(Context context) {
		long[] accounts = new long[0];
		if (context == null) return accounts;
		final String[] cols = new String[] { Accounts.USER_ID };
		final Cursor cur = context.getContentResolver().query(Accounts.CONTENT_URI, cols, Accounts.IS_ACTIVATED + "=1",
				null, Accounts.USER_ID);
		if (cur != null) {
			final int idx = cur.getColumnIndexOrThrow(Accounts.USER_ID);
			cur.moveToFirst();
			accounts = new long[cur.getCount()];
			int i = 0;
			while (!cur.isAfterLast()) {
				accounts[i] = cur.getLong(idx);
				i++;
				cur.moveToNext();
			}
			cur.close();
		}
		return accounts;
	}
	
	public Cursor getAccountsCursor(Context context) {
		final Uri uri = Accounts.CONTENT_URI;
		final String[] cols = new String[] { Accounts._ID, Accounts.USER_ID, Accounts.USERNAME, Accounts.IS_ACTIVATED };
		return context.getContentResolver().query(uri, cols, null, null, null);
	}
	
	// Better do this in an asynchronous thread
	public void sendRegistrationIdToServer(final Context context, final String deviceId, final String registrationId) {
		SharedPreferences preferences = context.getSharedPreferences(com.dwdesign.tweetings.Constants.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		
		if (preferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_PUSH_NOTIFICATIONS, false) == true) {
			
			final String[] cols = new String[] { Accounts.USER_ID, Accounts.USERNAME };
    		final Cursor cur = context.getContentResolver().query(Accounts.CONTENT_URI, cols, Accounts.IS_ACTIVATED + "=1",
    				null, Accounts.USER_ID);
    		if (cur != null) {
    			
    			cur.moveToFirst();
    			long user_id = 0;
    			String screen_name = null;
    			
    			while (!cur.isAfterLast()) {
    				user_id = cur.getLong(cur.getColumnIndexOrThrow(Accounts.USER_ID));
    				screen_name = cur.getString(cur.getColumnIndexOrThrow(Accounts.USERNAME));
    				cur.moveToNext();
    			}
    			final long userId = user_id;
    			final String screenName = screen_name;
    			
    			final SharedPreferences getPreferences = preferences;
    			Thread thread = new Thread() {
    			      public void run() {
    			    	  HttpClient client = HttpClientFactory.getThreadSafeClient();
    		    			HttpPost post = new HttpPost(com.dwdesign.tweetings.Constants.C2DM_SERVER_REGISTRATION_URL);
    		    			try {
    		    				List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
    		    				// Get the deviceID
    		    				nameValuePairs.add(new BasicNameValuePair("deviceid", deviceId));
    		    				//nameValuePairs.add(new BasicNameValuePair("registrationid", registrationId));
    		    				nameValuePairs.add(new BasicNameValuePair("gcm", registrationId));
    		    				
    		    				if (getPreferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_ENABLE_FAV, false) == true) {
    		    					nameValuePairs.add(new BasicNameValuePair("fav", "1"));
    		        			}
    		        			if (getPreferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_ENABLE_RT, false) == true) {
    		        				nameValuePairs.add(new BasicNameValuePair("rt", "1"));
    		        			}
    		        			if (getPreferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_ENABLE_FOLLOWS, false) == true) {
    		        				nameValuePairs.add(new BasicNameValuePair("fol", "1"));
    		        			}
    		        			if (getPreferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_ENABLE_LISTS, false) == true) {
    		        				nameValuePairs.add(new BasicNameValuePair("lis", "1"));
    		        			}
    		        			if (getPreferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_ENABLE_DIRECT_MESSAGES, false) == true) {
    		        				nameValuePairs.add(new BasicNameValuePair("dms", "1"));
    		        			}
    		        			if (getPreferences.getBoolean(com.dwdesign.tweetings.Constants.PREFERENCE_KEY_NOTIFICATION_ENABLE_MENTIONS, false) == true) {
    		        				nameValuePairs.add(new BasicNameValuePair("men", "1"));
    		        			}
    		    				nameValuePairs.add(new BasicNameValuePair("screenname", screenName));
    		    				nameValuePairs.add(new BasicNameValuePair("userid", String.valueOf(userId)));
    		    				post.setEntity(new UrlEncodedFormEntity(nameValuePairs));
    		    				HttpResponse response = client.execute(post);
    		    				BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
    		    				String line = "";
    		    				
    		    				while ((line = rd.readLine()) != null) {
    		    					Log.e("HttpResponse", line);
    		    				}
    		    			} catch (IOException e) {
    		    				e.printStackTrace();
    		    			}
    			      }
    			};
    			thread.start();
    			
    			cur.close();
    		}
    	}
		
	}
}