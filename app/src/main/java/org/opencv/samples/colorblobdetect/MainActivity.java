package org.opencv.samples.colorblobdetect;

import android.content.Context;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.widget.TextView;

import org.opencv.samples.colorblobdetect.R;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainActivity extends Activity {

    TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv = (TextView)findViewById(R.id.textView);

        String filename = getIntent().getStringExtra("filename");
        Log.wtf("MTP", "name: " + filename);
        readFile(filename);
    }

    void readFile(String filename){
        try {
            FileInputStream fis = getApplicationContext().openFileInput(filename);

            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader bufferedReader = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
                Log.wtf("MTP", line);
            }
            tv.setText(sb);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

}
