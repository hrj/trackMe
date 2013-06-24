package com.uprootlabs.trackme;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class LocationDBHelper extends SQLiteOpenHelper {

  private static final String REAL_TYPE = " REAL";
  private static final String INTEGER_TYPE = " INTEGER";
  private static final String BIGINT_TYPE = " BIGINT";

  private static final String COMMA_SEP = ",";

  private static final String SQL_CREATE_TABLE = "CREATE TABLE " + LocationDBDetails.TABLE_NAME + " (" + LocationDBDetails._ID
      + INTEGER_TYPE + " PRIMARY KEY" + COMMA_SEP + LocationDBDetails.COLUMN_NAME_LAT + REAL_TYPE + COMMA_SEP
      + LocationDBDetails.COLUMN_NAME_LNG + REAL_TYPE + COMMA_SEP + LocationDBDetails.COLUMN_NAME_ACC + INTEGER_TYPE + COMMA_SEP
      + LocationDBDetails.COLUMN_NAME_TS + BIGINT_TYPE + ")";

  private static final String SQL_DELETE_TABLE = "DROP TABLE IF EXISTS " + LocationDBDetails.TABLE_NAME;

  public LocationDBHelper(Context context) {
    super(context, LocationDBDetails.DATABASE_NAME, null, LocationDBDetails.DB_VERSION);
  }

  public void onCreate(SQLiteDatabase db) {
    db.execSQL(SQL_CREATE_TABLE);
  }

  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    db.execSQL(SQL_DELETE_TABLE);
    onCreate(db);
  }

  public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    onUpgrade(db, oldVersion, newVersion);
  }

}