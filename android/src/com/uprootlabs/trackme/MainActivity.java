package com.uprootlabs.trackme;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {

  public static final String MAIN_ACTIVITY_LOCATION_SERVICE_STATUS = "MainActivity/locationServiceStatus";
  public static final String MAIN_ACTIVITY_UPDATE_UI = "MainActivity/updateUI";
  public static final String MAIN_ACTIVITY_UPDATE_DEBUG_UI = "MainActivity/updateDebugUI";
  private static final String MAIN_ACTIVITY_TAG = "mainActivity";
  private final IntentFilter locationsServiceStatusIntentFilter = new IntentFilter();
  DebugHelper debugPreferences;

  private TextView valueLat;
  private TextView valueLng;
  private TextView valueAccuracy;
  private TextView valueTimeStamp;
  private TextView valueCaptureFrequency;
  private TextView valueUpdateFrequency;
  private TextView valueSessionID;
  private TextView debug;

  private Button startStopButton;

  MyPreference myPreference;

  private String captureServiceStatus;
  PendingIntent pi;

  private final BroadcastReceiver broadCastReceiverMainActivity = new BroadcastReceiver() {

    @SuppressWarnings("unused")
    @Override
    public void onReceive(final Context context, final Intent intent) {
      final String serviceStatus = intent.getStringExtra(LocationService.PARAM_LOCATION_SERVICE_STATUS);
      final String broadcastAction = intent.getAction();

      if (broadcastAction.equals(MAIN_ACTIVITY_LOCATION_SERVICE_STATUS)) {
        Log.d(MAIN_ACTIVITY_TAG, "serviceStatus recieved");
        if (serviceStatus.equals(LocationService.STATUS_WARMED_UP)) {
          captureServiceStatus = LocationService.STATUS_WARMED_UP;
          startStopButton.setEnabled(true);
        } else if (serviceStatus == null) {
          captureServiceStatus = null;
          startStopButton.setEnabled(false);
        }
      } else if (broadcastAction.equals(MAIN_ACTIVITY_UPDATE_UI)) {
        updateLocationDetails(intent);
        final String debugDetails = debugPreferences.getDebugDetails();
        debug.setText(debugDetails);
      } else if (broadcastAction.equals(MAIN_ACTIVITY_UPDATE_DEBUG_UI)) {
        final String debugDetails = debugPreferences.getDebugDetails();
        debug.setText(debugDetails);
      }
    }

  };

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.debug_main);

    valueCaptureFrequency = (TextView) findViewById(R.id.valueCaptureFrequency);
    valueUpdateFrequency = (TextView) findViewById(R.id.valueUpdateFrequency);
    valueSessionID = (TextView) findViewById(R.id.valueSessionID);
    debug = (TextView) findViewById(R.id.debug);
    valueLat = (TextView) findViewById(R.id.lat);
    valueLng = (TextView) findViewById(R.id.lng);
    valueAccuracy = (TextView) findViewById(R.id.accuracy);
    valueTimeStamp = (TextView) findViewById(R.id.timeStamp);
    startStopButton = (Button) findViewById(R.id.startStop);

    locationsServiceStatusIntentFilter.addAction(MAIN_ACTIVITY_LOCATION_SERVICE_STATUS);
    locationsServiceStatusIntentFilter.addAction(MAIN_ACTIVITY_UPDATE_UI);
    locationsServiceStatusIntentFilter.addAction(MAIN_ACTIVITY_UPDATE_DEBUG_UI);

    LocalBroadcastManager.getInstance(this).registerReceiver(broadCastReceiverMainActivity, locationsServiceStatusIntentFilter);

    final Intent intent = new Intent(LocationService.ACTION_QUERY_STATUS_MAIN_ACTIVITY);

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

    myPreference = new MyPreference(this);
    debugPreferences = new DebugHelper(this);
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

    final String captureFrequency = "" + (myPreference.getCaptureFrequency() / TrackMeHelper.MILLISECONDS_PER_SECOND) + "sec";
    final String updateFrequency = ""
        + (myPreference.getUpdateFrequency() / TrackMeHelper.SECONDS_PER_MINUTE / TrackMeHelper.MILLISECONDS_PER_SECOND) + "min";
    final String sessionID = myPreference.getSessionID();
    valueCaptureFrequency.setText(captureFrequency);
    valueUpdateFrequency.setText(updateFrequency);
    valueSessionID.setText(sessionID);
    final String debugDetails = debugPreferences.getDebugDetails();
    debug.setText(debugDetails);
  }

  @Override
  public boolean onCreateOptionsMenu(final Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(final MenuItem item) {
    switch (item.getItemId()) {

    case R.id.action_settings:
      final Intent settings = new Intent(this, MyPreferencesActivity.class);
      startActivity(settings);
      break;

    case R.id.action_upload:
      final Intent intent = new Intent(this, UploadService.class);
      intent.putExtra(UploadService.UPLOAD_TYPE, UploadService.MANUAL_UPLOAD);
      startService(intent);
      Log.d(MAIN_ACTIVITY_TAG, "Upload");
      break;

    case R.id.action_debug:
      final Intent debug = new Intent(this, DebugActivity.class);
      startActivity(debug);
      break;

    }

    return true;
  }

  public void onClickStartStop(final View v) {
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
    intent.setAction(LocationService.ACTION_WARM_UP_SERVICE);
    startService(intent);
  }

  private void startCapturingLocations() {
    if (myPreference.isAutoUpdateSet()) {

      final int updateFrequency = myPreference.getUpdateFrequency();

      if (!UploadService.pendingIntentExists(this)) {
        UploadService.setUploadAlarm(this, UploadService.AUTO_UPLOAD, updateFrequency);
      }

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

  private void updateLocationDetails(final Intent intent) {
    valueLat.setText(intent.getStringExtra(LocationService.LATITUDE));
    valueLng.setText(intent.getStringExtra(LocationService.LONGITUDE));
    valueAccuracy.setText(intent.getStringExtra(LocationService.ACCURACY));
    valueTimeStamp.setText(intent.getStringExtra(LocationService.TIMESTAMP));
  }

  public void onClickNewSession(final View v) {
    final AlertDialog.Builder alert = new AlertDialog.Builder(this);

    final String sessionID = myPreference.getSessionID();
    alert.setTitle(this.getResources().getString(R.string.new_session_id));
    alert.setMessage(this.getResources().getString(R.string.label_current_session_id) + sessionID);

    final EditText input = new EditText(this);
    input.setText(sessionID);
    alert.setView(input);

    alert.setPositiveButton(this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
      public void onClick(final DialogInterface dialog, final int whichButton) {
        final Editable value = input.getText();
        final String sessionID = value.toString();
        if (!sessionID.trim().equals("")) {
          myPreference.setSessoinID(sessionID);
          if (captureServiceStatus.equals(LocationService.STATUS_WARMED_UP))
            onClickStartStop(v);
        }
        valueSessionID.setText(myPreference.getSessionID());
      }
    });

    alert.setNegativeButton(this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
      public void onClick(final DialogInterface dialog, final int whichButton) {
        Toast.makeText(MainActivity.this, "SessionID changed to " + myPreference.getSessionID(), Toast.LENGTH_SHORT).show();
      }
    });

    alert.show();
  }

  public void onClickUpload(final View v) {
    final Intent intent = new Intent(this, UploadService.class);
    intent.putExtra(UploadService.UPLOAD_TYPE, UploadService.MANUAL_UPLOAD);
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