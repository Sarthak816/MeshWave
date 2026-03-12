package com.meshnet.chat.data.local;

import androidx.lifecycle.LiveData;

import com.meshnet.chat.data.model.MeshMessage;
import com.meshnet.chat.data.model.MessageEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository layer wrapping Room DAO.
 * Handles background thread execution for DB operations.
 */
public class MessageRepository {

    private final MessageDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public MessageRepository(MessageDao dao) {
        this.dao = dao;
    }

    /** Insert a mesh message (converts to entity). Runs on background thread. */
    public void insert(MeshMessage message) {
        executor.execute(() -> dao.insert(MessageEntity.fromDomain(message)));
    }

    /** Get all messages for a room as LiveData (auto-updates UI). */
    public LiveData<List<MessageEntity>> getMessagesByRoom(String roomId) {
        return dao.getMessagesByRoom(roomId);
    }

    /** Get all messages across all rooms. */
    public LiveData<List<MessageEntity>> getAllMessages() {
        return dao.getAllMessages();
    }

    /** Get all known message IDs (for sync handshake). Blocking call — use on background thread. */
    public List<String> getAllMessageIds() {
        return dao.getAllMessageIds();
    }

    /** Get specific messages by IDs (for sync response). Blocking. */
    public List<MeshMessage> getMessagesByIds(List<String> ids) {
        List<MessageEntity> entities = dao.getMessagesByIds(ids);
        List<MeshMessage> result = new ArrayList<>();
        for (MessageEntity e : entities) {
            result.add(e.toDomain());
        }
        return result;
    }

    /** Get messages waiting to be forwarded. Blocking. */
    public List<MeshMessage> getUndelivered() {
        List<MessageEntity> entities = dao.getUndelivered();
        List<MeshMessage> result = new ArrayList<>();
        for (MessageEntity e : entities) {
            result.add(e.toDomain());
        }
        return result;
    }

    public void markDelivered(String messageId) {
        executor.execute(() -> dao.markDelivered(messageId));
    }

    public void deleteAll() {
        executor.execute(dao::deleteAll);
    }
}
