package com.ramos.julian.syncdataservice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.wearable.view.WatchViewStub;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {
    static String folder="/dataSync";
    File file;
    Intent syncservice;
    Button sendButton;
    Toast toast;

    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        syncservice=new Intent(this,syncDataService.class);

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
                sendButton = (Button) findViewById(R.id.button);

                sendButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startService(syncservice);
                        toast = new Toast(getApplicationContext());
                        toast.makeText(getApplicationContext(),"Service started", Toast.LENGTH_SHORT).show();

                    }
                });
            }
        });

        file = new File(Environment.getExternalStorageDirectory()+folder);
        if(!file.exists()){
            file.mkdir();
        }









    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopService(syncservice);
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopService(syncservice);
    }
}
