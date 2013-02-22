package com.dwdesign.tweetings.preference;

import java.util.ArrayList;
import java.util.List;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.preference.DialogPreference;
import android.util.AttributeSet;

public class LinkShortenerPickerPreference extends DialogPreference implements Constants, OnClickListener {

	private SharedPreferences mPreferences;

	private final PackageManager mPackageManager;

	private AlertDialog mDialog;

	private ShortenerSpec[] mAvailableShorteners;

	public LinkShortenerPickerPreference(final Context context) {
		this(context, null);
	}

	public LinkShortenerPickerPreference(final Context context, final AttributeSet attrs) {
		this(context, attrs, android.R.attr.preferenceStyle);
	}

	public LinkShortenerPickerPreference(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
		mPackageManager = context.getPackageManager();
	}

	@Override
	public void onClick(final DialogInterface dialog, final int which) {
		final SharedPreferences.Editor editor = getEditor();
		if (editor == null) return;
		final ShortenerSpec spec = mAvailableShorteners[which];
		if (spec != null) {
			editor.putString(PREFERENCE_KEY_URL_SHORTENER, spec.cls);
			editor.commit();
		}
		if (mDialog != null && mDialog.isShowing()) {
			mDialog.dismiss();
		}
	}

	@Override
	public void onPrepareDialogBuilder(final AlertDialog.Builder builder) {
		mPreferences = getSharedPreferences();
		super.onPrepareDialogBuilder(builder);
		if (mPreferences == null) return;
		final String component = mPreferences.getString(PREFERENCE_KEY_URL_SHORTENER, "tinyurl");
		final ArrayList<ShortenerSpec> specs = new ArrayList<ShortenerSpec>();
		specs.add(new ShortenerSpec(getContext().getString(R.string.url_shortener_tinyurl), "tinyurl"));
		specs.add(new ShortenerSpec(getContext().getString(R.string.url_shortener_bitly), "bitly"));
		specs.add(new ShortenerSpec(getContext().getString(R.string.url_shortener_jmp), "jmp"));
		specs.add(new ShortenerSpec(getContext().getString(R.string.url_shortener_isgd), "isgd"));
		specs.add(new ShortenerSpec(getContext().getString(R.string.url_shortener_vgd), "vgd"));
		
		final Intent query_intent = new Intent(INTENT_ACTION_EXTENSION_URL_SHORTENER);
		final List<ResolveInfo> result = mPackageManager.queryIntentServices(query_intent, 0);
		for (final ResolveInfo info : result) {
			specs.add(new ShortenerSpec(info.loadLabel(mPackageManager).toString(), info.serviceInfo.packageName
					+ "/" + info.serviceInfo.name));
		}
		mAvailableShorteners = specs.toArray(new ShortenerSpec[specs.size()]);
		builder.setSingleChoiceItems(mAvailableShorteners, getIndex(component),
				LinkShortenerPickerPreference.this);
		builder.setNegativeButton(android.R.string.ok, null);
	}

	private int getIndex(final String cls) {
		if (mAvailableShorteners == null) return -1;
		if (cls == null) return 0;
		final int count = mAvailableShorteners.length;
		for (int i = 0; i < count; i++) {
			final ShortenerSpec spec = mAvailableShorteners[i];
			if (cls.equals(spec.cls)) return i;
		}
		return -1;
	}

	static class ShortenerSpec implements CharSequence {
		private final String name, cls;

		ShortenerSpec(final String name, final String cls) {
			this.name = name;
			this.cls = cls;
		}

		@Override
		public char charAt(final int index) {
			return name.charAt(index);
		}

		@Override
		public int length() {
			return name.length();
		}

		@Override
		public CharSequence subSequence(final int start, final int end) {
			return name.subSequence(start, end);
		}

		@Override
		public String toString() {
			return name;
		}
	}

}
