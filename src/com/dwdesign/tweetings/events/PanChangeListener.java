package com.dwdesign.tweetings.events;
 
import com.google.android.maps.GeoPoint;
 
public interface PanChangeListener {
	public void onPan(GeoPoint old, GeoPoint current);
}