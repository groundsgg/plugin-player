package gg.grounds.presence

import gg.grounds.player.presence.PlayerLoginResult
import gg.grounds.player.presence.PlayerLogoutResult
import gg.grounds.player.presence.PlayerPresenceClient
import gg.grounds.player.presence.PlayerPresenceClientConfig
import gg.grounds.player.presence.grpc.GrpcPlayerPresenceClient
import java.util.UUID
import org.slf4j.Logger

class PlayerPresenceService(private val logger: Logger) : AutoCloseable {
    @Volatile private var client: PlayerPresenceClient? = null

    fun configure(config: PlayerPresenceClientConfig) {
        close()
        client = GrpcPlayerPresenceClient.create(config)
    }

    fun tryLogin(playerId: UUID): PlayerLoginResult? {
        val current = client ?: return null
        return try {
            current.tryLogin(playerId)
        } catch (e: RuntimeException) {
            logger.warn("player presence tryLogin failed: {}", playerId, e)
            null
        }
    }

    fun logout(playerId: UUID): PlayerLogoutResult? {
        val current = client ?: return null
        return try {
            current.logout(playerId)
        } catch (e: RuntimeException) {
            logger.warn("player presence logout failed: {}", playerId, e)
            null
        }
    }

    override fun close() {
        val current = client
        client = null
        try {
            current?.close()
        } catch (e: RuntimeException) {
            logger.warn("player presence client close failed", e)
        }
    }
}
