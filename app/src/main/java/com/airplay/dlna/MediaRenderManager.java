package com.airplay.dlna;

import com.airplay.model.CastDevice;
import com.airplay.model.PlaybackState;
import com.airplay.util.LogBuffer;

import org.jupnp.UpnpService;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.Service;
import org.jupnp.model.types.ServiceType;
import org.jupnp.controlpoint.ActionCallback;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controls playback on a DLNA MediaRenderer via UPnP AVTransport service.
 */
public class MediaRenderManager {
    private static final String TAG = "MediaRender";
    private static final ServiceType AV_TRANSPORT_SERVICE =
        org.jupnp.model.types.ServiceType.valueOf("urn:schemas-upnp-org:service:AVTransport:1");
    private static final ServiceType RENDERING_CONTROL =
        org.jupnp.model.types.ServiceType.valueOf("urn:schemas-upnp-org:service:RenderingControl:1");

    private final UpnpService upnpService;
    private final CastDevice device;
    private PlaybackState currentState = PlaybackState.STOPPED;

    public MediaRenderManager(UpnpService upnpService, CastDevice device) {
        this.upnpService = upnpService;
        this.device = device;
    }

    public CastDevice getDevice() { return device; }
    public PlaybackState getCurrentState() { return currentState; }

    /** Set the AV transport URI and start playback. */
    public void playUri(String streamUrl, String metadata) {
        LogBuffer.d(TAG, "playUri: " + streamUrl + " on " + device.getName());

        Service<?, ?> avService = findService(AV_TRANSPORT_SERVICE);
        if (avService == null) {
            LogBuffer.w(TAG, "AVTransport service not found on " + device.getName());
            return;
        }
        LogBuffer.d(TAG, "AVTransport service found, sending SetAVTransportURI");

        org.jupnp.model.meta.Action action = avService.getAction("SetAVTransportURI");
        if (action == null) {
            LogBuffer.e(TAG, "SetAVTransportURI action not found on device " + device.getName());
            return;
        }

        // Log action arguments for debugging
        StringBuilder argsLog = new StringBuilder("SetAVTransportURI args:");
        for (org.jupnp.model.meta.ActionArgument arg : action.getArguments()) {
            argsLog.append(" ").append(arg.getName()).append("(").append(arg.getDatatype()).append(")");
        }
        LogBuffer.d(TAG, argsLog.toString());

        // First send Stop to reset state, then SetAVTransportURI
        stopInternal(avService, () -> {
            ActionInvocation setUri = new ActionInvocation(action);
            setUri.setInput("InstanceID", "0");
            setUri.setInput("CurrentURI", streamUrl);
            setUri.setInput("CurrentURIMetaData", metadata != null ? metadata : "");

            upnpService.getControlPoint().execute(new ActionCallback(setUri) {
                @Override
                public void success(ActionInvocation invocation) {
                    LogBuffer.i(TAG, "SetAVTransportURI success -> calling play()");
                    play();
                }

                @Override
                public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                    LogBuffer.e(TAG, "SetAVTransportURI failed: " + defaultMsg
                        + " (response status=" + (operation != null ? operation.getStatusCode() : "null") + ")");
                }
            });
        });
    }

    private void stopInternal(Service<?, ?> avService, Runnable onComplete) {
        org.jupnp.model.meta.Action action = avService.getAction("Stop");
        if (action == null) { onComplete.run(); return; }

        ActionInvocation stopInv = new ActionInvocation(action);
        stopInv.setInput("InstanceID", "0");
        upnpService.getControlPoint().execute(new ActionCallback(stopInv) {
            @Override public void success(ActionInvocation invocation) {
                LogBuffer.d(TAG, "Pre-Stop success");
                onComplete.run();
            }
            @Override public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                // Stop may fail (e.g. already stopped) — continue anyway
                LogBuffer.d(TAG, "Pre-Stop (optional): " + defaultMsg);
                onComplete.run();
            }
        });
    }

    /** Send Play command. */
    public void play() {
        Service<?, ?> avService = findService(AV_TRANSPORT_SERVICE);
        if (avService == null) return;

        org.jupnp.model.meta.Action action = avService.getAction("Play");
        if (action == null) {
            LogBuffer.e(TAG, "Play action not found on device " + device.getName());
            return;
        }

        ActionInvocation playInvocation = new ActionInvocation(action);
        playInvocation.setInput("InstanceID", "0");
        playInvocation.setInput("Speed", "1");

        upnpService.getControlPoint().execute(new ActionCallback(playInvocation) {
            @Override
            public void success(ActionInvocation invocation) {
                currentState = PlaybackState.PLAYING;
                LogBuffer.i(TAG, "Play success on " + device.getName());
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                LogBuffer.e(TAG, "Play failed: " + defaultMsg
                    + " (response status=" + (operation != null ? operation.getStatusCode() : "null") + ")");
            }
        });
    }

    /** Send Pause command. */
    public void pause() {
        Service<?, ?> avService = findService(AV_TRANSPORT_SERVICE);
        if (avService == null) return;

        org.jupnp.model.meta.Action action = avService.getAction("Pause");
        if (action == null) { LogBuffer.e(TAG, "Pause action not found"); return; }

        ActionInvocation pauseInvocation = new ActionInvocation(action);
        pauseInvocation.setInput("InstanceID", "0");

        upnpService.getControlPoint().execute(new ActionCallback(pauseInvocation) {
            @Override
            public void success(ActionInvocation invocation) {
                currentState = PlaybackState.PAUSED;
                LogBuffer.i(TAG, "Pause success");
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                LogBuffer.e(TAG, "Pause failed: " + defaultMsg
                    + " (response status=" + (operation != null ? operation.getStatusCode() : "null") + ")");
            }
        });
    }

    /** Send Stop command. */
    public void stop() {
        Service<?, ?> avService = findService(AV_TRANSPORT_SERVICE);
        if (avService == null) return;

        org.jupnp.model.meta.Action action = avService.getAction("Stop");
        if (action == null) { LogBuffer.e(TAG, "Stop action not found"); return; }

        ActionInvocation stopInvocation = new ActionInvocation(action);
        stopInvocation.setInput("InstanceID", "0");

        upnpService.getControlPoint().execute(new ActionCallback(stopInvocation) {
            @Override
            public void success(ActionInvocation invocation) {
                currentState = PlaybackState.STOPPED;
                LogBuffer.i(TAG, "Stop success");
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                LogBuffer.e(TAG, "Stop failed: " + defaultMsg
                    + " (response status=" + (operation != null ? operation.getStatusCode() : "null") + ")");
            }
        });
    }

    /** Seek to a specific position. */
    public void seek(long positionMs) {
        Service<?, ?> avService = findService(AV_TRANSPORT_SERVICE);
        if (avService == null) return;

        String timeFormat = formatTime(positionMs);

        org.jupnp.model.meta.Action action = avService.getAction("Seek");
        if (action == null) { LogBuffer.e(TAG, "Seek action not found"); return; }

        ActionInvocation seekInvocation = new ActionInvocation(action);
        seekInvocation.setInput("InstanceID", "0");
        seekInvocation.setInput("Unit", "REL_TIME");
        seekInvocation.setInput("Target", timeFormat);

        upnpService.getControlPoint().execute(new ActionCallback(seekInvocation) {
            @Override
            public void success(ActionInvocation invocation) {
                LogBuffer.d(TAG, "Seek success to " + timeFormat);
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                LogBuffer.e(TAG, "Seek failed: " + defaultMsg
                    + " (response status=" + (operation != null ? operation.getStatusCode() : "null") + ")");
            }
        });
    }

    /** Query current transport info. Returns position in ms, or -1 on failure. */
    public long getPositionMs() {
        Service<?, ?> avService = findService(AV_TRANSPORT_SERVICE);
        if (avService == null) return -1;

        org.jupnp.model.meta.Action action = avService.getAction("GetPositionInfo");
        if (action == null) { LogBuffer.e(TAG, "GetPositionInfo action not found"); return -1; }

        ActionInvocation posInvocation = new ActionInvocation(action);
        posInvocation.setInput("InstanceID", "0");

        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        upnpService.getControlPoint().execute(new ActionCallback(posInvocation) {
            @Override
            public void success(ActionInvocation invocation) {
                result.set((String) invocation.getOutput("AbsTime").getValue());
                latch.countDown();
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                LogBuffer.e(TAG, "GetPositionInfo failed: " + defaultMsg
                    + " (response status=" + (operation != null ? operation.getStatusCode() : "null") + ")");
                latch.countDown();
            }
        });

        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return -1;
        }

        String timeStr = result.get();
        if (timeStr == null) return -1;
        return parseTime(timeStr);
    }

    /** Set volume (0-100). */
    public void setVolume(int volume) {
        Service<?, ?> rcService = findService(RENDERING_CONTROL);
        if (rcService == null) {
            LogBuffer.w(TAG, "RenderingControl service not found");
            return;
        }

        org.jupnp.model.meta.Action action = rcService.getAction("SetVolume");
        if (action == null) { LogBuffer.e(TAG, "SetVolume action not found"); return; }

        ActionInvocation volInvocation = new ActionInvocation(action);
        volInvocation.setInput("InstanceID", "0");
        volInvocation.setInput("Channel", "Master");
        volInvocation.setInput("DesiredVolume", Math.max(0, Math.min(100, volume)));

        upnpService.getControlPoint().execute(new ActionCallback(volInvocation) {
            @Override
            public void success(ActionInvocation invocation) {
                LogBuffer.d(TAG, "Volume set to " + volume);
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                LogBuffer.e(TAG, "SetVolume failed: " + defaultMsg
                    + " (response status=" + (operation != null ? operation.getStatusCode() : "null") + ")");
            }
        });
    }

    // ---- Private helpers ----

    private Service<?, ?> findService(ServiceType type) {
        try {
            String descUrl = "http://" + device.getIpAddress() + ":" + device.getPort() + "/desc.xml";
            LogBuffer.d(TAG, "findService: looking for " + type + " on device " + device.getName()
                + " (UDN=" + device.getUdn() + ", descUrl=" + descUrl + ")");
            LogBuffer.d(TAG, "findService: registry has " + upnpService.getRegistry().getRemoteDevices().size() + " remote devices");
            for (RemoteDevice rd : upnpService.getRegistry().getRemoteDevices()) {
                String rdUdn = rd.getIdentity().getUdn().getIdentifierString();
                LogBuffer.d(TAG, "  checking device UDN=" + rdUdn + " name=" + rd.getDetails().getFriendlyName());
                if (rdUdn.equals(device.getUdn())) {
                    Service<?, ?> service = rd.findService(type);
                    if (service != null) {
                        LogBuffer.d(TAG, "  found service: " + type);
                        return service;
                    }
                    LogBuffer.w(TAG, "  device matched but service " + type + " not found on it");
                }
            }
            LogBuffer.w(TAG, "findService: device " + device.getUdn() + " not found in registry");
        } catch (Exception e) {
            LogBuffer.e(TAG, "findService error", e);
        }
        return null;
    }

    private String formatTime(long ms) {
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private long parseTime(String timeStr) {
        // Format: HH:MM:SS or MM:SS
        String[] parts = timeStr.split(":");
        long ms = 0;
        try {
            if (parts.length == 3) {
                ms = (Long.parseLong(parts[0]) * 3600
                    + Long.parseLong(parts[1]) * 60
                    + Long.parseLong(parts[2])) * 1000;
            } else if (parts.length == 2) {
                ms = (Long.parseLong(parts[0]) * 60
                    + Long.parseLong(parts[1])) * 1000;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        return ms;
    }
}
