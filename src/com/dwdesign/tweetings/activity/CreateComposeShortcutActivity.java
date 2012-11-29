package com.dwdesign.tweetings.activity;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;

import android.app.Activity;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.os.Bundle;

public class CreateComposeShortcutActivity extends Activity implements Constants {

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		final Intent intent = new Intent();
		final Intent launch_intent = new Intent(INTENT_ACTION_COMPOSE);
		final ShortcutIconResource icon = Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher);
		intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launch_intent);
		intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);
		intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.compose));
		setResult(RESULT_OK, intent);
		finish();
		super.onCreate(savedInstanceState);
	}
}
