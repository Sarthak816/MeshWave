# MeshWave

Offline mesh network chat for Android. Messages relay between nearby devices using Bluetooth LE and Wi-Fi Direct. No internet, no servers, no cell towers.

Built for [FOSS Hack](https://fossunited.org/hack).

## What it does

When you open MeshWave, your phone starts advertising itself over Bluetooth LE and scanning for other MeshWave users nearby. Once peers are found, messages are exchanged automatically.

If two users are not in direct range, messages hop through intermediate devices. A message can travel up to 7 hops by default before expiring.

**SOS Mode**: Tag any message as high-priority. SOS messages are always forwarded first across the network.

## Architecture

```
+------------------+      +------------------+      +------------------+
|   Device A       | BLE  |   Device B       | WiFi |   Device C       |
|                  |<---->|                  |<---->|                  |
|  Chat UI         |      |  Chat UI         |      |  Chat UI         |
|  MeshService     |      |  MeshService     |      |  MeshService     |
|  MessageRouter   |      |  MessageRouter   |      |  MessageRouter   |
|  Room DB         |      |  Room DB         |      |  Room DB         |
+------------------+      +------------------+      +------------------+
```

### Layers

- **Transport**: BLE for discovery and short messages. Wi-Fi Direct for high-throughput sync between connected peers.
- **Routing**: Epidemic/gossip style. Every new message is forwarded to all connected peers. Deduplication by message ID. TTL prevents infinite loops.
- **Sync**: Periodic handshake exchanges known message IDs. Missing messages are sent in batches, SOS-priority first.
- **Persistence**: Room (SQLite) stores all messages locally. Undelivered messages are queued for the next sync cycle.
- **UI**: Two tabs. Chat for messaging, Peers for network status and control.

### Message Flow

1. User types a message and taps send
2. Message gets a unique ID, timestamp, and TTL of 7
3. MessageRouter marks it as seen and queues it for outgoing sync
4. SyncManager picks it up on the next 5-second cycle
5. Message is serialized to JSON and sent to all connected peers (BLE GATT write or Wi-Fi Direct socket)
6. Receiving peer's router checks if it has seen this ID before
7. If new: store locally, display in chat, decrement TTL, queue for forwarding
8. If duplicate: drop silently
9. Process repeats at each hop until TTL reaches 0

## Project Structure

```
app/src/main/java/com/meshnet/chat/
    MeshWaveApp.java              # Application class, device identity
    data/
        local/
            MeshDatabase.java     # Room database
            MessageDao.java       # Data access object
            MessageRepository.java # Repository pattern
        model/
            MeshMessage.java      # Chat message with TTL, hops, priority
            MeshPeer.java         # Discovered peer info
            MeshPacket.java       # Wire protocol (Handshake, Sync, Batch, Ack)
            MessageEntity.java    # Room entity + domain mappers
            MessagePriority.java  # NORMAL, SOS
            ConnectionState.java  # DISCOVERED, CONNECTING, CONNECTED, DISCONNECTED
    mesh/
        MeshService.java          # Foreground service orchestrating everything
        ble/
            BleDiscoveryManager.java   # BLE scan + advertise
            BleConnectionManager.java  # GATT server/client for packet exchange
        wifi/
            WifiDirectManager.java     # Wi-Fi Direct discovery + connection
            WifiDirectDataTransfer.java # Socket-based message transfer
        routing/
            MessageRouter.java    # Epidemic routing, dedup, priority queue
            SyncManager.java      # Periodic sync loop, packet handling
    ui/
        MainActivity.java         # Tab navigation, permissions, service binding
        chat/
            ChatFragment.java     # Message list, input, SOS toggle
            ChatViewModel.java    # Message LiveData
            MessageAdapter.java   # RecyclerView adapter for chat bubbles
        peers/
            PeersFragment.java    # Peer list, mesh toggle
            PeersViewModel.java   # Peer LiveData
            PeerAdapter.java      # RecyclerView adapter for peer rows
```

## Tech Stack

- Java 17
- Android SDK (minSdk 26 / Android 8.0+)
- Bluetooth Low Energy (BLE) APIs
- Wi-Fi Direct (Wi-Fi P2P) APIs
- Room for local persistence
- Gson for JSON serialization
- Material Components for UI

## Building

1. Clone the repo
2. Open in Android Studio (Hedgehog or newer)
3. Sync Gradle
4. Build and run on a physical device (BLE and Wi-Fi Direct require real hardware)

Minimum two devices needed to test mesh functionality. Three or more to test multi-hop routing.

## Permissions

MeshWave requests the following at runtime:

- Bluetooth Scan, Advertise, Connect (Android 12+)
- Fine Location (required for BLE scanning)
- Nearby Wi-Fi Devices (Android 13+)
- Notifications (for foreground service)

All communication is local. No data leaves the mesh.

## License

MIT
