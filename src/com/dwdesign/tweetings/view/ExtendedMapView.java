package com.dwdesign.tweetings.view;
 
import java.util.ArrayList;
import java.util.List;
 
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import com.dwdesign.tweetings.events.PanChangeListener;
import com.dwdesign.tweetings.events.ZoomChangeListener;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;

public class ExtendedMapView extends MapView {
 
	private int currentZoomLevel = -1;
	private GeoPoint currentCenter;
	private List<ZoomChangeListener> zoomEvents = new ArrayList<ZoomChangeListener>();
	private List<PanChangeListener> panEvents = new ArrayList<PanChangeListener>();
 
	public ExtendedMapView(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
	}
 
	public ExtendedMapView(final Context context, final String apiKey) {
		super(context, apiKey);
	}
 
	public ExtendedMapView(final Context context, final AttributeSet attrs) {
		super(context, attrs);
	}
 
	public int[][] getBounds() {
		GeoPoint center = getMapCenter();
		int latitudeSpan = getLatitudeSpan();
		int longtitudeSpan = getLongitudeSpan();
		int[][] bounds = new int[2][2];
 
		bounds[0][0] = center.getLatitudeE6() + (latitudeSpan / 2);
		bounds[0][1] = center.getLongitudeE6() + (longtitudeSpan / 2);
 
		bounds[1][0] = center.getLatitudeE6() - (latitudeSpan / 2);
		bounds[1][1] = center.getLongitudeE6() - (longtitudeSpan / 2);
		return bounds;
	}
 
	public boolean onTouchEvent(final MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_UP) {
            GeoPoint centerGeoPoint = this.getMapCenter();
            if (currentCenter == null || 
                    (currentCenter.getLatitudeE6() != centerGeoPoint.getLatitudeE6()) ||
                    (currentCenter.getLongitudeE6() != centerGeoPoint.getLongitudeE6()) ) {
            	firePanEvent(currentCenter, this.getMapCenter());
            }
            currentCenter = this.getMapCenter();
        }
        return super.onTouchEvent(ev);
    }
 
	@Override
	protected void dispatchDraw(final Canvas canvas) {
		super.dispatchDraw(canvas);
		if(getZoomLevel() != currentZoomLevel){
			fireZoomLevel(currentZoomLevel, getZoomLevel());
			currentZoomLevel = getZoomLevel();
		}
	}
 
	private void fireZoomLevel(final int old, final int current){
		for(ZoomChangeListener event : zoomEvents){
			event.onZoom(old, current);
		}
	}
 
	private void firePanEvent(final GeoPoint old, final GeoPoint current){
		for(PanChangeListener event : panEvents){
			event.onPan(old, current);
		}
	}
 
	public void addZoomChangeListener(final ZoomChangeListener listener){
		this.zoomEvents.add(listener);
	}
 
	public void addPanChangeListener(final PanChangeListener listener){
		this.panEvents.add(listener);
	}
 
 
}