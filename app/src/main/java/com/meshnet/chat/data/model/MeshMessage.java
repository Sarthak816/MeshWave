package com.meshnet.chat.data.model;

import androidx.annotation.NonNull;
import java.util.UUID;

/**
 * A chat message that can be relayed across the mesh network.
 * Serialized to JSON via Gson before transmission.
 */
public class MeshMessage {

    public static final int DEFAULT_TTL = 7;

    private String id;
    private String senderId;
    private String senderName;
    private String roomId;
    private String text;
    private long timestamp;
    private int ttl;
    private int hopCount;
    private MessagePriority priority;

    public MeshMessage() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.ttl = DEFAULT_TTL;
        this.hopCount = 0;
        this.priority = MessagePriority.NORMAL;
    }

    public MeshMessage(String senderId, String senderName, String roomId, String text) {
        this();
        this.senderId = senderId;
        this.senderName = senderName;
        this.roomId = roomId;
        this.text = text;
    }

    /** Create a forwarded copy with decremented TTL and incremented hop count. Returns null if TTL exhausted. */
    public MeshMessage forwarded() {
        int newTtl = ttl - 1;
        if (newTtl <= 0) return null;

        MeshMessage copy = new MeshMessage();
        copy.id = this.id;
        copy.senderId = this.senderId;
        copy.senderName = this.senderName;
        copy.roomId = this.roomId;
        copy.text = this.text;
        copy.timestamp = this.timestamp;
        copy.ttl = newTtl;
        copy.hopCount = this.hopCount + 1;
        copy.priority = this.priority;
        return copy;
    }

    // ── Getters & Setters ──────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = ttl; }

    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }

    public MessagePriority getPriority() { return priority; }
    public void setPriority(MessagePriority priority) { this.priority = priority; }

    @NonNull
    @Override
    public String toString() {
        return "MeshMessage{id=" + id + ", from=" + senderName + ", hops=" + hopCount + "}";
    }
}
