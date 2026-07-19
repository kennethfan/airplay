package com.airplay.dlna;

import android.content.Context;

import org.jupnp.DefaultUpnpServiceConfiguration;
import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceImpl;

/**
 * Singleton holder for the UPnP service, shared by discovery and rendering managers.
 */
public class UpnpServiceHolder {
    private static UpnpService instance;

    public static synchronized UpnpService getService(Context context) {
        if (instance == null) {
            instance = new UpnpServiceImpl(new DefaultUpnpServiceConfiguration());
            instance.startup();
        }
        return instance;
    }

    public static synchronized void shutdown() {
        if (instance != null) {
            try {
                instance.shutdown();
            } catch (Exception ignored) {}
            instance = null;
        }
    }
}
