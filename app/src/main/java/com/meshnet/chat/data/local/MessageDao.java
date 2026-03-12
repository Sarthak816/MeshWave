package com.meshnet.chat.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.*;

import com.meshnet.chat.data.model.MessageEntity;

import java.util.List;

/** Room DAO for chat messages. */
@Dao
public interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(MessageEntity message);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<MessageEntity> messages);

    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    LiveData<List<MessageEntity>> getMessagesByRoom(String roomId);

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    LiveData<List<MessageEntity>> getAllMessages();

    @Query("SELECT id FROM messages")
    List<String> getAllMessageIds();

    @Query("SELECT * FROM messages WHERE id IN (:ids)")
    List<MessageEntity> getMessagesByIds(List<String> ids);

    @Query("SELECT * FROM messages WHERE delivered = 0 ORDER BY timestamp ASC")
    List<MessageEntity> getUndelivered();

    @Query("UPDATE messages SET delivered = 1 WHERE id = :messageId")
    void markDelivered(String messageId);

    @Query("DELETE FROM messages")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM messages")
    int getCount();
}
