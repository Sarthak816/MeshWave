package com.meshnet.chat.data.model

/**
 * Represents a discovered peer on the mesh network.
 */
data class MeshPeer(
    val peerId: String,
    val displayName: String,
    val bluetoothAddress: String? = null,
    val wifiDirectAddress: String? = null,
    val lastSeen: Long = System.currentTimeMillis(),
    val rssi: Int = 0,               // signal strength (BLE)
    val connectionState: ConnectionState = ConnectionState.DISCOVERED
)

enum class ConnectionState {
    DISCOVERED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}
