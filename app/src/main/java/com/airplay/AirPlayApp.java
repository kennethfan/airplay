package com.airplay;

import android.app.Application;

import com.airplay.dlna.DeviceDiscoveryManager;
import com.airplay.dlna.StreamServerManager;
import com.airplay.service.CastForegroundService;

import java.io.IOException;

public class AirPlayApp extends Application {
    private DeviceDiscoveryManager discoveryManager;
    private StreamServerManager streamServer;

    @Override
    public void onCreate() {
        super.onCreate();
        initManagers();
        startStreamServer();
        CastForegroundService.start(this);
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
