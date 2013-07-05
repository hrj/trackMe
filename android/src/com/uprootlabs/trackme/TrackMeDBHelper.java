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

  private static final String SQL_CREATE_LOCATION_TABLE = "CREATE TABLE " + TrackMeDBDetails.LOCATION_TABLE_NAME + " ("
      + TrackMeDBDetails._ID + INTEGER_TYPE + " PRIMARY KEY" + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_SESSION_ID + TEXT_TYPE
      + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_LAT + REAL_TYPE + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_LNG + REAL_TYPE + COMMA_SEP
      + TrackMeDBDetails.COLUMN_NAME_ACC + INTEGER_TYPE + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_TS + BIGINT_TYPE + COMMA_SEP 
      + TrackMeDBDetails.COLUMN_NAME_BATCH_ID + TEXT_TYPE + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_UPLOAD_ID + INTEGER_TYPE + ")";

  private static final String SQL_CREATE_SESSION_TABLE = "CREATE TABLE " + TrackMeDBDetails.SESSION_TABLE_NAME + " ("
      + TrackMeDBDetails._ID + INTEGER_TYPE + " PRIMARY KEY" + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_SESSION_ID + TEXT_TYPE
      + COMMA_SEP + TrackMeDBDetails.COLUMN_NAME_LAST_BATCH_ID + INTEGER_TYPE + ")";

  private static final String SQL_DELETE_TABLE = "DROP TABLE IF EXISTS " + TrackMeDBDetails.LOCATION_TABLE_NAME;

  public TrackMeDBHelper(Context context) {
    super(context, TrackMeDBDetails.DATABASE_NAME, null, TrackMeDBDetails.DB_VERSION);
  }

  public void onCreate(SQLiteDatabase db) {
    db.execSQL(SQL_CREATE_LOCATION_TABLE);
    db.execSQL(SQL_CREATE_SESSION_TABLE);
  }

  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    db.execSQL(SQL_DELETE_TABLE);
    onCreate(db);
  }

  public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    onUpgrade(db, oldVersion, newVersion);
  }

}