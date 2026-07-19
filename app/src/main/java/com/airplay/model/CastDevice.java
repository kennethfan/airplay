package com.airplay.model;

public class CastDevice {
    private final String name;
    private final String ipAddress;
    private final int port;
    private final String udn;
    private final boolean isOnline;

    public CastDevice(String name, String ipAddress, int port, String udn, boolean isOnline) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.udn = udn;
        this.isOnline = isOnline;
    }

    public String getName() { return name; }
    public String getIpAddress() { return ipAddress; }
    public int getPort() { return port; }
    public String getUdn() { return udn; }
    public boolean isOnline() { return isOnline; }

    @Override
    public String toString() {
        return name + " (" + ipAddress + ":" + port + ")";
    }
}
