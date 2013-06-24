package com.uproot.trackme;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class LoginActivity extends Activity {

  public static String uid;
  public static String psk;
  SeekBar seekbar;
  TextView value;
  public static int prog;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.login);
		value = (TextView) findViewById(R.id.textview);
		value.setText("30");
		seekbar = (SeekBar) findViewById(R.id.seekbar);

		seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
		  
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				value.setText("SeekBar value is " + progress);
				prog = progress;
			}

			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    // getMenuInflater().inflate(R.menu.main, menu);
		
    Button btn1 = (Button) findViewById(R.id.submitBTN);
    btn1.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        // TODO Auto-generated method stub

        EditText et = (EditText) findViewById(R.id.userid);
        uid = et.getText().toString();
        EditText pk = (EditText) findViewById(R.id.passkey);
        psk = pk.getText().toString();
        SeekBar skb = (SeekBar) findViewById(R.id.seekbar);
        prog = skb.getProgress();

        Intent intent = new Intent(LoginActivity.this, LocationActivity.class);
        /*
         * to send additional data EditText editText = (EditText)
         * findViewById(R.id.edit_message); String message =
         * editText.getText().toString(); intent.putExtra(EXTRA_MESSAGE,
         * message);
         */
        startActivity(intent);
      }

        
    });
    return true;
  }
}