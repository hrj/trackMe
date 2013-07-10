package com.uprootlabs.trackme;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.widget.Toast;

public class MyPreferencesActivity extends PreferenceActivity {
  @Override
  public void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);
    
    final MyEditTextPreference captureFrequencyPreference = (MyEditTextPreference) getPreferenceScreen().findPreference("captureFrequency");
    
    captureFrequencyPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
      
      @Override
      public boolean onPreferenceChange(final Preference preference, final Object newValue) {
        if(newValue.equals("") || Integer.parseInt(newValue.toString()) <= 0) { 
          final Context context = getApplicationContext();
          final String text_message = "Invalid input, time cannot be empty, 0 or negative";
          final int duration = Toast.LENGTH_LONG;

          //TODO Make it a alert box instead of a toast
          final Toast toast = Toast.makeText(context, text_message, duration);
          toast.show();
          return false;
        }
        return true;
      }
    });

    final MyEditTextPreference updateFrequencyPreference = (MyEditTextPreference) getPreferenceScreen().findPreference("updateFrequency");
    
    updateFrequencyPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
      
      @Override
      public boolean onPreferenceChange(final Preference preference, final Object newValue) {
        if(newValue.equals("") || Integer.parseInt(newValue.toString()) <= 0) { 
          final Context context = getApplicationContext();
          final String text_message = "Invalid input, time cannot be empty, 0 or negative";
          final int duration = Toast.LENGTH_LONG;
          
          //TODO Make it a alert box instead of a toast
          final Toast toast = Toast.makeText(context, text_message, duration);
          toast.show();
          return false;
        }
        return true;
      }
    });
  }

}
