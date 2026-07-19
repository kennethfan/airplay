package com.airplay.model;

import android.net.Uri;

public class VideoItem {
    private final long id;
    private final String name;
    private final String path;
    private final long durationMs;
    private final long fileSize;
    private final Uri thumbnailUri;

    public VideoItem(long id, String name, String path, long durationMs, long fileSize, Uri thumbnailUri) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.durationMs = durationMs;
        this.fileSize = fileSize;
        this.thumbnailUri = thumbnailUri;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getPath() { return path; }
    public long getDurationMs() { return durationMs; }
    public long getFileSize() { return fileSize; }
    public Uri getThumbnailUri() { return thumbnailUri; }

    /** Format duration as HH:MM:SS or MM:SS */
    public String getDurationFormatted() {
        long totalSec = durationMs / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }
}
