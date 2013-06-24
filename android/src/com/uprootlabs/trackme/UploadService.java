package com.uprootlabs.trackme;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.http.AndroidHttpClient;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class UploadService extends Service {

  private static final String UPLOAD_TAG = "uploadService";
  private String sessionDetails = "";
  private String userId;
  private String passKey;
  private Thread t;
  LocationDBHelper myLocationDB = new LocationDBHelper(this);
  private static final int MILLISECONDS_PER_SECOND = 1000;
  private static final int SECONDS_PER_MINUTE = 60;
  SharedPreferences myPreferences;
  PendingIntent piAutoUpdate;
  AlarmManager alarmManager;

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d(UPLOAD_TAG, "Upload Service Created");
  }

  @Override
  public IBinder onBind(Intent intent) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    myPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    userId = myPreferences.getString("userID", "");
    passKey = myPreferences.getString("passKey", "");

    setForegroundService();

    if (userId.equals("") || passKey.equals("")) {
      Log.d(UPLOAD_TAG, "Empty UserID or PassKey");
      Toast.makeText(this, "Empty UserID or PassKey", Toast.LENGTH_LONG).show();
      stopSelf();
    } else {
      Log.d(UPLOAD_TAG, "Upload Service Started");
      sessionDetails = getSessionDetails();

      uploadeSession(sessionDetails);

      boolean autoUpdate = myPreferences.getBoolean("autoUpdate", false);
      boolean captureLocations = myPreferences.getBoolean("captureLocations", false);
      Log.d(UPLOAD_TAG, "autoupdate" + autoUpdate + " " + "Capture" + captureLocations);
      setAutoUpload(autoUpdate, captureLocations);
    }
    Log.d(UPLOAD_TAG, "exiting service");
    return Service.START_NOT_STICKY;
  }

  private void setForegroundService() {
    Intent intentNotification = new Intent(this, MainActivity.class);
    PendingIntent pi = PendingIntent.getActivity(this, 1, intentNotification, 0);
    Notification notificaion = new Notification(R.drawable.ic_launcher, "Uploading", System.currentTimeMillis());
    notificaion.setLatestEventInfo(this, "TrackMe", "Uploading Locations", pi);
    notificaion.flags |= Notification.FLAG_ONGOING_EVENT;
    startForeground(2, notificaion);
  }

  private void setAutoUpload(boolean autoUpdate, boolean captureLocations) {
    if (autoUpdate && captureLocations) {
      int updateFrequency = Integer.parseInt(myPreferences.getString("updateFrequency", "0")) * SECONDS_PER_MINUTE;
      piAutoUpdate = PendingIntent.getService(this, 0, new Intent(this, UploadService.class), 0);
      alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
      long timeOrLengthOfWait = updateFrequency * MILLISECONDS_PER_SECOND;
      int alarmType = AlarmManager.ELAPSED_REALTIME_WAKEUP;
      alarmManager.set(alarmType, SystemClock.elapsedRealtime() + timeOrLengthOfWait, piAutoUpdate);
      Log.d(UPLOAD_TAG, "Auto Update Set" + " " + updateFrequency);
    }
  }

  private String getSessionDetails() {
    Log.d(UPLOAD_TAG, "started retrival");
    SQLiteDatabase db = myLocationDB.getReadableDatabase();
    // SQLiteDatabase db = openOrCreateDatabase(LocationDBDetails.DATABASE_NAME,
    // SQLiteDatabase.OPEN_READONLY, null);
    String[] projection = { LocationDBDetails.COLUMN_NAME_LAT, LocationDBDetails.COLUMN_NAME_LNG, LocationDBDetails.COLUMN_NAME_ACC,
        LocationDBDetails.COLUMN_NAME_TS };

    String sortOrder = LocationDBDetails.COLUMN_NAME_TS + " ASC";
    Cursor c = db.query(LocationDBDetails.TABLE_NAME, projection, null, null, null, null, sortOrder);

    StringBuffer locations = new StringBuffer("<session id=\"session1\" userid=\"" + userId + "\" passkey =\"" + passKey + "\">");

    if (c.moveToFirst()) {
      do {
        double latitude = c.getDouble(c.getColumnIndexOrThrow(LocationDBDetails.COLUMN_NAME_LAT));
        double longitude = c.getDouble(c.getColumnIndexOrThrow(LocationDBDetails.COLUMN_NAME_LNG));
        long accuracy = (long) c.getDouble(c.getColumnIndexOrThrow(LocationDBDetails.COLUMN_NAME_ACC));
        long timeStamp = (long) c.getDouble(c.getColumnIndexOrThrow(LocationDBDetails.COLUMN_NAME_TS));
        locations.append("<location latitude=\"");
        locations.append(latitude);
        locations.append("\" longitude=\"");
        locations.append(longitude);
        locations.append("\" accuracy=\"");
        locations.append(accuracy);
        locations.append("\" timestamp=\"");
        locations.append(timeStamp);
        locations.append("\" />");
      } while (c.moveToNext());
    }
    locations.append("</session>");

    db.close();

    Log.d(UPLOAD_TAG, "Locations Retreived");
    return locations.toString();

  }

  private void uploadeSession(final String session) {
    Log.d(UPLOAD_TAG, "Starting Upload Thread");
    t = new Thread() {
      @Override
      public void run() {
        Log.d(UPLOAD_TAG, "Thread Started");
        Log.d(UPLOAD_TAG, session);
        // String session1 =
        // "<session id=\"chetan123\" userid=\"trackme.git@gmail.com\" passkey=\"123456\">"
        // +
        // "<location latitude=\"1.5\" longitude=\"2.5\" accuracy=\"10\" timestamp=\"12345678765\" />"
        // +
        // "</session>";
        String serverURL = "https://testtrackme.appspot.com";
        AndroidHttpClient http = AndroidHttpClient.newInstance("TrackMe");
        HttpPost httpPost = new HttpPost(serverURL + "/api/xml/store?userId=" + userId + "&passKey=" + passKey);
        GzipHelper.setCompressedEntity(UploadService.this, session, httpPost);
        try {
          httpPost.addHeader("userID", userId);
          httpPost.addHeader("passkey", passKey);
          long execTime = System.currentTimeMillis();
          HttpResponse response = http.execute(httpPost);
          int code = response.getStatusLine().getStatusCode();
          http.close();
          if (code == HttpStatus.SC_OK) {
            clearDB(execTime);
          } else if (code == HttpStatus.SC_BAD_REQUEST || code == 500) {

          }
        } catch (ClientProtocolException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }
        Log.d(UPLOAD_TAG, "Thread Compleated");
      }
    };
    t.start();
    stopSelf();
  }

  private void clearDB(long time) {
    Log.d(UPLOAD_TAG, "Clearing");
    String selection = LocationDBDetails.COLUMN_NAME_TS + " < ?";
    String[] selectionArgs = { String.valueOf(time) };
    SQLiteDatabase db = myLocationDB.getWritableDatabase();
    // SQLiteDatabase db = openOrCreateDatabase(LocationDBDetails.DATABASE_NAME,
    // SQLiteDatabase.OPEN_READWRITE, null);
    db.delete(LocationDBDetails.TABLE_NAME, selection, selectionArgs);
    db.close();
    Log.d(UPLOAD_TAG, "Cleared");
  }

  @Override
  public void onDestroy() {
    Log.d(UPLOAD_TAG, "Destroyed");
  }
}