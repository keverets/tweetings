package com.dwdesign.tweetings.model;

import static com.dwdesign.tweetings.util.Utils.bundleEquals;
import static com.dwdesign.tweetings.util.Utils.classEquals;
import static com.dwdesign.tweetings.util.Utils.objectEquals;
import android.os.Bundle;
import android.support.v4.app.Fragment;

public class TabSpec {

	public final String name;
	public Object icon;
	public final Class<? extends Fragment> cls;
	public final Bundle args;
	public final int position;

	public TabSpec(final String name, final Object icon, final Class<? extends Fragment> cls, final Bundle args, final int position) {
		if (cls == null) throw new IllegalArgumentException("Fragment cannot be null!");
		if (name == null && icon == null)
			throw new IllegalArgumentException("You must specify a name or icon for this tab!");
		this.name = name;
		this.icon = icon;
		this.cls = cls;
		this.args = args;
		this.position = position;

	}
	
	public void setIcon(Object icon) {
		this.icon = icon;
	}

	@Override
	public boolean equals(final Object o) {
		if (!(o instanceof TabSpec)) return false;
		final TabSpec spec = (TabSpec) o;
		return objectEquals(name, spec.name) && objectEquals(icon, spec.icon) && classEquals(cls, spec.cls)
				&& bundleEquals(args, spec.args) && position == spec.position;
	}

}