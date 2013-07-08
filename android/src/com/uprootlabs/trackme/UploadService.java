package com.uprootlabs.trackme;

import java.io.IOException;
import java.net.UnknownHostException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.AndroidHttpClient;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public final class UploadService extends Service {

  private final class UploadThread extends Thread {

    private UploadThread() {
    }

    @Override
    public void run() {
      Log.d(UPLOAD_SERVICE_TAG, "Thread Started");
      final String serverURL = myPreference.getServerLocation();
      final String userID = myPreference.getUserID();
      final String passKey = myPreference.getPassKey();
      if (userAuthenticated(userID, passKey, serverURL)) {

        db.clearUploadIDs();
        boolean errorExit;
        do {
          errorExit = false;
          int retryCount = 0;
          int code = -1;
          HttpResponse response = null;
          final String locations = db.getLocationsAsXML(uploadTime);
          Log.d(UPLOAD_SERVICE_TAG, locations);
          final AndroidHttpClient http = AndroidHttpClient.newInstance("TrackMe");
          final HttpPost httpPost = new HttpPost(serverURL);
          GzipHelper.setCompressedEntity(UploadService.this, locations, httpPost);
          httpPost.addHeader("userID", userID);
          httpPost.addHeader("passkey", passKey);
          while (retryCount < MAX_RETRY_COUNT) {

            try {
              response = http.execute(httpPost);
              Log.d(UPLOAD_SERVICE_TAG, response.toString());
              code = response.getStatusLine().getStatusCode();
              errorExit = false;
              retryCount = MAX_RETRY_COUNT;
            } catch (final ClientProtocolException e) {
              retryCount += 1;
              errorExit = true;
            } catch (final IOException e) {
              retryCount += 1;
              errorExit = true;
              e.printStackTrace();
            }

          }
          http.close();
          if (code == HttpStatus.SC_OK) {
            // db.moveLocations(uploadID, sessions)
          } else if (code == HttpStatus.SC_BAD_REQUEST || code == 500) {

          }
        } while (db.getQueuedLocationsCount(uploadTime) > 0 && !errorExit);

      }

      synchronized (UploadService.this) {
        running = false;
        stopForeground(true);
      }
      Log.d(UPLOAD_SERVICE_TAG, "Thread Compleated");
    }
  }

  private static final String UPLOAD_SERVICE_TAG = "uploadService";
  public static final String UPLOAD_TYPE = "uploadType";
  public static final String MANUAL_UPLOAD = "manual";
  public static final String AUTO_UPLOAD = "auto";
  public static final String UPLOAD_TIME = "uploadTime";
  public static final int MAX_RETRY_COUNT = 5;

  SQLiteDatabase myDb;
  TrackMeDB db;
  MyPreference myPreference;
  private long uploadTime;
  private PendingIntent piAutoUpdate;
  private boolean running = false;

  @Override
  public IBinder onBind(final Intent intent) {
    return null;
  }

  public static boolean pendingIntentExists(final Context context, final Intent intent) {
    final PendingIntent pi = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_NO_CREATE);
    return (pi != null);
  }

  public void setAlarm(final Context context, final PendingIntent pi) {

    final long timeOrLengthOfWait = myPreference.getUpdateFrequency();
    final int alarmType = AlarmManager.ELAPSED_REALTIME_WAKEUP;

    final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarmManager.set(alarmType, SystemClock.elapsedRealtime() + timeOrLengthOfWait, pi);
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

    String captureServiceStatus;
    final String uploadType = intent.getStringExtra(UPLOAD_TYPE);

    if (uploadType.equals(MANUAL_UPLOAD)) {
      uploadTime = System.currentTimeMillis();
    } else if (uploadType.equals(AUTO_UPLOAD)) {
      uploadTime = intent.getLongExtra(UPLOAD_TIME, System.currentTimeMillis());

      final Intent intentStatus = new Intent(LocationService.ACTION_QUERY_STATUS_UPLOAD_SERVICE);
      LocalBroadcastManager.getInstance(this).sendBroadcastSync(intentStatus);

      captureServiceStatus = intentStatus.getStringExtra(LocationService.PARAM_LOCATION_SERVICE_STATUS);
      if (captureServiceStatus.equals(LocationService.STATUS_CAPTURING_LOCATIONS)) {
        setAutoUpdate();
      }
    }

    uploadeSession();

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

  private void setAutoUpdate() {
    if (myPreference.isAutoUpdateSet() && running) {
      final Intent intent = new Intent(this, UploadService.class);
      intent.putExtra(UPLOAD_TYPE, AUTO_UPLOAD);
      final long uploadTime = System.currentTimeMillis() + myPreference.getUpdateFrequency();
      intent.putExtra(UPLOAD_TIME, uploadTime);
      piAutoUpdate = PendingIntent.getService(this, 0, intent, 0);
      setAlarm(this, piAutoUpdate);
      Log.d(UPLOAD_SERVICE_TAG, "Auto Update Set");
    }
  }

  private void uploadeSession() {
    Log.d(UPLOAD_SERVICE_TAG, "Starting Upload Thread");
    synchronized (this) {
      if (!running && uploadPossible(uploadTime)) {
        setForegroundService();
        running = true;

        final Thread t = new UploadThread();
        t.start();
      } else {
        running = false;
      }
    }
    Log.d(UPLOAD_SERVICE_TAG, "Upload Complete");
  }

  // private void clearDB(final long time) {
  // Log.d(UPLOAD_SERVICE_TAG, "Clearing");
  // final String selection = TrackMeDBDetails.COLUMN_NAME_TS + " < ?";
  // final String[] selectionArgs = { String.valueOf(time) };
  // final SQLiteDatabase db = myLocationDB.getWritableDatabase();
  // SQLiteDatabase db = openOrCreateDatabase(TrackMeDBDetails.DATABASE_NAME,
  // SQLiteDatabase.OPEN_READWRITE, null);
  // db.delete(TrackMeDBDetails.LOCATION_TABLE_NAME, selection,
  // selectionArgs);
  // db.close();
  // Log.d(UPLOAD_SERVICE_TAG, "Cleared");
  // }

  private boolean uploadPossible(final long uploadTime) {
    final boolean userValidation = myPreference.userDetailsNotNull();
    final boolean serverLocationValidation = myPreference.serverLocationSet();
    final boolean dbValidation = db.getQueuedLocationsCount(uploadTime) > 0;
    final boolean networkValidation = isNetworkAvailable();
    boolean possible = true;
    final StringBuffer message = new StringBuffer();
    message.append("Upload not possible due to : ");

    if (!userValidation) {
      possible = false;
      message.append("\nUserID or PassKey not provided");
    }

    if (!serverLocationValidation) {
      possible = false;
      message.append("\nServer Location not set");
    }

    if (!dbValidation) {
      possible = false;
      message.append("\nNo locations to upload");
    }

    if (!networkValidation) {
      possible = false;
      message.append("\nNetwoek not available");
    }

    if (!possible) {
      final Intent dialogIntent = new Intent(getBaseContext(), DialogActivity.class);
      dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      dialogIntent.putExtra(DialogActivity.STR_ERROR_TYPE, DialogActivity.STR_ERROR_USER);
      dialogIntent.putExtra(DialogActivity.STR_ERROR_MESSAGE, message.toString());
      getApplication().startActivity(dialogIntent);
    }

    return possible;
  }

  private boolean isNetworkAvailable() {
    final ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    final NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
    final boolean connected = (activeNetworkInfo != null && activeNetworkInfo.isConnected());
    return connected;
  }

  private boolean userAuthenticated(final String userID, final String passKey, final String serverURL) {
    final AndroidHttpClient http = AndroidHttpClient.newInstance("TrackMe");
    final HttpGet httpGet = new HttpGet(serverURL);
    httpGet.addHeader("userID", userID);
    httpGet.addHeader("passkey", passKey);
    int code = -1;
    String message = "";
    try {
      final HttpResponse response = http.execute(httpGet);
      Log.d(UPLOAD_SERVICE_TAG, response.getStatusLine().toString());
      code = response.getStatusLine().getStatusCode();
    } catch (final ClientProtocolException e) {
      message = "Internet not available";
      Log.d(UPLOAD_SERVICE_TAG, "Service Timeout");
    } catch (final UnknownHostException e) {
      message = "Server was not known or unreachable";
      Log.d(UPLOAD_SERVICE_TAG, "Unknown Host");
    } catch (final IllegalStateException e) {
      message = "Invalid Server URL";
      Log.d(UPLOAD_SERVICE_TAG, "Illegal");
    } catch (final IOException e) {
      message = "Unknown Error";
      e.printStackTrace();
    }
    http.close();

    final Intent dialogIntent = new Intent(getBaseContext(), DialogActivity.class);
    dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    dialogIntent.putExtra(DialogActivity.STR_ERROR_TYPE, DialogActivity.STR_ERROR_USER);

    if (code == HttpStatus.SC_OK) {
      Log.d(UPLOAD_SERVICE_TAG, "valid");
      return true;
    } else if (code == -1) {
      Log.d(UPLOAD_SERVICE_TAG, "Invalid" + " " + code);
      dialogIntent.putExtra(DialogActivity.STR_ERROR_MESSAGE, message);
      getApplication().startActivity(dialogIntent);
      return false;
    } else {
      message = "Invalid UserID or PassKey";
      dialogIntent.putExtra(DialogActivity.STR_ERROR_MESSAGE, message);
      getApplication().startActivity(dialogIntent);
      return false;
    }
  }

  @Override
  public void onDestroy() {
    Log.d(UPLOAD_SERVICE_TAG, "Destroyed");
  }

}