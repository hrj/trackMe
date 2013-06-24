package com.uprootlabs.trackme;

import android.provider.BaseColumns;

public final class LocationDBDetails implements BaseColumns {
  public static final String TABLE_NAME = "locationDetails";
  public static final String COLUMN_NAME_LAT = "latitude";
  public static final String COLUMN_NAME_LNG = "longitude";
  public static final String COLUMN_NAME_ACC = "accuracy";
  public static final String COLUMN_NAME_TS = "timestamp";
  public static final int DB_VERSION = 1;
  public static final String DATABASE_NAME = "TrackMe.db";
}