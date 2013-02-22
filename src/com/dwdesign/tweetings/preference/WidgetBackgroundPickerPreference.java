package com.dwdesign.tweetings.preference;

import java.util.ArrayList;
import java.util.List;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.preference.DialogPreference;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.AttributeSet;

public class WidgetBackgroundPickerPreference extends DialogPreference implements Constants, OnClickListener {

	private SharedPreferences mPreferences;

	private final PackageManager mPackageManager;

	private BackgroundSpec[] mAvailableBackgrounds;

	public WidgetBackgroundPickerPreference(final Context context) {
		this(context, null);
	}

	public WidgetBackgroundPickerPreference(final Context context, final AttributeSet attrs) {
		this(context, attrs, android.R.attr.preferenceStyle);
	}

	public WidgetBackgroundPickerPreference(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
		mPackageManager = context.getPackageManager();
	}

	@Override
	public void onClick(final DialogInterface dialog, final int which) {
		final SharedPreferences.Editor editor = getEditor();
		if (editor == null) return;
		final BackgroundSpec spec = mAvailableBackgrounds[which];
		if (spec != null) {
			editor.putString(PREFERENCE_KEY_WIDGET_BACKGROUND, spec.cls);
			editor.commit();
		}
		dialog.dismiss();
	}

	@Override
	public void onPrepareDialogBuilder(final AlertDialog.Builder builder) {
		mPreferences = getSharedPreferences();
		super.onPrepareDialogBuilder(builder);
		if (mPreferences == null) return;
		final String component = mPreferences.getString(PREFERENCE_KEY_WIDGET_BACKGROUND, null);
		final ArrayList<BackgroundSpec> specs = new ArrayList<BackgroundSpec>();
		specs.add(new BackgroundSpec(getContext().getString(R.string.black_transparent), null));
		specs.add(new BackgroundSpec(getContext().getString(R.string.grey), "grey"));
		
		final Intent query_intent = new Intent(INTENT_ACTION_EXTENSION_UPLOAD_IMAGE);
		final List<ResolveInfo> result = mPackageManager.queryIntentServices(query_intent, 0);
		for (final ResolveInfo info : result) {
			specs.add(new BackgroundSpec(info.loadLabel(mPackageManager).toString(), info.serviceInfo.packageName
					+ "/" + info.serviceInfo.name));
		}
		mAvailableBackgrounds = specs.toArray(new BackgroundSpec[specs.size()]);
		builder.setSingleChoiceItems(mAvailableBackgrounds, getIndex(component),
				WidgetBackgroundPickerPreference.this);
		builder.setNegativeButton(android.R.string.ok, null);
	}

	private int getIndex(final String cls) {
		if (mAvailableBackgrounds == null) return -1;
		if (cls == null) return 0;
		final int count = mAvailableBackgrounds.length;
		for (int i = 0; i < count; i++) {
			final BackgroundSpec spec = mAvailableBackgrounds[i];
			if (cls.equals(spec.cls)) return i;
		}
		return -1;
	}

	static class BackgroundSpec implements CharSequence {
		private final String name, cls;

		BackgroundSpec(final String name, final String cls) {
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
