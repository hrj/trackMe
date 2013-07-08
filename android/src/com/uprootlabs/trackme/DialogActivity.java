package com.uprootlabs.trackme;

import android.app.Activity;
import android.app.AlertDialog;
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
  public static final String STR_ERROR_TYPE = "errorType";
  public static final String STR_ERROR_GOOGLE = "google";
  public static final String STR_ERROR_USER = "user";
  public static final String STR_ERROR_CODE = "errorCode";
  public static final String STR_ERROR_MESSAGE = "errorMessage";

  DialogInterface.OnDismissListener dismissListener = new DialogInterface.OnDismissListener() {

    @Override
    public void onDismiss(DialogInterface dialog) {
      // TODO Auto-generated method stub
      finish();
    }

  };

  DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {

    @Override
    public void onClick(DialogInterface dialog, int which) {
      // TODO Auto-generated method stub
      Log.d(DIALOG_ACTIVITY_TAG, "Dialog Cloased OK");
      finish();
    }

  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // setContentView(R.layout.activity_dialog);
    this.requestWindowFeature(Window.FEATURE_NO_TITLE);

    Intent intent = getIntent();
    String errorType = intent.getStringExtra(STR_ERROR_TYPE);
    if (errorType.equals(STR_ERROR_GOOGLE)) {
      int resultCode = intent.getIntExtra(STR_ERROR_CODE, 0);
      Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, CONNECTION_FAILURE_RESOLUTION_REQEUST);
      if (errorDialog != null) {
        errorDialog.setOnDismissListener(dismissListener);
        errorDialog.show();
      }
    } else {
      String errorMessage = intent.getStringExtra(STR_ERROR_MESSAGE);
      AlertDialog.Builder errorDialog = new AlertDialog.Builder(this);
      errorDialog.setTitle("Error");
      errorDialog.setMessage(errorMessage);
      errorDialog.setNeutralButton(this.getResources().getString(R.string.ok), clickListener);
      errorDialog.show();
    }

  }

  @Override
  public void onResume() {
    super.onResume();
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