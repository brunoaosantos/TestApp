package com.example.brunosantos.testapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.DetectedActivity;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public class MainActivity extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private Context mContext;
    public static final String DETECTED_ACTIVITY = ".DETECTED_ACTIVITY";

    //Define an ActivityRecognitionClient//
    private ActivityRecognitionClient mActivityRecognitionClient;
    private ActivitiesAdapter mAdapter;

    private Intent writingIntentService;

    //Set the activity detection interval//
    int samplingInterval = 5000;

    //Determines the current state of the sampling interval button, either available or unavailable
    String samplingState = "available";

    //Set the timer that allows to change the sampling interval
    int timer = 0;

    SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;

        //ask for writing permissions
        checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        // register local broadcast
        IntentFilter filter = new IntentFilter(WritingIntentService.CUSTOM_ACTION);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mReceiver, filter);

        //Retrieve the ListView where we’ll display our activity data//
        ListView detectedActivitiesListView = (ListView) findViewById(R.id.activities_listview);

        ArrayList<DetectedActivity> detectedActivities = ActivityIntentService.detectedActivitiesFromJson(
                PreferenceManager.getDefaultSharedPreferences(this).getString(
                        DETECTED_ACTIVITY, ""));

        //Bind the adapter to the ListView//
        mAdapter = new ActivitiesAdapter(this, detectedActivities);
        detectedActivitiesListView.setAdapter(mAdapter);
        //mActivityRecognitionClient = new ActivityRecognitionClient(this);

        Button walkingButton = (Button)findViewById(R.id.walking);
        walkingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeToFile(new String[]{"WALKING"});
            }
        });
        Button stairsButton = (Button)findViewById(R.id.stairs);
        stairsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeToFile(new String[]{"STAIRS"});
            }
        });
        Button bicycleButton = (Button)findViewById(R.id.bicycle);
        bicycleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeToFile(new String[]{"BICYCLE"});
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
        updateDetectedActivitiesList();
        updateSamplingInterval();
        updateSamplingButton();
        updateTrackingButton();
    }

    @Override
    protected void onPause() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    /**
     * Broadcast receiver to receive the data
     */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            writeToFile(new String[]{"JOB terminated at " + formatter.format(new Date())});
            //launch a new job intent service, once when this function is triggered means that the previous job has stopped
            createWritingPendingIntent();
        }
    };


    //called by clicking on start tracking button
    public void requestUpdatesHandler(View view) {
        if (writingIntentService == null) {
            samplingState = "unavailable";
            updateSamplingButton();
            timer = (int) System.currentTimeMillis();
            updateTrackingButton();
            //creates the service that will write the tracking results
            createWritingPendingIntent();
        }
    }

    private void createWritingPendingIntent() {
        writingIntentService = new Intent(mContext, WritingIntentService.class);
        writingIntentService.putExtra("samplingInterval", samplingInterval);
        WritingIntentService.enqueueWork(mContext, writingIntentService);
    }

    //Process the list of activities//
    @SuppressLint("WrongConstant")
    protected void updateDetectedActivitiesList() {
        ArrayList<DetectedActivity> detectedActivities = ActivityIntentService.detectedActivitiesFromJson(
                PreferenceManager.getDefaultSharedPreferences(mContext)
                        .getString(DETECTED_ACTIVITY, ""));

        mAdapter.updateActivities(detectedActivities);

        /** probabilities is an array that stores all 8 possible activities detected by
         * Google Activity Recognition, starting all at zero because only the activities with
         * probability values higher than zero are on detectedActivities
         */
        String[] probabilities = {"0","0","0","0","0","0","0","0"};

        for (DetectedActivity activity : detectedActivities) {
            /**
             * Google Activity Recognition can detect 8 different activities,
             * each assigned to a respective value;
             * IN_VEHICLE -> 0
             * ON_BICYCLE -> 1
             * ON_FOOT -> 2
             * RUNNING -> 8
             * STILL -> 3
             * TILTING -> 5
             * UNKNOWN -> 4
             * WALKING -> 7
             */
            if(activity.getType() < 6) {
                probabilities[activity.getType()] = Integer.toString(activity.getConfidence());
            }
            else {
                /**
                 * It is necessary to subtract one to the array position once there is no number 6
                 * it jumps from 5 to 7 and 8
                 */
                probabilities[activity.getType()-1] = Integer.toString(activity.getConfidence());
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals(DETECTED_ACTIVITY)) {
            updateDetectedActivitiesList();
        }
    }

    public void writeToFile(String[] probabilities) {
        if(isExternalStorageWritable()) {
            checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            File externalStorageDir = Environment.getExternalStorageDirectory();
            File myFile = new File(externalStorageDir, "test.txt");
            Date date = new Date();

            try {
                String toWrite = Arrays.toString(probabilities)
                        .replace("[","")
                        .replace("]","") +
                        ", " + formatter.format(date) + ", " +
                        getBatteryPercentage() + "%\n\r";
                long fileLength = myFile.length();
                RandomAccessFile raf = new RandomAccessFile(myFile, "rw");
                raf.seek(fileLength);
                raf.writeBytes(toWrite);
                raf.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            Toast.makeText(this, "Permission denied!", Toast.LENGTH_SHORT).show();       }
    }

    public int getBatteryPercentage() {
        if (Build.VERSION.SDK_INT >= 21) {
            BatteryManager bm = (BatteryManager) this.getSystemService(BATTERY_SERVICE);
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }
        else {
            IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = this.registerReceiver(null, iFilter);

            int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
            int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;

            double batteryPct = level / (double) scale;

            return (int) (batteryPct * 100);
        }
    }

    public boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    public void checkPermission(String permission){
        String[] PERMISSIONS_STORAGE = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
        int check = ContextCompat.checkSelfPermission(this, permission);

        if (check != PackageManager.PERMISSION_GRANTED) {
            //if the app is not allowed to write in the external storage it will ask the user to give permission
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    1);
        }
    }

    @SuppressLint("DefaultLocale")
    public void setSamplingInterval(View view) {
        /**
        * For testing purposes only four different sampling interval are considered and used,
        * being them 5, 10, 30 and 60 seconds respectively
        */
        int now = (int) System.currentTimeMillis();
        int diff = now - timer;
        if( diff > 180000 && timer != 0) {
            //means that has passed half an hour since lock
            timer = now;
            samplingState = "available";
            updateSamplingButton();
            setSamplingInterval(view);
        }
        else if(timer == 0 && samplingState.equals("available")) {
            //if timer = 0 means that is the first press and locks the timer
            timer = (int) System.currentTimeMillis();
            samplingInterval = 10000;
            updateSamplingInterval();
            writeToFile(new String[]{"New sampling " + samplingInterval});
            Toast.makeText(this, "Sampling interval changed to: " + samplingInterval/1000, Toast.LENGTH_SHORT).show();
        }
        else if(diff <= 30000 && samplingState.equals("available")) {
            /**
            *  if diff <= 30000 means that we are still in the 30 seconds window,
            *  since the first press where we can
            *  change the sampling interval
            */
            if(samplingInterval <= 5000) {
                samplingInterval = 10000;
            }
            else if(samplingInterval == 10000) {
                samplingInterval = 30000;
            }
            else if(samplingInterval == 30000) {
                samplingInterval = 60000;
            }
            else {
                samplingInterval = 5000;
            }
            updateSamplingInterval();
            writeToFile(new String[]{"New sampling " + samplingInterval});
            Toast.makeText(this, "Sampling interval changed to: " + samplingInterval/1000, Toast.LENGTH_SHORT).show();
        }
        else {
            //occurred some error or between the 30 seconds window where you can change the sampling
            // interval and the 30 minutes where you can change again the interval
            samplingState = "unavailable";
            updateSamplingButton();
            Toast.makeText(this, "Sampling period changing interval timed out", Toast.LENGTH_SHORT).show();

        }
    }

    @SuppressLint("DefaultLocale")
    public void updateSamplingInterval () {
        TextView sampling = (TextView) this.findViewById(R.id.sampling);
        sampling.setText(String.format("%d", samplingInterval / 1000));
    }

    public void updateSamplingButton () {
        Button setSampling = (Button) this.findViewById(R.id.setSampling);
        if(samplingState.equals("unavailable")) {
            setSampling.setBackgroundColor(Color.RED);
        }
        else {
            setSampling.setBackgroundColor(Color.GRAY);
        }
    }

    public void updateTrackingButton () {
        Button tracking = (Button) this.findViewById(R.id.get_activity);
        if(samplingState.equals("unavailable")) {
            tracking.setBackgroundColor(Color.RED);
        }
        else {
            tracking.setBackgroundColor(Color.GRAY);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {

        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.

        savedInstanceState.putInt("timer", timer);
        savedInstanceState.putInt("samplingInterval", samplingInterval);
        savedInstanceState.putString("samplingState", samplingState);

        super.onSaveInstanceState(savedInstanceState);
    }

    //onRestoreInstanceState

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {

        super.onRestoreInstanceState(savedInstanceState);

        // Restore UI state from the savedInstanceState.
        // This bundle has also been passed to onCreate.

        timer = savedInstanceState.getInt("timer");
        samplingInterval = savedInstanceState.getInt("samplingInterval");
        samplingState = savedInstanceState.getString("samplingState");
    }

}