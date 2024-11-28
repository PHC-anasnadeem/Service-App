package com.phc.serviceapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class SyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Start the UploadService to sync data
        context.startService(new Intent(context, UploadService.class));

        // Optional: Notify user that sync is in progress
//        Toast.makeText(context, "Data sync started at 10 PM", Toast.LENGTH_SHORT).show();
    }
}

