package gg.grounds.presence

import gg.grounds.grpc.player.PlayerLogoutReply
import java.util.UUID

class PlayerPresenceService : AutoCloseable {
    private lateinit var client: GrpcPlayerPresenceClient

    data class HeartbeatBatchResult(
        val success: Boolean,
        val message: String,
        val updated: Int,
        val missing: Int,
    )

    fun configure(target: String) {
        close()
        client = GrpcPlayerPresenceClient.create(target)
    }

    fun tryLogin(playerId: UUID): PlayerLoginResult {
        return try {
            client.tryLogin(playerId)
        } catch (e: RuntimeException) {
            PlayerLoginResult.Error(e.message ?: e::class.java.name)
        }
    }

    fun logout(playerId: UUID): PlayerLogoutReply? {
        return try {
            client.logout(playerId)
        } catch (e: RuntimeException) {
            null
        }
    }

    fun heartbeatBatch(playerIds: Collection<UUID>): HeartbeatBatchResult {
        return try {
            val reply = client.heartbeatBatch(playerIds)
            HeartbeatBatchResult(reply.success, reply.message, reply.updated, reply.missing)
        } catch (e: RuntimeException) {
            HeartbeatBatchResult(
                success = false,
                message = e.message ?: e::class.java.name,
                updated = 0,
                missing = playerIds.size,
            )
        }
    }

    override fun close() {
        if (this::client.isInitialized) {
            client.close()
        }
    }
}
