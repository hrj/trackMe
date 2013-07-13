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
    public void onDismiss(final DialogInterface dialog) {
      finish();
    }

  };

  DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
      Log.d(DIALOG_ACTIVITY_TAG, "Dialog Cloased OK");
      finish();
    }

  };

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // setContentView(R.layout.activity_dialog);
    this.requestWindowFeature(Window.FEATURE_NO_TITLE);

    final Intent intent = getIntent();
    final String errorType = intent.getStringExtra(STR_ERROR_TYPE);
    if (errorType.equals(STR_ERROR_GOOGLE)) {
      final int resultCode = intent.getIntExtra(STR_ERROR_CODE, 0);
      final Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, CONNECTION_FAILURE_RESOLUTION_REQEUST);
      if (errorDialog != null) {
        errorDialog.setOnDismissListener(dismissListener);
        errorDialog.show();
      }
    } else {
      final String errorMessage = intent.getStringExtra(STR_ERROR_MESSAGE);
      final AlertDialog.Builder errorDialog = new AlertDialog.Builder(this);
      errorDialog.setTitle("Error");
      errorDialog.setMessage(errorMessage);
      errorDialog.setNeutralButton(this.getResources().getString(R.string.ok), clickListener);
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