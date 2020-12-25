package com.example.brunosantos.testapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

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

    //Set the activity detection interval//
    int samplingInterval = 5000;

    //Set the timer that allows to change the sampling interval
    int timer = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;

//Retrieve the ListView where we’ll display our activity data//
        ListView detectedActivitiesListView = (ListView) findViewById(R.id.activities_listview);

        ArrayList<DetectedActivity> detectedActivities = ActivityIntentService.detectedActivitiesFromJson(
                PreferenceManager.getDefaultSharedPreferences(this).getString(
                        DETECTED_ACTIVITY, ""));

//Bind the adapter to the ListView//
        mAdapter = new ActivitiesAdapter(this, detectedActivities);
        detectedActivitiesListView.setAdapter(mAdapter);
        mActivityRecognitionClient = new ActivityRecognitionClient(this);

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
    }
    @Override
    protected void onPause() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }
    public void requestUpdatesHandler(View view) {
        Task<Void> task = mActivityRecognitionClient.requestActivityUpdates(
                samplingInterval,
                getActivityDetectionPendingIntent());
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void result) {
                updateDetectedActivitiesList();
            }
        });
    }
    //Get a PendingIntent//
    private PendingIntent getActivityDetectionPendingIntent() {
        //Send the activity data to our DetectedActivitiesIntentService class//
        Intent intent = new Intent(this, ActivityIntentService.class);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

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
        //write the activities to the file
        writeToFile(probabilities);
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
            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");

            try {
                String toWrite = Arrays.toString(probabilities)
                        .replace("[","")
                        .replace("]","") +
                        ", " + formatter.format(date) + "\n\r";
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

    public boolean isExternalStorageWritable() {
        //System.out.println(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()));
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
        * For testing purposes only three different sampling interval are considered and used,
        * being them 5, 10 and 30 seconds respectively
        */
        int now = (int) System.currentTimeMillis();
        int diff = now - timer;
        if( diff > 1800000) {
            //means that has passed half an hour since lock
            timer = 0;
            Button setSampling = (Button) this.findViewById(R.id.setSampling);
            setSampling.setBackgroundColor(Color.WHITE);
        }
        else if(diff <= 30000) {
            /**
            *  if diff <= 30000 means that we are still in the 30 seconds window,
            *  since the first press where we can
            *  change the sampling interval
            */
            if(timer == 0) {
                //if timer = 0 means that is the first press and locks the timer
                timer = (int) System.currentTimeMillis();
            }
            if(samplingInterval <= 5000) {
                samplingInterval = 10000;
            }
            else if(samplingInterval == 10000) {
                samplingInterval = 30000;
            }
            else {
                samplingInterval = 5000;
            }
            TextView sampling = (TextView) this.findViewById(R.id.sampling);
            sampling.setText(String.format("%d", samplingInterval / 1000));
            writeToFile(new String[]{"New sampling " + samplingInterval});
            Toast.makeText(this, "Sampling interval changed to: " + samplingInterval/1000, Toast.LENGTH_SHORT).show();
        }
        else {
            //occurred some error or between the 30 seconds window where you can change the sampling
            // interval and the 30 minutes where you can change again the interval
            Button setSampling = (Button) this.findViewById(R.id.setSampling);
            setSampling.setBackgroundColor(Color.RED);
            Toast.makeText(this, "Sampling period changing interval timed out", Toast.LENGTH_SHORT).show();

        }
    }

}