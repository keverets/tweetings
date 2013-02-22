package com.dwdesign.tweetings.fragment;

import com.dwdesign.tweetings.R;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;

public class ProgressDialogFragment extends BaseDialogFragment {

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState) {
		final ProgressDialog dialog = new ProgressDialog(getActivity());
		dialog.setMessage(getString(R.string.please_wait));
		return dialog;
	}

}