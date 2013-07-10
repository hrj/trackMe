package com.uprootlabs.trackme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;

public class OnBoot extends BroadcastReceiver {
  MyPreference myPreference;
  SQLiteDatabase myDb;
  TrackMeDB db;

  @Override
  public void onReceive(final Context context, final Intent intent) {
    myDb = new TrackMeDBHelper(context).getWritableDatabase();
    db = new TrackMeDB(myDb, context);
    myPreference = new MyPreference(context);
    final long uploadTime = System.currentTimeMillis();
    if (myPreference.isAutoUpdateSet() && db.getQueuedLocationsCount(uploadTime) > 0) {
      final Intent intentNew = new Intent(context, UploadService.class);
      intentNew.putExtra(UploadService.UPLOAD_TYPE, UploadService.AUTO_UPLOAD);
      intentNew.putExtra(UploadService.UPLOAD_TIME, uploadTime);
      context.startService(intentNew);
    }

  }

}
