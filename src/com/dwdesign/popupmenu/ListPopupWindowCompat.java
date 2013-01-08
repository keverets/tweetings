/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dwdesign.popupmenu;

import com.dwdesign.tweetings.R;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsListView.LayoutParams;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupWindow;

/**
 * A ListPopupWindow anchors itself to a host view and displays a list of
 * choices.
 * 
 * <p>
 * ListPopupWindow contains a number of tricky behaviors surrounding
 * positioning, scrolling parents to fit the dropdown, interacting sanely with
 * the IME if present, and others.
 * 
 * @see android.widget.AutoCompleteTextView
 * @see android.widget.Spinner
 */
public class ListPopupWindowCompat implements ListPopupWindow {
	private static final String TAG = "ListPopupWindow";
	private static final boolean DEBUG = false;

	/**
	 * This value controls the length of time that the user must leave a pointer
	 * down without scrolling to expand the autocomplete dropdown list to cover
	 * the IME.
	 */
	private static final int EXPAND_LIST_TIMEOUT = 250;

	private final Context mContext;
	private final PopupWindow mPopup;
	private ListAdapter mAdapter;
	private DropDownListView mDropDownList;

	private int mDropDownHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
	private int mDropDownWidth = ViewGroup.LayoutParams.WRAP_CONTENT;
	private int mDropDownHorizontalOffset;
	private int mDropDownVerticalOffset;
	private boolean mDropDownVerticalOffsetSet;

	private boolean mDropDownAlwaysVisible = false;
	private boolean mForceIgnoreOutsideTouch = false;
	int mListItemExpandMaximum = Integer.MAX_VALUE;

	private View mPromptView;
	private int mPromptPosition = POSITION_PROMPT_ABOVE;

	private DataSetObserver mObserver;

	private View mDropDownAnchorView;

	private Drawable mDropDownListHighlight;

	private AdapterView.OnItemClickListener mItemClickListener;
	private AdapterView.OnItemSelectedListener mItemSelectedListener;

	private final ResizePopupRunnable mResizePopupRunnable = new ResizePopupRunnable();
	private final PopupTouchInterceptor mTouchInterceptor = new PopupTouchInterceptor();
	// private final PopupScrollListener mScrollListener = new
	// PopupScrollListener();
	private final ListSelectorHider mHideSelector = new ListSelectorHider();
	private Runnable mShowDropDownRunnable;

	private final Handler mHandler = new Handler();

	private final Rect mTempRect = new Rect();

	private boolean mModal;

	// from ICS ListView
	static final int NO_POSITION = -1;

	/**
	 * Create a new, empty popup window capable of displaying items from a
	 * ListAdapter. Backgrounds should be set using
	 * {@link #setBackgroundDrawable(Drawable)}.
	 * 
	 * @param context Context used for contained views.
	 */
	public ListPopupWindowCompat(final Context context) {
		this(context, null, 0, 0);
	}

	/**
	 * Create a new, empty popup window capable of displaying items from a
	 * ListAdapter. Backgrounds should be set using
	 * {@link #setBackgroundDrawable(Drawable)}.
	 * 
	 * @param context Context used for contained views.
	 * @param attrs Attributes from inflating parent views used to style the
	 *            popup.
	 */
	public ListPopupWindowCompat(final Context context, final AttributeSet attrs) {
		this(context, attrs, 0, 0);
	}

	/**
	 * Create a new, empty popup window capable of displaying items from a
	 * ListAdapter. Backgrounds should be set using
	 * {@link #setBackgroundDrawable(Drawable)}.
	 * 
	 * @param context Context used for contained views.
	 * @param attrs Attributes from inflating parent views used to style the
	 *            popup.
	 * @param defStyleAttr Default style attribute to use for popup content.
	 */
	public ListPopupWindowCompat(final Context context, final AttributeSet attrs, final int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	/**
	 * Create a new, empty popup window capable of displaying items from a
	 * ListAdapter. Backgrounds should be set using
	 * {@link #setBackgroundDrawable(Drawable)}.
	 * 
	 * @param context Context used for contained views.
	 * @param attrs Attributes from inflating parent views used to style the
	 *            popup.
	 * @param defStyleAttr Style attribute to read for default styling of popup
	 *            content.
	 * @param defStyleRes Style resource ID to use for default styling of popup
	 *            content.
	 */
	public ListPopupWindowCompat(final Context context, final AttributeSet attrs, final int defStyleAttr,
			final int defStyleRes) {
		mContext = context;
		mPopup = new PopupWindow(context, attrs);
		mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);

		final Theme theme = context.getTheme();
		final TypedArray array = theme.obtainStyledAttributes(new int[] { R.attr.popupBackground,
				android.R.attr.listSelector });
		mPopup.setBackgroundDrawable(array.getDrawable(0));
		mDropDownListHighlight = array.getDrawable(1);
		array.recycle();
	}

	/**
	 * Clear any current list selection. Only valid when {@link #isShowing()} ==
	 * {@code true}.
	 */
	@Override
	public void clearListSelection() {
		final DropDownListView list = mDropDownList;
		if (list != null) {
			// WARNING: Please read the comment where mListSelectionHidden is
			// declared
			list.mListSelectionHidden = true;
			// list.hideSelector();
			list.requestLayout();
		}
	}

	/**
	 * Dismiss the popup window.
	 */
	@Override
	public void dismiss() {
		mPopup.dismiss();
		removePromptView();
		mPopup.setContentView(null);
		mDropDownList = null;
		mHandler.removeCallbacks(mResizePopupRunnable);
	}

	/**
	 * Returns the view that will be used to anchor this popup.
	 * 
	 * @return The popup's anchor view
	 */
	@Override
	public View getAnchorView() {
		return mDropDownAnchorView;
	}

	/**
	 * Returns the animation style that will be used when the popup window is
	 * shown or dismissed.
	 * 
	 * @return Animation style that will be used.
	 */
	@Override
	public int getAnimationStyle() {
		return mPopup.getAnimationStyle();
	}

	/**
	 * @return The background drawable for the popup window.
	 */
	@Override
	public Drawable getBackground() {
		return mPopup.getBackground();
	}

	/**
	 * @return The height of the popup window in pixels.
	 */
	@Override
	public int getHeight() {
		return mDropDownHeight;
	}

	/**
	 * @return The horizontal offset of the popup from its anchor in pixels.
	 */
	@Override
	public int getHorizontalOffset() {
		return mDropDownHorizontalOffset;
	}

	/**
	 * Return the current value in {@link #setInputMethodMode(int)}.
	 * 
	 * @see #setInputMethodMode(int)
	 */
	@Override
	public int getInputMethodMode() {
		return mPopup.getInputMethodMode();
	}

	/**
	 * @return The {@link ListView} displayed within the popup window. Only
	 *         valid when {@link #isShowing()} == {@code true}.
	 */
	@Override
	public ListView getListView() {
		return mDropDownList;
	}

	/**
	 * @return Where the optional prompt view should appear.
	 * 
	 * @see #POSITION_PROMPT_ABOVE
	 * @see #POSITION_PROMPT_BELOW
	 */
	@Override
	public int getPromptPosition() {
		return mPromptPosition;
	}

	/**
	 * @return The currently selected item or null if the popup is not showing.
	 */
	@Override
	public Object getSelectedItem() {
		if (!isShowing()) return null;
		return mDropDownList.getSelectedItem();
	}

	/**
	 * @return The ID of the currently selected item or
	 *         {@link ListView#INVALID_ROW_ID} if {@link #isShowing()} ==
	 *         {@code false}.
	 * 
	 * @see ListView#getSelectedItemId()
	 */
	@Override
	public long getSelectedItemId() {
		if (!isShowing()) return ListView.INVALID_ROW_ID;
		return mDropDownList.getSelectedItemId();
	}

	/**
	 * @return The position of the currently selected item or
	 *         {@link ListView#INVALID_POSITION} if {@link #isShowing()} ==
	 *         {@code false}.
	 * 
	 * @see ListView#getSelectedItemPosition()
	 */
	@Override
	public int getSelectedItemPosition() {
		if (!isShowing()) return ListView.INVALID_POSITION;
		return mDropDownList.getSelectedItemPosition();
	}

	/**
	 * @return The View for the currently selected item or null if
	 *         {@link #isShowing()} == {@code false}.
	 * 
	 * @see ListView#getSelectedView()
	 */
	@Override
	public View getSelectedView() {
		if (!isShowing()) return null;
		return mDropDownList.getSelectedView();
	}

	/**
	 * Returns the current value in {@link #setSoftInputMode(int)}.
	 * 
	 * @see #setSoftInputMode(int)
	 * @see android.view.WindowManager.LayoutParams#softInputMode
	 */
	@Override
	public int getSoftInputMode() {
		return mPopup.getSoftInputMode();
	}

	/**
	 * @return The vertical offset of the popup from its anchor in pixels.
	 */
	@Override
	public int getVerticalOffset() {
		if (!mDropDownVerticalOffsetSet) return 0;
		return mDropDownVerticalOffset;
	}

	/**
	 * @return The width of the popup window in pixels.
	 */
	@Override
	public int getWidth() {
		return mDropDownWidth;
	}

	/**
	 * @return Whether the drop-down is visible under special conditions.
	 * 
	 * @hide Only used by AutoCompleteTextView under special conditions.
	 */
	public boolean isDropDownAlwaysVisible() {
		return mDropDownAlwaysVisible;
	}

	/**
	 * @return {@code true} if this popup is configured to assume the user does
	 *         not need to interact with the IME while it is showing,
	 *         {@code false} otherwise.
	 */
	@Override
	public boolean isInputMethodNotNeeded() {
		return mPopup.getInputMethodMode() == INPUT_METHOD_NOT_NEEDED;
	}

	/**
	 * Returns whether the popup window will be modal when shown.
	 * 
	 * @return {@code true} if the popup window will be modal, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isModal() {
		return mModal;
	}

	/**
	 * @return {@code true} if the popup is currently showing, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isShowing() {
		return mPopup.isShowing();
	}

	/**
	 * Filter key down events. By forwarding key down events to this function,
	 * views using non-modal ListPopupWindow can have it handle key selection of
	 * items.
	 * 
	 * @param keyCode keyCode param passed to the host view's onKeyDown
	 * @param event event param passed to the host view's onKeyDown
	 * @return true if the event was handled, false if it was ignored.
	 * 
	 * @see #setModal(boolean)
	 */
	public boolean onKeyDown(final int keyCode, final KeyEvent event) {
		// when the drop down is shown, we drive it directly
		if (isShowing()) {
			// the key events are forwarded to the list in the drop down view
			// note that ListView handles space but we don't want that to happen
			// also if selection is not currently in the drop down, then don't
			// let center or enter presses go there since that would cause it
			// to select one of its items
			if (keyCode != KeyEvent.KEYCODE_SPACE
					&& (mDropDownList.getSelectedItemPosition() >= 0 || keyCode != KeyEvent.KEYCODE_ENTER
							&& keyCode != KeyEvent.KEYCODE_DPAD_CENTER)) {
				final int curIndex = mDropDownList.getSelectedItemPosition();
				boolean consumed;

				final boolean below = !mPopup.isAboveAnchor();

				final ListAdapter adapter = mAdapter;

				int firstItem = Integer.MAX_VALUE;
				int lastItem = Integer.MIN_VALUE;

				if (adapter != null) {
					firstItem = 0;
					lastItem = adapter.getCount() - 1;
					// firstItem = allEnabled ? 0 :
					// mDropDownList.lookForSelectablePosition(0, true);
					// lastItem = allEnabled ? adapter.getCount() - 1 :
					// mDropDownList.lookForSelectablePosition(adapter.getCount()
					// - 1, false);
				}

				if (below && keyCode == KeyEvent.KEYCODE_DPAD_UP && curIndex <= firstItem || !below
						&& keyCode == KeyEvent.KEYCODE_DPAD_DOWN && curIndex >= lastItem) {
					// When the selection is at the top, we block the key
					// event to prevent focus from moving.
					clearListSelection();
					mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
					show();
					return true;
				} else {
					// WARNING: Please read the comment where
					// mListSelectionHidden
					// is declared
					mDropDownList.mListSelectionHidden = false;
				}

				consumed = mDropDownList.onKeyDown(keyCode, event);
				if (DEBUG) {
					Log.v(TAG, "Key down: code=" + keyCode + " list consumed=" + consumed);
				}

				if (consumed) {
					// If it handled the key event, then the user is
					// navigating in the list, so we should put it in front.
					mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
					// Here's a little trick we need to do to make sure that
					// the list view is actually showing its focus indicator,
					// by ensuring it has focus and getting its window out
					// of touch mode.
					mDropDownList.requestFocusFromTouch();
					show();

					switch (keyCode) {
					// avoid passing the focus from the text view to the
					// next component
						case KeyEvent.KEYCODE_ENTER:
						case KeyEvent.KEYCODE_DPAD_CENTER:
						case KeyEvent.KEYCODE_DPAD_DOWN:
						case KeyEvent.KEYCODE_DPAD_UP:
							return true;
					}
				} else {
					if (below && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
						// when the selection is at the bottom, we block the
						// event to avoid going to the next focusable widget
						if (curIndex == lastItem) return true;
					} else if (!below && keyCode == KeyEvent.KEYCODE_DPAD_UP && curIndex == firstItem) return true;
				}
			}
		}

		return false;
	}

	/**
	 * Filter pre-IME key events. By forwarding
	 * {@link View#onKeyPreIme(int, KeyEvent)} events to this function, views
	 * using ListPopupWindow can have it dismiss the popup when the back key is
	 * pressed.
	 * 
	 * @param keyCode keyCode param passed to the host view's onKeyPreIme
	 * @param event event param passed to the host view's onKeyPreIme
	 * @return true if the event was handled, false if it was ignored.
	 * 
	 * @see #setModal(boolean)
	 */
	public boolean onKeyPreIme(final int keyCode, final KeyEvent event) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR)
			return OnKeyPreImeAccessor.onKeyPreIme(this, keyCode, event);
		return false;
	}

	/**
	 * Filter key down events. By forwarding key up events to this function,
	 * views using non-modal ListPopupWindow can have it handle key selection of
	 * items.
	 * 
	 * @param keyCode keyCode param passed to the host view's onKeyUp
	 * @param event event param passed to the host view's onKeyUp
	 * @return true if the event was handled, false if it was ignored.
	 * 
	 * @see #setModal(boolean)
	 */
	public boolean onKeyUp(final int keyCode, final KeyEvent event) {
		if (isShowing() && mDropDownList.getSelectedItemPosition() >= 0) {
			final boolean consumed = mDropDownList.onKeyUp(keyCode, event);
			if (consumed) {
				switch (keyCode) {
				// if the list accepts the key events and the key event
				// was a click, the text view gets the selected item
				// from the drop down as its content
					case KeyEvent.KEYCODE_ENTER:
					case KeyEvent.KEYCODE_DPAD_CENTER:
						dismiss();
						break;
				}
			}
			return consumed;
		}
		return false;
	}

	/**
	 * Perform an item click operation on the specified list adapter position.
	 * 
	 * @param position Adapter position for performing the click
	 * @return true if the click action could be performed, false if not. (e.g.
	 *         if the popup was not showing, this method would return false.)
	 */
	@Override
	public boolean performItemClick(final int position) {
		if (isShowing()) {
			if (mItemClickListener != null) {
				final DropDownListView list = mDropDownList;
				final View child = list.getChildAt(position - list.getFirstVisiblePosition());
				final ListAdapter adapter = list.getAdapter();
				mItemClickListener.onItemClick(list, child, position, adapter.getItemId(position));
			}
			return true;
		}
		return false;
	}

	/**
	 * Post a {@link #show()} call to the UI thread.
	 */
	@Override
	public void postShow() {
		mHandler.post(mShowDropDownRunnable);
	}

	/**
	 * Sets the adapter that provides the data and the views to represent the
	 * data in this popup window.
	 * 
	 * @param adapter The adapter to use to create this window's content.
	 */
	@Override
	public void setAdapter(final ListAdapter adapter) {
		if (mObserver == null) {
			mObserver = new PopupDataSetObserver();
		} else if (mAdapter != null) {
			mAdapter.unregisterDataSetObserver(mObserver);
		}
		mAdapter = adapter;
		if (mAdapter != null) {
			adapter.registerDataSetObserver(mObserver);
		}

		if (mDropDownList != null) {
			mDropDownList.setAdapter(mAdapter);
		}
	}

	/**
	 * Sets the popup's anchor view. This popup will always be positioned
	 * relative to the anchor view when shown.
	 * 
	 * @param anchor The view to use as an anchor.
	 */
	@Override
	public void setAnchorView(final View anchor) {
		mDropDownAnchorView = anchor;
	}

	/**
	 * Set an animation style to use when the popup window is shown or
	 * dismissed.
	 * 
	 * @param animationStyle Animation style to use.
	 */
	@Override
	public void setAnimationStyle(final int animationStyle) {
		mPopup.setAnimationStyle(animationStyle);
	}

	/**
	 * Sets a drawable to be the background for the popup window.
	 * 
	 * @param d A drawable to set as the background.
	 */
	@Override
	public void setBackgroundDrawable(final Drawable d) {
		mPopup.setBackgroundDrawable(d);
	}

	/**
	 * Sets the width of the popup window by the size of its content. The final
	 * width may be larger to accommodate styled window dressing.
	 * 
	 * @param width Desired width of content in pixels.
	 */
	@Override
	public void setContentWidth(final int width) {
		final Drawable popupBackground = mPopup.getBackground();
		if (popupBackground != null) {
			popupBackground.getPadding(mTempRect);
			mDropDownWidth = mTempRect.left + mTempRect.right + width;
		} else {
			setWidth(width);
		}
	}

	/**
	 * Sets whether the drop-down should remain visible under certain
	 * conditions.
	 * 
	 * The drop-down will occupy the entire screen below {@link #getAnchorView}
	 * regardless of the size or content of the list. {@link #getBackground()}
	 * will fill any space that is not used by the list.
	 * 
	 * @param dropDownAlwaysVisible Whether to keep the drop-down visible.
	 * 
	 * @hide Only used by AutoCompleteTextView under special conditions.
	 */
	public void setDropDownAlwaysVisible(final boolean dropDownAlwaysVisible) {
		mDropDownAlwaysVisible = dropDownAlwaysVisible;
	}

	/**
	 * Forces outside touches to be ignored. Normally if
	 * {@link #isDropDownAlwaysVisible()} is false, we allow outside touch to
	 * dismiss the dropdown. If this is set to true, then we ignore outside
	 * touch even when the drop down is not set to always visible.
	 * 
	 * @hide Used only by AutoCompleteTextView to handle some internal special
	 *       cases.
	 */
	public void setForceIgnoreOutsideTouch(final boolean forceIgnoreOutsideTouch) {
		mForceIgnoreOutsideTouch = forceIgnoreOutsideTouch;
	}

	/**
	 * Sets the height of the popup window in pixels. Can also be
	 * {@link #MATCH_PARENT}.
	 * 
	 * @param height Height of the popup window.
	 */
	@Override
	public void setHeight(final int height) {
		mDropDownHeight = height;
	}

	/**
	 * Set the horizontal offset of this popup from its anchor view in pixels.
	 * 
	 * @param offset The horizontal offset of the popup from its anchor.
	 */
	@Override
	public void setHorizontalOffset(final int offset) {
		mDropDownHorizontalOffset = offset;
	}

	/**
	 * Control how the popup operates with an input method: one of
	 * {@link #INPUT_METHOD_FROM_FOCUSABLE}, {@link #INPUT_METHOD_NEEDED}, or
	 * {@link #INPUT_METHOD_NOT_NEEDED}.
	 * 
	 * <p>
	 * If the popup is showing, calling this method will take effect only the
	 * next time the popup is shown or through a manual call to the
	 * {@link #show()} method.
	 * </p>
	 * 
	 * @see #getInputMethodMode()
	 * @see #show()
	 */
	@Override
	public void setInputMethodMode(final int mode) {
		mPopup.setInputMethodMode(mode);
	}

	/**
	 * Sets a drawable to use as the list item selector.
	 * 
	 * @param selector List selector drawable to use in the popup.
	 */
	@Override
	public void setListSelector(final Drawable selector) {
		mDropDownListHighlight = selector;
	}

	/**
	 * Set whether this window should be modal when shown.
	 * 
	 * <p>
	 * If a popup window is modal, it will receive all touch and key input. If
	 * the user touches outside the popup window's content area the popup window
	 * will be dismissed.
	 * 
	 * @param modal {@code true} if the popup window should be modal,
	 *            {@code false} otherwise.
	 */
	@Override
	public void setModal(final boolean modal) {
		mModal = true;
		mPopup.setFocusable(modal);
	}

	/**
	 * Set a listener to receive a callback when the popup is dismissed.
	 * 
	 * @param listener Listener that will be notified when the popup is
	 *            dismissed.
	 */
	@Override
	public void setOnDismissListener(final PopupWindow.OnDismissListener listener) {
		mPopup.setOnDismissListener(listener);
	}

	/**
	 * Sets a listener to receive events when a list item is clicked.
	 * 
	 * @param clickListener Listener to register
	 * 
	 * @see ListView#setOnItemClickListener(android.widget.AdapterView.OnItemClickListener)
	 */
	@Override
	public void setOnItemClickListener(final AdapterView.OnItemClickListener clickListener) {
		mItemClickListener = clickListener;
	}

	/**
	 * Sets a listener to receive events when a list item is selected.
	 * 
	 * @param selectedListener Listener to register.
	 * 
	 * @see ListView#setOnItemSelectedListener(android.widget.AdapterView.OnItemSelectedListener)
	 */
	@Override
	public void setOnItemSelectedListener(final AdapterView.OnItemSelectedListener selectedListener) {
		mItemSelectedListener = selectedListener;
	}

	/**
	 * Set where the optional prompt view should appear. The default is
	 * {@link #POSITION_PROMPT_ABOVE}.
	 * 
	 * @param position A position constant declaring where the prompt should be
	 *            displayed.
	 * 
	 * @see #POSITION_PROMPT_ABOVE
	 * @see #POSITION_PROMPT_BELOW
	 */
	@Override
	public void setPromptPosition(final int position) {
		mPromptPosition = position;
	}

	/**
	 * Set a view to act as a user prompt for this popup window. Where the
	 * prompt view will appear is controlled by {@link #setPromptPosition(int)}.
	 * 
	 * @param prompt View to use as an informational prompt.
	 */
	@Override
	public void setPromptView(final View prompt) {
		final boolean showing = isShowing();
		if (showing) {
			removePromptView();
		}
		mPromptView = prompt;
		if (showing) {
			show();
		}
	}

	/**
	 * Set the selected position of the list. Only valid when
	 * {@link #isShowing()} == {@code true}.
	 * 
	 * @param position List position to set as selected.
	 */
	@Override
	public void setSelection(final int position) {
		final DropDownListView list = mDropDownList;
		if (isShowing() && list != null) {
			list.mListSelectionHidden = false;
			list.setSelection(position);
			if (list.getChoiceMode() != ListView.CHOICE_MODE_NONE) {
				list.setItemChecked(position, true);
			}
		}
	}

	/**
	 * Sets the operating mode for the soft input area.
	 * 
	 * @param mode The desired mode, see
	 *            {@link android.view.WindowManager.LayoutParams#softInputMode}
	 *            for the full list
	 * 
	 * @see android.view.WindowManager.LayoutParams#softInputMode
	 * @see #getSoftInputMode()
	 */
	@Override
	public void setSoftInputMode(final int mode) {
		mPopup.setSoftInputMode(mode);
	}

	/**
	 * Set the vertical offset of this popup from its anchor view in pixels.
	 * 
	 * @param offset The vertical offset of the popup from its anchor.
	 */
	@Override
	public void setVerticalOffset(final int offset) {
		mDropDownVerticalOffset = offset;
		mDropDownVerticalOffsetSet = true;
	}

	/**
	 * Sets the width of the popup window in pixels. Can also be
	 * {@link #MATCH_PARENT} or {@link #WRAP_CONTENT}.
	 * 
	 * @param width Width of the popup window.
	 */
	@Override
	public void setWidth(final int width) {
		mDropDownWidth = width;
	}

	/**
	 * Show the popup list. If the list is already showing, this method will
	 * recalculate the popup's size and position.
	 */
	@Override
	public void show() {
		final int height = buildDropDown();

		int widthSpec = 0;
		int heightSpec = 0;

		final boolean noInputMethod = isInputMethodNotNeeded();
		// mPopup.setAllowScrollingAnchorParent(!noInputMethod);

		if (mPopup.isShowing()) {
			if (mDropDownWidth == ViewGroup.LayoutParams.MATCH_PARENT) {
				// The call to PopupWindow's update method below can accept -1
				// for any
				// value you do not want to update.
				widthSpec = -1;
			} else if (mDropDownWidth == ViewGroup.LayoutParams.WRAP_CONTENT) {
				widthSpec = getAnchorView().getWidth();
			} else {
				widthSpec = mDropDownWidth;
			}

			if (mDropDownHeight == ViewGroup.LayoutParams.MATCH_PARENT) {
				// The call to PopupWindow's update method below can accept -1
				// for any
				// value you do not want to update.
				heightSpec = noInputMethod ? height : ViewGroup.LayoutParams.MATCH_PARENT;
				if (noInputMethod) {
					mPopup.setWindowLayoutMode(
							mDropDownWidth == ViewGroup.LayoutParams.MATCH_PARENT ? ViewGroup.LayoutParams.MATCH_PARENT
									: 0, 0);
				} else {
					mPopup.setWindowLayoutMode(
							mDropDownWidth == ViewGroup.LayoutParams.MATCH_PARENT ? ViewGroup.LayoutParams.MATCH_PARENT
									: 0, ViewGroup.LayoutParams.MATCH_PARENT);
				}
			} else if (mDropDownHeight == ViewGroup.LayoutParams.WRAP_CONTENT) {
				heightSpec = height;
			} else {
				heightSpec = mDropDownHeight;
			}

			mPopup.setOutsideTouchable(!mForceIgnoreOutsideTouch && !mDropDownAlwaysVisible);

			mPopup.update(getAnchorView(), mDropDownHorizontalOffset, mDropDownVerticalOffset, widthSpec, heightSpec);
		} else {
			if (mDropDownWidth == ViewGroup.LayoutParams.MATCH_PARENT) {
				widthSpec = ViewGroup.LayoutParams.MATCH_PARENT;
			} else {
				if (mDropDownWidth == ViewGroup.LayoutParams.WRAP_CONTENT) {
					mPopup.setWidth(getAnchorView().getWidth());
				} else {
					mPopup.setWidth(mDropDownWidth);
				}
			}

			if (mDropDownHeight == ViewGroup.LayoutParams.MATCH_PARENT) {
				heightSpec = ViewGroup.LayoutParams.MATCH_PARENT;
			} else {
				if (mDropDownHeight == ViewGroup.LayoutParams.WRAP_CONTENT) {
					mPopup.setHeight(height);
				} else {
					mPopup.setHeight(mDropDownHeight);
				}
			}

			mPopup.setWindowLayoutMode(widthSpec, heightSpec);
			// mPopup.setClipToScreenEnabled(true); might be important aap

			// use outside touchable to dismiss drop down when touching outside
			// of it, so
			// only set this if the dropdown is not always visible
			mPopup.setOutsideTouchable(!mForceIgnoreOutsideTouch && !mDropDownAlwaysVisible);
			mPopup.setTouchInterceptor(mTouchInterceptor);
			mPopup.showAsDropDown(getAnchorView(), mDropDownHorizontalOffset, mDropDownVerticalOffset);
			mDropDownList.setSelection(ListView.INVALID_POSITION);

			if (!mModal || mDropDownList.isInTouchMode()) {
				clearListSelection();
			}
			if (!mModal) {
				mHandler.post(mHideSelector);
			}
		}
	}

	/**
	 * <p>
	 * Builds the popup window's content and returns the height the popup should
	 * have. Returns -1 when the content already exists.
	 * </p>
	 * 
	 * @return the content's height or -1 if content already exists
	 */
	private int buildDropDown() {
		ViewGroup dropDownView;
		int otherHeights = 0;

		if (mDropDownList == null) {
			final Context context = mContext;

			/**
			 * This Runnable exists for the sole purpose of checking if the view
			 * layout has got completed and if so call showDropDown to display
			 * the drop down. This is used to show the drop down as soon as
			 * possible after user opens up the search dialog, without waiting
			 * for the normal UI pipeline to do it's job which is slower than
			 * this method.
			 */
			mShowDropDownRunnable = new Runnable() {
				@Override
				public void run() {
					// View layout should be all done before displaying the drop
					// down.
					final View view = getAnchorView();
					if (view != null && view.getWindowToken() != null) {
						show();
					}
				}
			};

			mDropDownList = new DropDownListView(context, !mModal);
			if (mDropDownListHighlight != null) {
				mDropDownList.setSelector(mDropDownListHighlight);
			}
			mDropDownList.setAdapter(mAdapter);
			mDropDownList.setOnItemClickListener(mItemClickListener);
			mDropDownList.setFocusable(true);
			mDropDownList.setFocusableInTouchMode(true);
			mDropDownList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(final AdapterView<?> parent, final View view, final int position,
						final long id) {

					if (position != -1) {
						final DropDownListView dropDownList = mDropDownList;

						if (dropDownList != null) {
							dropDownList.mListSelectionHidden = false;
						}
					}
				}

				@Override
				public void onNothingSelected(final AdapterView<?> parent) {
				}
			});
			// mDropDownList.setOnScrollListener(mScrollListener);

			if (mItemSelectedListener != null) {
				mDropDownList.setOnItemSelectedListener(mItemSelectedListener);
			}

			dropDownView = mDropDownList;

			final View hintView = mPromptView;
			if (hintView != null) {
				// if an hint has been specified, we accomodate more space for
				// it and
				// add a text view in the drop down menu, at the bottom of the
				// list
				final LinearLayout hintContainer = new LinearLayout(context);
				hintContainer.setOrientation(LinearLayout.VERTICAL);

				LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);

				switch (mPromptPosition) {
					case POSITION_PROMPT_BELOW:
						hintContainer.addView(dropDownView, hintParams);
						hintContainer.addView(hintView);
						break;

					case POSITION_PROMPT_ABOVE:
						hintContainer.addView(hintView);
						hintContainer.addView(dropDownView, hintParams);
						break;

					default:
						Log.e(TAG, "Invalid hint position " + mPromptPosition);
						break;
				}

				// measure the hint's height to find how much more vertical
				// space
				// we need to add to the drop down's height
				final int widthSpec = MeasureSpec.makeMeasureSpec(mDropDownWidth, MeasureSpec.AT_MOST);
				final int heightSpec = MeasureSpec.UNSPECIFIED;
				hintView.measure(widthSpec, heightSpec);

				hintParams = (LinearLayout.LayoutParams) hintView.getLayoutParams();
				otherHeights = hintView.getMeasuredHeight() + hintParams.topMargin + hintParams.bottomMargin;

				dropDownView = hintContainer;
			}

			mPopup.setContentView(dropDownView);
		} else {
			dropDownView = (ViewGroup) mPopup.getContentView();
			final View view = mPromptView;
			if (view != null) {
				final LinearLayout.LayoutParams hintParams = (LinearLayout.LayoutParams) view.getLayoutParams();
				otherHeights = view.getMeasuredHeight() + hintParams.topMargin + hintParams.bottomMargin;
			}
		}

		// getMaxAvailableHeight() subtracts the padding, so we put it back
		// to get the available height for the whole window
		int padding = 0;
		final Drawable background = mPopup.getBackground();
		if (background != null) {
			background.getPadding(mTempRect);
			padding = mTempRect.top + mTempRect.bottom;

			// If we don't have an explicit vertical offset, determine one from
			// the window
			// background so that content will line up.
			if (!mDropDownVerticalOffsetSet) {
				mDropDownVerticalOffset = -mTempRect.top;
			}
		}

		final int maxHeight = mPopup.getMaxAvailableHeight(getAnchorView(), mDropDownVerticalOffset);

		if (mDropDownAlwaysVisible || mDropDownHeight == ViewGroup.LayoutParams.MATCH_PARENT)
			return maxHeight + padding;

		final int listContent = measureHeightOfChildren(MeasureSpec.UNSPECIFIED, 0, NO_POSITION, maxHeight
				- otherHeights, -1);
		// add padding only if the list has items in it, that way we don't show
		// the popup if it is not needed
		if (listContent > 0) {
			otherHeights += padding;
		}

		return listContent + otherHeights;
	}

	private void measureScrapChild(final View child, final int position, final int widthMeasureSpec, final Rect padding) {
		LayoutParams p = (LayoutParams) child.getLayoutParams();
		if (p == null) {
			p = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
			child.setLayoutParams(p);
		}
		// p.viewType = mAdapter.getItemViewType(position);
		// p.forceAdd = true;

		final int childWidthSpec = ViewGroup.getChildMeasureSpec(widthMeasureSpec, padding.left + padding.right,
				p.width);
		final int lpHeight = p.height;
		int childHeightSpec;
		if (lpHeight > 0) {
			childHeightSpec = MeasureSpec.makeMeasureSpec(lpHeight, MeasureSpec.EXACTLY);
		} else {
			childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
		}
		child.measure(childWidthSpec, childHeightSpec);
	}

	private void removePromptView() {
		if (mPromptView != null) {
			final ViewParent parent = mPromptView.getParent();
			if (parent instanceof ViewGroup) {
				final ViewGroup group = (ViewGroup) parent;
				group.removeView(mPromptView);
			}
		}
	}

	/**
	 * This method borrowed from ICS ListView.
	 * 
	 * Measures the height of the given range of children (inclusive) and
	 * returns the height with this ListView's padding and divider heights
	 * included. If maxHeight is provided, the measuring will stop when the
	 * current height reaches maxHeight.
	 * 
	 * @param widthMeasureSpec The width measure spec to be given to a child's
	 *            {@link View#measure(int, int)}.
	 * @param startPosition The position of the first child to be shown.
	 * @param endPosition The (inclusive) position of the last child to be
	 *            shown. Specify {@link #NO_POSITION} if the last child should
	 *            be the last available child from the adapter.
	 * @param maxHeight The maximum height that will be returned (if all the
	 *            children don't fit in this value, this value will be
	 *            returned).
	 * @param disallowPartialChildPosition In general, whether the returned
	 *            height should only contain entire children. This is more
	 *            powerful--it is the first inclusive position at which partial
	 *            children will not be allowed. Example: it looks nice to have
	 *            at least 3 completely visible children, and in portrait this
	 *            will most likely fit; but in landscape there could be times
	 *            when even 2 children can not be completely shown, so a value
	 *            of 2 (remember, inclusive) would be good (assuming
	 *            startPosition is 0).
	 * @return The height of this ListView with the given children.
	 */
	final int measureHeightOfChildren(final int widthMeasureSpec, final int startPosition, int endPosition,
			final int maxHeight, final int disallowPartialChildPosition) {

		final Rect padding = new Rect();
		// not sure if this is the right padding to use...
		mPopup.getBackground().getPadding(padding);

		final ListAdapter adapter = mAdapter;
		if (adapter == null) return padding.top + padding.bottom;

		// Include the padding of the list
		int returnedHeight = padding.top + padding.bottom;
		final int dividerHeight = 0; // ((mDividerHeight > 0) && mDivider !=
										// null) ? mDividerHeight : 0;
		// The previous height value that was less than maxHeight and contained
		// no partial children
		int prevHeightWithoutPartialChild = 0;
		int i;
		View child;

		// mItemCount - 1 since endPosition parameter is inclusive
		endPosition = endPosition == NO_POSITION ? adapter.getCount() - 1 : endPosition;
		// final AbsListView.RecycleBin recycleBin = mRecycler;
		// final boolean recyle = recycleOnMeasure();
		// final boolean[] isScrap = mIsScrap;

		for (i = startPosition; i <= endPosition; ++i) {
			// child = mDropDownList.obtainView(i);

			child = mAdapter.getView(i, null, mDropDownList);

			measureScrapChild(child, i, widthMeasureSpec, padding);

			if (i > 0) {
				// Count the divider for all but one child
				returnedHeight += dividerHeight;
			}

			// Recycle the view before we possibly return from the method
			// if (recyle && recycleBin.shouldRecycleViewType(
			// ((LayoutParams) child.getLayoutParams()).viewType)) {
			// recycleBin.addScrapView(child, -1);
			// }

			returnedHeight += child.getMeasuredHeight();

			if (returnedHeight >= maxHeight) // We went over, figure out which
												// height to return. If
				// returnedHeight > maxHeight,
				// then the i'th position did not fit completely.
				return disallowPartialChildPosition >= 0 // Disallowing is
															// enabled (> -1)
						&& i > disallowPartialChildPosition // We've past the
															// min pos
						&& prevHeightWithoutPartialChild > 0 // We have a prev
																// height
						&& returnedHeight != maxHeight // i'th child did not
														// fit completely
				? prevHeightWithoutPartialChild : maxHeight;

			if (disallowPartialChildPosition >= 0 && i >= disallowPartialChildPosition) {
				prevHeightWithoutPartialChild = returnedHeight;
			}
		}

		// At this point, we went through the range of children, and they each
		// completely fit, so return the returnedHeight
		return returnedHeight;
	}

	/**
	 * The maximum number of list items that can be visible and still have the
	 * list expand when touched.
	 * 
	 * @param max Max number of items that can be visible and still allow the
	 *            list to expand.
	 */
	void setListItemExpandMax(final int max) {
		mListItemExpandMaximum = max;
	}

	/**
	 * <p>
	 * Wrapper class for a ListView. This wrapper can hijack the focus to make
	 * sure the list uses the appropriate drawables and states when displayed on
	 * screen within a drop down. The focus is never actually passed to the drop
	 * down in this mode; the list only looks focused.
	 * </p>
	 */
	private static class DropDownListView extends ListView {
		/*
		 * WARNING: This is a workaround for a touch mode issue.
		 * 
		 * Touch mode is propagated lazily to windows. This causes problems in
		 * the following scenario: - Type something in the AutoCompleteTextView
		 * and get some results - Move down with the d-pad to select an item in
		 * the list - Move up with the d-pad until the selection disappears -
		 * Type more text in the AutoCompleteTextView *using the soft keyboard*
		 * and get new results; you are now in touch mode - The selection comes
		 * back on the first item in the list, even though the list is supposed
		 * to be in touch mode
		 * 
		 * Using the soft keyboard triggers the touch mode change but that
		 * change is propagated to our window only after the first list layout,
		 * therefore after the list attempts to resurrect the selection.
		 * 
		 * The trick to work around this issue is to pretend the list is in
		 * touch mode when we know that the selection should not appear, that is
		 * when we know the user moved the selection away from the list.
		 * 
		 * This boolean is set to true whenever we explicitly hide the list's
		 * selection and reset to false whenever we know the user moved the
		 * selection back to the list.
		 * 
		 * When this boolean is true, isInTouchMode() returns true, otherwise it
		 * returns super.isInTouchMode().
		 */
		private boolean mListSelectionHidden;

		/**
		 * True if this wrapper should fake focus.
		 */
		private final boolean mHijackFocus;

		/**
		 * <p>
		 * Creates a new list view wrapper.
		 * </p>
		 * 
		 * @param context this view's context
		 */
		public DropDownListView(final Context context, final boolean hijackFocus) {
			super(context, null, android.R.attr.dropDownListViewStyle);
			mHijackFocus = hijackFocus;
			// setCacheColorHint(0);
			setPadding(0, 0, 0, 0);
		}

		/**
		 * <p>
		 * Returns the focus state in the drop down.
		 * </p>
		 * 
		 * @return true always if hijacking focus
		 */
		@Override
		public boolean hasFocus() {
			return mHijackFocus || super.hasFocus();
		}

		/**
		 * <p>
		 * Returns the focus state in the drop down.
		 * </p>
		 * 
		 * @return true always if hijacking focus
		 */
		@Override
		public boolean hasWindowFocus() {
			return mHijackFocus || super.hasWindowFocus();
		}

		/**
		 * <p>
		 * Returns the focus state in the drop down.
		 * </p>
		 * 
		 * @return true always if hijacking focus
		 */
		@Override
		public boolean isFocused() {
			return mHijackFocus || super.isFocused();
		}

		@Override
		public boolean isInTouchMode() {
			// WARNING: Please read the comment where mListSelectionHidden is
			// declared
			return mHijackFocus && mListSelectionHidden || super.isInTouchMode();
		}

	}

	private class ListSelectorHider implements Runnable {
		@Override
		public void run() {
			clearListSelection();
		}
	}

	private class PopupDataSetObserver extends DataSetObserver {
		@Override
		public void onChanged() {
			if (isShowing()) {
				// Resize the popup to fit new content
				show();
			}
		}

		@Override
		public void onInvalidated() {
			dismiss();
		}
	}

	private class PopupTouchInterceptor implements OnTouchListener {
		@Override
		public boolean onTouch(final View v, final MotionEvent event) {
			final int action = event.getAction();
			final int x = (int) event.getX();
			final int y = (int) event.getY();

			if (action == MotionEvent.ACTION_DOWN && mPopup != null && mPopup.isShowing() && x >= 0
					&& x < mPopup.getWidth() && y >= 0 && y < mPopup.getHeight()) {
				mHandler.postDelayed(mResizePopupRunnable, EXPAND_LIST_TIMEOUT);
			} else if (action == MotionEvent.ACTION_UP) {
				mHandler.removeCallbacks(mResizePopupRunnable);
			}
			return false;
		}
	}

	private class ResizePopupRunnable implements Runnable {
		@Override
		public void run() {
			if (mDropDownList != null && mDropDownList.getCount() > mDropDownList.getChildCount()
					&& mDropDownList.getChildCount() <= mListItemExpandMaximum) {
				mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
				show();
			}
		}
	}

	@SuppressLint("NewApi")
	static class OnKeyPreImeAccessor {
		public static boolean onKeyPreIme(final ListPopupWindowCompat window, final int keyCode, final KeyEvent event) {
			if (keyCode == KeyEvent.KEYCODE_BACK && window.isShowing()) {
				// special case for the back key, we do not even try to send it
				// to the drop down list but instead, consume it immediately
				final View anchorView = window.mDropDownAnchorView;
				if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
					final KeyEvent.DispatcherState state = anchorView.getKeyDispatcherState();
					if (state != null) {
						state.startTracking(event, window);
					}
					return true;
				} else if (event.getAction() == KeyEvent.ACTION_UP) {
					final KeyEvent.DispatcherState state = anchorView.getKeyDispatcherState();
					if (state != null) {
						state.handleUpEvent(event);
					}
					if (event.isTracking() && !event.isCanceled()) {
						window.dismiss();
						return true;
					}
				}
			}
			return false;
		}
	}

}