package com.meshnet.chat.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room DB entity for persisted chat messages. */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderName: String,
    val roomId: String,
    val text: String,
    val timestamp: Long,
    val hopCount: Int,
    val priority: String,
    val delivered: Boolean = false
)

fun MeshMessage.toEntity(delivered: Boolean = false) = MessageEntity(
    id = id,
    senderId = senderId,
    senderName = senderName,
    roomId = roomId,
    text = text,
    timestamp = timestamp,
    hopCount = hopCount,
    priority = priority.name,
    delivered = delivered
)

fun MessageEntity.toDomain() = MeshMessage(
    id = id,
    senderId = senderId,
    senderName = senderName,
    roomId = roomId,
    text = text,
    timestamp = timestamp,
    hopCount = hopCount,
    priority = MessagePriority.valueOf(priority)
)
