package com.uprootlabs.trackme;

import java.io.IOException;
import java.net.UnknownHostException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
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
          final HttpPost httpPost = new HttpPost(serverURL + "/api/v1/xml/store");
          GzipHelper.setCompressedEntity(UploadService.this, locations, httpPost);
          httpPost.addHeader("userid", userID);
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
            final Document doc = ResponseParsing.getDomElement(ResponseParsing.getXML(response));

            final int uploadID = Integer.parseInt(doc.getDocumentElement().getAttribute("uid"));

            final NodeList nl = doc.getElementsByTagName("batch");

            for (int i = 0; i < nl.getLength(); i++) {
              final Element e = (Element) nl.item(i);
              final String sessionID = e.getAttribute("sid");
              final int batchID = Integer.parseInt(e.getAttribute("bid"));

              if (e.getAttribute("accepted").equals("true")) {
                Log.d(UPLOAD_SERVICE_TAG, "Boolean New " + e.getAttribute("accepted"));
                final int uploadedCount = db.moveLocationsToSessionTable(uploadID, sessionID, batchID);
                updatePreferences.addUploadedCount(uploadedCount);
                final Intent intent = new Intent(MainActivity.MAIN_ACTIVITY_UPDATE_DEBUG_UI);
                LocalBroadcastManager.getInstance(UploadService.this).sendBroadcast(intent);
              } else {
                final int archivedCount = db.archiveLocations(uploadID, sessionID, batchID);
                updatePreferences.addArchivedCount(archivedCount);
                final Intent intent = new Intent(MainActivity.MAIN_ACTIVITY_UPDATE_DEBUG_UI);
                LocalBroadcastManager.getInstance(UploadService.this).sendBroadcast(intent);
              }

            }
          } else {
            final String message = "Server response:\n" + response.getStatusLine().getReasonPhrase();
            getApplication().startActivity(UserError.makeIntent(getBaseContext(), message));
            errorExit = true;
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
  public static final String CANCEL_ALARM = "cancelAlarm";
  public static final String AUTO_UPLOAD = "auto";
  public static final String UPLOAD_TIME = "uploadTime";
  public static final int MAX_RETRY_COUNT = 5;

  DebugHelper updatePreferences;

  SQLiteDatabase myDb;
  TrackMeDB db;
  MyPreference myPreference;
  private long uploadTime;
  private boolean running = false;

  @Override
  public IBinder onBind(final Intent intent) {
    return null;
  }

  public static boolean pendingIntentExists(final Context context) {
    final Intent intent = new Intent(context, UploadService.class);
    final PendingIntent pi = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_ONE_SHOT);
    return (pi != null);
  }

  public static void setUploadAlarm(final Context context, final String uploadType, final int updateFrequency) {
    final Intent intent = new Intent(context, UploadService.class);
    intent.putExtra(UPLOAD_TYPE, uploadType);
    final PendingIntent piAutoUpdate = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);
    final int alarmType = AlarmManager.ELAPSED_REALTIME_WAKEUP;
    final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

    alarmManager.set(alarmType, SystemClock.elapsedRealtime() + updateFrequency, piAutoUpdate);

    Log.d(UPLOAD_SERVICE_TAG, "Auto Update Set");
  }

  public static void cancelUploadAlarm(final Context context) {
    final Intent alarmIntent = new Intent(context, UploadService.class);
    final PendingIntent piCancelAlarm = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_ONE_SHOT);
    piCancelAlarm.cancel();

    Log.d(UPLOAD_SERVICE_TAG, "Alarm Cancelled");
  }

  @Override
  public void onCreate() {
    super.onCreate();
    myDb = new TrackMeDBHelper(this).getWritableDatabase();
    db = new TrackMeDB(myDb, this);
    myPreference = new MyPreference(this);
    updatePreferences = new DebugHelper(this);
    Log.d(UPLOAD_SERVICE_TAG, "Upload Service Created");
  }

  @Override
  public int onStartCommand(final Intent intent, final int flags, final int startId) {

    String captureServiceStatus;
    final String uploadType = intent.getStringExtra(UPLOAD_TYPE);
    uploadTime = System.currentTimeMillis();

    if (uploadType.equals(CANCEL_ALARM)) {
      cancelUploadAlarm(this);
    } else if (uploadType.equals(MANUAL_UPLOAD)) {
      uploadeSession();
    } else if (uploadType.equals(AUTO_UPLOAD)) {

      final Intent intentStatus = new Intent(LocationService.ACTION_QUERY_STATUS_UPLOAD_SERVICE);
      LocalBroadcastManager.getInstance(this).sendBroadcastSync(intentStatus);

      captureServiceStatus = intentStatus.getStringExtra(LocationService.PARAM_LOCATION_SERVICE_STATUS);

      if (LocationService.STATUS_CAPTURING_LOCATIONS.equals(captureServiceStatus) | db.getQueuedLocationsCount(uploadTime) > 0) {
        setAutoUpdate();
      }

      uploadeSession();
    }

    Log.d(UPLOAD_SERVICE_TAG, "exiting service");
    return Service.START_NOT_STICKY;
  }

  private void setForegroundService() {
    final Intent intentNotification = new Intent(this, MainActivity.class);
    final PendingIntent pi = PendingIntent.getActivity(this, 1, intentNotification, 0);
    final Notification notification = new Notification(R.drawable.uploading, "Uploading", System.currentTimeMillis());
    notification.setLatestEventInfo(this, "TrackMe", "Uploading Locations", pi);
    notification.flags |= Notification.FLAG_ONGOING_EVENT;
    startForeground(2, notification);
  }

  private void setAutoUpdate() {
    if (myPreference.isAutoUpdateSet()) {
      if (!pendingIntentExists(this)) {
        final int updateFrequence = myPreference.getUpdateFrequency();
        setUploadAlarm(this, AUTO_UPLOAD, updateFrequence);
      }
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

  private boolean uploadPossible(final long uploadTime) {
    final boolean userValidation = myPreference.userDetailsNotNull();
    final boolean serverLocationValidation = myPreference.serverLocationSet();
    final boolean dbValidation = db.getQueuedLocationsCount(uploadTime) > 0;
    final boolean networkValidation = isNetworkAvailable(this);
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
      getApplication().startActivity(UserError.makeIntent(getBaseContext(), message.toString()));
      cancelUploadAlarm(this);
    }

    return possible;
  }

  public static boolean isNetworkAvailable(final Context context) {
    final ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    final NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
    final boolean connected = (activeNetworkInfo != null && activeNetworkInfo.isConnected());
    return connected;
  }

  private boolean userAuthenticated(final String userID, final String passKey, final String serverURL) {
    final AndroidHttpClient http = AndroidHttpClient.newInstance("TrackMe");
    final HttpGet httpGet = new HttpGet(serverURL + "/api/v1/xml/validate");
    httpGet.addHeader("userid", userID);
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
      code = 0;
      e.printStackTrace();
      final Intent intentNotification = new Intent(this, MainActivity.class);
      final PendingIntent pi = PendingIntent.getActivity(this, 1, intentNotification, 0);
      final NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      final Notification notification = new Notification(R.drawable.uploading, "Upload Failed", System.currentTimeMillis());
      notification.setLatestEventInfo(this, "UploadFailed", "Unknown Server Error", pi);
      notification.flags |= Notification.FLAG_AUTO_CANCEL;
      notificationManager.notify(9, notification);
    }
    http.close();

    if (code == HttpStatus.SC_OK) {
      Log.d(UPLOAD_SERVICE_TAG, "valid");
      return true;
    } else if (code == -1) {
      Log.d(UPLOAD_SERVICE_TAG, "Invalid" + " " + code);
      getApplication().startActivity(UserError.makeIntent(getBaseContext(), message));
      cancelUploadAlarm(this);
      return false;
    } else if (code == 0) {
      cancelUploadAlarm(this);
      return false;
    } else {
      message = "Invalid UserID or PassKey";
      getApplication().startActivity(UserError.makeIntent(getBaseContext(), message));
      cancelUploadAlarm(this);
      return false;
    }
  }

  @Override
  public void onDestroy() {
    Log.d(UPLOAD_SERVICE_TAG, "Destroyed");
  }

}