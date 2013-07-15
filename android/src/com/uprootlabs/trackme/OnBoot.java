package com.uprootlabs.trackme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class OnBoot extends BroadcastReceiver {
  private final String ON_BOOT_TAG = "onBoot";
  private final String onBoot = "android.intent.action.BOOT_COMPLETED";
  private final String onNetworkChange = "android.net.conn.CONNECTIVITY_CHANGE";
  private MyPreference myPreference;

  private void startUpload(final Context context) {

    final Intent intentNew = new Intent(context, UploadService.class);
    intentNew.putExtra(UploadService.UPLOAD_TYPE, UploadService.AUTO_UPLOAD);
    context.startService(intentNew);

  }

  private void cancelAlarm(final Context context) {

    final Intent broadcastIntent = new Intent(context, UploadService.class);
    broadcastIntent.putExtra(UploadService.UPLOAD_TYPE, UploadService.CANCEL_ALARM);
    context.startService(broadcastIntent);

  }

  @Override
  public void onReceive(final Context context, final Intent intent) {
    final String broadCastAction = intent.getAction();
    myPreference = new MyPreference(context);
    if (broadCastAction.equals(onBoot)) {

      if (myPreference.isAutoUpdateSet())
        startUpload(context);

    } else if (broadCastAction.equals(onNetworkChange)) {

      if (UploadService.isNetworkAvailable(context) && myPreference.isAutoUpdateSet()) {

        Log.d(ON_BOOT_TAG, "Net available");

        startUpload(context);

      } else {

        Log.d(ON_BOOT_TAG, "No internet connection");

        cancelAlarm(context);

      }

    }

  }

}
