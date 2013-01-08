package com.dwdesign.tweetings.view;

import android.text.TextPaint;
import android.text.style.ClickableSpan;

/**
 * @author Aidan Follestad
 */
public abstract class NoUnderlineClickableSpan extends ClickableSpan {

	@Override
	public void updateDrawState(TextPaint ds) {
		ds.setColor(ds.linkColor);
		ds.setUnderlineText(false);
	}

}