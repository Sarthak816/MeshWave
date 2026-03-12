package com.meshnet.chat.data.model

import kotlinx.serialization.Serializable

/**
 * Wire‑level envelope exchanged between peers during sync.
 */
@Serializable
sealed class MeshPacket {

    /** Handshake: exchange peer identity + known message IDs for sync. */
    @Serializable
    data class Handshake(
        val peerId: String,
        val displayName: String,
        val knownMessageIds: List<String>  // bloom‑filter replacement (simple list for MVP)
    ) : MeshPacket()

    /** Sync request: "send me messages I'm missing." */
    @Serializable
    data class SyncRequest(
        val missingIds: List<String>
    ) : MeshPacket()

    /** Payload: one or more messages being relayed. */
    @Serializable
    data class MessageBatch(
        val messages: List<MeshMessage>
    ) : MeshPacket()

    /** Acknowledgement that messages were received. */
    @Serializable
    data class Ack(
        val receivedIds: List<String>
    ) : MeshPacket()
}
