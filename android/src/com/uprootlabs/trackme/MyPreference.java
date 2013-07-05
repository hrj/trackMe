package com.uprootlabs.trackme;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class MyPreference {

  SharedPreferences myPreferences;
  private final String USER_ID;
  private final String PASSKEY;
  private final String SERVER_LOCATION;
  private final String AUTO_UPDATE;
  private final String CAPTURE_FREQUENCY;
  private final String UPDATE_FREQUENCY;
  private final String SESSION_ID;
  private final String UPLOAD_ID;
  private final String NOT_SET = "empty";

  public MyPreference(Context context) {
    myPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    USER_ID = context.getResources().getString(R.string.key_userid);
    PASSKEY = context.getResources().getString(R.string.key_passkey);
    SERVER_LOCATION = context.getResources().getString(R.string.key_server_location);
    AUTO_UPDATE = context.getResources().getString(R.string.key_auto_update);
    CAPTURE_FREQUENCY = context.getResources().getString(R.string.key_capture_frequency);
    UPDATE_FREQUENCY = context.getResources().getString(R.string.key_update_frequency);
    SESSION_ID = context.getResources().getString(R.string.key_session_id);
    UPLOAD_ID = context.getResources().getString(R.string.key_upload_id);
  }

  public boolean userDetailsNotNull() {
    if (getUserID().equals(NOT_SET) || getPassKey().equals(NOT_SET) || getServerLocation().equals(NOT_SET)) {
      return false;
    } else {
      return true;
    }
  }

  public String getUserID() {
    return myPreferences.getString(USER_ID, NOT_SET);
  }

  public String getPassKey() {
    return myPreferences.getString(PASSKEY, NOT_SET);
  }

  public String getServerLocation() {
    return myPreferences.getString(SERVER_LOCATION, NOT_SET);
  }

  public String getSessionID() {
    return myPreferences.getString(SESSION_ID, NOT_SET);
  }

  public int getCaptureFrequency() {
    return Integer.parseInt(myPreferences.getString(CAPTURE_FREQUENCY, "10")) * TrackMeHelper.MILLISECONDS_PER_SECOND;
  }

  public int getUpdateFrequency() {
    return Integer.parseInt(myPreferences.getString(UPDATE_FREQUENCY, "15")) * TrackMeHelper.SECONDS_PER_MINUTE
        * TrackMeHelper.MILLISECONDS_PER_SECOND;
  }

  public boolean isAutoUpdateSet() {
    return myPreferences.getBoolean(AUTO_UPDATE, false);
  }

  public String getNewSessionID() {
    return getSessionID();
  }

  public int getNewUploadID() {
    return Integer.parseInt(myPreferences.getString(UPLOAD_ID, "1"));
  }

}
