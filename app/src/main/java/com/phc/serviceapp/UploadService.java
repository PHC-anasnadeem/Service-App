package com.phc.serviceapp;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import java.io.File;
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
        // Simulate upload process
        for (String contact : contacts) {
            System.out.println("Uploading contact: " + contact);
        }

        for (String filePath : galleryFiles) {
            System.out.println("Uploading file: " + new File(filePath).getName());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

