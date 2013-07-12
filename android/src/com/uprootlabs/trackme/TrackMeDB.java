package com.uprootlabs.trackme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;

final class TrackMeDB {
  private final SQLiteDatabase db;
  private final MyPreference myPreferences;

  public TrackMeDB(final SQLiteDatabase db, final Context context) {
    this.db = db;
    myPreferences = new MyPreference(context);
  }

  public boolean insertNewLocations(final Location location, final long timeStamp) {
    final double lat = location.getLatitude() * TrackMeHelper.PI_BY_180;
    final double lng = location.getLongitude() * TrackMeHelper.PI_BY_180;
    final long acc = (long) location.getAccuracy();
    final String sessionID = myPreferences.getSessionID();

    if (acc <= TrackMeDBDetails.LOCATIONS_ACCURACY_LIMIT) {
      final ContentValues values = new ContentValues();
      values.put(TrackMeDBDetails.COLUMN_NAME_SESSION_ID, sessionID);
      values.put(TrackMeDBDetails.COLUMN_NAME_LAT, lat);
      values.put(TrackMeDBDetails.COLUMN_NAME_LNG, lng);
      values.put(TrackMeDBDetails.COLUMN_NAME_ACC, acc);
      values.put(TrackMeDBDetails.COLUMN_NAME_TS, timeStamp);

      db.insert(TrackMeDBDetails.TABLE_LOCATIONS, null, values);
      return true;
    } else {
      return false;
    }
  }

  // private Cursor getLocations(final String selection, final String[]
  // selectionArgs, final String orderBy, final String limit) {
  //
  // final String[] columns = { TrackMeDBDetails.COLUMN_NAME_SESSION_ID,
  // TrackMeDBDetails.COLUMN_NAME_LAT, TrackMeDBDetails.COLUMN_NAME_LNG,
  // TrackMeDBDetails.COLUMN_NAME_ACC, TrackMeDBDetails.COLUMN_NAME_TS,
  // TrackMeDBDetails.COLUMN_NAME_BATCH_ID,
  // TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID };
  // Cursor c = db.query(TrackMeDBDetails.TABLE_LOCATIONS, columns,
  // selection, selectionArgs, null, null, orderBy, limit);
  // return c;
  // }

  public String getLocationsAsXML(final long time) {
    final int uploadID = myPreferences.getNewUploadID();
    assignUploadID(uploadID, time);
    final Cursor c = getLocationsByUploadID(uploadID);

    final Map<SessionBatchTuple, List<String>> sessionLocations = batching(c, uploadID);
    return locationsToXML(sessionLocations, uploadID);
  }

  private String mkString(final List<String> arrayString) {
    final StringBuffer batch = new StringBuffer();
    for (final String loc : arrayString) {
      batch.append(loc);
    }
    return batch.toString();
  }

  private String locationsToXML(final Map<SessionBatchTuple, List<String>> sessions, final int uploadID) {
    final StringBuffer locationsAsXML = new StringBuffer();
    final String userID = myPreferences.getUserID();
    final String passKey = myPreferences.getPassKey();
    locationsAsXML.append("<upload userid=\"" + userID + "\" passkey=\"" + passKey + "\" uid=\"" + uploadID + "\">");
    for (final Map.Entry<SessionBatchTuple, List<String>> session : sessions.entrySet()) {
      final StringBuffer batch = new StringBuffer();
      final SessionBatchTuple t = session.getKey();
      final String locations = mkString(session.getValue());
      batch.append("<batch sid=\"" + t.getSessionID() + "\" bid=\"" + t.getBatchID() + "\">");
      batch.append(locations);
      batch.append("</batch>");
      locationsAsXML.append(batch);
    }
    locationsAsXML.append("</upload>");
    return locationsAsXML.toString();
  }

  private Cursor getLocationsByUploadID(final int uploadID) {
    final String[] columns = { TrackMeDBDetails.COLUMN_NAME_SESSION_ID, TrackMeDBDetails.COLUMN_NAME_LAT, TrackMeDBDetails.COLUMN_NAME_LNG,
        TrackMeDBDetails.COLUMN_NAME_ACC, TrackMeDBDetails.COLUMN_NAME_TS, TrackMeDBDetails.COLUMN_NAME_BATCH_ID,
        TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID };
    final Cursor c = db.query(TrackMeDBDetails.TABLE_LOCATIONS, columns, TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + "=" + uploadID, null,
        null, null, TrackMeDBDetails.COLUMN_NAME_TS + " ASC", TrackMeDBDetails.LOCATIONS_QUERY_LIMIT);
    return c;
  }

  public int getQueuedLocationsCount(final long uploadTime) {
    final String[] columns = { TrackMeDBDetails._ID };
    final Cursor c = db.query(TrackMeDBDetails.TABLE_LOCATIONS, columns, TrackMeDBDetails.COLUMN_NAME_TS + "<=" + uploadTime, null, null,
        null, null, null);
    final int count = c.getCount();
    c.close();
    return count;
  }

  public void assignUploadID(final int uploadID, final long uploadTime) {
    final String select = "SELECT " + TrackMeDBDetails._ID + " FROM " + TrackMeDBDetails.TABLE_LOCATIONS + " WHERE "
        + TrackMeDBDetails.COLUMN_NAME_TS + " < " + uploadTime + " ORDER BY " + TrackMeDBDetails.COLUMN_NAME_TS + " ASC " + " LIMIT "
        + TrackMeDBDetails.LOCATIONS_QUERY_LIMIT;
    final String sql = "UPDATE " + TrackMeDBDetails.TABLE_LOCATIONS + " SET " + TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + " = " + uploadID
        + " WHERE " + TrackMeDBDetails._ID + " IN " + "(" + select + ")";
    db.execSQL(sql);
  }

  private int getBatchID(final String sessionID) {
    final String[] columns = { TrackMeDBDetails.COLUMN_NAME_LAST_BATCH_ID };
    final String selection = TrackMeDBDetails.COLUMN_NAME_SESSION_ID + "=?";
    final String[] selectionArgs = { String.valueOf(sessionID) };
    int batchID;
    final Cursor c = db.query(TrackMeDBDetails.TABLE_SESSION, columns, selection, selectionArgs, null, null, null, null);
    if (c.moveToFirst()) {
      batchID = c.getInt(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_LAST_BATCH_ID));
    } else {
      newSession(sessionID);
      batchID = TrackMeDBDetails.FIRST_BATCH_ID;
    }
    c.close();
    return batchID;
  }

  private int getNewBatchID(final String sessionID) {
    int batchID = getBatchID(sessionID);
    batchID = batchID + 1;
    return batchID;
  }

  public void updateBatchIDs(final Map<String, Integer> sessionBatches, final int uploadID) {
    for (final Map.Entry<String, Integer> session : sessionBatches.entrySet()) {
      final String sessionID = session.getKey();
      final int batchID = session.getValue();
      final ContentValues lvalues = new ContentValues();
      lvalues.put(TrackMeDBDetails.COLUMN_NAME_LAST_BATCH_ID, batchID);
      final String selection = TrackMeDBDetails.COLUMN_NAME_SESSION_ID + "=?";
      final String[] selectionArgs = { sessionID };
      db.update(TrackMeDBDetails.TABLE_SESSION, lvalues, selection, selectionArgs);

      final ContentValues values = new ContentValues();
      values.put(TrackMeDBDetails.COLUMN_NAME_BATCH_ID, batchID);
      final String where = TrackMeDBDetails.COLUMN_NAME_SESSION_ID + "=? AND " + TrackMeDBDetails.COLUMN_NAME_BATCH_ID + " is null AND "
          + TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + "=?";
      final String[] whereArgs = new String[] { sessionID, "" + uploadID };
      db.update(TrackMeDBDetails.TABLE_LOCATIONS, values, where, whereArgs);

    }
  }

  private int moveLocations(final String tableName, final int uploadID, final String sessionID, final int batchID) {
    final String where =  TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + "=" + uploadID + " AND "
        + TrackMeDBDetails.COLUMN_NAME_SESSION_ID + "=\"" + sessionID + "\" AND " + TrackMeDBDetails.COLUMN_NAME_BATCH_ID + "=" + batchID;

    final String cols = TrackMeDBDetails.COLUMN_NAME_SESSION_ID + ", " + TrackMeDBDetails.COLUMN_NAME_LAT + ", "
        + TrackMeDBDetails.COLUMN_NAME_LNG + ", " + TrackMeDBDetails.COLUMN_NAME_ACC + ", " + TrackMeDBDetails.COLUMN_NAME_TS + ", "
        + TrackMeDBDetails.COLUMN_NAME_BATCH_ID;

    final String moveSql = "INSERT INTO " + tableName + " (" + cols + ") " + "SELECT " + cols + " FROM " + TrackMeDBDetails.TABLE_LOCATIONS
        + " WHERE " + where;

    db.execSQL(moveSql);

    return db.delete(TrackMeDBDetails.TABLE_LOCATIONS, where, null);
  }

  public int archiveLocations(final int uploadID, final String sessionID, final int batchID) {
    return moveLocations(TrackMeDBDetails.TABLE_ARCHIVED_LOCATIONS, uploadID, sessionID, batchID);
  }

  public int moveLocationsToSessionTable(final int uploadID, final String sessionID, final int batchID) {
    db.execSQL(TrackMeDBHelper.makeSessionTableSQL(sessionID));
    return moveLocations(sessionID, uploadID, sessionID, batchID);
  }

  private Map<SessionBatchTuple, List<String>> batching(final Cursor c, final int uploadID) {
    final Map<SessionBatchTuple, List<String>> map = new HashMap<SessionBatchTuple, List<String>>();
    final Map<String, Integer> sessionBatches = new HashMap<String, Integer>();
    int batchID;
    c.moveToFirst();
    do {
      final String sessionID = c.getString(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_SESSION_ID));
      if (sessionBatches.get(sessionID) == null) {
        sessionBatches.put(sessionID, getNewBatchID(sessionID));
      }
      try {
        batchID = c.getInt(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_BATCH_ID));
        if (batchID == 0) {
          batchID = sessionBatches.get(sessionID);
        }
      } catch (final android.database.SQLException e) {
        batchID = sessionBatches.get(sessionID);
      }
      final double latitude = c.getDouble(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_LAT));
      final double longitude = c.getDouble(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_LNG));
      final long accuracy = (long) c.getDouble(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_ACC));
      final long timeStamp = (long) c.getDouble(c.getColumnIndexOrThrow(TrackMeDBDetails.COLUMN_NAME_TS));

      final SessionBatchTuple mapKey = new SessionBatchTuple(sessionID, batchID);
      List<String> batch = map.get(mapKey);
      final String location = "<loc lat=\"" + latitude + "\" lng=\"" + longitude + "\" acc=\"" + accuracy + "\" ts=\"" + timeStamp
          + "\" />";

      if (batch == null)
        map.put(mapKey, batch = new ArrayList<String>());
      batch.add(location);

    } while (c.moveToNext());
    c.close();

    updateBatchIDs(sessionBatches, uploadID);

    return map;
  }

  public void newSession(final String sessionID) {
    final ContentValues values = new ContentValues();
    values.put(TrackMeDBDetails.COLUMN_NAME_SESSION_ID, sessionID);
    db.insert(TrackMeDBDetails.TABLE_SESSION, null, values);
  }

  public void clearUploadIDs() {
    final String sql = "UPDATE " + TrackMeDBDetails.TABLE_LOCATIONS + " SET " + TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + " = null"
        + " WHERE " + TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + " != null ";
    db.execSQL(sql);
  }

}