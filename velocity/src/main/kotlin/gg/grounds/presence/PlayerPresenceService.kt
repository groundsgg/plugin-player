package gg.grounds.presence

import gg.grounds.player.presence.HttpPlayerPresenceClient
import gg.grounds.player.presence.PlayerHeartbeatResult
import gg.grounds.player.presence.PlayerLoginResult
import gg.grounds.player.presence.PlayerLogoutResult
import gg.grounds.player.presence.PlayerSessionInfo
import gg.grounds.player.presence.ProxyPlayerCounts
import gg.grounds.player.presence.ServerPlayerCounts
import java.util.UUID

/**
 * The proxy's handle on service-player.
 *
 * Thin by design: the client already resolves every failure into the answer the caller can act on,
 * so this exists to own the client's lifecycle and to keep the rest of the plugin from importing
 * the transport.
 */
class PlayerPresenceService : AutoCloseable {
    private var client: HttpPlayerPresenceClient? = null

    fun configure(baseUrl: String) {
        close()
        client = HttpPlayerPresenceClient(baseUrl)
    }

    fun tryLogin(
        playerId: UUID,
        playerName: String,
        proxyId: String,
        region: String,
    ): PlayerLoginResult =
        withClient(PlayerLoginResult.Unavailable("presence service is not configured")) {
            it.tryLogin(playerId, playerName, proxyId, region)
        }

    fun logout(playerId: UUID, proxyId: String = ""): PlayerLogoutResult =
        withClient(PlayerLogoutResult.Failed("presence service is not configured")) {
            it.logout(playerId, proxyId)
        }

    fun heartbeatBatch(playerIds: Collection<UUID>): PlayerHeartbeatResult =
        withClient(
            PlayerHeartbeatResult(
                success = false,
                message = "presence service is not configured",
                updated = 0,
                missing = playerIds.size,
            )
        ) {
            it.heartbeatBatch(playerIds)
        }

    /** Cross-proxy lookups. Null or empty means "unknown"; the caller falls back to local. */
    fun getSession(playerId: UUID): PlayerSessionInfo? =
        withClient(null) { it.getSession(playerId) }

    fun resolveName(playerName: String): PlayerSessionInfo? =
        withClient(null) { it.resolveName(playerName) }

    fun suggestNames(prefix: String, limit: Int): List<String> =
        withClient(emptyList()) { it.suggestNames(prefix, limit) }

    fun countPlayersByServer(): ServerPlayerCounts? = withClient(null) { it.countPlayersByServer() }

    fun countPlayersByProxy(): ProxyPlayerCounts? = withClient(null) { it.countPlayersByProxy() }

    fun updateServer(playerId: UUID, serverName: String): Boolean =
        withClient(false) { it.updateServer(playerId, serverName) }

    /** The player's stored language tag, or null when none is set. */
    fun getLocale(playerId: UUID): String? = withClient(null) { it.getLocale(playerId) }

    /** Persists, or with a blank tag clears, the player's language. */
    fun setLocale(playerId: UUID, locale: String): Boolean =
        withClient(false) { it.setLocale(playerId, locale) }

    /**
     * An unconfigured service answers like an unreachable one. A plugin that half-loaded should
     * degrade the way the network already knows how to handle, not throw into an event listener.
     */
    private fun <T> withClient(fallback: T, call: (HttpPlayerPresenceClient) -> T): T {
        val current = client ?: return fallback
        return try {
            call(current)
        } catch (_: RuntimeException) {
            fallback
        }
    }

    override fun close() {
        client?.close()
        client = null
    }
}
