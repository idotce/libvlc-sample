package com.libvlc.sample;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.RadioButton;

public class MainActivity extends Activity {
    public final static String TAG = "LibVLC.MainActivity";

    DirectoryAdapter mAdapter;

    private void init () {
        // Nothing
    }

    public void requestExternalStoragePermission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE }, 0x1);
        } else {
            init();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Initialize the LibVLC multimedia framework.
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestExternalStoragePermission();

        mAdapter = new DirectoryAdapter();
        final ListView mediaView = (ListView) findViewById(R.id.mediaView);
        mediaView.setAdapter(mAdapter);
        mediaView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1,
                int position, long arg3) {
                Intent intent = new Intent(MainActivity.this, VideoActivity.class);
                intent.putExtra(VideoActivity.LOCATION, (String) mAdapter.getItem(position));
                intent.putExtra(VideoActivity.DISPLAY, (Boolean) mAdapter.isOpenGLMode());
                startActivity(intent);
            }
        });
        RadioButton radioOpenGL = (RadioButton)findViewById(R.id.radioOpenGL);
        radioOpenGL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAdapter.setOpenGLMode(true);
                mAdapter.refresh();
            }
        });
        RadioButton surfaceVideo = (RadioButton)findViewById(R.id.radioSurface);
        surfaceVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAdapter.setOpenGLMode(false);
                mAdapter.refresh();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
    };

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.action_settings:
            Log.d(TAG, "Setting item selected.");
            return true;
        case R.id.action_refresh:
            mAdapter.refresh();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
}
