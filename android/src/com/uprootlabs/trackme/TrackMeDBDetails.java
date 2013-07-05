package com.uprootlabs.trackme;

import android.provider.BaseColumns;

final class TrackMeDBDetails implements BaseColumns {
  public static final String LOCATION_TABLE_NAME = "locationDetails";
  public static final String SESSION_TABLE_NAME = "sessionDetails";
  public static final String COLUMN_NAME_SESSION_ID = "sessionId";
  public static final String COLUMN_NAME_BATCH_ID = "batchId";
  public static final String COLUMN_NAME_LAST_BATCH_ID = "lastBatchId";
  public static final String COLUMN_NAME_STATUS= "status";
  public static final String COLUMN_NAME_LAT = "latitude";
  public static final String COLUMN_NAME_LNG = "longitude";
  public static final String COLUMN_NAME_ACC = "accuracy";
  public static final String COLUMN_NAME_TS = "timestamp";
  public static final int DB_VERSION = 1;
  public static final String DATABASE_NAME = "TrackMe.db";
}