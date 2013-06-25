package com.uprootlabs.trackme;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;

import com.google.android.gms.common.GooglePlayServicesUtil;

public class DialogActivity extends Activity {

  private static final String DIALOG_ACTIVITY_TAG = "dialogActivity";
  private static final int CONNECTION_FAILURE_RESOLUTION_REQEUST = 9000;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // setContentView(R.layout.activity_dialog);
    this.requestWindowFeature(Window.FEATURE_NO_TITLE);
  }

  @Override
  public void onResume() {
    super.onResume();
    Intent intent = getIntent();
    int resultCode = intent.getIntExtra("errorCode", 0);
    Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, CONNECTION_FAILURE_RESOLUTION_REQEUST);
    if (errorDialog != null) {
      Log.d(DIALOG_ACTIVITY_TAG, "Dialog Connc");
      errorDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
          Log.d(DIALOG_ACTIVITY_TAG, "Dialog Cloased");
          finish();
        }
      });
      errorDialog.show();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    finish();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

}