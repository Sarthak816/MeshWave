package com.meshnet.chat.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A chat message that can be relayed across the mesh.
 *
 * Wire format: serialized to JSON via kotlinx-serialization, then
 * encrypted with the room key before transmission.
 */
@Serializable
data class MeshMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val roomId: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val ttl: Int = DEFAULT_TTL,
    val hopCount: Int = 0,
    val priority: MessagePriority = MessagePriority.NORMAL
) {
    companion object {
        const val DEFAULT_TTL = 7
    }

    /** Create a forwarded copy with decremented TTL and incremented hop count. */
    fun forwarded(): MeshMessage? {
        val newTtl = ttl - 1
        if (newTtl <= 0) return null
        return copy(ttl = newTtl, hopCount = hopCount + 1)
    }
}

@Serializable
enum class MessagePriority {
    NORMAL,
    SOS   // High‑priority emergency messages forwarded first
}
