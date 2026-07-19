package com.airplay.dlna;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.util.Log;

import com.airplay.util.LogBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

public class StreamServerManager extends NanoHTTPD {
    private static final String TAG = "StreamServer";
    private static final int PORT = 8899;
    private static final Pattern RANGE_HEADER = Pattern.compile("bytes=(\\d+)-(\\d*)");

    private final Context context;

    public StreamServerManager(Context context) {
        super(PORT);
        this.context = context.getApplicationContext();
    }

    public String getLocalIpAddress() {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (iface.isLoopback() || !iface.isUp()) continue;
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "Failed to get local IP", e);
        }
        return "127.0.0.1";
    }

    public String buildStreamUrl(String videoPath) {
        String ip = getLocalIpAddress();
        String url = "http://" + ip + ":" + PORT + "/stream?path=" + Uri.encode(videoPath);
        LogBuffer.d(TAG, "buildStreamUrl: ip=" + ip + " url=" + url);
        return url;
    }

    private boolean isContentUri(String path) {
        return path != null && path.startsWith("content://");
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, List<String>> params = session.getParameters();
        String path = params.containsKey("path") ? params.get("path").get(0) : null;

        if (!"/stream".equals(uri) || path == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found");
        }

        String mimeType;
        long fileLength;

        if (isContentUri(path)) {
            // Content URI (Android 10+ scoped storage)
            Uri contentUri = Uri.parse(path);
            try (AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(contentUri, "r")) {
                if (afd == null) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found");
                }
                fileLength = afd.getLength();
                mimeType = context.getContentResolver().getType(contentUri);
                if (mimeType == null) mimeType = "application/octet-stream";
            } catch (IOException e) {
                Log.e(TAG, "Failed to open content URI", e);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Open failed");
            }

            String rangeHeader = session.getHeaders().get("range");
            if (rangeHeader != null) {
                Matcher m = RANGE_HEADER.matcher(rangeHeader);
                if (m.matches()) {
                    long start = Long.parseLong(m.group(1));
                    long end = m.group(2).isEmpty() ? fileLength - 1 : Long.parseLong(m.group(2));
                    long contentLength = end - start + 1;
                    try {
                        InputStream is = new ContentUriStream(context, contentUri, start, contentLength);
                        Response response = newChunkedResponse(Response.Status.PARTIAL_CONTENT, mimeType, is);
                        response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                        response.addHeader("Content-Length", String.valueOf(contentLength));
                        response.addHeader("Accept-Ranges", "bytes");
                        return response;
                    } catch (IOException e) {
                        Log.e(TAG, "Stream error", e);
                        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Stream error");
                    }
                }
            }

            try {
                InputStream is = new ContentUriStream(context, contentUri, 0, fileLength);
                Response response = newChunkedResponse(Response.Status.OK, mimeType, is);
                response.addHeader("Content-Length", String.valueOf(fileLength));
                response.addHeader("Accept-Ranges", "bytes");
                return response;
            } catch (IOException e) {
                Log.e(TAG, "Stream error", e);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Stream error");
            }
        } else {
            // Regular file path
            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found");
            }

            mimeType = getMimeType(file.getName());
            fileLength = file.length();

            String rangeHeader = session.getHeaders().get("range");
            if (rangeHeader != null) {
                Matcher m = RANGE_HEADER.matcher(rangeHeader);
                if (m.matches()) {
                    long start = Long.parseLong(m.group(1));
                    long end = m.group(2).isEmpty() ? fileLength - 1 : Long.parseLong(m.group(2));
                    long contentLength = end - start + 1;
                    try {
                        Response response = newChunkedResponse(Response.Status.PARTIAL_CONTENT, mimeType, new FileStream(file, start, contentLength));
                        response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                        response.addHeader("Content-Length", String.valueOf(contentLength));
                        response.addHeader("Accept-Ranges", "bytes");
                        return response;
                    } catch (IOException e) {
                        Log.e(TAG, "Stream error", e);
                        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Stream error");
                    }
                }
            }

            try {
                Response response = newChunkedResponse(Response.Status.OK, mimeType, new FileStream(file, 0, fileLength));
                response.addHeader("Content-Length", String.valueOf(fileLength));
                response.addHeader("Accept-Ranges", "bytes");
                return response;
            } catch (IOException e) {
                Log.e(TAG, "Stream error", e);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Stream error");
            }
        }
    }

    private String getMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".wmv")) return "video/x-ms-wmv";
        if (lower.endsWith(".flv")) return "video/x-flv";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".ts")) return "video/mp2t";
        if (lower.endsWith(".m4v")) return "video/x-m4v";
        return "application/octet-stream";
    }

    private static class FileStream extends InputStream {
        private final FileInputStream fis;
        private long remaining;

        FileStream(File file, long start, long length) throws IOException {
            this.fis = new FileInputStream(file);
            this.remaining = length;
            fis.skip(start);
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            remaining--;
            return fis.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            int read = fis.read(b, off, toRead);
            if (read > 0) remaining -= read;
            return read;
        }

        @Override
        public void close() throws IOException {
            fis.close();
        }
    }

    private static class ContentUriStream extends InputStream {
        private final InputStream input;
        private long remaining;

        ContentUriStream(Context context, Uri uri, long start, long length) throws IOException {
            ContentResolver resolver = context.getContentResolver();
            this.input = resolver.openInputStream(uri);
            if (this.input == null) throw new IOException("Cannot open " + uri);
            this.remaining = length;
            if (start > 0) input.skip(start);
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            remaining--;
            return input.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            int read = input.read(b, off, toRead);
            if (read > 0) remaining -= read;
            return read;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }
}
