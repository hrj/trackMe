package com.uprootlabs.trackme;

import android.content.Context;
import android.content.Intent;

final class UserError {

  public static Intent makeIntent(final Context context, final String message) {
    final Intent dialogIntent = new Intent(context, DialogActivity.class);
    dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    dialogIntent.putExtra(DialogActivity.STR_ERROR_TYPE, DialogActivity.STR_ERROR_USER);
    dialogIntent.putExtra(DialogActivity.STR_ERROR_MESSAGE, message); 
    return dialogIntent;
  }

}
