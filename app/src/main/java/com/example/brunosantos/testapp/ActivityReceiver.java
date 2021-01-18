package com.example.brunosantos.testapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ActivityReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        intent.setClass(context, ActivityIntentService.class);
        ActivityIntentService.enqueueWork(context, intent);
    }
}
