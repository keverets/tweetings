package com.dwdesign.popupmenu;

import com.dwdesign.internal.menu.MenuAdapter;
import com.dwdesign.internal.menu.MenuImpl;
import com.dwdesign.popupmenu.ListPopupWindow.InstanceHelper;
import com.dwdesign.tweetings.R;

import android.content.Context;
import android.os.Build;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.PopupWindow.OnDismissListener;

public class PopupMenu implements OnDismissListener, OnItemClickListener, OnTouchListener {

	private OnMenuItemClickListener mItemClickListener;
	private OnDismissListener mDismissListener;

	private Menu mMenu;
	private final Context mContext;
	private final View mView;
	private final ListPopupWindow mWindow;

	private boolean mDidAction;

	private final MenuAdapter mAdapter;

	/**
	 * Constructor for default vertical layout
	 * 
	 * @param context Context
	 */
	public PopupMenu(final Context context, final View view) {
		mContext = context;
		mView = view;
		mAdapter = new MenuAdapter(context);
		mMenu = new MenuImpl(mContext, mAdapter);
		mWindow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ? InstanceHelper.getInstance(context)
				: new ListPopupWindowCompat(context);
		mWindow.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
		mWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
		mWindow.setAnchorView(mView);
		mWindow.setWidth(mContext.getResources().getDimensionPixelSize(R.dimen.popup_window_width));
		mWindow.setAdapter(mAdapter);
		mWindow.setOnItemClickListener(this);
		mWindow.setPromptPosition(ListPopupWindow.POSITION_PROMPT_BELOW);
		mWindow.setModal(true);
	}

	/**
	 * Dismiss the popup window.
	 */
	public void dismiss() {
		if (isPopupWindowShowing()) {
			mWindow.dismiss();
		}
	}

	public Menu getMenu() {
		return mMenu;
	}

	public MenuInflater getMenuInflater() {
		return new MenuInflater(mContext);
	}

	public void inflate(final int menuRes) {
		new MenuInflater(mContext).inflate(menuRes, mMenu);
	}

	@Override
	public void onDismiss() {
		if (!mDidAction && mDismissListener != null) {
			mDismissListener.onDismiss(this);
		}
	}

	@Override
	public void onItemClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		mDidAction = true;
		dismiss();
		final MenuItem item = mAdapter.getItem(position);
		if (item.hasSubMenu()) {
			showMenu(item.getSubMenu());
		} else {
			if (mItemClickListener != null) {
				mItemClickListener.onMenuItemClick(item);
			}
		}
	}

	@Override
	public boolean onTouch(final View v, final MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
			mWindow.dismiss();

			return true;
		}

		return false;
	}

	public void setMenu(final Menu menu) {
		mMenu = menu;
	}

	/**
	 * Set listener for window dismissed. This listener will only be fired if
	 * the quickaction dialog is dismissed by clicking outside the dialog or
	 * clicking on sticky item.
	 */
	public void setOnDismissListener(final PopupMenu.OnDismissListener listener) {
		mWindow.setOnDismissListener(listener != null ? this : null);

		mDismissListener = listener;
	}

	/**
	 * Set listener for action item clicked.
	 * 
	 * @param listener Listener
	 */
	public void setOnMenuItemClickListener(final OnMenuItemClickListener listener) {
		mItemClickListener = listener;
	}

	public void show() {
		if (isPopupWindowShowing()) {
			dismiss();
		}
		showMenu(getMenu());
	}

	private boolean isPopupWindowShowing() {
		if (mWindow == null) return false;
		return mWindow.isShowing();
	}

	private void showMenu(final Menu menu) {
		mAdapter.setMenu(menu);
		mWindow.show();
	}

	public static PopupMenu getInstance(final Context context, final View view) {
		return new PopupMenu(context, view);
	}

	/**
	 * Listener for window dismiss
	 * 
	 */
	public static interface OnDismissListener {
		public void onDismiss(PopupMenu PopupMenu);
	}

	/**
	 * Listener for item click
	 * 
	 */
	public static interface OnMenuItemClickListener {
		public boolean onMenuItemClick(MenuItem item);
	}

}
