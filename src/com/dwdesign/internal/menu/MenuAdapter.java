package com.dwdesign.internal.menu;

import java.util.List;

import com.dwdesign.tweetings.R;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

public final class MenuAdapter extends ArrayAdapter<MenuItem> {

	private Menu mMenu;

	public MenuAdapter(final Context context) {
		super(context, R.layout.menu_list_item, android.R.id.text1);
	}

	@Override
	public long getItemId(final int index) {
		return getItem(index).getItemId();
	}

	@Override
	public View getView(final int position, final View convertView, final ViewGroup parent) {
		final View view = super.getView(position, convertView, parent);
		final ImageView icon = (ImageView) view.findViewById(android.R.id.icon);
		final MenuItem item = getItem(position);
		icon.setImageDrawable(item.getIcon());
		return view;
	}

	public void setMenu(final Menu menu) {
		mMenu = menu;
		setMenuItems();
	}

	void setMenuItems() {
		clear();
		final List<MenuItem> items = mMenu == null ? null : ((MenuImpl) mMenu).getMenuItems();
		if (items == null) return;
		for (final MenuItem item : items) {
			if (item.isVisible()) {
				add(item);
			}
		}
	}

}
