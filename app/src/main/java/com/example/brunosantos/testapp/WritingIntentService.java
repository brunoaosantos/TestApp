package com.example.brunosantos.testapp;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
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

import androidx.core.app.JobIntentService;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


public class WritingIntentService extends JobIntentService
        implements SharedPreferences.OnSharedPreferenceChangeListener{

    /**
     * Unique job ID for this service.
     */
    private static final int JOB_ID = 2;

    private Context mContext;
    public static final String DETECTED_ACTIVITY = ".DETECTED_ACTIVITY";

    //Define an ActivityRecognitionClient//
    private ActivityRecognitionClient mActivityRecognitionClient;

    //Keeps the sampling interval
    protected int samplingInt;

    //Normal frequency of writings
    protected int writingFrequency = 5000;

    //Determines if the thread was stopped by the Job Scheduler
    protected boolean endTime = false;

    Date date1 = new Date();
    SimpleDateFormat formatter1 = new SimpleDateFormat("HH:mm:ss");


    public static final String CUSTOM_ACTION = "YOUR_CUSTOM_ACTION";


    public WritingIntentService() {
        super();
    }

    @Override
    public void onCreate() {
        mContext = this;
        mActivityRecognitionClient = new ActivityRecognitionClient(mContext);
        super.onCreate();
        writeToFileService(new String[]{"CREATED: " + formatter1.format(new Date())});
    }

    public static void enqueueWork(Context context, Intent intent) {
        enqueueWork(context, WritingIntentService.class, JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(Intent intent) {
        samplingInt = intent.getIntExtra("samplingInterval",5000);
        int startTracking = (int) System.currentTimeMillis();
        if (samplingInt == 5000) {
            //starts tracking the activity
            while (true) {
                try {
                    int now = (int) System.currentTimeMillis();
                    if ((now - startTracking) > 360000 || endTime || isStopped()) {
                        //six minutes after started tracking or job interrupted by android's Job Scheduler
                        //force stop otherwise the service will be always running
                        break;
                    }
                    Task<Void> task = mActivityRecognitionClient.requestActivityUpdates(
                            samplingInt,
                            getActivityDetectionPendingIntent());
                    task.addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            updateDetectedActivitiesList();
                        }
                    });
                    Thread.sleep(writingFrequency);
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        else if (samplingInt == 10000 || samplingInt == 30000 | samplingInt == 60000) {
            /**
             * means that it is necessary to preform empty writings to keep the
             * system writing the same amount of time no matter the sampling interval
             * so it is needed to create a thread to perform those writings
             */
            final int fakeWritings = (samplingInt / writingFrequency) - 1;
            int cnt = 0;
            while (true) {
                try {
                    int now = (int) System.currentTimeMillis();
                    if ((now - startTracking) > 360000 || endTime || isStopped()) {
                        //six minutes after started tracking or job interrupted by android's Job Scheduler
                        //force stop otherwise the service will be always running
                        break;
                    }
                    if (cnt == 0) {
                        //means the cnt == 0 so it must be performed a real writing
                        cnt++;
                        Task<Void> task = mActivityRecognitionClient.requestActivityUpdates(
                                samplingInt,
                                getActivityDetectionPendingIntent());
                        task.addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                updateDetectedActivitiesList();
                            }
                        });
                        Thread.sleep(writingFrequency);
                    }
                    else if (cnt <= fakeWritings) {
                        //need to perform an empty writing
                        if (cnt ==fakeWritings) {
                            //need to be restarted
                            cnt = 0;
                        }
                        else {
                            cnt++;
                        }
                        writeToFileService(new String[]{"nothing " + formatter1.format(date1)});
                        Thread.sleep(writingFrequency);
                    }
                    else {
                        Toast.makeText(this, "ERROR WITH CNT!", Toast.LENGTH_SHORT).show();
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        else {
            Toast.makeText(this, "ERROR WITH SAMPLING INTERVAL!", Toast.LENGTH_SHORT).show();
        }
    }

    private PendingIntent getActivityDetectionPendingIntent() {
        //Send the activity data to our DetectedActivitiesIntentService class//
        Intent intent = new Intent(this, ActivityReceiver.class);
        return PendingIntent.getBroadcast(this, 0, intent,PendingIntent.FLAG_UPDATE_CURRENT);
    }

    //Process the list of activities//
    @SuppressLint("WrongConstant")
    protected void updateDetectedActivitiesList() {
        ArrayList<DetectedActivity> detectedActivities = ActivityIntentService.detectedActivitiesFromJson(
                PreferenceManager.getDefaultSharedPreferences(mContext)
                        .getString(DETECTED_ACTIVITY, ""));

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
        //write the activities in the file
        writeToFileService(probabilities);
    }

    public void writeToFileService(String[] probabilities) {
        if(isExternalStorageWritableService()) {
            File externalStorageDir = Environment.getExternalStorageDirectory();
            File myFile = new File(externalStorageDir, "test.txt");
            Date date = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");

            try {
                String toWrite = Arrays.toString(probabilities)
                        .replace("[","")
                        .replace("]","") +
                        ", " + formatter.format(date) + ", " +
                        getBatteryPercentageService() + "%\n\r";
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

    public int getBatteryPercentageService() {
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

    public boolean isExternalStorageWritableService() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {

    }

    /**
     * job exceeded maximum time and JobManager kills it
     * if returned true it will reschedule the work, otherwise it ends completely
     */
    @Override
    public boolean onStopCurrentWork() {
        //kill current job
        endTime = true;
        //to force to continue
        writeToFileService(new String[]{"stop job " + formatter1.format(new Date())});
        stopSelf();
        return false;
    }

    @Override
    public void onDestroy() {
        writeToFileService(new String[]{"destroy job " + formatter1.format(new Date())});
        //kill current job intent service
        stopSelf();
        sendLocalBroadcast();
        super.onDestroy();
    }

    /**
     * Control whether code executing in onHandleWork(Intent) will be interrupted if the job is stopped.
     * By default this is false.
     * If called and set to true, any time onStopCurrentWork() is called,
     * the class will first call AsyncTask.cancel(true) to interrupt the running task.
     */
    @Override
    public void setInterruptIfStopped(boolean interruptIfStopped) {
        super.setInterruptIfStopped(true);
    }

    private void sendLocalBroadcast() {

        Intent newIntent = new Intent(CUSTOM_ACTION);
        Log.d(WritingIntentService.class.getSimpleName(), "sending broadcast");

        // send local broadcast
        LocalBroadcastManager.getInstance(this).sendBroadcast(newIntent);
    }
}
