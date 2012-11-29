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

public class SyncTypePreference extends DialogPreference implements Constants, OnClickListener {

	private SharedPreferences mPreferences;

	private final PackageManager mPackageManager;

	private AlertDialog mDialog;

	private SyncTypeSpec[] mAvailableSyncTypes;

	public SyncTypePreference(final Context context) {
		this(context, null);
	}

	public SyncTypePreference(final Context context, final AttributeSet attrs) {
		this(context, attrs, android.R.attr.preferenceStyle);
	}

	public SyncTypePreference(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
		mPackageManager = context.getPackageManager();
	}

	@Override
	public void onClick(final DialogInterface dialog, final int which) {
		final SharedPreferences.Editor editor = getEditor();
		if (editor == null) return;
		final SyncTypeSpec spec = mAvailableSyncTypes[which];
		if (spec != null) {
			editor.putString(PREFERENCE_KEY_SYNC_TYPE, spec.cls);
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
		final String component = mPreferences.getString(PREFERENCE_KEY_SYNC_TYPE, null);
		final ArrayList<SyncTypeSpec> specs = new ArrayList<SyncTypeSpec>();
		specs.add(new SyncTypeSpec(getContext().getString(R.string.timeline_sync_tweetmarker), "tweetmarker"));
		specs.add(new SyncTypeSpec(getContext().getString(R.string.timeline_sync_tweetings), "tweetings"));
		
		final Intent query_intent = new Intent(INTENT_ACTION_EXTENSION_SYNC_TYPE);
		final List<ResolveInfo> result = mPackageManager.queryIntentServices(query_intent, 0);
		for (final ResolveInfo info : result) {
			specs.add(new SyncTypeSpec(info.loadLabel(mPackageManager).toString(), info.serviceInfo.packageName
					+ "/" + info.serviceInfo.name));
		}
		mAvailableSyncTypes = specs.toArray(new SyncTypeSpec[specs.size()]);
		builder.setSingleChoiceItems(mAvailableSyncTypes, getIndex(component),
				SyncTypePreference.this);
		builder.setNegativeButton(android.R.string.ok, null);
	}

	private int getIndex(final String cls) {
		if (mAvailableSyncTypes == null) return -1;
		if (cls == null) return 0;
		final int count = mAvailableSyncTypes.length;
		for (int i = 0; i < count; i++) {
			final SyncTypeSpec spec = mAvailableSyncTypes[i];
			if (cls.equals(spec.cls)) return i;
		}
		return -1;
	}

	static class SyncTypeSpec implements CharSequence {
		private final String name, cls;

		SyncTypeSpec(final String name, final String cls) {
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
