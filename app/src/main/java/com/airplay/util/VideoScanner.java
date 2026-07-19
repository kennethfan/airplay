package com.airplay.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.airplay.model.VideoItem;

import java.util.ArrayList;
import java.util.List;

public class VideoScanner {
    private static final String TAG = "VideoScanner";

    public static List<VideoItem> scanVideos(Context context) {
        // First try: strict query (duration > 5s)
        List<VideoItem> videos = queryVideos(context,
                MediaStore.Video.Media.DURATION + " > ?", new String[]{ "5000" },
                MediaStore.Video.Media.DATE_ADDED + " DESC", "strict");

        // Second try: no duration filter (maybe duration column is null on some devices)
        if (videos.isEmpty()) {
            Log.d(TAG, "Strict query returned 0, trying broad query...");
            videos = queryVideos(context, null, null,
                    MediaStore.Video.Media.DATE_ADDED + " DESC", "broad");
        }

        Log.d(TAG, "Total videos found: " + videos.size());
        return videos;
    }

    private static List<VideoItem> queryVideos(Context context,
                                                String selection, String[] selectionArgs,
                                                String sortOrder, String label) {
        List<VideoItem> videos = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();

        String[] projection = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE
        };

        Log.d(TAG, "[" + label + "] querying with selection: " + selection);

        try (Cursor cursor = resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder)) {

            if (cursor == null) {
                Log.w(TAG, "[" + label + "] cursor is null");
                return videos;
            }

            Log.d(TAG, "[" + label + "] cursor count=" + cursor.getCount() + " cols=" + cursor.getColumnCount());

            int idCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int dataCol   = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
            int durCol    = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
            int sizeCol   = cursor.getColumnIndex(MediaStore.Video.Media.SIZE);
            int mimeCol   = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE);

            while (cursor.moveToNext()) {
                long id       = cursor.getLong(idCol);
                String name   = cursor.getString(nameCol);
                String path   = dataCol >= 0 ? cursor.getString(dataCol) : null;
                long duration = durCol >= 0 ? cursor.getLong(durCol) : 0;
                long size     = sizeCol >= 0 ? cursor.getLong(sizeCol) : 0;
                String mime   = mimeCol >= 0 ? cursor.getString(mimeCol) : "";

                // On Android 10+, DATA column may be null; fall back to content URI
                if (path == null || path.isEmpty()) {
                    Uri contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    path = contentUri.toString();
                    Log.d(TAG, "[" + label + "] DATA is null for " + name + ", using content URI: " + path);
                }

                Uri videoContentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                Uri thumbUri = Uri.withAppendedPath(videoContentUri, String.valueOf(id));

                videos.add(new VideoItem(id, name, path, duration, size, thumbUri));
                Log.d(TAG, "[" + label + "] found: " + name + " (" + mime + ") path=" + path);
            }
        } catch (Exception e) {
            Log.e(TAG, "[" + label + "] query failed", e);
        }

        return videos;
    }
}
