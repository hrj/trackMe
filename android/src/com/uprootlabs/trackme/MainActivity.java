package com.uprootlabs.trackme;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
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
  private static final String MAIN_ACTIVITY_TAG = "mainActivity";
  private final IntentFilter locationsServiceStatusIntentFilter = new IntentFilter();

  private TextView valueLat;
  private TextView valueLng;
  private TextView valueAccuracy;
  private TextView valueTimeStamp;
  private TextView valueCaptureFrequency;
  private TextView valueUpdateFrequency;

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
        Log.d(MAIN_ACTIVITY_TAG, "updateUI broadcast");
        valueLat.setText(intent.getStringExtra(LocationService.LATITUDE));
        valueLng.setText(intent.getStringExtra(LocationService.LONGITUDE));
        valueAccuracy.setText(intent.getStringExtra(LocationService.ACCURACY));
        valueTimeStamp.setText(intent.getStringExtra(LocationService.TIMESTAMP));
      }
    }

  };

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    valueCaptureFrequency = (TextView) findViewById(R.id.valueCaptureFrequency);
    valueUpdateFrequency = (TextView) findViewById(R.id.valueUpdateFrequency);
    valueLat = (TextView) findViewById(R.id.lat);
    valueLng = (TextView) findViewById(R.id.lng);
    valueAccuracy = (TextView) findViewById(R.id.accuracy);
    valueTimeStamp = (TextView) findViewById(R.id.timeStamp);
    startStopButton = (Button) findViewById(R.id.startStop);

    locationsServiceStatusIntentFilter.addAction(MAIN_ACTIVITY_LOCATION_SERVICE_STATUS);
    locationsServiceStatusIntentFilter.addAction(MAIN_ACTIVITY_UPDATE_UI);

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

    String captureFrequency = "" + (myPreference.getCaptureFrequency() / TrackMeHelper.MILLISECONDS_PER_SECOND) + "sec";
    String updateFrequency = ""
        + (myPreference.getUpdateFrequency() / TrackMeHelper.SECONDS_PER_MINUTE / TrackMeHelper.MILLISECONDS_PER_SECOND) + "min";
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

      final Intent intent = new Intent(this, UploadService.class);
      intent.putExtra(UploadService.UPLOAD_TYPE, UploadService.AUTO_UPLOAD);
      final int updateFrequency = myPreference.getUpdateFrequency();
      final long uploadTime = System.currentTimeMillis() + updateFrequency;
      intent.putExtra(UploadService.UPLOAD_TIME, uploadTime);
      if (!UploadService.pendingIntentExists(this, intent)) {
        pi = PendingIntent.getService(this, 0, intent, 0);
        final long timeOrLengthOfWait = updateFrequency;
        final int alarmType = AlarmManager.ELAPSED_REALTIME_WAKEUP;

        final AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(alarmType, SystemClock.elapsedRealtime() + timeOrLengthOfWait, pi);
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

  public void onClickNewSession(final View v) {
    final AlertDialog.Builder alert = new AlertDialog.Builder(this);

    alert.setTitle(this.getResources().getString(R.string.new_session_id));
    alert.setMessage(this.getResources().getString(R.string.label_current_session_id) + myPreference.getSessionID());

    final EditText input = new EditText(this);
    input.setHint("Enter New SessionID");
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
        Toast.makeText(MainActivity.this, "New session started with SessionID : " + myPreference.getSessionID(), Toast.LENGTH_SHORT).show();
      }
    });

    alert.setNegativeButton(this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
      public void onClick(final DialogInterface dialog, final int whichButton) {
        Toast.makeText(MainActivity.this, "SessionID changed to " + myPreference.getSessionID(), Toast.LENGTH_SHORT).show();
      }
    });

    alert.show();
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