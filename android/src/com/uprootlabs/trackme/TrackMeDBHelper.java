package com.uprootlabs.trackme;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

final class TrackMeDBHelper extends SQLiteOpenHelper {

  private static final String TEXT_TYPE = " TEXT";
  private static final String REAL_TYPE = " REAL";
  private static final String INTEGER_TYPE = " INTEGER";
  private static final String BIGINT_TYPE = " BIGINT";

  private static final String COMMA_SEP = ",";

  private static final String SQL_CREATE_LOCATION_TABLE = "CREATE TABLE " + TrackMeDBDetails.TABLE_LOCATIONS + " (" + TrackMeDBDetails._ID
      + INTEGER_TYPE + " PRIMARY KEY" + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_SESSION_ID + TEXT_TYPE + COMMA_SEP
      + TrackMeDBDetails.COLUMN_NAME_LAT + REAL_TYPE + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_LNG + REAL_TYPE + COMMA_SEP
      + TrackMeDBDetails.COLUMN_NAME_ACC + INTEGER_TYPE + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_TS + BIGINT_TYPE + COMMA_SEP
      + TrackMeDBDetails.COLUMN_NAME_BATCH_ID + TEXT_TYPE + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + INTEGER_TYPE + ")";

  private static final String SQL_CREATE_SESSION_TABLE = "CREATE TABLE " + TrackMeDBDetails.TABLE_SESSION + " (" + TrackMeDBDetails._ID
      + INTEGER_TYPE + " PRIMARY KEY" + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_SESSION_ID + TEXT_TYPE + COMMA_SEP
      + TrackMeDBDetails.COLUMN_NAME_LAST_BATCH_ID + INTEGER_TYPE + ")";

  private static final String SQL_CREATE_ARCHIVED_LOCATIONS_TABLE = "CREATE TABLE " + TrackMeDBDetails.TABLE_ARCHIVED_LOCATIONS + " ("
      + TrackMeDBDetails._ID + INTEGER_TYPE + " PRIMARY KEY" + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_SESSION_ID + TEXT_TYPE + COMMA_SEP
      + TrackMeDBDetails.COLUMN_NAME_LAT + REAL_TYPE + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_LNG + REAL_TYPE + COMMA_SEP
      + TrackMeDBDetails.COLUMN_NAME_ACC + INTEGER_TYPE + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_TS + BIGINT_TYPE + COMMA_SEP
      + TrackMeDBDetails.COLUMN_NAME_BATCH_ID + TEXT_TYPE + ")";

  private static final String SQL_DELETE_LOCATION_TABLE = "DROP TABLE IF EXISTS " + TrackMeDBDetails.TABLE_LOCATIONS;
  private static final String SQL_DELETE_SESSION_TABLE = "DROP TABLE IF EXISTS " + TrackMeDBDetails.TABLE_SESSION;
  private static final String SQL_DELETE_ARCHIVED_LOCATIONS_TABLE = "DROP TABLE IF EXISTS " + TrackMeDBDetails.TABLE_ARCHIVED_LOCATIONS;

  public TrackMeDBHelper(final Context context) {
    super(context, TrackMeDBDetails.DATABASE_NAME, null, TrackMeDBDetails.DB_VERSION);
  }

  public void onCreate(final SQLiteDatabase db) {
    db.execSQL(SQL_CREATE_LOCATION_TABLE);
    db.execSQL(SQL_CREATE_SESSION_TABLE);
    db.execSQL(SQL_CREATE_ARCHIVED_LOCATIONS_TABLE);
  }

  public static String makeSessionTableSQL(final String tableName) {
    final String sessionTable = "CREATE TABLE IF NOT EXISTS " + tableName + " (" + TrackMeDBDetails._ID + INTEGER_TYPE + " PRIMARY KEY"
        + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_SESSION_ID + TEXT_TYPE + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_LAT + REAL_TYPE
        + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_LNG + REAL_TYPE + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_ACC + INTEGER_TYPE
        + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_TS + BIGINT_TYPE + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_BATCH_ID + TEXT_TYPE + ")";
    return sessionTable;
  }

  public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
    db.execSQL(SQL_DELETE_LOCATION_TABLE);
    db.execSQL(SQL_DELETE_SESSION_TABLE);
    db.execSQL(SQL_DELETE_ARCHIVED_LOCATIONS_TABLE);
    onCreate(db);
  }

  public void onDowngrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
    onUpgrade(db, oldVersion, newVersion);
  }

}