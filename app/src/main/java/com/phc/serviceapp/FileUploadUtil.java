package com.phc.serviceapp;

import android.content.Context;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class FileUploadUtil {

    private static final String serverUrl = "http://192.168.200.78:8080/upload";
    private static final String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";  // A unique boundary

    public static void uploadFile(Context context, File file) {
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(serverUrl).openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                // Write file data to output stream
                DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
                outputStream.writeBytes("--" + boundary + "\r\n");
                outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n");
                outputStream.writeBytes("Content-Type: " + "image/jpeg" + "\r\n");  // Change to appropriate content type
                outputStream.writeBytes("\r\n");
                FileInputStream fileInputStream = new FileInputStream(file);
                int bytesRead;
                byte[] buffer = new byte[4096];
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                fileInputStream.close();
                outputStream.writeBytes("\r\n");
                outputStream.writeBytes("--" + boundary + "--\r\n");
                outputStream.flush();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d("Upload", "File uploaded successfully");
                } else {
                    Log.d("Upload", "Failed to upload file: " + responseCode);
                }
            } catch (IOException e) {
                Log.e("Upload", "Error uploading file", e);
            }
        }).start();
    }
}

