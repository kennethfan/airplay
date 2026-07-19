package com.airplay.dlna;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import com.airplay.model.CastDevice;
import com.airplay.util.LogBuffer;

import org.jupnp.android.AndroidUpnpServiceConfiguration;
import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.types.ServiceType;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DeviceDiscoveryManager {
    private static final String TAG = "DeviceDiscovery";
    private static final ServiceType AV_TRANSPORT =
        ServiceType.valueOf("urn:schemas-upnp-org:service:AVTransport:1");
    private static final long SEARCH_INTERVAL_MS = 15000; // periodic M-SEARCH every 15s
    private static final long SCAN_TIMEOUT_MS = 120000;   // give up after 2 minutes

    private final Context context;
    private final List<CastDevice> devices = new CopyOnWriteArrayList<>();
    private final List<DiscoveryListener> listeners = new CopyOnWriteArrayList<>();
    private UpnpService upnpService;
    private boolean isScanning = false;
    private WifiManager.MulticastLock multicastLock;
    private WifiManager.WifiLock wifiLock;
    private Handler searchHandler;
    private Runnable searchRunnable;
    private long scanStartTime;

    public interface DiscoveryListener {
        void onDeviceFound(CastDevice device);
        void onDeviceLost(CastDevice device);
        void onDiscoveryError(String message);
    }

    public DeviceDiscoveryManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void startDiscovery() {
        if (isScanning) return;
        isScanning = true;
        scanStartTime = System.currentTimeMillis();
        devices.clear();

        try {
            acquireMulticastLock();
            acquireWifiLock();
            upnpService = new UpnpServiceImpl(new AndroidUpnpServiceConfiguration());
            upnpService.startup();

            Registry registry = upnpService.getRegistry();
            if (registry == null) {
                throw new RuntimeException("UPnP registry not available after startup");
            }

            registry.addListener(new RegistryListener() {
                @Override public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {}
                @Override public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception e) {
                    LogBuffer.w(TAG, "Discovery failed for " + device.getDetails().getFriendlyName(), e);
                }
                @Override public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
                    LogBuffer.d(TAG, "Device added: " + device.getDetails().getFriendlyName()
                        + " @ " + device.getIdentity().getDescriptorURL());
                    if (isMediaRenderer(device)) {
                        CastDevice castDevice = toCastDevice(device);
                        devices.add(castDevice);
                        LogBuffer.i(TAG, "Media renderer found: " + castDevice.getName()
                            + " (" + castDevice.getIpAddress() + ")");
                        for (DiscoveryListener l : listeners) l.onDeviceFound(castDevice);
                    } else {
                        LogBuffer.d(TAG, "Ignored non-media device: "
                            + device.getDetails().getFriendlyName());
                    }
                }
                @Override public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
                    CastDevice removed = findDevice(device);
                    if (removed != null) {
                        devices.remove(removed);
                        for (DiscoveryListener l : listeners) l.onDeviceLost(removed);
                    }
                }
                @Override public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {}
                @Override public void localDeviceAdded(Registry registry, org.jupnp.model.meta.LocalDevice device) {}
                @Override public void localDeviceRemoved(Registry registry, org.jupnp.model.meta.LocalDevice device) {}
                @Override public void beforeShutdown(Registry registry) {}
                @Override public void afterShutdown() {}
            });

            upnpService.getControlPoint().search();
            startPeriodicSearch();

            LogBuffer.i(TAG, "UPnP discovery started — searching every " + SEARCH_INTERVAL_MS + "ms");
        } catch (Throwable e) {
            LogBuffer.e(TAG, "startDiscovery failed", e);
            isScanning = false;
            releaseMulticastLock();
            releaseWifiLock();
            for (DiscoveryListener l : listeners) l.onDiscoveryError(
                "UPnP init failed: " + e.getMessage());
        }
    }

    public void searchNow() {
        if (upnpService != null && isScanning) {
            LogBuffer.d(TAG, "Manual M-SEARCH triggered");
            upnpService.getControlPoint().search();
        } else {
            startDiscovery();
        }
    }

    public void restartDiscovery() {
        LogBuffer.d(TAG, "Restarting discovery");
        isScanning = false;
        stopPeriodicSearch();
        if (upnpService != null) {
            try { upnpService.shutdown(); } catch (Exception ignored) {}
            upnpService = null;
        }
        releaseMulticastLock();
        releaseWifiLock();
        devices.clear();
        startDiscovery();
    }

    private void startPeriodicSearch() {
        stopPeriodicSearch();
        searchHandler = new Handler(Looper.getMainLooper());
        searchRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isScanning || upnpService == null) return;

                if (System.currentTimeMillis() - scanStartTime > SCAN_TIMEOUT_MS) {
                    LogBuffer.w(TAG, "Scan timeout reached (" + SCAN_TIMEOUT_MS + "ms)");
                    stopDiscovery();
                    for (DiscoveryListener l : listeners)
                        l.onDiscoveryError("搜索超时，请确认电视已开启投屏功能");
                    return;
                }

                upnpService.getControlPoint().search();
                searchHandler.postDelayed(this, SEARCH_INTERVAL_MS);
            }
        };
        searchHandler.postDelayed(searchRunnable, SEARCH_INTERVAL_MS);
    }

    private void stopPeriodicSearch() {
        if (searchHandler != null && searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
    }

    public void stopDiscovery() {
        isScanning = false;
        stopPeriodicSearch();
        if (upnpService != null) {
            try {
                upnpService.shutdown();
            } catch (Exception e) {
                LogBuffer.e(TAG, "shutdown error", e);
            }
            upnpService = null;
        }
        releaseMulticastLock();
        releaseWifiLock();
        devices.clear();
    }

    public List<CastDevice> getDevices() { return new ArrayList<>(devices); }
    public void addListener(DiscoveryListener listener) { listeners.add(listener); }
    public void removeListener(DiscoveryListener listener) { listeners.remove(listener); }
    public boolean isScanning() { return isScanning; }

    /** Returns the running UpnpService, or null if discovery hasn't started. */
    public UpnpService getUpnpService() { return upnpService; }

    private void acquireMulticastLock() {
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("airplay-dlna");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
        } catch (Exception e) {
            LogBuffer.w(TAG, "Could not acquire multicast lock", e);
        }
    }

    private void releaseMulticastLock() {
        if (multicastLock != null) {
            try {
                multicastLock.release();
            } catch (Exception ignored) {}
            multicastLock = null;
        }
    }

    private void acquireWifiLock() {
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "airplay-discovery");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
        } catch (Exception e) {
            LogBuffer.w(TAG, "Could not acquire wifi lock", e);
        }
    }

    private void releaseWifiLock() {
        if (wifiLock != null) {
            try {
                wifiLock.release();
            } catch (Exception ignored) {}
            wifiLock = null;
        }
    }

    private boolean isMediaRenderer(RemoteDevice device) {
        return device.findService(AV_TRANSPORT) != null;
    }

    private CastDevice toCastDevice(RemoteDevice device) {
        return new CastDevice(
            device.getDetails().getFriendlyName(),
            device.getIdentity().getDescriptorURL().getHost(),
            device.getIdentity().getDescriptorURL().getPort(),
            device.getIdentity().getUdn().getIdentifierString(),
            true);
    }

    private CastDevice findDevice(RemoteDevice device) {
        String udn = device.getIdentity().getUdn().getIdentifierString();
        for (CastDevice d : devices) {
            if (d.getUdn().equals(udn)) return d;
        }
        return null;
    }
}
