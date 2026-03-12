package com.meshnet.chat.data.model;

/**
 * Represents a discovered peer on the mesh network.
 */
public class MeshPeer {

    private String peerId;
    private String displayName;
    private String bluetoothAddress;
    private String wifiDirectAddress;
    private long lastSeen;
    private int rssi;
    private ConnectionState connectionState;

    public MeshPeer() {
        this.lastSeen = System.currentTimeMillis();
        this.connectionState = ConnectionState.DISCOVERED;
    }

    public MeshPeer(String peerId, String displayName, String bluetoothAddress) {
        this();
        this.peerId = peerId;
        this.displayName = displayName;
        this.bluetoothAddress = bluetoothAddress;
    }

    // ── Getters & Setters ──────────────────────────────────────

    public String getPeerId() { return peerId; }
    public void setPeerId(String peerId) { this.peerId = peerId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getBluetoothAddress() { return bluetoothAddress; }
    public void setBluetoothAddress(String bluetoothAddress) { this.bluetoothAddress = bluetoothAddress; }

    public String getWifiDirectAddress() { return wifiDirectAddress; }
    public void setWifiDirectAddress(String wifiDirectAddress) { this.wifiDirectAddress = wifiDirectAddress; }

    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }

    public int getRssi() { return rssi; }
    public void setRssi(int rssi) { this.rssi = rssi; }

    public ConnectionState getConnectionState() { return connectionState; }
    public void setConnectionState(ConnectionState connectionState) { this.connectionState = connectionState; }
}
