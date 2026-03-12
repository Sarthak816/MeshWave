package com.meshnet.chat.mesh;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.meshnet.chat.R;
import com.meshnet.chat.data.local.MeshDatabase;
import com.meshnet.chat.data.local.MessageRepository;
import com.meshnet.chat.data.model.MeshMessage;
import com.meshnet.chat.data.model.MeshPacket;
import com.meshnet.chat.data.model.MeshPeer;
import com.meshnet.chat.mesh.ble.BleConnectionManager;
import com.meshnet.chat.mesh.ble.BleDiscoveryManager;
import com.meshnet.chat.mesh.routing.MessageRouter;
import com.meshnet.chat.mesh.routing.SyncManager;
import com.meshnet.chat.mesh.wifi.WifiDirectDataTransfer;
import com.meshnet.chat.mesh.wifi.WifiDirectManager;
import com.meshnet.chat.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Foreground service that keeps the mesh network alive.
 * Manages BLE discovery, Wi-Fi Direct connections, message routing,
 * and sync across all connected peers.
 */
public class MeshService extends Service {

    private static final String TAG = "MeshService";
    private static final String CHANNEL_ID = "meshwave_service";
    private static final int NOTIFICATION_ID = 1001;
    private static final long STALE_PEER_CHECK_MS = 15_000;

    private final IBinder binder = new MeshBinder();

    // Mesh components
    private BleDiscoveryManager bleDiscovery;
    private BleConnectionManager bleConnection;
    private WifiDirectManager wifiDirect;
    private WifiDirectDataTransfer wifiTransfer;
    private MessageRouter router;
    private SyncManager syncManager;
    private MessageRepository repository;

    // Device identity
    private String localPeerId;
    private String localDisplayName;

    // Status
    private final MutableLiveData<Boolean> meshActiveLive = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> peerCountLive = new MutableLiveData<>(0);
    private final MutableLiveData<String> statusTextLive = new MutableLiveData<>("Idle");

    private Handler handler;
    private boolean isRunning = false;

    public class MeshBinder extends Binder {
        public MeshService getService() {
            return MeshService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        initializeComponents();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Starting mesh network..."));
        startMesh();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopMesh();
        super.onDestroy();
    }

    // Public API

    public void startMesh() {
        if (isRunning) return;
        isRunning = true;

        bleDiscovery.startAdvertising();
        bleDiscovery.startScan();
        bleConnection.startGattServer();

        wifiDirect.initialize();
        wifiDirect.discoverPeers();

        syncManager.startSync(localPeerId, localDisplayName);

        meshActiveLive.postValue(true);
        statusTextLive.postValue("Scanning for peers...");
        startStalePeerCleanup();

        Log.i(TAG, "Mesh started as " + localDisplayName + " (" + localPeerId + ")");
    }

    public void stopMesh() {
        if (!isRunning) return;
        isRunning = false;

        bleDiscovery.stopScan();
        bleDiscovery.stopAdvertising();
        bleConnection.stopGattServer();
        wifiDirect.cleanup();
        wifiTransfer.shutdown();
        syncManager.shutdown();
        handler.removeCallbacksAndMessages(null);

        meshActiveLive.postValue(false);
        statusTextLive.postValue("Idle");

        Log.i(TAG, "Mesh stopped");
    }

    public void sendMessage(String text, String roomId) {
        MeshMessage msg = new MeshMessage(localPeerId, localDisplayName, roomId, text);
        router.sendLocal(msg);
        repository.insert(msg);
        Log.d(TAG, "Sent message: " + msg.getId());
    }

    public MessageRouter getRouter() { return router; }
    public MessageRepository getRepository() { return repository; }
    public LiveData<Map<String, MeshPeer>> getDiscoveredPeers() { return bleDiscovery.getDiscoveredPeers(); }
    public LiveData<Boolean> getMeshActive() { return meshActiveLive; }
    public LiveData<Integer> getPeerCount() { return peerCountLive; }
    public LiveData<String> getStatusText() { return statusTextLive; }
    public String getLocalPeerId() { return localPeerId; }
    public String getLocalDisplayName() { return localDisplayName; }

    public void setLocalDisplayName(String name) {
        this.localDisplayName = name;
    }

    // Internal setup

    private void initializeComponents() {
        // Load identity
        localPeerId = getSharedPreferences("meshwave", MODE_PRIVATE)
                .getString("peer_id", java.util.UUID.randomUUID().toString());
        localDisplayName = getSharedPreferences("meshwave", MODE_PRIVATE)
                .getString("display_name", "User-" + localPeerId.substring(0, 6));

        // Save if first time
        getSharedPreferences("meshwave", MODE_PRIVATE).edit()
                .putString("peer_id", localPeerId)
                .putString("display_name", localDisplayName)
                .apply();

        // Database
        MeshDatabase db = MeshDatabase.getInstance(this);
        repository = new MessageRepository(db.messageDao());

        // Routing
        router = new MessageRouter();
        router.setRouterListener(new MessageRouter.RouterListener() {
            @Override
            public void onMessageForLocal(MeshMessage message) {
                repository.insert(message);
            }

            @Override
            public void onMessageForForwarding(MeshMessage message) {
                // Forwarding is handled by SyncManager drain loop
                Log.d(TAG, "Message queued for forwarding: " + message.getId());
            }
        });

        // BLE
        bleDiscovery = new BleDiscoveryManager(this);
        bleConnection = new BleConnectionManager(this);
        bleConnection.setPacketListener((fromAddress, packet) -> {
            syncManager.handleIncomingPacket(fromAddress, packet);
            updatePeerCount();
        });

        // Wi-Fi Direct
        wifiDirect = new WifiDirectManager(this);
        wifiTransfer = new WifiDirectDataTransfer();
        wifiTransfer.setDataListener(new WifiDirectDataTransfer.DataListener() {
            @Override
            public void onPacketReceived(MeshPacket packet) {
                syncManager.handleIncomingPacket("wifi-peer", packet);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Wi-Fi Direct transfer error: " + error);
            }
        });

        // Observe Wi-Fi Direct connection changes to start server/client
        wifiDirect.getConnectionInfo().observeForever(this::onWifiDirectConnected);

        // Sync manager wiring
        syncManager = new SyncManager(router);
        syncManager.setPeerTransport(new SyncManager.PeerTransport() {
            @Override
            public void sendToPeer(String peerId, MeshPacket packet) {
                // Try BLE first, fall back to Wi-Fi Direct
                bleConnection.sendPacket(peerId, packet);
            }

            @Override
            public List<String> getConnectedPeerIds() {
                List<String> ids = new ArrayList<>();
                Map<String, MeshPeer> peers = bleDiscovery.getDiscoveredPeers().getValue();
                if (peers != null) {
                    ids.addAll(peers.keySet());
                }
                return ids;
            }
        });
    }

    private void onWifiDirectConnected(WifiP2pInfo info) {
        if (info == null || !info.groupFormed) return;

        if (info.isGroupOwner) {
            wifiTransfer.startServer();
            statusTextLive.postValue("Wi-Fi Direct: hosting group");
        } else if (info.groupOwnerAddress != null) {
            String ownerIp = info.groupOwnerAddress.getHostAddress();
            statusTextLive.postValue("Wi-Fi Direct: connected to " + ownerIp);
        }
    }

    private void updatePeerCount() {
        Map<String, MeshPeer> peers = bleDiscovery.getDiscoveredPeers().getValue();
        int count = peers != null ? peers.size() : 0;
        peerCountLive.postValue(count);
        updateNotification(count);
    }

    private void startStalePeerCleanup() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                bleDiscovery.removeStalePeers(30_000);
                updatePeerCount();
                handler.postDelayed(this, STALE_PEER_CHECK_MS);
            }
        }, STALE_PEER_CHECK_MS);
    }

    // Notification

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "MeshWave Service", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps the mesh network active in the background");
            NotificationManager mgr = getSystemService(NotificationManager.class);
            if (mgr != null) mgr.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MeshWave")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_mesh)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @SuppressLint("MissingPermission")
    private void updateNotification(int peerCount) {
        String text = peerCount == 0
                ? "Scanning for peers..."
                : peerCount + " peer" + (peerCount == 1 ? "" : "s") + " nearby";
        statusTextLive.postValue(text);

        NotificationManager mgr = getSystemService(NotificationManager.class);
        if (mgr != null) {
            mgr.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }
}
