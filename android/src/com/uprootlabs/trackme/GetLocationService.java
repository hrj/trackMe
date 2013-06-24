package com.uprootlabs.trackme;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

public class GetLocationService extends Service implements LocationListener, GooglePlayServicesClient.ConnectionCallbacks,
    GooglePlayServicesClient.OnConnectionFailedListener {

  private static final String LOCATION_TAG = "locationService";
  private static final int MILLISECONDS_PER_SECOND = 1000;
  private int captureFrequency;
  private LocationClient myLocationClient;
  private LocationRequest myLocationRequest;
  private int errorCode;
  private static final double PI_BY_180 = Math.PI / 180;
  LocationDBHelper myLocationDB = new LocationDBHelper(this);
  SharedPreferences myPreferences;

  @Override
  public void onCreate() {
    super.onCreate();
    myLocationClient = new LocationClient(this, this, this);
    Log.d(LOCATION_TAG, "Service Created");
  }

  @Override
  public IBinder onBind(Intent intent) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    myPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    setForegroundService();
    captureFrequency = (Integer.parseInt(myPreferences.getString("captureFrequency", "10"))) * MILLISECONDS_PER_SECOND;
    myLocationClient.connect();
    Log.d(LOCATION_TAG, "Service Started");
    return Service.START_STICKY;
  }

  private void setForegroundService() {
    Intent intentNotification = new Intent(this, MainActivity.class);
    PendingIntent pi = PendingIntent.getActivity(this, 1, intentNotification, 0);
    Notification notificaion = new Notification(R.drawable.ic_launcher, "Capturing", System.currentTimeMillis());
    notificaion.setLatestEventInfo(this, "TrackMe", "Capturing Locatoins", pi);
    notificaion.flags |= Notification.FLAG_ONGOING_EVENT;

    startForeground(1, notificaion);
  }

  @Override
  public void onDestroy() {
    if (myLocationClient.isConnected())
      myLocationClient.removeLocationUpdates(this);
    Log.d(LOCATION_TAG, "Destroyed");
  }

  @Override
  public void onLocationChanged(Location location) {
    long timeStamp = System.currentTimeMillis();
    Log.d(LOCATION_TAG, "Locations Changed");
    double lat = location.getLatitude() * PI_BY_180;
    double lng = location.getLongitude() * PI_BY_180;
    long acc = (long) location.getAccuracy();

    if (acc < 500) {
      SQLiteDatabase db = myLocationDB.getWritableDatabase();
//      SQLiteDatabase db = openOrCreateDatabase(LocationDBDetails.DATABASE_NAME, SQLiteDatabase.OPEN_READWRITE, null);
      ContentValues values = new ContentValues();
      values.put(LocationDBDetails.COLUMN_NAME_LAT, lat);
      values.put(LocationDBDetails.COLUMN_NAME_LNG, lng);
      values.put(LocationDBDetails.COLUMN_NAME_ACC, acc);
      values.put(LocationDBDetails.COLUMN_NAME_TS, timeStamp);
      db.insert(LocationDBDetails.TABLE_NAME, null, values);
      Log.d(LOCATION_TAG, "Location Added");
    } else {
      Log.d(LOCATION_TAG, "Location Denied");
    }

    Log.d(LOCATION_TAG, "" + lat + " " + lng + " " + acc + " " + timeStamp);
  }

  private boolean servicesConnected() {
    int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

    if (ConnectionResult.SUCCESS == resultCode) {
      return true;
    } else {
      Log.d(LOCATION_TAG, "Google Play Service Not Available");
      Intent dialogIntent = new Intent(getBaseContext(), DialogActivity.class);
      dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      dialogIntent.putExtra("errorCode", errorCode);
      getApplication().startActivity(dialogIntent);
      return false;
    }
  }

  @Override
  public void onConnectionFailed(ConnectionResult connectionResult) {
    // TODO Auto-generated method stub
    errorCode = connectionResult.getErrorCode();
    if (connectionResult.hasResolution()) {
      Log.d(LOCATION_TAG, "Start Resolution Error");
      // try {
      // connectionResult.startResolutionForResult(this,
      // CONNECTION_FAILURE_RESOLUTION_REQEUST);
      // } catch (IntentSender.SendIntentException e) {
      // e.printStackTrace();
      // }
    } else {
      Log.d(LOCATION_TAG, "Connection Failure");
      Intent dialogIntent = new Intent(getBaseContext(), DialogActivity.class);
      dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      dialogIntent.putExtra("errorCode", errorCode);
      getApplication().startActivity(dialogIntent);
    }
  }

  @Override
  public void onConnected(Bundle bundle) {
    // TODO Auto-generated method stub
    myLocationRequest = LocationRequest.create();
    myLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    myLocationRequest.setInterval(captureFrequency);
    myLocationRequest.setFastestInterval(captureFrequency);
    if (servicesConnected())
      myLocationClient.requestLocationUpdates(myLocationRequest, this);
  }

  @Override
  public void onDisconnected() {
    // TODO Auto-generated method stub

  }

}