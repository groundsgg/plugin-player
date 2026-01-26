package gg.grounds.presence

import gg.grounds.grpc.player.PlayerLogoutReply
import gg.grounds.player.presence.GrpcPlayerPresenceClient
import gg.grounds.player.presence.PlayerLoginResult
import java.util.UUID

class PlayerPresenceService : AutoCloseable {
    private lateinit var client: GrpcPlayerPresenceClient

    data class HeartbeatBatchResult(val success: Boolean, val message: String)

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
            HeartbeatBatchResult(reply.success, reply.message)
        } catch (e: RuntimeException) {
            HeartbeatBatchResult(false, e.message ?: e::class.java.name)
        }
    }

    override fun close() {
        if (this::client.isInitialized) {
            client.close()
        }
    }
}
