package com.uprootlabs.trackme;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class MyPreference {

  SharedPreferences myPreferences;

  public MyPreference(Context context) {
    myPreferences = PreferenceManager.getDefaultSharedPreferences(context);
  }
  
 public boolean userDetailsNotNull() {
   return false;
 }
  
  public String getUserID() {
    return "";
  }

  public String getPassKey() {
    return "";
  }

  private String getSessionID() {
    return "";
  }

  public String getServerLocation() {
    return "";
  }

  public int getCaptureFrequency() {
    return 0;
  }

  public int getUpdateFrequency() {
    return 0;
  }

  public boolean isAutoUpdateSet() {
    return false;
  }
  
  public String getNewSessionID() {
    String sessionID = getSessionID();
    return sessionID; 
  }
  
  public String getNewUploadID() {
    return "";
  }

}
