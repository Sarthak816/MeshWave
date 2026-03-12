package com.meshnet.chat.data.model;

import java.util.List;

/**
 * Wire-level packet types exchanged between mesh peers during sync.
 * Serialized to JSON with a "type" discriminator field.
 */
public class MeshPacket {

    public enum Type {
        HANDSHAKE,
        SYNC_REQUEST,
        MESSAGE_BATCH,
        ACK
    }

    private Type type;

    // Handshake fields
    private String peerId;
    private String displayName;
    private List<String> knownMessageIds;

    // SyncRequest fields
    private List<String> missingIds;

    // MessageBatch fields
    private List<MeshMessage> messages;

    // Ack fields
    private List<String> receivedIds;

    public MeshPacket() {}

    // ── Factory methods ────────────────────────────────────────

    public static MeshPacket handshake(String peerId, String displayName, List<String> knownIds) {
        MeshPacket p = new MeshPacket();
        p.type = Type.HANDSHAKE;
        p.peerId = peerId;
        p.displayName = displayName;
        p.knownMessageIds = knownIds;
        return p;
    }

    public static MeshPacket syncRequest(List<String> missingIds) {
        MeshPacket p = new MeshPacket();
        p.type = Type.SYNC_REQUEST;
        p.missingIds = missingIds;
        return p;
    }

    public static MeshPacket messageBatch(List<MeshMessage> messages) {
        MeshPacket p = new MeshPacket();
        p.type = Type.MESSAGE_BATCH;
        p.messages = messages;
        return p;
    }

    public static MeshPacket ack(List<String> receivedIds) {
        MeshPacket p = new MeshPacket();
        p.type = Type.ACK;
        p.receivedIds = receivedIds;
        return p;
    }

    // ── Getters ────────────────────────────────────────────────

    public Type getType() { return type; }

    public String getPeerId() { return peerId; }
    public String getDisplayName() { return displayName; }
    public List<String> getKnownMessageIds() { return knownMessageIds; }

    public List<String> getMissingIds() { return missingIds; }

    public List<MeshMessage> getMessages() { return messages; }

    public List<String> getReceivedIds() { return receivedIds; }
}
