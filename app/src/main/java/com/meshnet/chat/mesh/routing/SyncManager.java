package com.meshnet.chat.mesh.routing;

import android.util.Log;

import com.meshnet.chat.data.model.MeshMessage;
import com.meshnet.chat.data.model.MeshPacket;
import com.meshnet.chat.data.model.MeshPeer;

import java.util.*;
import java.util.concurrent.*;

/**
 * Orchestrates periodic sync between this node and connected peers.
 *
 * Runs a sync loop that:
 * 1. Exchanges handshakes with each connected peer
 * 2. Computes missing messages
 * 3. Sends batches of missing messages
 */
public class SyncManager {

    private static final String TAG = "SyncManager";
    private static final long SYNC_INTERVAL_MS = 5_000;
    private static final int MAX_BATCH_SIZE = 20;

    private final MessageRouter router;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> syncTask;

    /** Callback for sending packets to specific peers. */
    public interface PeerTransport {
        void sendToPeer(String peerId, MeshPacket packet);
        List<String> getConnectedPeerIds();
    }

    private PeerTransport peerTransport;

    public SyncManager(MessageRouter router) {
        this.router = router;
    }

    public void setPeerTransport(PeerTransport transport) {
        this.peerTransport = transport;
    }

    /** Start the periodic sync loop. */
    public void startSync(String localPeerId, String localDisplayName) {
        if (syncTask != null) return;

        syncTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                performSync(localPeerId, localDisplayName);
            } catch (Exception e) {
                Log.e(TAG, "Sync error", e);
            }
        }, 0, SYNC_INTERVAL_MS, TimeUnit.MILLISECONDS);

        Log.i(TAG, "Sync loop started (interval=" + SYNC_INTERVAL_MS + "ms)");
    }

    public void stopSync() {
        if (syncTask != null) {
            syncTask.cancel(false);
            syncTask = null;
        }
        Log.i(TAG, "Sync loop stopped");
    }

    private void performSync(String localPeerId, String localDisplayName) {
        if (peerTransport == null) return;

        List<String> peerIds = peerTransport.getConnectedPeerIds();
        if (peerIds.isEmpty()) return;

        // Send handshake with known message IDs to all connected peers
        List<String> knownIds = router.getKnownMessageIds();
        MeshPacket handshake = MeshPacket.handshake(localPeerId, localDisplayName, knownIds);

        for (String peerId : peerIds) {
            peerTransport.sendToPeer(peerId, handshake);
        }

        // Drain and broadcast outgoing messages
        List<MeshMessage> outgoing = router.drainOutgoing(MAX_BATCH_SIZE);
        if (!outgoing.isEmpty()) {
            MeshPacket batch = MeshPacket.messageBatch(outgoing);
            for (String peerId : peerIds) {
                peerTransport.sendToPeer(peerId, batch);
            }
            Log.d(TAG, "Broadcast " + outgoing.size() + " messages to " + peerIds.size() + " peers");
        }
    }

    /** Handle an incoming packet from a peer and route appropriately. */
    public void handleIncomingPacket(String fromPeerId, MeshPacket packet) {
        if (packet == null || packet.getType() == null) return;

        switch (packet.getType()) {
            case HANDSHAKE:
                handleHandshake(fromPeerId, packet);
                break;

            case SYNC_REQUEST:
                handleSyncRequest(fromPeerId, packet);
                break;

            case MESSAGE_BATCH:
                handleMessageBatch(packet);
                break;

            case ACK:
                Log.d(TAG, "Received ACK from " + fromPeerId);
                break;
        }
    }

    private void handleHandshake(String fromPeerId, MeshPacket packet) {
        Log.d(TAG, "Handshake from " + packet.getDisplayName() + " with " +
                (packet.getKnownMessageIds() != null ? packet.getKnownMessageIds().size() : 0) + " known IDs");

        // Compute what peer is missing and send sync request back
        if (packet.getKnownMessageIds() != null) {
            List<String> theyAreMissing = router.computeMissingIds(packet.getKnownMessageIds());
            if (!theyAreMissing.isEmpty() && peerTransport != null) {
                // We could send them the actual messages they're missing
                // For now, send a sync request so they know to request them
                Log.d(TAG, "Peer " + fromPeerId + " is missing " + theyAreMissing.size() + " messages");
            }
        }
    }

    private void handleSyncRequest(String fromPeerId, MeshPacket packet) {
        // Peer is requesting specific messages - we'd look them up in the DB
        Log.d(TAG, "Sync request from " + fromPeerId + " for " +
                (packet.getMissingIds() != null ? packet.getMissingIds().size() : 0) + " messages");
    }

    private void handleMessageBatch(MeshPacket packet) {
        if (packet.getMessages() == null) return;

        int accepted = 0;
        for (MeshMessage msg : packet.getMessages()) {
            if (router.processIncoming(msg)) {
                accepted++;
            }
        }
        Log.d(TAG, "Processed batch: " + accepted + "/" + packet.getMessages().size() + " new");
    }

    public void shutdown() {
        stopSync();
        scheduler.shutdownNow();
    }
}
