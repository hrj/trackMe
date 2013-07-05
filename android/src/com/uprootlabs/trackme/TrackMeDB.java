package com.uprootlabs.trackme;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;

final class TrackMeDB {
  private SQLiteDatabase db;
  private MyPreference myPreferences;

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
        TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID};
    Cursor c = db.query(TrackMeDBDetails.LOCATION_TABLE_NAME, columns, selection, selectionArgs, null, null, orderBy, limit);
    return c;
  }

  public String getLocationsAsXML(final long time) {
    final String locations;
    final int uploadID = myPreferences.getNewUploadID();
    assignUploadID(uploadID);
    Cursor c = getLocationsByUploadID(uploadID);
    final List<String> sessionIDs = getSessoinIDs(uploadID, c);
    final List<String> batchIDs = getNewBatchIDs(sessionIDs);
    updateBatchID(batchIDs, sessionIDs, uploadID, c);
//    final String selection = TrackMeDBDetails.COLUMN_NAME_TS + " < ? AND " + TrackMeDBDetails.COLUMN_NAME_STATUS + " != ?";
//    final String[] selectionArgs = { String.valueOf(time), "1" };
//    final String orderBy = TrackMeDBDetails.COLUMN_NAME_TS + " ASC";

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
    //TODO the number of locations queued
    return 0;
  }
  
  public void assignUploadID(final int uploadID) {
    //TODO assigning a upload batch with an uploadID
    
  }
  
  public void updateBatchIDs(final List<String> sessionID, final List<String> batchIDs, final int uploadID) {
    //TODO assign batch id to the locations
    
  }
  
  private String getLastBatchID(final int sessionID) {
    //TODO get the last assigned batch id for sessionID
   return ""; 
  }

  public List<String> getNewBatchIDs(List<String> sessionIDs) {
    List<String> batchIDs = new ArrayList<String>();
    //TODO increment it by one and return the value
    return batchIDs;
  }

  public void moveLocations(final int uploadID, final List<String> sessions) {
    //TODO move the locations from current table to its respective table after upload
    
  }
  
  private List<String> getSessoinIDs(final int uploadID, final Cursor cursor) {
    List<String> sessionIDs = new ArrayList<String>();
    return sessionIDs;
  }
  
  private StringBuffer parseXML(final Cursor c) {
    return new StringBuffer();
  }
  
  public void clearUploadIDs() {
    
  }

}