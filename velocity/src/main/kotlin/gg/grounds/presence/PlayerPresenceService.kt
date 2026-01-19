package gg.grounds.presence

import gg.grounds.grpc.player.PlayerLogoutReply
import gg.grounds.player.presence.GrpcPlayerPresenceClient
import gg.grounds.player.presence.PlayerLoginResult
import gg.grounds.player.presence.PlayerPresenceClientConfig
import java.util.*
import org.slf4j.Logger

class PlayerPresenceService(private val logger: Logger) : AutoCloseable {
    private lateinit var client: GrpcPlayerPresenceClient

    fun configure(config: PlayerPresenceClientConfig) {
        close()
        client = GrpcPlayerPresenceClient.create(config)
    }

    fun tryLogin(playerId: UUID): PlayerLoginResult {
        return try {
            client.tryLogin(playerId)
        } catch (e: RuntimeException) {
            logger.warn("player presence tryLogin failed: {}", playerId, e)
            PlayerLoginResult.Error(e.message ?: e::class.java.name)
        }
    }

    fun logout(playerId: UUID): PlayerLogoutReply? {
        return try {
            client.logout(playerId)
        } catch (e: RuntimeException) {
            logger.warn("player presence logout failed: {}", playerId, e)
            null
        }
    }

    override fun close() {
        if (this::client.isInitialized) {
            client.close()
        }
    }
}
