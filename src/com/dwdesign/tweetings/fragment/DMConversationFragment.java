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

package com.dwdesign.tweetings.fragment;

import static com.dwdesign.tweetings.util.Utils.buildDirectMessageConversationUri;
import static com.dwdesign.tweetings.util.Utils.isNullOrEmpty;
import static com.dwdesign.tweetings.util.Utils.openUserProfile;
import static com.dwdesign.tweetings.util.Utils.parseString;

import com.dwdesign.popupmenu.PopupMenu;
import com.dwdesign.popupmenu.PopupMenu.OnMenuItemClickListener;
import com.dwdesign.tweetings.R;
import com.dwdesign.tweetings.adapter.DirectMessagesConversationAdapter;
import com.dwdesign.tweetings.adapter.UserAutoCompleteAdapter;
import com.dwdesign.tweetings.app.TweetingsApplication;
import com.dwdesign.tweetings.model.Account;
import com.dwdesign.tweetings.model.DirectMessageConversationViewHolder;
import com.dwdesign.tweetings.model.Panes;
import com.dwdesign.tweetings.model.ParcelableDirectMessage;
import com.dwdesign.tweetings.provider.TweetStore;
import com.dwdesign.tweetings.provider.TweetStore.DirectMessages;
import com.dwdesign.tweetings.util.ImageLoaderWrapper;
import com.dwdesign.tweetings.util.ClipboardUtils;
import com.dwdesign.tweetings.util.ServiceInterface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.twitter.Validator;

public class DMConversationFragment extends BaseFragment implements LoaderCallbacks<Cursor>, OnItemClickListener,
		OnItemLongClickListener, OnMenuItemClickListener, TextWatcher, OnClickListener, Panes.Right,
		OnItemSelectedListener, OnEditorActionListener {

	private final Validator mValidator = new Validator();
	private ServiceInterface mService;
	private SharedPreferences mPreferences;

	private ListView mListView;
	private EditText mEditText;
	private TextView mTextCount;
	private AutoCompleteTextView mEditScreenName;
	private ImageButton mSendButton;
	private Button mScreenNameConfirmButton;
	private View mConversationContainer, mScreenNameContainer;
	private Spinner mAccountSelector;

	private PopupMenu mPopupMenu;

	private ParcelableDirectMessage mSelectedDirectMessage;
	private final Bundle mArguments = new Bundle();
	private Account mSelectedAccount;
	
	private DirectMessagesConversationAdapter mAdapter;
	private UserAutoCompleteAdapter mUserAutoCompleteAdapter;
	private AccountsAdapter mAccountsAdapter;
	private boolean isVisibleToUser = false;
	
	// Begin Sync Code
	private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
        	final String action = intent.getAction();
        	if (BROADCAST_VOLUME_UP.equals(action)) {
        		//if (isVisible()) {
					int currentPosition = mListView.getFirstVisiblePosition();  
				    if (currentPosition == 0)   
				        return;  
				    mListView.setSelection(currentPosition - 1);  
				    mListView.clearFocus(); 
        		//}
			}
			else if (BROADCAST_VOLUME_DOWN.equals(action)) {
				//if (isVisible()) {
					int currentPosition = mListView.getFirstVisiblePosition();  
				    if (currentPosition == mListView.getCount() - 1)   
				        return;  
				    mListView.setSelection(currentPosition + 1);  
				    mListView.clearFocus(); 
				//}
			}
        }
    };
    
    @Override
    public void setMenuVisibility(final boolean visible) {
        super.setMenuVisibility(visible);
        isVisibleToUser = visible;
    }
    
    @Override
	public void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_VOLUME_UP);
		filter.addAction(BROADCAST_VOLUME_DOWN);
        registerReceiver(receiver, filter);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(receiver);
	}

	private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (BROADCAST_RECEIVED_DIRECT_MESSAGES_DATABASE_UPDATED.equals(action)
					|| BROADCAST_SENT_DIRECT_MESSAGES_DATABASE_UPDATED.equals(action)) {
				getLoaderManager().restartLoader(0, mArguments, DMConversationFragment.this);
			} else if (BROADCAST_RECEIVED_DIRECT_MESSAGES_REFRESHED.equals(action)
					|| BROADCAST_SENT_DIRECT_MESSAGES_REFRESHED.equals(action)) {
				getLoaderManager().restartLoader(0, mArguments, DMConversationFragment.this);
			} else if (BROADCAST_REFRESHSTATE_CHANGED.equals(action)) {
				setProgressBarIndeterminateVisibility(mService.isReceivedDirectMessagesRefreshing()
						|| mService.isSentDirectMessagesRefreshing());
			}
		}
	};

	private final TextWatcher mScreenNameTextWatcher = new TextWatcher() {

		@Override
		public void afterTextChanged(final Editable s) {

		}

		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {

		}

		@Override
		public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
			if (mScreenNameConfirmButton == null) return;
			mScreenNameConfirmButton.setEnabled(s.length() > 0 && s.length() < 20);
		}
	};
	
	private TweetingsApplication mApplication;

	@Override
	public void afterTextChanged(final Editable s) {

	}

	@Override
	public void beforeTextChanged(final CharSequence s, final int start, final int count, final  int after) {

	}
	
	@Override
	public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
		if (mSendButton == null || s == null) return;
		mSendButton.setEnabled(mValidator.isValidTweet(s.toString()));
		
		if (mTextCount != null) {
			final int acount = mValidator.getTweetLength(s.toString());
			final float hue = acount < 140 ? acount >= 130 ? 5 * (140 - acount) : 50 : 0;
			final float[] hsv = new float[] { hue, 1.0f, 1.0f };
			mTextCount.setTextColor(acount >= 130 ? Color.HSVToColor(0x80, hsv) : 0x80808080);
			mTextCount.setText(parseString(140-acount));
		}
	}
	
	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mApplication = getApplication();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mService = getServiceInterface();

		mAdapter = new DirectMessagesConversationAdapter(getActivity());
		mListView.setAdapter(mAdapter);
		mListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
		mListView.setStackFromBottom(true);
		mListView.setOnItemClickListener(this);
		mListView.setOnItemLongClickListener(this);
		final Bundle args = savedInstanceState == null ? getArguments() : savedInstanceState.getBundle(INTENT_KEY_DATA);
		if (args != null) {
			mArguments.putAll(args);
		}
		getLoaderManager().initLoader(0, mArguments, this);

		if (mPreferences.getBoolean(PREFERENCE_KEY_QUICK_SEND, false)) {
			mEditText.setOnEditorActionListener(this);
		}
		
		mEditText.addTextChangedListener(this);
		final String text = savedInstanceState != null ? savedInstanceState.getString(INTENT_KEY_TEXT) : null;
		if (text != null) {
			mEditText.setText(text);
		}

		mAccountsAdapter = new AccountsAdapter(getActivity());
		mAccountSelector.setAdapter(mAccountsAdapter);
		mAccountSelector.setOnItemSelectedListener(this);

		mUserAutoCompleteAdapter = new UserAutoCompleteAdapter(getActivity());

		mEditScreenName.addTextChangedListener(mScreenNameTextWatcher);
		mEditScreenName.setAdapter(mUserAutoCompleteAdapter);

		mSendButton.setOnClickListener(this);
		mSendButton.setEnabled(false);
		mScreenNameConfirmButton.setOnClickListener(this);
		mScreenNameConfirmButton.setEnabled(false);
		mTextCount = (TextView) getActivity().findViewById(R.id.text_count);
	}

	@Override
	public void onClick(final View view) {
		switch (view.getId()) {
			case R.id.send: {
				send();
				break;
			}
			case R.id.screen_name_confirm: {
				final CharSequence text = mEditScreenName.getText();
				if (text == null || mSelectedAccount == null) return;
				final String screen_name = text.toString();
				mArguments.putString(INTENT_KEY_SCREEN_NAME, screen_name);
				mArguments.putLong(INTENT_KEY_ACCOUNT_ID, mSelectedAccount.account_id);
				getLoaderManager().restartLoader(0, mArguments, this);
				break;
			}
		}

	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Tell the framework to try to keep this fragment around
		// during a configuration change.
		setRetainInstance(true);
	}

	@Override
	public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
		if (args == null || !args.containsKey(INTENT_KEY_ACCOUNT_ID))
			return new CursorLoader(getActivity(), TweetStore.NULL_CONTENT_URI, null, null, null, null);
		final String[] cols = DirectMessages.COLUMNS;
		final long account_id = args != null ? args.getLong(INTENT_KEY_ACCOUNT_ID, -1) : -1;
		final long conversation_id = args != null ? args.getLong(INTENT_KEY_CONVERSATION_ID, -1) : -1;
		final String screen_name = args != null ? args.getString(INTENT_KEY_SCREEN_NAME) : null;
		final Uri uri = buildDirectMessageConversationUri(account_id, conversation_id, screen_name);
		mConversationContainer.setVisibility(account_id <= 0 || conversation_id <= 0 && screen_name == null ? View.GONE
				: View.VISIBLE);
		mScreenNameContainer
				.setVisibility(account_id <= 0 || conversation_id <= 0 && screen_name == null ? View.VISIBLE
						: View.GONE);
		return new CursorLoader(getActivity(), uri, cols, null, null, DirectMessages.Conversation.DEFAULT_SORT_ORDER);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.direct_messages_conversation, null);
		mListView = (ListView) view.findViewById(android.R.id.list);
		mEditText = (EditText) view.findViewById(R.id.edit_text);
		mSendButton = (ImageButton) view.findViewById(R.id.send);
		mConversationContainer = view.findViewById(R.id.conversation_container);
		mScreenNameContainer = view.findViewById(R.id.screen_name_container);
		mEditScreenName = (AutoCompleteTextView) view.findViewById(R.id.screen_name);
		mAccountSelector = (Spinner) view.findViewById(R.id.account_selector);
		mScreenNameConfirmButton = (Button) view.findViewById(R.id.screen_name_confirm);
		return view;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	public boolean onEditorAction(final TextView view, final  int actionId, final KeyEvent event) {
		switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_ENTER: {
				send();
				return true;
			}
		}
		return false;
	}

	@Override
	public void onItemClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		final Object tag = view.getTag();
		if (tag instanceof DirectMessageConversationViewHolder) {
		}
	}

	@Override
	public boolean onItemLongClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		final Object tag = view.getTag();
		if (tag instanceof DirectMessageConversationViewHolder) {
			final ParcelableDirectMessage dm = mSelectedDirectMessage = mAdapter.findItem(id);
			mPopupMenu = PopupMenu.getInstance(getActivity(), view);
			mPopupMenu.inflate(R.menu.action_direct_message);
			final Menu menu = mPopupMenu.getMenu();
			final MenuItem view_profile_item = menu.findItem(MENU_VIEW_PROFILE);
			if (view_profile_item != null && dm != null) {
				view_profile_item.setVisible(dm.account_id != dm.sender_id);
			}
			mPopupMenu.setOnMenuItemClickListener(this);
			mPopupMenu.show();
			return true;
		}
		return false;
	}

	@Override
	public void onItemSelected(final AdapterView<?> parent, final View view, final int pos, final long id) {
		mSelectedAccount = null;
		if (mAccountsAdapter == null) return;
		mSelectedAccount = mAccountsAdapter.getItem(pos);
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader) {
		mAdapter.swapCursor(null);
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor cursor) {
		mAdapter.swapCursor(cursor);

	}

	@Override
	public boolean onMenuItemClick(final MenuItem item) {
		if (mSelectedDirectMessage != null) {
			final long message_id = mSelectedDirectMessage.message_id;
			final long account_id = mSelectedDirectMessage.account_id;
			switch (item.getItemId()) {
				case MENU_REPLY: {
					break;
				}
				case MENU_DELETE: {
					mService.destroyDirectMessage(account_id, message_id);
					break;
				}
				case MENU_COPY: {
					if (ClipboardUtils.setText(getActivity(), mSelectedDirectMessage.text)) {
						Toast.makeText(getActivity(), R.string.text_copied, Toast.LENGTH_SHORT).show();
				 	}
				 	break;
				}
				case MENU_VIEW_PROFILE: {
					if (mSelectedDirectMessage == null) return false;
					openUserProfile(getActivity(), account_id, mSelectedDirectMessage.sender_id,
							mSelectedDirectMessage.sender_screen_name);
					break;
				}
				default:
					return false;
			}
		}
		return true;
	}

	@Override
	public void onNothingSelected(final AdapterView<?> view) {

	}

	@Override
	public void onSaveInstanceState(final Bundle outState) {
		if (mEditText != null) {
			outState.putString(INTENT_KEY_TEXT, parseString(mEditText.getText()));
		}
		outState.putBundle(INTENT_KEY_DATA, mArguments);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onStart() {
		super.onStart();
		final IntentFilter filter = new IntentFilter(BROADCAST_REFRESHSTATE_CHANGED);
		filter.addAction(BROADCAST_RECEIVED_DIRECT_MESSAGES_DATABASE_UPDATED);
		filter.addAction(BROADCAST_SENT_DIRECT_MESSAGES_DATABASE_UPDATED);
		filter.addAction(BROADCAST_RECEIVED_DIRECT_MESSAGES_REFRESHED);
		filter.addAction(BROADCAST_SENT_DIRECT_MESSAGES_REFRESHED);
		registerReceiver(mStatusReceiver, filter);

		final float text_size = mPreferences.getFloat(PREFERENCE_KEY_TEXT_SIZE, PREFERENCE_DEFAULT_TEXT_SIZE);
		final boolean display_profile_image = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_PROFILE_IMAGE, true);
		final boolean display_name = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_NAME, false);
		mAdapter.setDisplayProfileImage(display_profile_image);
		mAdapter.setDisplayName(display_name);
		mAdapter.setTextSize(text_size);
	}

	@Override
	public void onStop() {
		unregisterReceiver(mStatusReceiver);
		if (mPopupMenu != null) {
			mPopupMenu.dismiss();
		}
		super.onStop();
	}

	public void showConversation(final long account_id, final long conversation_id) {
		final Bundle args = new Bundle();
		args.putLong(INTENT_KEY_ACCOUNT_ID, account_id);
		args.putLong(INTENT_KEY_CONVERSATION_ID, conversation_id);
		getLoaderManager().restartLoader(0, args, this);
	}
	
	private void send() {
	 	 final Editable text = mEditText.getText();
	 	 if (isNullOrEmpty(text)) return;
	 	 final String message = text.toString();
	 	 if (mValidator.isValidTweet(message)) {
	 	   final long account_id = mArguments.getLong(INTENT_KEY_ACCOUNT_ID, -1);
	 	   final long conversation_id = mArguments.getLong(INTENT_KEY_CONVERSATION_ID, -1);
	 	   final String screen_name = mArguments.getString(INTENT_KEY_SCREEN_NAME);
	 	   mService.sendDirectMessage(account_id, screen_name, conversation_id, message);
	 	   text.clear();
	 	 }
	}

	private static class AccountsAdapter extends ArrayAdapter<Account> {

		public AccountsAdapter(final Context context) {
			super(context, R.layout.spinner_item, Account.getAccounts(context, true));
			setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		}

	}

}