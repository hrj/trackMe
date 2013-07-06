package com.uprootlabs.trackme;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.params.HttpConnectionParams;

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
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

public final class UploadService extends Service {

  private final class UploadThread extends Thread {

    private UploadThread() {
    }

    @Override
    public void run() {
      Log.d(UPLOAD_SERVICE_TAG, "Thread Started");
//      boolean uploadMore = true;
//      while (uploadMore) {

//        StringBuffer locations = db.getLocationsAsXML(time);

//        if (c.moveToFirst()) {

//          sessionDetails = getSessionDetails(c);


          for(int i=0; i<10000; i++){
            xyz.append("this is a small string to test what will be the result of data connection");
            xyz.append("this is a small string to test what will be the result of data connection");
            xyz.append("this is a small string to test what will be the result of data connection");
          }
          sessionDetails = xyz.toString();
          Log.d(UPLOAD_SERVICE_TAG, sessionDetails);
          final String serverURL = "https://testtrackme.appspot.com";
          final AndroidHttpClient http = AndroidHttpClient.newInstance("TrackMe");
          final HttpPost httpPost = new HttpPost(serverURL + "/api/xml/store?userId=" + userId + "&passKey=" + passKey);
          GzipHelper.setCompressedEntity(UploadService.this, sessionDetails, httpPost);
          try {
            httpPost.addHeader("userID", userId);
            httpPost.addHeader("passkey", passKey);
            final long execTime = System.currentTimeMillis();
            final HttpResponse response = http.execute(httpPost);
            Log.d(UPLOAD_SERVICE_TAG, response.toString());
            final int code = response.getStatusLine().getStatusCode();
            http.close();
            if (code == HttpStatus.SC_OK) {
              clearDB(execTime);
            } else if (code == HttpStatus.SC_BAD_REQUEST || code == 500) {

            }
          } catch (final ClientProtocolException e) {
            e.printStackTrace();
          } catch (final IOException e) {
            e.printStackTrace();
          }

//        } else {
//          uploadMore = false;
//        }

//      }
//      synchronized (UploadService.this) {
//        running = false;
//        stopForeground(true);
//      }
      // TODO What if the user changes userId, passkey or the server location
      // during an ongoing upload
      Log.d(UPLOAD_SERVICE_TAG, "Thread Compleated");
    }
  }

  private static final String UPLOAD_SERVICE_TAG = "uploadService";
  SQLiteDatabase myDb;
  TrackMeDB db;
  MyPreference myPreference; 
  private String sessionDetails = "";
  private String captureServiceStatus;
  private String userId;
  private String passKey;
  private int locationCount;
  private PendingIntent piAutoUpdate;
  private boolean running = false;
  private StringBuffer xyz;

  @Override
  public IBinder onBind(final Intent intent) {
    return null;
  }

  public static boolean pendingIntentExists(final Context context, final Intent intent) {
    final PendingIntent pi = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_NO_CREATE);
    return (pi != null);
  }

  public static void startAlarm(final Context context, final PendingIntent pi) {
    final SharedPreferences myPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    final int updateFrequency = Integer.parseInt(myPreferences.getString("updateFrequency", "0"));

    final long timeOrLengthOfWait = updateFrequency * TrackMeHelper.MILLISECONDS_PER_SECOND * TrackMeHelper.SECONDS_PER_MINUTE;
    final int alarmType = AlarmManager.ELAPSED_REALTIME_WAKEUP;

    final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarmManager.set(alarmType, SystemClock.elapsedRealtime() + timeOrLengthOfWait, pi);
  }

  public static void updateAlarm(final Context context) {

  }

  @Override
  public void onCreate() {
    super.onCreate();
    myDb = new TrackMeDBHelper(this).getWritableDatabase();
    db = new TrackMeDB(myDb, this);
    myPreference = new MyPreference(this);
    Log.d(UPLOAD_SERVICE_TAG, "Upload Service Created");
  }

  @Override
  public int onStartCommand(final Intent intent, final int flags, final int startId) {

        Thread t = new UploadThread();
        t.start();
    // TODO Check for continuous blank spaces and add a check for server
    // location.

//    if (userId.equals("") || passKey.equals("")) {
//      Log.d(UPLOAD_SERVICE_TAG, "Empty UserID or PassKey");
//      Toast.makeText(this, "Empty UserID or PassKey", Toast.LENGTH_LONG).show();
//    } else {
//
//      Log.d(UPLOAD_SERVICE_TAG, "Upload Service Started");
//
//      uploadeSession();
//      // Logic to check condition for next alarm
//      final Intent intentStatus = new Intent(LocationService.ACTION_QUERY_STATUS_UPLOAD_SERVICE);
//      LocalBroadcastManager.getInstance(this).sendBroadcastSync(intentStatus);
//
//      captureServiceStatus = intentStatus.getStringExtra(LocationService.PARAM_LOCATION_SERVICE_STATUS);
//      Log.d(UPLOAD_SERVICE_TAG, captureServiceStatus + " " + "Status");
//
//      final boolean autoUpdate = TrackMeHelper.myPreferences.getBoolean("autoUpdate", false);
//      boolean captureLocations = false;
//      if (captureServiceStatus.equals(LocationService.STATUS_CAPTURING_LOCATIONS)) {
//        captureLocations = true;
//      }
//      Log.d(UPLOAD_SERVICE_TAG, "autoupdate" + autoUpdate + " " + "Capture" + captureLocations);
//      setAutoUpload(autoUpdate, captureLocations);
//    }
    Log.d(UPLOAD_SERVICE_TAG, "exiting service");
    return Service.START_NOT_STICKY;
  }

  private void setForegroundService() {
    final Intent intentNotification = new Intent(this, MainActivity.class);
    final PendingIntent pi = PendingIntent.getActivity(this, 1, intentNotification, 0);
    final Notification notificaion = new Notification(R.drawable.ic_launcher, "Uploading", System.currentTimeMillis());
    notificaion.setLatestEventInfo(this, "TrackMe", "Uploading Locations", pi);
    notificaion.flags |= Notification.FLAG_ONGOING_EVENT;
    startForeground(2, notificaion);
  }

  private void setAutoUpload(final boolean autoUpdate, final boolean captureLocations) {
    if (autoUpdate && captureLocations) {
      piAutoUpdate = PendingIntent.getService(this, 0, new Intent(this, UploadService.class), 0);
      startAlarm(this, piAutoUpdate);
      Log.d(UPLOAD_SERVICE_TAG, "Auto Update Set");
    }
  }

  private void uploadeSession() {
    Log.d(UPLOAD_SERVICE_TAG, "Starting Upload Thread");
    synchronized (this) {
      if (!running) {
        setForegroundService();
        running = true;

        TrackMeHelper.myPreferences = PreferenceManager.getDefaultSharedPreferences(UploadService.this);

        userId = TrackMeHelper.myPreferences.getString("userID", "");
        passKey = TrackMeHelper.myPreferences.getString("passKey", "");
        Thread t = new UploadThread();
        t.start();
      }
    }
    Log.d(UPLOAD_SERVICE_TAG, "Upload Complete");
  }

  private void clearDB(final long time) {
    Log.d(UPLOAD_SERVICE_TAG, "Clearing");
    final String selection = TrackMeDBDetails.COLUMN_NAME_TS + " < ?";
    final String[] selectionArgs = { String.valueOf(time) };
//    final SQLiteDatabase db = myLocationDB.getWritableDatabase();
    // SQLiteDatabase db = openOrCreateDatabase(TrackMeDBDetails.DATABASE_NAME,
    // SQLiteDatabase.OPEN_READWRITE, null);
//    db.delete(TrackMeDBDetails.LOCATION_TABLE_NAME, selection, selectionArgs);
//    db.close();
    Log.d(UPLOAD_SERVICE_TAG, "Cleared");
  }

  private boolean uploadPossible(long uploadTime) {
    //TODO userDetailsNotNull() and getQueuedLocationsCount(uploadTime) > 0 and isNetworkAvailable()
    locationCount = db.getQueuedLocationsCount(uploadTime);
    return myPreference.userDetailsNotNull() &&  locationCount > 0;
  } 
  

  @Override
  public void onDestroy() {
    Log.d(UPLOAD_SERVICE_TAG, "Destroyed");
  }

}