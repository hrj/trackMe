package com.uprootlabs.trackme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;

final class TrackMeDB {
  private SQLiteDatabase db;
  private MyPreference myPreferences;

  private class Tuple {
    final private String sessionID;
    final private int batchID;

    public Tuple(String sessionID, int batchID) {
      this.sessionID = sessionID;
      this.batchID = batchID;
    }

    public String getSessionID() {
      return sessionID;
    }

    private int getBatchID() {
      return batchID;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public boolean equals(Object obj) {
      Tuple t = (Tuple) obj;
      return (t.sessionID.equals(sessionID) && t.batchID == batchID);
    }
  }

  public TrackMeDB(final SQLiteDatabase db, Context context) {
    this.db = db;
    myPreferences = new MyPreference(context);
  }

  public boolean insertLocations(Location location, final long timeStamp) {
    final double lat = location.getLatitude() * TrackMeHelper.PI_BY_180;
    final double lng = location.getLongitude() * TrackMeHelper.PI_BY_180;
    final long acc = (long) location.getAccuracy();

    if (acc <= TrackMeDBDetails.LOCATIONS_ACCURACY_LIMIT) {
      final ContentValues values = new ContentValues();

      values.put(TrackMeDBDetails.COLUMN_NAME_SESSION_ID, myPreferences.getSessionID());
      values.put(TrackMeDBDetails.COLUMN_NAME_LAT, lat);
      values.put(TrackMeDBDetails.COLUMN_NAME_LNG, lng);
      values.put(TrackMeDBDetails.COLUMN_NAME_ACC, acc);
      values.put(TrackMeDBDetails.COLUMN_NAME_TS, timeStamp);

      db.insert(TrackMeDBDetails.LOCATION_TABLE_NAME, null, values);
      return true;
    } else {
      return false;
    }
  }

  private Cursor getLocations(final String selection, final String[] selectionArgs, final String orderBy, final String limit) {

    final String[] columns = { TrackMeDBDetails.COLUMN_NAME_SESSION_ID, TrackMeDBDetails.COLUMN_NAME_LAT, TrackMeDBDetails.COLUMN_NAME_LNG,
        TrackMeDBDetails.COLUMN_NAME_ACC, TrackMeDBDetails.COLUMN_NAME_TS, TrackMeDBDetails.COLUMN_NAME_BATCH_ID,
        TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID };
    Cursor c = db.query(TrackMeDBDetails.LOCATION_TABLE_NAME, columns, selection, selectionArgs, null, null, orderBy, limit);
    return c;
  }

  public String getLocationsAsXML(final long time) {
    final String locations;
    final int uploadID = myPreferences.getNewUploadID();
    assignUploadID(uploadID);
    Cursor c = getLocationsByUploadID(uploadID);
    final Map<Tuple, List<String>> sessionLocations = batching(c, uploadID);
    final List<String> sessionIDs = getSessionIDs(sessionLocations.keySet());
    final List<Integer> batchIDs = getBatchIDs(sessionIDs);
    updateBatchID(batchIDs, sessionIDs, uploadID, c);
    // final String selection = TrackMeDBDetails.COLUMN_NAME_TS + " < ? AND " +
    // TrackMeDBDetails.COLUMN_NAME_STATUS + " != ?";
    // final String[] selectionArgs = { String.valueOf(time), "1" };
    // final String orderBy = TrackMeDBDetails.COLUMN_NAME_TS + " ASC";

    locations = locationsToXML(c);

    c.close();

    return locations;
  }

  private String locationsToXML(Cursor c) {
    final StringBuffer locations = new StringBuffer();
    c.moveToFirst();
    do {

      final double latitude = c.getDouble(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_LAT));
      final double longitude = c.getDouble(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_LNG));
      final long accuracy = (long) c.getDouble(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_ACC));
      final long timeStamp = (long) c.getDouble(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_TS));
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
    return locations.toString();
  }


  private void updateBatchID(List<String> batchIDs, List<String> sessionIDs, int uploadID, Cursor c) {
    // TODO Auto-generated method stub

  }

  private Cursor getLocationsByUploadID(int uploadID) {
    // TODO Auto-generated method stub
    return null;
  }

  public int getQueuedLocationsCount() {
    // TODO the number of locations queued
    return 0;
  }

  public void assignUploadID(final int uploadID) {
    // TODO assigning a upload batch with an uploadID

  }

  private int getBatchID(String sessionID) {
    final String[] columns = { TrackMeDBDetails.COLUMN_NAME_BATCH_ID };
    final String selection = TrackMeDBDetails.COLUMN_NAME_SESSION_ID + "=?";
    final String[] selectionArgs = { String.valueOf(sessionID) };
    int batchID;
    Cursor c = db.query(TrackMeDBDetails.LOCATION_TABLE_NAME, columns, selection, selectionArgs, null, null, null, null);
    if (c.moveToFirst()) {
      batchID = c.getInt(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_BATCH_ID));
    } else {
      batchID = TrackMeDBDetails.FIRST_BATCH_ID;
    }
    c.close();
    return batchID;
  }

  private int getNewBatchID(String sessionID) {
    int batchID = getBatchID(sessionID);
    batchID = batchID + 1;
    return batchID;
  }

  public void updateBatchIDs(final Map<String, Integer> sessionBatches, final int uploadID) {
    for (Map.Entry<String, Integer> session : sessionBatches.entrySet()) {
      String sessionID = session.getKey();
      int batchID = session.getValue();
      final ContentValues values = new ContentValues();
      values.put(TrackMeDBDetails.COLUMN_NAME_BATCH_ID, batchID);
      if (batchID == TrackMeDBDetails.FIRST_BATCH_ID) {
        db.insert(TrackMeDBDetails.SESSION_TABLE_NAME, null, values);
      } else {
        db.update(TrackMeDBDetails.SESSION_TABLE_NAME, values, TrackMeDBDetails.COLUMN_NAME_SESSION_ID + "=" + sessionID, null);
      }
      db.update(TrackMeDBDetails.LOCATION_TABLE_NAME, values, TrackMeDBDetails.COLUMN_NAME_SESSION_ID + "=" + sessionID, null);
    }
  }


  public List<Integer> getBatchIDs(List<String> sessionIDs) {
    List<Integer> batchIDs = new ArrayList<Integer>();
    for (String sessionID : sessionIDs) {
      batchIDs.add(getBatchID(sessionID));
    }
    return batchIDs;
  }

  public void moveLocations(final int uploadID, final List<String> sessions) {
    // TODO move the locations from current table to its respective table after
    // upload

  }

  private Map<Tuple, List<String>> batching(final Cursor c, final int uploadID) {
    Map<Tuple, List<String>> map = new HashMap<Tuple, List<String>>();
    Map<String, Integer> sessionBatches = new HashMap<String, Integer>();
    int batchID;
    c.moveToFirst();
    do {
      final String sessionID = c.getString(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_SESSION_ID));
      if (sessionBatches.get(sessionID) == null) {
        sessionBatches.put(sessionID, getNewBatchID(sessionID));
      }
      try {
        batchID = c.getInt(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_BATCH_ID));
      } catch (android.database.SQLException e) {
        batchID = sessionBatches.get(sessionID);
      }
      final double latitude = c.getDouble(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_LAT));
      final double longitude = c.getDouble(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_LNG));
      final long accuracy = (long) c.getDouble(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_ACC));
      final long timeStamp = (long) c.getDouble(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_TS));

      Tuple mapKey = new Tuple(sessionID, batchID);
      List<String> batch = map.get(mapKey);
      String location = "<loc lat=\"" + latitude + "\" lng=\"" + longitude + "\" acc=\"" + accuracy + "\" ts=\"" + timeStamp + "\" />";

      if (batch == null)
        map.put(mapKey, batch = new ArrayList<String>());
      batch.add(location);

    } while (c.moveToNext());
    c.close();

    updateBatchIDs(sessionBatches, uploadID);

    return map;
  }

  private List<String> getSessionIDs(Set<Tuple> tuples) {
    List<String> sessionIDs = new ArrayList<String>();
    for (Tuple t : tuples) {
      if (!sessionIDs.contains(t.sessionID)) {
        sessionIDs.add(t.sessionID);
      }
    }
    return sessionIDs;
  }

  private StringBuffer parseXML(final Cursor c) {
    return new StringBuffer();
  }


  public void clearUploadIDs() {

  }

}