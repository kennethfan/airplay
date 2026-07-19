package com.airplay.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.airplay.model.VideoItem;

import java.io.File;
import java.io.FileOutputStream;
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

                Uri thumbUri = getOrCreateThumbnailUri(context, id, path, name);

                videos.add(new VideoItem(id, name, path, duration, size, thumbUri));
                Log.d(TAG, "[" + label + "] found: " + name + " (" + mime + ") path=" + path);
            }
        } catch (Exception e) {
            Log.e(TAG, "[" + label + "] query failed", e);
        }

        return videos;
    }

    /**
     * Resolves a thumbnail URI for the given video.
     *
     * Strategy (in order):
     * 1. Query the system videothumbnails table by video_id to get the real thumbnail _ID
     * 2. Fall back to generating a thumbnail via MediaStore.Video.Thumbnails API, cached to a local JPEG
     * 3. Ultimate fallback: the video content URI itself (UI will show ErrorFallback)
     */
    private static Uri getOrCreateThumbnailUri(Context context, long videoId, String videoPath, String displayName) {
        ContentResolver resolver = context.getContentResolver();

        // Strategy 1: Query the thumbnails table with the correct video_id column
        Uri systemUri = querySystemThumbnailUri(resolver, videoId);
        if (systemUri != null) {
            Log.d(TAG, "Using system thumbnail for " + displayName + " -> " + systemUri);
            return systemUri;
        }

        // Strategy 2: Generate thumbnail via system API and cache to local file
        Uri cachedUri = generateAndCacheThumbnail(context, videoId, videoPath, displayName);
        if (cachedUri != null) {
            Log.d(TAG, "Using cached thumbnail for " + displayName + " -> " + cachedUri);
            return cachedUri;
        }

        // Strategy 3: Fallback to video content URI (Coil will fail, UI shows ErrorFallback)
        Uri fallback = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId);
        Log.w(TAG, "No thumbnail available for " + displayName + ", using fallback: " + fallback);
        return fallback;
    }

    /**
     * Queries the system videothumbnails table to find a thumbnail entry matching the video ID.
     * Returns the correct content URI for the thumbnail, or null if none exists.
     */
    private static Uri querySystemThumbnailUri(ContentResolver resolver, long videoId) {
        Uri thumbnailsUri = MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI;
        String[] projection = { MediaStore.Video.Thumbnails._ID };
        String selection = MediaStore.Video.Thumbnails.VIDEO_ID + " = ?";
        String[] selectionArgs = { String.valueOf(videoId) };

        try (Cursor cursor = resolver.query(thumbnailsUri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long thumbId = cursor.getLong(
                        cursor.getColumnIndexOrThrow(MediaStore.Video.Thumbnails._ID));
                return ContentUris.withAppendedId(thumbnailsUri, thumbId);
            }
        } catch (Exception e) {
            Log.w(TAG, "querySystemThumbnailUri failed for video " + videoId, e);
        }
        return null;
    }

    /**
     * Generates a thumbnail using MediaStore's built-in API and caches it to a local JPEG file.
     * Returns the file URI, or null if generation failed.
     */
    private static Uri generateAndCacheThumbnail(Context context, long videoId, String videoPath, String displayName) {
        Bitmap bitmap = null;

        // Try system thumbnail generation API first (uses MediaStore's built-in decoder)
        try {
            bitmap = MediaStore.Video.Thumbnails.getThumbnail(
                    context.getContentResolver(),
                    videoId,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null);
        } catch (Exception e) {
            Log.w(TAG, "getThumbnail failed for " + displayName, e);
        }

        // Fallback: use MediaMetadataRetriever if we have a file path
        if (bitmap == null && videoPath != null && !videoPath.startsWith("content://")) {
            try {
                android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                retriever.setDataSource(videoPath);
                bitmap = retriever.getFrameAtTime(1_000_000); // 1 second in microseconds
                retriever.release();
            } catch (Exception e) {
                Log.w(TAG, "MediaMetadataRetriever failed for " + displayName, e);
            }
        }

        if (bitmap == null) return null;

        // Cache the bitmap to a local file
        try {
            File cacheDir = new File(context.getCacheDir(), "thumbs");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            File cacheFile = new File(cacheDir, "thumb_" + videoId + ".jpg");
            if (cacheFile.exists()) {
                // Already cached from a previous run
                return Uri.fromFile(cacheFile);
            }

            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            }
            bitmap.recycle();

            return Uri.fromFile(cacheFile);
        } catch (Exception e) {
            Log.w(TAG, "Failed to cache thumbnail for " + displayName, e);
            return null;
        }
    }
}
