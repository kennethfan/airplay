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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

public class StreamServerManager extends NanoHTTPD {
    private static final String TAG = "StreamServer";
    private static final int PORT = 8899;
    private static final Pattern RANGE_HEADER = Pattern.compile("bytes=(\\d+)-(\\d*)");

    /** 允许流式读取的路径上限，避免无限增长 */
    private static final int MAX_ALLOWED_PATHS = 200;

    private final Context context;

    /**
     * 已登记的允许访问路径。
     *
     * 只有通过 {@link #buildStreamUrl(String)} 投屏过的视频才会进入此集合，
     * 从而阻止同网段其他设备构造 path 参数读取 App 可访问的任意文件。
     * 使用 LRU 语义（超出上限时淘汰最早登记的条目）。
     */
    private final Set<String> allowedPaths = Collections.newSetFromMap(
        new LinkedHashMap<String, Boolean>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > MAX_ALLOWED_PATHS;
            }
        });

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
        // 登记允许访问的路径——serve() 只服务已登记路径
        synchronized (allowedPaths) {
            allowedPaths.add(videoPath);
        }
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

        // 白名单校验：只有通过 buildStreamUrl() 登记过的路径才允许读取，
        // 防止局域网内其他设备构造任意 path 读取 App 私有文件。
        // 注意：这里只做精确匹配，不对 path 做二次解码——
        // 二次解码会让 %252F 之类的双重编码绕过白名单。
        boolean allowed;
        synchronized (allowedPaths) {
            allowed = allowedPaths.contains(path);
        }
        if (!allowed) {
            Log.w(TAG, "Rejected unregistered path: " + path);
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden");
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
