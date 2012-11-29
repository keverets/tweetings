package com.dwdesign.tweetings.util;

import com.dwdesign.tweetings.Constants;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ConnectivityReceiver extends BroadcastReceiver implements Constants {
	
	@Override
    public void onReceive(Context context, Intent intent) {
		context.sendBroadcast(new Intent(BROADCAST_NETWORK_STATE_CHANGED));
    }
}
