package com.meshnet.chat.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Room DB entity for persisted chat messages. */
@Entity(tableName = "messages")
public class MessageEntity {

    @PrimaryKey
    @NonNull
    private String id;
    private String senderId;
    private String senderName;
    private String roomId;
    private String text;
    private long timestamp;
    private int hopCount;
    private String priority;
    private boolean delivered;

    public MessageEntity() {}

    // ── Domain mappers ─────────────────────────────────────────

    public static MessageEntity fromDomain(MeshMessage msg) {
        MessageEntity e = new MessageEntity();
        e.id = msg.getId();
        e.senderId = msg.getSenderId();
        e.senderName = msg.getSenderName();
        e.roomId = msg.getRoomId();
        e.text = msg.getText();
        e.timestamp = msg.getTimestamp();
        e.hopCount = msg.getHopCount();
        e.priority = msg.getPriority().name();
        e.delivered = false;
        return e;
    }

    public MeshMessage toDomain() {
        MeshMessage msg = new MeshMessage();
        msg.setId(id);
        msg.setSenderId(senderId);
        msg.setSenderName(senderName);
        msg.setRoomId(roomId);
        msg.setText(text);
        msg.setTimestamp(timestamp);
        msg.setHopCount(hopCount);
        msg.setPriority(MessagePriority.valueOf(priority));
        return msg;
    }

    // ── Getters & Setters ──────────────────────────────────────

    @NonNull public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

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

    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public boolean isDelivered() { return delivered; }
    public void setDelivered(boolean delivered) { this.delivered = delivered; }
}
