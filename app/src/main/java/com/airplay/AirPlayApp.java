package com.airplay;

import android.app.Application;

import com.airplay.dlna.DeviceDiscoveryManager;
import com.airplay.dlna.StreamServerManager;

import java.io.IOException;

public class AirPlayApp extends Application {
    private DeviceDiscoveryManager discoveryManager;
    private StreamServerManager streamServer;

    @Override
    public void onCreate() {
        super.onCreate();
        initManagers();
        startStreamServer();
        // 注意：前台服务不再在此处启动。
        // 原实现在 App 一打开就常驻通知栏并持有 4 小时 wake lock，
        // 即使根本没有投屏；且 targetSdk 34 下从 Application 启动 FGS 受限。
        // 现改为在真正开始投屏时启动（见 MainViewModel.castVideos）。
    }

    private void initManagers() {
        discoveryManager = new DeviceDiscoveryManager(this);
    }

    private void startStreamServer() {
        streamServer = new StreamServerManager(this);
        try {
            streamServer.start();
            android.util.Log.d("AirPlayApp", "Stream server started on port 8899");
        } catch (IOException e) {
            android.util.Log.e("AirPlayApp", "Failed to start stream server", e);
        }
    }

    public DeviceDiscoveryManager getDiscoveryManager() {
        return discoveryManager;
    }

    public StreamServerManager getStreamServer() {
        return streamServer;
    }

    @Override
    public void onTerminate() {
        if (streamServer != null) streamServer.stop();
        if (discoveryManager != null) discoveryManager.stopDiscovery();
        super.onTerminate();
    }
}
