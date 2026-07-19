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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class VideoScanner {
    private static final String TAG = "VideoScanner";

    public static List<VideoItem> scanVideos(Context context) {
        // Single query without duration filter — some devices return null for DURATION,
        // making a SQL WHERE clause useless and requiring a second query.
        // We filter short videos (< 5s) in Java instead.
        List<VideoItem> videos = queryVideos(context);

        // Exclude short clips (ringtones, notifications, etc.)
        List<VideoItem> filtered = new ArrayList<>();
        for (VideoItem v : videos) {
            if (v.getDurationMs() >= 5000) {
                filtered.add(v);
            }
        }

        // Sort by date added descending in memory (avoids slow ContentProvider-side ORDER BY)
        Collections.sort(filtered, new Comparator<VideoItem>() {
            @Override
            public int compare(VideoItem a, VideoItem b) {
                return Long.compare(b.getDateAdded(), a.getDateAdded());
            }
        });

        Log.d(TAG, "Total videos found: " + filtered.size() + " (filtered from " + videos.size() + ")");
        return filtered;
    }

    private static List<VideoItem> queryVideos(Context context) {
        List<VideoItem> videos = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();

        String[] projection = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        };

        Log.d(TAG, "querying all videos...");

        try (Cursor cursor = resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null)) {

            if (cursor == null) {
                Log.w(TAG, "cursor is null");
                return videos;
            }

            Log.d(TAG, "cursor count=" + cursor.getCount() + " cols=" + cursor.getColumnCount());

            int idCol      = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int dataCol    = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
            int durCol     = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
            int sizeCol    = cursor.getColumnIndex(MediaStore.Video.Media.SIZE);
            int dateCol    = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED);

            while (cursor.moveToNext()) {
                long id       = cursor.getLong(idCol);
                String name   = cursor.getString(nameCol);
                String path   = dataCol >= 0 ? cursor.getString(dataCol) : null;
                long duration = durCol >= 0 ? cursor.getLong(durCol) : 0;
                long size     = sizeCol >= 0 ? cursor.getLong(sizeCol) : 0;
                long dateAdded = dateCol >= 0 ? cursor.getLong(dateCol) : 0;

                // On Android 10+, DATA column may be null; fall back to content URI
                if (path == null || path.isEmpty()) {
                    Uri contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    path = contentUri.toString();
                    Log.d(TAG, "DATA is null for " + name + ", using content URI: " + path);
                }

                // Use content URI as placeholder thumbnail — real thumbnails resolved async later
                Uri placeholderThumb = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);

                videos.add(new VideoItem(id, name, path, duration, size, placeholderThumb, dateAdded));
                Log.d(TAG, "found: " + name + " path=" + path);
            }
        } catch (Exception e) {
            Log.e(TAG, "query failed", e);
        }

        return videos;
    }

    /**
     * Resolves a single thumbnail URI for the given video.
     * Priority: disk cache > system thumbnails table > generate + cache.
     * Returns null if all strategies fail (UI will show ErrorFallback).
     */
    public static Uri resolveThumbnail(Context context, long videoId, String videoPath, String displayName) {
        // Priority 1: Disk cache (fastest — no DB query)
        File cached = getCachedFile(context, videoId);
        if (cached != null) {
            Log.d(TAG, "Cache hit for " + displayName + " -> " + cached);
            return Uri.fromFile(cached);
        }

        ContentResolver resolver = context.getContentResolver();

        // Priority 2: System videothumbnails table
        Uri systemUri = querySystemThumbnailUri(resolver, videoId);
        if (systemUri != null) {
            Log.d(TAG, "System thumbnail for " + displayName + " -> " + systemUri);
            return systemUri;
        }

        // Priority 3: Generate from video and cache
        Uri generatedUri = generateAndCacheThumbnail(context, videoId, videoPath, displayName);
        if (generatedUri != null) {
            Log.d(TAG, "Generated thumbnail for " + displayName + " -> " + generatedUri);
        }

        return generatedUri;
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
     * Returns the cached thumbnail file for the given video, or null if not cached.
     */
    private static File getCachedFile(Context context, long videoId) {
        File cacheDir = new File(context.getCacheDir(), "thumbs");
        File cacheFile = new File(cacheDir, "thumb_" + videoId + ".jpg");
        return cacheFile.exists() ? cacheFile : null;
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
