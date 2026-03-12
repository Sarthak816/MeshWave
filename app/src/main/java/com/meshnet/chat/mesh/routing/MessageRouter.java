package com.meshnet.chat.mesh.routing;

import android.util.Log;

import com.meshnet.chat.data.model.MeshMessage;
import com.meshnet.chat.data.model.MessagePriority;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Epidemic/gossip-style mesh message router.
 *
 * Maintains a set of seen message IDs to deduplicate,
 * queues outgoing messages, and decides forwarding logic
 * based on TTL and priority.
 */
public class MessageRouter {

    private static final String TAG = "MessageRouter";
    private static final int MAX_SEEN_IDS = 10_000;

    private final Set<String> seenMessageIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Queue<MeshMessage> outgoingQueue = new PriorityQueue<>((a, b) -> {
        // SOS messages always go first
        if (a.getPriority() == MessagePriority.SOS && b.getPriority() != MessagePriority.SOS) return -1;
        if (b.getPriority() == MessagePriority.SOS && a.getPriority() != MessagePriority.SOS) return 1;
        // Then by timestamp (older first)
        return Long.compare(a.getTimestamp(), b.getTimestamp());
    });

    /** Listener for messages that should be forwarded to peers or displayed locally. */
    public interface RouterListener {
        void onMessageForLocal(MeshMessage message);
        void onMessageForForwarding(MeshMessage message);
    }

    private RouterListener routerListener;

    public void setRouterListener(RouterListener listener) {
        this.routerListener = listener;
    }

    /**
     * Process an incoming message (from BLE or Wi-Fi Direct).
     * Returns true if the message was new and accepted.
     */
    public boolean processIncoming(MeshMessage message) {
        if (message == null || message.getId() == null) return false;

        // Deduplicate
        if (seenMessageIds.contains(message.getId())) {
            Log.d(TAG, "Duplicate message dropped: " + message.getId());
            return false;
        }

        markSeen(message.getId());

        // Deliver locally
        if (routerListener != null) {
            routerListener.onMessageForLocal(message);
        }

        // Forward if TTL allows
        MeshMessage forwarded = message.forwarded();
        if (forwarded != null) {
            enqueueForForwarding(forwarded);
        }

        Log.d(TAG, "Processed message: " + message.getId() + " hops=" + message.getHopCount());
        return true;
    }

    /**
     * Queue a locally-created message for sending.
     */
    public void sendLocal(MeshMessage message) {
        if (message == null) return;
        markSeen(message.getId());
        enqueueForForwarding(message);

        // Also deliver to local display
        if (routerListener != null) {
            routerListener.onMessageForLocal(message);
        }
    }

    /**
     * Get the next batch of messages to forward to a peer.
     * Returns up to maxCount messages, SOS-priority first.
     */
    public List<MeshMessage> drainOutgoing(int maxCount) {
        List<MeshMessage> batch = new ArrayList<>();
        synchronized (outgoingQueue) {
            while (!outgoingQueue.isEmpty() && batch.size() < maxCount) {
                batch.add(outgoingQueue.poll());
            }
        }
        return batch;
    }

    /**
     * Get IDs of all messages this node has seen.
     * Used during handshake to compute missing messages.
     */
    public List<String> getKnownMessageIds() {
        return new ArrayList<>(seenMessageIds);
    }

    /**
     * Given a peer's known IDs, compute which IDs they are missing.
     */
    public List<String> computeMissingIds(List<String> peerKnownIds) {
        Set<String> peerSet = new HashSet<>(peerKnownIds);
        List<String> missing = new ArrayList<>();
        for (String id : seenMessageIds) {
            if (!peerSet.contains(id)) {
                missing.add(id);
            }
        }
        return missing;
    }

    public boolean hasSeen(String messageId) {
        return seenMessageIds.contains(messageId);
    }

    public int getSeenCount() {
        return seenMessageIds.size();
    }

    public int getOutgoingCount() {
        return outgoingQueue.size();
    }

    // ── Internal ────────────────────────────────────────────────

    private void markSeen(String id) {
        // Evict oldest if too many (simple approach: clear half)
        if (seenMessageIds.size() >= MAX_SEEN_IDS) {
            Iterator<String> it = seenMessageIds.iterator();
            int toRemove = MAX_SEEN_IDS / 2;
            while (it.hasNext() && toRemove > 0) {
                it.next();
                it.remove();
                toRemove--;
            }
        }
        seenMessageIds.add(id);
    }

    private void enqueueForForwarding(MeshMessage message) {
        synchronized (outgoingQueue) {
            outgoingQueue.add(message);
        }
        if (routerListener != null) {
            routerListener.onMessageForForwarding(message);
        }
    }
}
