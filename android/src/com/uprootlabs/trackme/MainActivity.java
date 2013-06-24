package com.uprootlabs.trackme;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

  private static final String MAIN_TAG = "mainActivity";
  private static final int MILLISECONDS_PER_SECOND = 1000;
  private static final String NOT_SET = "Value not Set!";

  // private TextView valueLat;
  // private TextView valueLng;
  // private TextView valueAccuracy;
  // private TextView valueTimeStamp;
  private TextView valueCaptureFrequency;
  private TextView valueUpdateFrequency;

  private Button startStopButton;

  SharedPreferences myPreferences;
  SharedPreferences.Editor myPreferencesEditor;

  PendingIntent pi;
  AlarmManager alarmManager;

  public boolean captureLocations;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // valueLat = (TextView) findViewById(R.id.lat);
    // valueLng = (TextView) findViewById(R.id.lng);
    // valueAccuracy = (TextView) findViewById(R.id.accuracy);
    // valueTimeStamp = (TextView) findViewById(R.id.timeStamp);
    valueCaptureFrequency = (TextView) findViewById(R.id.valueCaptureFrequency);
    valueUpdateFrequency = (TextView) findViewById(R.id.valueUpdateFrequency);
    startStopButton = (Button) findViewById(R.id.startStop);

    myPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    myPreferencesEditor = myPreferences.edit();
  }

  @Override
  public void onStart() {
    super.onStart();
  }

  public void onPause() {
    super.onPause();
  }

  public void onResume() {
    super.onResume();
    captureLocations = myPreferences.getBoolean("captureLocations", false);
    Log.d(MAIN_TAG, " " + captureLocations);

    if (captureLocations)
      startStopButton.setText(R.string.stop_capturing);
    else
      startStopButton.setText(R.string.start_capturing);

    String captureFrequency = myPreferences.getString("captureFrequency", NOT_SET);
    String updateFrequency = myPreferences.getString("updateFrequency", NOT_SET);
    valueCaptureFrequency.setText(captureFrequency);
    valueUpdateFrequency.setText(updateFrequency);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.action_settings) {
      Intent settings = new Intent(this, MyPreferencesActivity.class);
      startActivity(settings);
    }

    // case R.id.upload_locations:
    // getActivity().startActivity(item.getIntent());
    // break;

    return true;
  }

  public void startStopCapturing(View v) {
    Log.d(MAIN_TAG, "Start/Stop");

    if (captureLocations)
      captureLocations = false;
    else
      captureLocations = true;

    myPreferencesEditor.putBoolean("captureLocations", captureLocations);
    myPreferencesEditor.commit();

    if (captureLocations) {
      startCapturingLocations();
      startStopButton.setText(R.string.stop_capturing);
    } else {
      stopCapturingLocations();
      startStopButton.setText(R.string.start_capturing);
    }

  }

  public void startCapturingLocations() {
    boolean autoUpdate = myPreferences.getBoolean("autoUpdate", false);
    if (autoUpdate) {
      int updateFrequency = Integer.parseInt(myPreferences.getString("updateFrequency", "0"));
      pi = PendingIntent.getService(this, 0, new Intent(this, UploadService.class), 0);
      alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
      long timeOrLengthOfWait = updateFrequency * MILLISECONDS_PER_SECOND;
      int alarmType = AlarmManager.ELAPSED_REALTIME_WAKEUP;
      alarmManager.set(alarmType, SystemClock.elapsedRealtime() + timeOrLengthOfWait, pi);
      Log.d(MAIN_TAG, "Auto Update Set");
    }
    Intent intent = new Intent(this, GetLocationService.class);
    startService(intent);
  }

  public void uploadLocations(View v) {
    Intent intent = new Intent(this, UploadService.class);
    startService(intent);
    Log.d(MAIN_TAG, "Upload");
  }

  public void stopCapturingLocations() {
    stopService(new Intent(this, GetLocationService.class));
  }

  protected void onStop() {
    super.onStop();
  }

}