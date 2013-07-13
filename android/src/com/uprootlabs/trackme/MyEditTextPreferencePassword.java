package com.uprootlabs.trackme;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

final class MyEditTextPreferencePassword extends EditTextPreference {

  private String summary;

  public MyEditTextPreferencePassword(final Context context) {
    super(context);
    // TODO Auto-generated constructor stub
  }

  public MyEditTextPreferencePassword(final Context context, final AttributeSet attrs) {
    super(context, attrs);
    summary = getSummary().toString();
  }

  public MyEditTextPreferencePassword(final Context context, final AttributeSet attrs, final int defStyle) {
    super(context, attrs, defStyle);
  }

  public void setText(final String text) {
    super.setText(text);
    if (text.length() > 0)
      setSummary(text.replaceAll(".", "*"));
    else
      setSummary(summary);
  }

}
