package com.uprootlabs.trackme;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class MainActivity extends Activity {

  public static final String MAIN_ACTIVITY_LOCATION_SERVICE_STATUS = "MainActivity/locationServiceStatus";
  private static final String MAIN_ACTIVITY_TAG = "mainActivity";
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

  private String captureServiceStatus;
  PendingIntent pi;

  private final BroadcastReceiver broadCastReceiverMainActivity = new BroadcastReceiver() {

    @Override
    public void onReceive(final Context context, final Intent intent) {
      final String serviceStatus = intent.getStringExtra(LocationService.PARAM_LOCATION_SERVICE_STATUS);

      if (serviceStatus.equals(LocationService.STATUS_WARMED_UP)) {
        captureServiceStatus = LocationService.STATUS_WARMED_UP;
        startStopButton.setEnabled(true);
      }
    }

  };

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    valueCaptureFrequency = (TextView) findViewById(R.id.valueCaptureFrequency);
    valueUpdateFrequency = (TextView) findViewById(R.id.valueUpdateFrequency);
    startStopButton = (Button) findViewById(R.id.startStop);

    final IntentFilter locationsServiceStatusIntentFilter = new IntentFilter(MAIN_ACTIVITY_LOCATION_SERVICE_STATUS);

    LocalBroadcastManager.getInstance(this).registerReceiver(broadCastReceiverMainActivity, locationsServiceStatusIntentFilter);

    final Intent intent = new Intent("locationServiceStatus/MainActivity");
    intent.setAction(LocationService.ACTION_QUERY_STATUS_MAIN_ACTIVITY);

    LocalBroadcastManager.getInstance(this).sendBroadcastSync(intent);

    captureServiceStatus = intent.getStringExtra(LocationService.PARAM_LOCATION_SERVICE_STATUS);
    Log.d(MAIN_ACTIVITY_TAG, "on Create" + " " + captureServiceStatus);

    if (captureServiceStatus == null) {
      startServiceWarmUp();
    } else if (captureServiceStatus.equals(LocationService.STATUS_WARMED_UP)) {
      startStopButton.setEnabled(true);
    } else if (captureServiceStatus.equals(LocationService.STATUS_CAPTURING_LOCATIONS)) {
      startStopButton.setEnabled(true);
      startStopButton.setText(R.string.stop_capturing);
    }

    // valueLat = (TextView) findViewById(R.id.lat);
    // valueLng = (TextView) findViewById(R.id.lng);
    // valueAccuracy = (TextView) findViewById(R.id.accuracy);
    // valueTimeStamp = (TextView) findViewById(R.id.timeStamp);

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

    final String captureFrequency = myPreferences.getString("captureFrequency", NOT_SET);
    final String updateFrequency = myPreferences.getString("updateFrequency", NOT_SET);
    valueCaptureFrequency.setText(captureFrequency);
    valueUpdateFrequency.setText(updateFrequency);
  }

  @Override
  public boolean onCreateOptionsMenu(final Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(final MenuItem item) {
    if (item.getItemId() == R.id.action_settings) {
      final Intent settings = new Intent(this, MyPreferencesActivity.class);
      startActivity(settings);
    }

    // case R.id.upload_locations:
    // getActivity().startActivity(item.getIntent());
    // break;

    return true;
  }

  public void startStopCapturing(final View v) {
    Log.d(MAIN_ACTIVITY_TAG, "Start/Stop");

    Log.d(MAIN_ACTIVITY_TAG, captureServiceStatus + " " + "Service Status");
    if (captureServiceStatus.equals(LocationService.STATUS_WARMED_UP)) {
      startCapturingLocations();
    } else if (captureServiceStatus.equals(LocationService.STATUS_CAPTURING_LOCATIONS)) {
      stopCapturingLocations();
      startStopButton.setText(R.string.start_capturing);
    }

  }

  private void startServiceWarmUp() {
    final Intent intent = new Intent(this, LocationService.class);
    intent.setAction("warmUpLocationService/MainActivity");
    startService(intent);
  }

  private void startCapturingLocations() {
    final boolean autoUpdate = myPreferences.getBoolean("autoUpdate", false);
    if (autoUpdate) {

      final Intent intent = new Intent(this, UploadService.class);
      if (!UploadService.pendingIntentExists(this, intent)) {
        pi = PendingIntent.getService(this, 0, intent, 0);
        UploadService.startAlarm(this, pi);
      }

      Log.d(MAIN_ACTIVITY_TAG, "Auto Update Set");
    }

    final Intent intentStatus = new Intent(LocationService.ACTION_CAPTURE_LOCATIONS);
    LocalBroadcastManager.getInstance(this).sendBroadcastSync(intentStatus);

    captureServiceStatus = intentStatus.getStringExtra(LocationService.PARAM_LOCATION_SERVICE_STATUS);

    if (captureServiceStatus.equals(LocationService.STATUS_CAPTURING_LOCATIONS)) {
      startStopButton.setText(R.string.stop_capturing);
    } else if (captureServiceStatus.equals(LocationService.ERROR_CAPTURING_LOCATIONS)) {
      showErrorDialog();
    }
  }

  private void showErrorDialog() {
    // TODO Auto-generated method stub

  }

  public void uploadLocations(final View v) {

    final Intent intent = new Intent(this, UploadService.class);
    startService(intent);
    Log.d(MAIN_ACTIVITY_TAG, "Upload");
  }

  private void stopCapturingLocations() {
    final Intent intentStatus = new Intent(LocationService.ACTION_STOP_CAPTURIGN_LOCATIONS);
    LocalBroadcastManager.getInstance(this).sendBroadcastSync(intentStatus);

    captureServiceStatus = intentStatus.getStringExtra(LocationService.PARAM_LOCATION_SERVICE_STATUS);
    Log.d(MAIN_ACTIVITY_TAG, captureServiceStatus + " " + "Status");

    if (captureServiceStatus.equals(LocationService.STATUS_WARMED_UP)) {
      startStopButton.setText(R.string.start_capturing);
    } else if (captureServiceStatus.equals(LocationService.ERROR_CAPTURING_LOCATIONS)) {
      showErrorDialog();
    }

  }

  protected void onStop() {
    super.onStop();
  }

}