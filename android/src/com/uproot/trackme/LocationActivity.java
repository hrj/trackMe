/*

 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uproot.trackme;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class LocationActivity extends FragmentActivity {
  private TextView mLatLng;
  private TextView mAddress;
  private LocationManager mLocationManager;
  private Handler mHandler;
  private boolean mGeocoderAvailable;
  private boolean mUseFine;
  private boolean mUseBoth;
  public int code;

  // Keys for maintaining UI states after rotation.
  private static final String KEY_FINE = "use_fine";
  private static final String KEY_BOTH = "use_both";
  // UI handler codes.
  private static final int UPDATE_ADDRESS = 1;
  private static final int UPDATE_LATLNG = 2;

  private static final int TEN_METERS = 10;
  private static final int TWO_MINUTES = 1000 * 60 * 2;
  private static final double PI_BY_180 = Math.PI / 180;

  private static final int ckX = 250;
  private static final int ckY = 950;

  public static final String DATABASE_NAME = "GPSLOGGERDB";
  public static final String POINTS_TABLE_NAME = "LOCATION_POINTS";
  private SQLiteDatabase db;

  public String passkey = com.uproot.trackme.LoginActivity.psk;
  public String userid = com.uproot.trackme.LoginActivity.uid;
  public int setF = com.uproot.trackme.LoginActivity.prog;
  public String fileContents = "";
  Timer myTimer = new Timer();

  /**
   * This sample demonstrates how to incorporate location based services in your
   * app and process location updates. The app also shows how to convert
   * lat/long coordinates to human-readable addresses.
   */
  @SuppressLint({ "NewApi", "HandlerLeak" })
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    // clearDB();
    initDatabase();

    Button btn1 = (Button) findViewById(R.id.btn1);
    btn1.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        // TODO Auto-generated method stub
        finish();
      }
    });

    if (savedInstanceState != null) {
      mUseFine = savedInstanceState.getBoolean(KEY_FINE);
      mUseBoth = savedInstanceState.getBoolean(KEY_BOTH);
    } else {
      mUseFine = false;
      mUseBoth = false;
    }
    mUseFine = false;
    mUseBoth = true;
    mLatLng = (TextView) findViewById(R.id.latlng);
    mAddress = (TextView) findViewById(R.id.address);

    // The isPresent() helper method is only available on Gingerbread or
    // above.
    mGeocoderAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && Geocoder.isPresent();

    // Handler for updating text fields on the UI like the lat/long and
    // address.
    mHandler = new Handler() {
      public void handleMessage(Message msg) {
        switch (msg.what) {
        case UPDATE_ADDRESS:
          mAddress.setText((String) msg.obj);
          break;
        case UPDATE_LATLNG:
          mLatLng.setText((String) msg.obj);
          break;
        }
      }
    };
    // Get a reference to the LocationManager object.
    mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    setup();

    myTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        getLocs();
      }
    }, 0, setF * 1000);

  }

  // initializing database

  protected void getLocs() {
    this.runOnUiThread(Timer_Tick);
  } // end TimerMethod

  private Runnable Timer_Tick = new Runnable() {
    public void run() {
      setup();
    }
  };

  // Restores UI states after rotation.
  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(KEY_FINE, mUseFine);
    outState.putBoolean(KEY_BOTH, mUseBoth);
  }

  @Override
  protected void onResume() {
    super.onResume();
  }

  @Override
  protected void onStart() {
    super.onStart();

    // Check if the GPS setting is currently enabled on the device.
    // This verification should be done during onStart() because the system
    // calls this method
    // when the user returns to the activity, which ensures the desired
    // location provider is
    // enabled each time the activity resumes from the stopped state.
    LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    final boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

    if (!gpsEnabled) {
      // Build an alert dialog here that requests that the user enable
      // the location services, then when the user clicks the "OK" button,
      // call enableLocationSettings()
      new EnableGpsDialogFragment().show(getSupportFragmentManager(), "enableGpsDialog");
    }
    setup();
  }

  // Method to launch Settings
  private void enableLocationSettings() {
    Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    startActivity(settingsIntent);
  }

  // Stop receiving location updates whenever the Activity becomes invisible.
  @Override
  protected void onStop() {
    super.onStop();
    mLocationManager.removeUpdates(listener);
    myTimer.cancel();
  }

  // Set up fine and/or coarse location providers depending on whether the
  // fine provider or
  // both providers button is pressed.
  private void setup() {
    Location gpsLocation = null;
    Location networkLocation = null;
    mLocationManager.removeUpdates(listener);
    mLatLng.setText(R.string.unknown);
    mAddress.setText(R.string.unknown);
    // Get fine location updates only.
    if (mUseFine) {
      // Request updates from just the fine (gps) provider.
      gpsLocation = requestUpdatesFromProvider(LocationManager.GPS_PROVIDER, R.string.not_support_gps);
      // Update the UI immediately if a location is obtained.
      if (gpsLocation != null)
        updateUILocation(gpsLocation);
    } else if (mUseBoth) {
      // Get coarse and fine location updates.
      // Request updates from both fine (gps) and coarse (network)
      // providers.
      gpsLocation = requestUpdatesFromProvider(LocationManager.GPS_PROVIDER, R.string.not_support_gps);
      networkLocation = requestUpdatesFromProvider(LocationManager.NETWORK_PROVIDER, R.string.not_support_network);

      // If both providers return last known locations, compare the two
      // and use the better
      // one to update the UI. If only one provider returns a location,
      // use it.
      if (gpsLocation != null && networkLocation != null) {
        updateUILocation(getBetterLocation(gpsLocation, networkLocation));
      } else if (gpsLocation != null) {
        updateUILocation(gpsLocation);
      } else if (networkLocation != null) {
        updateUILocation(networkLocation);
      }
    }
  }

  /**
   * Method to register location updates with a desired location provider. If
   * the requested provider is not available on the device, the app displays a
   * Toast with a message referenced by a resource id.
   * 
   * @param provider
   *          Name of the requested provider.
   * @param errorResId
   *          Resource id for the string message to be displayed if the provider
   *          does not exist on the device.
   * @return A previously returned {@link android.location.Location} from the
   *         requested provider, if exists.
   */
  private Location requestUpdatesFromProvider(final String provider, final int errorResId) {
    Location location = null;
    if (mLocationManager.isProviderEnabled(provider)) {
      mLocationManager.requestLocationUpdates(provider, 1000 * setF, TEN_METERS, listener);
      location = mLocationManager.getLastKnownLocation(provider);
    } else {
      Toast.makeText(this, errorResId, Toast.LENGTH_LONG).show();
    }
    return location;
  }

  private void doReverseGeocoding(Location location) {
    // Since the geocoding API is synchronous and may take a while. You
    // don't want to lock
    // up the UI thread. Invoking reverse geocoding in an AsyncTask.
    (new ReverseGeocodingTask(this)).execute(new Location[] { location });
  }

  private void updateUILocation(Location location) {
    // We're sending the update to a handler which then updates the UI with
    // the new
    // location.
    Message.obtain(mHandler, UPDATE_LATLNG, location.getLatitude() + ", " + location.getLongitude()).sendToTarget();

    // Bypass reverse-geocoding only if the Geocoder service is available on
    // the device.
    if (mGeocoderAvailable)
      doReverseGeocoding(location);

  }

  private final LocationListener listener = new LocationListener() {

    @Override
    public void onLocationChanged(Location location) {
      // A new location update is received. Do something useful with it.
      // Update the UI with
      // the location update.
      if (location.hasAccuracy() && location.getAccuracy() < 500) {
        updateUILocation(location);
        insertDb(location);
        try {
          showData();
        } catch (IOException e) {
          Toast("IO exception", ckX, ckY);
        }
      }
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }
  };

  /**
   * Determines whether one Location reading is better than the current Location
   * fix. Code taken from http://developer.android.com/guide/topics/location
   * /obtaining-user-location.html
   * 
   * @param newLocation
   *          The new Location that you want to evaluate
   * @param currentBestLocation
   *          The current Location fix, to which you want to compare the new one
   * @return The better Location object based on recency and accuracy.
   */
  protected Location getBetterLocation(Location newLocation, Location currentBestLocation) {
    if (currentBestLocation == null) {
      // A new location is always better than no location
      return newLocation;
    }

    // Check whether the new location fix is newer or older
    long timeDelta = newLocation.getTime() - currentBestLocation.getTime();
    boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
    boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
    boolean isNewer = timeDelta > 0;

    // If it's been more than two minutes since the current location, use
    // the new location
    // because the user has likely moved.
    if (isSignificantlyNewer) {
      return newLocation;
      // If the new location is more than two minutes older, it must be
      // worse
    } else if (isSignificantlyOlder) {
      return currentBestLocation;
    }

    // Check whether the new location fix is more or less accurate
    int accuracyDelta = (int) (newLocation.getAccuracy() - currentBestLocation.getAccuracy());
    boolean isLessAccurate = accuracyDelta > 0;
    boolean isMoreAccurate = accuracyDelta < 0;
    boolean isSignificantlyLessAccurate = accuracyDelta > 200;

    // Check if the old and new location are from the same provider
    boolean isFromSameProvider = isSameProvider(newLocation.getProvider(), currentBestLocation.getProvider());

    // Determine location quality using a combination of timeliness and
    // accuracy
    if (isMoreAccurate) {
      return newLocation;
    } else if (isNewer && !isLessAccurate) {
      return newLocation;
    } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
      return newLocation;
    }
    return currentBestLocation;
  }

  /** Checks whether two providers are the same */
  private boolean isSameProvider(String provider1, String provider2) {
    if (provider1 == null) {
      return provider2 == null;
    }
    return provider1.equals(provider2);
  }

  // AsyncTask encapsulating the reverse-geocoding API. Since the geocoder API
  // is blocked,
  // we do not want to invoke it from the UI thread.
  private class ReverseGeocodingTask extends AsyncTask<Location, Void, Void> {
    Context mContext;

    public ReverseGeocodingTask(Context context) {
      super();
      mContext = context;
    }

    @Override
    protected Void doInBackground(Location... params) {
      Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());

      Location loc = params[0];
      List<Address> addresses = null;
      try {
        addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
      } catch (IOException e) {
        e.printStackTrace();
        // Update address field with the exception.
        Message.obtain(mHandler, UPDATE_ADDRESS, e.toString()).sendToTarget();
      }
      if (addresses != null && addresses.size() > 0) {
        Address address = addresses.get(0);
        // Format the first line of address (if available), city, and
        // country name.
        String addressText = String.format("%s, %s, %s", address.getMaxAddressLineIndex() > 0 ? address.getAddressLine(0) : "",
            address.getLocality(), address.getCountryName());
        // Update address field on UI.
        Message.obtain(mHandler, UPDATE_ADDRESS, addressText).sendToTarget();
      }
      return null;
    }
  }

  /**
   * Dialog to prompt users to enable GPS on the device.
   */
  @SuppressLint("ValidFragment")
  private class EnableGpsDialogFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      return new AlertDialog.Builder(getActivity()).setTitle(R.string.enable_gps).setMessage(R.string.enable_gps_dialog)
          .setPositiveButton(R.string.enable_gps, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              enableLocationSettings();
            }
          }).create();
    }
  }

  private void initDatabase() {
    db = openOrCreateDatabase(DATABASE_NAME, SQLiteDatabase.OPEN_READWRITE, null);

    db.execSQL("CREATE TABLE IF NOT EXISTS " + POINTS_TABLE_NAME + " (LATITUDE REAL, LONGITUDE REAL,GMTTIMESTAMP BIGINT, ACCURACY INT);");

    db.close();

  }

  private void insertDb(Location location) {
    try {
      StringBuffer queryBuf = new StringBuffer();
      double lat = (location.getLatitude()) * PI_BY_180;
      double lng = (location.getLongitude()) * PI_BY_180;
      long timeStamp = System.currentTimeMillis();
      long acc = (long) location.getAccuracy();

      queryBuf.append("INSERT INTO " + POINTS_TABLE_NAME + "(LATITUDE,LONGITUDE,GMTTIMESTAMP,ACCURACY) VALUES (" + lat + "," + lng + ","
          + timeStamp + "," + acc + ");");

      db = openOrCreateDatabase(DATABASE_NAME, SQLiteDatabase.OPEN_READWRITE, null);

      db.execSQL(queryBuf.toString());

    } catch (Exception e) {
    } finally {
      if (db.isOpen())
        db.close();
    }
  }

  public void showData() throws IOException {

    SQLiteDatabase db = null;
    Cursor cursor = null;
    db = openOrCreateDatabase(DATABASE_NAME, SQLiteDatabase.OPEN_READWRITE, null);
    cursor = db.rawQuery("SELECT * " + " FROM " + POINTS_TABLE_NAME + " ORDER BY GMTTIMESTAMP ASC", null);

    StringBuffer str = new StringBuffer("<session id=\"chetan123\" userid=\"" + userid + "\" passkey=\"" + passkey + "\">");

    int latitudeColumnIndex = cursor.getColumnIndexOrThrow("LATITUDE");
    int longitudeColumnIndex = cursor.getColumnIndexOrThrow("LONGITUDE");
    int TSColumnIndex = cursor.getColumnIndexOrThrow("GMTTIMESTAMP");
    int ACCColumnIndex = cursor.getColumnIndexOrThrow("ACCURACY");
    if (cursor.moveToFirst()) {
      do {
        double latitude = cursor.getDouble(latitudeColumnIndex);
        double longitude = cursor.getDouble(longitudeColumnIndex);
        long timestamp = (long) cursor.getDouble(TSColumnIndex);
        long accuracy = (long) cursor.getDouble(ACCColumnIndex);
        str.append("<location latitude=\"");
        str.append(latitude);
        str.append("\" longitude=\"");
        str.append(longitude);
        str.append("\" accuracy=\"");
        str.append(accuracy);
        str.append("\" timestamp=\"");
        str.append(timestamp);
        str.append("\" />");
      } while (cursor.moveToNext());
      str.append("</session>");
      fileContents = str.toString();
    }
    db.close();
  }

  @SuppressLint("NewApi")
  public void sendFunc(View view) {
    new Thread() {
      public void run() {

        String baseURL = "https://testtrackme.appspot.com";
        // String baseURL = "http://10.0.0.5:8888";
        AndroidHttpClient http = AndroidHttpClient.newInstance("trackMe");
        HttpPost httppost = new HttpPost(baseURL + "/api/xml/store?userId=" + userid + "&passKey=" + passkey);

        GzipHelper.setCompressedEntity(LocationActivity.this, fileContents, httppost);
        try {
          httppost.addHeader("userID", userid);
          httppost.addHeader("passkey", "123456");
          long execTS = System.currentTimeMillis();
          HttpResponse response = http.execute(httppost);
          code = response.getStatusLine().getStatusCode();
          http.close();
          if (code == HttpStatus.SC_OK) {
            clearDB(execTS);
            initDatabase();
          } else if (code == HttpStatus.SC_BAD_REQUEST || code == 500) {
            //TODO Inform user about the failure
          }

        } catch (ClientProtocolException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (IOException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }

      }
    }.start();

  }

  public void clearDB(long ts) {
    String whereClause = "GMTTIMESTAMP < ?";// + ts;
    String[] val = new String[] {"" + ts};
    db = openOrCreateDatabase(DATABASE_NAME, SQLiteDatabase.OPEN_READWRITE, null);
    int stat = db.delete(POINTS_TABLE_NAME, whereClause, val);
    db.close();
  }

  public void Toast(String s, int Xoff, int Yoff) {
    Context context = getApplicationContext();
    CharSequence text = s;
    int duration = Toast.LENGTH_LONG;
    Toast toast = Toast.makeText(context, text, duration);
    toast.setGravity(Gravity.TOP | Gravity.LEFT, Xoff, Yoff);
    toast.show();
  }

}
