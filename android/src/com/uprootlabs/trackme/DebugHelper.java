package com.uprootlabs.trackme;

import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

final class DebugHelper {
  public static final String PREFERENCE_NAME = "debug";
  public static final String PREFERENCE_TOTAL_LOCATION_COUNT = "total";
  public static final String PREFERENCE_ARCHIVED_LOCATION_COUNT = "archived";
  public static final String PREFERENCE_UPLOADED_LOCATION_COUNT = "uploaded";
  public static final String PREFERENCE_TOTAL_QUEUED_LOCATION_COUNT = "queued";
  SQLiteDatabase db;
  SharedPreferences debug;
  SharedPreferences.Editor debugEditor;

  public DebugHelper(final Context context) {
    debug = context.getSharedPreferences(PREFERENCE_NAME, 0);
    debugEditor = debug.edit();
    db = new TrackMeDBHelper(context).getReadableDatabase();
  }

  public synchronized void addCapturedCount() {
    final int captureCount = debug.getInt(PREFERENCE_TOTAL_LOCATION_COUNT, 0);
    final int queued = debug.getInt(PREFERENCE_TOTAL_QUEUED_LOCATION_COUNT, 0);
    debugEditor.putInt(PREFERENCE_TOTAL_LOCATION_COUNT, captureCount + 1);
    debugEditor.putInt(PREFERENCE_TOTAL_QUEUED_LOCATION_COUNT, queued + 1);
    debugEditor.commit();
  }

  public synchronized void addArchivedCount(final int newArchivedCount) {
    final int oldArchivedCount = debug.getInt(PREFERENCE_ARCHIVED_LOCATION_COUNT, 0);
    final int archivedCount = oldArchivedCount + newArchivedCount;
    final int queued = debug.getInt(PREFERENCE_TOTAL_QUEUED_LOCATION_COUNT, 0);
    debugEditor.putInt(PREFERENCE_ARCHIVED_LOCATION_COUNT, archivedCount);
    debugEditor.putInt(PREFERENCE_TOTAL_QUEUED_LOCATION_COUNT, queued - newArchivedCount);
    debugEditor.commit();
  }

  public synchronized void addUploadedCount(final int newUploadedCount) {
    final int oldUploadedCount = debug.getInt(PREFERENCE_UPLOADED_LOCATION_COUNT, 0);
    final int queued = debug.getInt(PREFERENCE_TOTAL_QUEUED_LOCATION_COUNT, 0);
    final int uploadedCount = oldUploadedCount + newUploadedCount ;
    debugEditor.putInt(PREFERENCE_UPLOADED_LOCATION_COUNT, uploadedCount);
    debugEditor.putInt(PREFERENCE_TOTAL_QUEUED_LOCATION_COUNT, queued - newUploadedCount);
    debugEditor.commit();
  }

  private synchronized String getQueuedLocationsDetails() {
    final String sql = "SELECT " + TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + ", " + TrackMeDBDetails.COLUMN_NAME_SESSION_ID + ", "
        + TrackMeDBDetails.COLUMN_NAME_BATCH_ID + ", " + "COUNT(*) FROM " + TrackMeDBDetails.TABLE_LOCATIONS + " GROUP BY "
        + TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + ", " + TrackMeDBDetails.COLUMN_NAME_SESSION_ID + ", "
        + TrackMeDBDetails.COLUMN_NAME_BATCH_ID;

    final Cursor c = db.rawQuery(sql, null);

    int totalQueuedLocationCount = 0;

    final StringBuffer queued = new StringBuffer();
    final String header = String.format("\nLocations not Uploaded\n%-8s|%-8s|%-8s|%s\n", "Uid", "Sid", "Bid", "Count");
    queued.append(header);
    if (c.moveToFirst()) {
      do {
        final int count = c.getInt(3);
        final String value = String.format(Locale.US, "%8d|%-8s|%8d|%8d\n", c.getInt(0), c.getString(1), c.getInt(2), count);
        queued.append(value);
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

  public synchronized String getDebugDetails() {
    final int totalCount = debug.getInt(DebugHelper.PREFERENCE_TOTAL_LOCATION_COUNT, 0);
    final int uploadedCount = debug.getInt(DebugHelper.PREFERENCE_UPLOADED_LOCATION_COUNT, 0);
    final int archivedCount = debug.getInt(DebugHelper.PREFERENCE_ARCHIVED_LOCATION_COUNT, 0);
    final int queuedCount = debug.getInt(DebugHelper.PREFERENCE_TOTAL_QUEUED_LOCATION_COUNT, 0);
    final int sumCount = uploadedCount + archivedCount + queuedCount;

    final StringBuffer debugDetails = new StringBuffer();
    final String header = String.format("%-8s|%-8s|%-8s|%-8s|%s\n", "Queued", "Archived", "Uploaded", "Sum", "Total");
    debugDetails.append(header);
    final String value = String.format(Locale.US, "%8d|%8d|%8d|%8d|%8d\n", queuedCount, archivedCount, uploadedCount, sumCount, totalCount);
    debugDetails.append(value);
    debugDetails.append(getQueuedLocationsDetails());

    return debugDetails.toString();
  }

}
