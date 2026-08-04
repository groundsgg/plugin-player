package gg.grounds.presence

import gg.grounds.player.presence.PlayerSessionInfo as PresenceSessionInfo
import gg.grounds.player.presence.ProxyPlayerCounts
import gg.grounds.player.presence.ServerPlayerCounts
import gg.grounds.proxy.api.NetworkPlayerCounts
import gg.grounds.proxy.api.NetworkProxyCounts
import gg.grounds.proxy.api.PlayerSessionInfo
import gg.grounds.proxy.api.PlayerSessionQuery
import gg.grounds.proxy.api.ProxyPlayers
import java.util.UUID

/**
 * Answers "who is this player, and are they online" for the whole network, from service-player.
 *
 * plugin-proxy's ProxyService resolves locally first and falls back to whatever is registered here;
 * without this, a proxy could only ever see its own players, which is why /msg and party invites
 * did not cross proxies.
 */
class PlayerSessionQueryImpl(private val presenceService: PlayerPresenceService) :
    PlayerSessionQuery {

    override fun getSession(playerId: UUID): PlayerSessionInfo? =
        presenceService.getSession(playerId)?.let(::toInfo)

    override fun resolveByName(name: String): PlayerSessionInfo? =
        presenceService.resolveName(name)?.let(::toInfo)

    override fun suggestNames(prefix: String, limit: Int): List<String> =
        presenceService.suggestNames(prefix, limit)

    override fun countPlayersByServer(): NetworkPlayerCounts? =
        presenceService.countPlayersByServer()?.let(::toNetworkPlayerCounts)

    override fun countPlayersByProxy(): NetworkProxyCounts? =
        presenceService.countPlayersByProxy()?.let(::toNetworkProxyCounts)

    /** A session with no usable name tells the caller nothing — drop it rather than half-answer. */
    private fun toInfo(session: PresenceSessionInfo): PlayerSessionInfo? {
        val name = session.playerName ?: return null
        return PlayerSessionInfo(
            playerId = session.playerId,
            name = name,
            proxyId = session.proxyId,
            server = session.serverName,
            connectedAt = session.connectedAtMillis,
            region = session.region,
        )
    }

    /**
     * `proxies` has one row per occupied proxy; a proxy that declares no region is already null
     * here rather than "", so callers have one shape for "unknown".
     */
    internal fun toNetworkProxyCounts(counts: ProxyPlayerCounts): NetworkProxyCounts =
        NetworkProxyCounts(
            proxies = counts.proxies.map { ProxyPlayers(it.proxyId, it.region, it.players) },
            total = counts.total,
        )

    /**
     * `servers` has one row per occupied backend server — a server nobody is on is absent, not a
     * zero entry.
     */
    internal fun toNetworkPlayerCounts(counts: ServerPlayerCounts): NetworkPlayerCounts =
        NetworkPlayerCounts(
            byServer = counts.servers.associate { it.serverName to it.players },
            total = counts.total,
        )
}
