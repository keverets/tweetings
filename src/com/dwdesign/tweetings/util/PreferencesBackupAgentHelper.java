package com.dwdesign.tweetings.util;

import android.app.backup.BackupAgentHelper; 
import android.app.backup.SharedPreferencesBackupHelper; 
import com.dwdesign.tweetings.Constants;

public class PreferencesBackupAgentHelper extends BackupAgentHelper implements Constants {
    // An arbitrary string used within the BackupAgentHelper implementation to
    // identify the SharedPreferenceBackupHelper's data. 
    static final String MY_PREFS_BACKUP_KEY = "prefs"; 

    // Simply allocate a helper and install it 
    @Override
	public
    void onCreate() { 
        SharedPreferencesBackupHelper helper = 
                new SharedPreferencesBackupHelper(this, SHARED_PREFERENCES_NAME, USER_COLOR_PREFERENCES_NAME);
        addHelper(MY_PREFS_BACKUP_KEY, helper); 
    } 
}