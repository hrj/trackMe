package com.uprootlabs.trackme;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class DebugHelper {
  public static final String PREFERENCE_NAME = "debug";
  public static final String PREFERENCE_TOTAL_LOCATION_COUNT = "total";
  public static final String PREFERENCE_ARCHIVED_LOCATION_COUNT = "archived";
  public static final String PREFERENCE_UPLOADED_LOCATION_COUNT = "uploaded";
  public static final String PREFERENCE_TOTAL_QUEUED_LOCATION_COUNT = "queued";
  SQLiteDatabase db;
  SharedPreferences debug;
  SharedPreferences.Editor debugEditor;

  public DebugHelper(Context context) {
    debug = context.getSharedPreferences(PREFERENCE_NAME, 0);
    debugEditor = debug.edit();
    db = new TrackMeDBHelper(context).getReadableDatabase();
  }

  public void addCapturedCount() {
    int captureCount = debug.getInt(PREFERENCE_TOTAL_LOCATION_COUNT, 0);
    debugEditor.putInt(PREFERENCE_TOTAL_LOCATION_COUNT, captureCount + 1);
    debugEditor.commit();

    inDBLocations();
  }

  public void addArchivedCount(int archivedCount) {
    archivedCount += debug.getInt(PREFERENCE_ARCHIVED_LOCATION_COUNT, 0);
    debugEditor.putInt(PREFERENCE_ARCHIVED_LOCATION_COUNT, archivedCount);
    debugEditor.commit();
  }

  public void addUploadedCount(int uploadedCount) {
    uploadedCount += debug.getInt(PREFERENCE_UPLOADED_LOCATION_COUNT, 0);
    debugEditor.putInt(PREFERENCE_UPLOADED_LOCATION_COUNT, uploadedCount);
    debugEditor.commit();
  }

  private void inDBLocations() {
    String[] col = { TrackMeDBDetails._ID };
    Cursor c = db.query(TrackMeDBDetails.TABLE_LOCATIONS, col, null, null, null, null, null);
    int count = c.getCount();

    debugEditor.putInt(PREFERENCE_TOTAL_QUEUED_LOCATION_COUNT, count);
    debugEditor.commit();
  }

  public String getQueuedLocationsCount() {
    String sql = "SELECT " + TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + ", " + TrackMeDBDetails.COLUMN_NAME_SESSION_ID + ", "
        + TrackMeDBDetails.COLUMN_NAME_BATCH_ID + ", " + "COUNT(*) FROM " + TrackMeDBDetails.TABLE_LOCATIONS + " GROUP BY "
        + TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + ", " + TrackMeDBDetails.COLUMN_NAME_SESSION_ID + ", "
        + TrackMeDBDetails.COLUMN_NAME_BATCH_ID;

    Cursor c = db.rawQuery(sql, null);

    int totalQueuedLocationCount = 0;

    StringBuffer queued = new StringBuffer();
    if (c.moveToFirst()) {
      do {
        queued.append(c.getInt(0) + " ");
        queued.append(c.getString(1) + " ");
        queued.append(c.getInt(2) + " ");
        int count = c.getInt(3);
        queued.append(count + "\n");
        totalQueuedLocationCount += count;
      } while (c.moveToNext());
    }

    if (totalQueuedLocationCount > 0) {
      debugEditor.putInt(PREFERENCE_TOTAL_QUEUED_LOCATION_COUNT, totalQueuedLocationCount);
      debugEditor.commit();
    }
    c.close();

    return queued.toString();
  }

}
