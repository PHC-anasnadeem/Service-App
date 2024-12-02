package com.phc.serviceapp;

import android.content.Context;

import fi.iki.elonen.NanoHTTPD;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LocalServer extends NanoHTTPD {

//    private static final String UPLOAD_DIR = "uploads";
private final String UPLOAD_DIR;

    public LocalServer(Context context, int port) {
        super(port);
        UPLOAD_DIR = context.getFilesDir().getAbsolutePath() + "/uploads";
        createUploadDirectory();
    }

    private void createUploadDirectory() {
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
    }


    @Override
    public Response serve(IHTTPSession session) {
        if (Method.POST.equals(session.getMethod())) {
            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);

                // Save uploaded files
                for (Map.Entry<String, String> entry : files.entrySet()) {
                    File tempFile = new File(entry.getValue());
                    File destFile = new File(UPLOAD_DIR, tempFile.getName());
                    tempFile.renameTo(destFile);
                }

                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Files uploaded successfully!");
            } catch (Exception e) {
                e.printStackTrace();
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: " + e.getMessage());
            }
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Local server is running.");
    }
}
