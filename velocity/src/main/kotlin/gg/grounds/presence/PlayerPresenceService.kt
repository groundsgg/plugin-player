package gg.grounds.presence

import com.velocitypowered.api.proxy.Player
import gg.grounds.player.presence.ExactPlayerLoginResult
import gg.grounds.player.presence.HttpPlayerPresenceClient
import gg.grounds.player.presence.PlayerHeartbeatResult
import gg.grounds.player.presence.PlayerLoginResult
import gg.grounds.player.presence.PlayerLogoutResult
import gg.grounds.player.presence.PlayerSessionInfo
import gg.grounds.player.presence.ProxyPlayerCounts
import gg.grounds.player.presence.ServerPlayerCounts
import gg.grounds.player.presence.SessionHandoff
import java.net.InetSocketAddress
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import net.kyori.adventure.key.Key
import tools.jackson.databind.json.JsonMapper

/**
 * The proxy's handle on service-player.
 *
 * Thin by design: the client already resolves every failure into the answer the caller can act on,
 * so this exists to own the client's lifecycle and to keep the rest of the plugin from importing
 * the transport.
 */
class PlayerPresenceService : AutoCloseable {
    private var client: HttpPlayerPresenceClient? = null
    private val connections =
        Collections.synchronizedMap(IdentityHashMap<Player, ConnectionLease>())
    private val outboundTransfers =
        Collections.newSetFromMap(Collections.synchronizedMap(IdentityHashMap<Player, Boolean>()))
    private val mapper = JsonMapper.builder().build()

    data class ConnectionLease(
        val playerId: UUID,
        val sessionId: UUID,
        val leaseExpiresAtMillis: Long,
    )

    fun claim(player: Player, proxyId: String, region: String): ExactPlayerLoginResult {
        val sessionId = UUID.randomUUID()
        val result =
            withClient(ExactPlayerLoginResult.Unavailable("presence service is not configured")) {
                it.claimExactSession(player.uniqueId, sessionId, player.username, proxyId, region)
            }
        if (result is ExactPlayerLoginResult.Accepted) {
            connections[player] =
                ConnectionLease(player.uniqueId, sessionId, result.session.leaseExpiresAtMillis)
        }
        return result
    }

    fun issueHandoff(player: Player): SessionHandoff? {
        val lease = lease(player) ?: return null
        return withClient(null) {
            it.issueHandoff(lease.playerId, lease.sessionId, TransferAdmissionBridge.required)
        }
    }

    fun consumeHandoff(
        player: Player,
        token: String,
        sessionId: UUID,
        admissionConnectionId: UUID? = null,
    ): ExactPlayerLoginResult {
        val result =
            withClient(ExactPlayerLoginResult.Unavailable("presence service is not configured")) {
                it.consumeHandoff(
                    player.uniqueId,
                    sessionId,
                    token,
                    player.username,
                    proxyId(),
                    region(),
                    admissionConnectionId,
                )
            }
        if (result is ExactPlayerLoginResult.Accepted) {
            val lease =
                ConnectionLease(player.uniqueId, sessionId, result.session.leaseExpiresAtMillis)
            synchronized(connections) {
                if (player.isActive) connections[player] = lease else logoutExact(lease)
            }
        }
        return result
    }

    fun lease(player: Player): ConnectionLease? = connections[player]

    fun remove(player: Player): ConnectionLease? {
        outboundTransfers.remove(player)
        return connections.remove(player)
    }

    fun leasesFor(players: Collection<Player>): List<ConnectionLease> = players.mapNotNull(::lease)

    fun logoutExact(lease: ConnectionLease) =
        withClient(PlayerLogoutResult.Failed("presence service is not configured")) {
            it.logoutExactSession(lease.playerId, lease.sessionId)
        }

    fun heartbeatExact(leases: Collection<ConnectionLease>): PlayerHeartbeatResult =
        withClient(
            PlayerHeartbeatResult(false, "presence service is not configured", 0, leases.size)
        ) {
            it.heartbeatExactSessions(leases.map { lease -> lease.playerId to lease.sessionId })
        }

    fun confirmHeartbeat(leases: Collection<ConnectionLease>, leaseMillis: Long) {
        val expiresAt = System.currentTimeMillis() + leaseMillis
        synchronized(connections) {
            connections.entries.toList().forEach { (player, current) ->
                if (
                    leases.any {
                        it.playerId == current.playerId && it.sessionId == current.sessionId
                    }
                ) {
                    connections[player] = current.copy(leaseExpiresAtMillis = expiresAt)
                }
            }
        }
    }

    fun updateServerExact(lease: ConnectionLease, serverName: String): Boolean =
        withClient(false) {
            it.updateExactSessionServer(lease.playerId, lease.sessionId, serverName)
        }

    fun transferToHost(player: Player, host: String, port: Int): Boolean {
        val lease = lease(player) ?: return false
        if (!outboundTransfers.add(player)) return false
        var sent = false
        try {
            val handoff = issueHandoff(player) ?: return false
            player.storeCookie(
                HANDOFF_COOKIE,
                mapper.writeValueAsBytes(
                    mapOf("playerId" to lease.playerId.toString(), "token" to handoff.token)
                ),
            )
            player.transferToHost(InetSocketAddress.createUnresolved(host, port))
            sent = true
            return true
        } finally {
            if (!sent) outboundTransfers.remove(player)
        }
    }

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

    /** The player's preferred region code, or null when none is set or it could not be read. */
    fun getRegion(playerId: UUID): String? = withClient(null) { it.getRegion(playerId) }

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

    private fun proxyId(): String = System.getenv("PROXY_ID") ?: ""

    private fun region(): String = System.getenv("REGION") ?: ""

    companion object {
        val HANDOFF_COOKIE: Key = Key.key("grounds", "player-session-handoff")
    }

    override fun close() {
        client?.close()
        client = null
        connections.clear()
    }
}
