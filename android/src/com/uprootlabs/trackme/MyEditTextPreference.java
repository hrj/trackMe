package com.uprootlabs.trackme;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

public class MyEditTextPreference extends EditTextPreference {
  private String summary;

  public MyEditTextPreference(Context context) {
    super(context);
    // TODO Auto-generated constructor stub
  }
  
  public MyEditTextPreference(Context context, AttributeSet attrs){
    super(context, attrs);
    summary = getSummary().toString();
  }

  public MyEditTextPreference(Context context, AttributeSet attrs, int defStyle){
    super(context, attrs, defStyle);
  }

 public void setText(String text){
   super.setText(text);
   if(text.length() > 0) setSummary(text);
   else setSummary(summary);
 }
  
}
