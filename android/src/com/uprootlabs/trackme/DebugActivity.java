package com.uprootlabs.trackme;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class DebugActivity extends Activity {

  public static final String DEBUG_ACTIVITY_UPDATE_UI = "DebugActivity/updateUI";
  private static final String DEBUG_ACTIVITY_TAG = "debugActivity";
  SQLiteDatabase db;
  SharedPreferences debug;
  SharedPreferences.Editor debugEditor;

  private static TextView total;
  private static TextView uploaded;
  private static TextView archived;
  private static TextView queued;
  private static TextView sum;

  private final BroadcastReceiver broadCastReceiverDebugActivity = new BroadcastReceiver() {

    @Override
    public void onReceive(final Context context, final Intent intent) {
      final String broadcastAction = intent.getAction();

      if (broadcastAction.equals(DEBUG_ACTIVITY_UPDATE_UI)) {
        Log.d(DEBUG_ACTIVITY_TAG, "updateUI broadcast");
        updateUI();
      }
    }

  };

  public void updateUI() {
    final int totalCount = debug.getInt(DebugHelper.PREFERENCE_TOTAL_LOCATION_COUNT, 0);
    final int uploadedCount = debug.getInt(DebugHelper.PREFERENCE_UPLOADED_LOCATION_COUNT, 0);
    final int archivedCount = debug.getInt(DebugHelper.PREFERENCE_ARCHIVED_LOCATION_COUNT, 0);
    final int queuedCount = debug.getInt(DebugHelper.PREFERENCE_TOTAL_QUEUED_LOCATION_COUNT, 0);
    final int sumCount = uploadedCount + archivedCount + queuedCount;

    total.setText("" + totalCount);
    uploaded.setText("" + uploadedCount);
    archived.setText("" + archivedCount);
    queued.setText("" + queuedCount);
    sum.setText("" + sumCount);

  }

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_debug);

    LocalBroadcastManager.getInstance(this).registerReceiver(broadCastReceiverDebugActivity, new IntentFilter(DEBUG_ACTIVITY_UPDATE_UI));

    debug = getSharedPreferences(DebugHelper.PREFERENCE_NAME, 0);
    debugEditor = debug.edit();
    db = new TrackMeDBHelper(this).getReadableDatabase();

    total = (TextView) findViewById(R.id.value_total);
    uploaded = (TextView) findViewById(R.id.value_uploaded);
    archived = (TextView) findViewById(R.id.value_archived);
    queued = (TextView) findViewById(R.id.value_queued);
    sum = (TextView) findViewById(R.id.value_sum);
  }

  @Override
  public boolean onCreateOptionsMenu(final Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.debug, menu);
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
      break;

    }
    return super.onOptionsItemSelected(item);
  }

  public void onResume() {
    super.onResume();
    updateUI();
  }

}
