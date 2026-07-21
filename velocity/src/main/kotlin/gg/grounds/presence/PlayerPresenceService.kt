package gg.grounds.presence

import gg.grounds.grpc.player.CountPlayersByServerReply
import gg.grounds.grpc.player.PlayerLogoutReply
import gg.grounds.grpc.player.PlayerSessionInfo
import gg.grounds.player.presence.GrpcPlayerPresenceClient
import gg.grounds.player.presence.PlayerLoginResult
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

    fun tryLogin(playerId: UUID, playerName: String, proxyId: String): PlayerLoginResult {
        return try {
            client.tryLogin(playerId, playerName, proxyId)
        } catch (e: RuntimeException) {
            PlayerLoginResult.Error(e.message ?: e::class.java.name)
        }
    }

    /**
     * Cross-proxy lookups. Never throw: a failure means "unknown", and the caller falls back to
     * local.
     */
    fun getSession(playerId: UUID): PlayerSessionInfo? {
        return try {
            client.getSession(playerId)
        } catch (e: RuntimeException) {
            null
        }
    }

    fun resolveName(playerName: String): PlayerSessionInfo? {
        return try {
            client.resolveName(playerName)
        } catch (e: RuntimeException) {
            null
        }
    }

    fun suggestNames(prefix: String, limit: Int): List<String> {
        return try {
            client.suggestNames(prefix, limit)
        } catch (e: RuntimeException) {
            emptyList()
        }
    }

    fun countPlayersByServer(): CountPlayersByServerReply? {
        return try {
            client.countPlayersByServer()
        } catch (e: RuntimeException) {
            null
        }
    }

    fun updateServer(playerId: UUID, serverName: String): Boolean {
        return try {
            client.updateServer(playerId, serverName)
        } catch (e: RuntimeException) {
            false
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
