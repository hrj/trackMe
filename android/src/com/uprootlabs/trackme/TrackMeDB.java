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

    private String getSessionID() {
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
    final int uploadID = myPreferences.getNewUploadID();
    assignUploadID(uploadID, time);
    Cursor c = getLocationsByUploadID(uploadID);
    final Map<Tuple, List<String>> sessionLocations = batching(c, uploadID);

    return locationsToXML(sessionLocations, uploadID);
  }

  private String locationsToXML(Map<Tuple, List<String>> sessions, int uploadID) {
    final StringBuffer locationsAsXML = new StringBuffer();
    String userID = myPreferences.getUserID();
    String passKey = myPreferences.getPassKey();
    locationsAsXML.append("<sessions userID=\"" + userID + "\" passKey=\"" + passKey + "\" uid=\"" + uploadID);
    for (Map.Entry<Tuple, List<String>> session : sessions.entrySet()) {
      StringBuffer batch = new StringBuffer();
      Tuple t = session.getKey();
      // TODO toString does not work
      String locations = session.getValue().toString();
      batch.append("<session sid=\"" + t.getSessionID() + "\" bid=\"" + t.getBatchID() + "\">");
      batch.append(locations);
      batch.append("</session>");
      locationsAsXML.append(batch);
    }
    locationsAsXML.append("</sessions>");
    return locationsAsXML.toString();
  }

  private Cursor getLocationsByUploadID(int uploadID) {
    final String[] columns = { TrackMeDBDetails.COLUMN_NAME_SESSION_ID, TrackMeDBDetails.COLUMN_NAME_LAT, TrackMeDBDetails.COLUMN_NAME_LNG,
        TrackMeDBDetails.COLUMN_NAME_ACC, TrackMeDBDetails.COLUMN_NAME_TS, TrackMeDBDetails.COLUMN_NAME_BATCH_ID,
        TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID };
    Cursor c = db.query(TrackMeDBDetails.LOCATION_TABLE_NAME, columns, TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + "=" + uploadID, null, null,
        null, "ASC", TrackMeDBDetails.LOCATIONS_QUERY_LIMIT);
    return c;
  }

  public int getQueuedLocationsCount() {
    // TODO the number of locations queued
    return 0;
  }

  public void assignUploadID(final int uploadID, final long uploadTime) {
    String sql = "UPDATE " + TrackMeDBDetails.LOCATION_TABLE_NAME + " SET " + TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + " = " + uploadID
        + " WHERE " + TrackMeDBDetails.COLUMN_NAME_TS + " < " + uploadTime + " ORDER BY " + TrackMeDBDetails.COLUMN_NAME_TS + " ASC "
        + " LIMIT " + TrackMeDBDetails.LOCATIONS_QUERY_LIMIT;
    db.execSQL(sql);
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
      String where = TrackMeDBDetails.COLUMN_NAME_SESSION_ID + " = " + sessionID + " AND " + TrackMeDBDetails.COLUMN_NAME_BATCH_ID
          + " = null" + " AND " + TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + " = " + uploadID;
      db.update(TrackMeDBDetails.LOCATION_TABLE_NAME, values, where, null);
    }
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

  public void clearUploadIDs() {
    // TODO
  }

}