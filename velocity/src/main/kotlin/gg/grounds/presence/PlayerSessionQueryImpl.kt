package gg.grounds.presence

import gg.grounds.grpc.player.CountPlayersByProxyReply
import gg.grounds.grpc.player.CountPlayersByServerReply
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

    /**
     * A session with no usable id or name tells the caller nothing — drop it rather than
     * half-answer.
     */
    private fun toInfo(session: gg.grounds.grpc.player.PlayerSessionInfo): PlayerSessionInfo? {
        val playerId = runCatching { UUID.fromString(session.playerId) }.getOrNull() ?: return null
        val name = session.playerName.takeIf { it.isNotEmpty() } ?: return null
        return PlayerSessionInfo(
            playerId = playerId,
            name = name,
            proxyId = session.proxyId.takeIf { it.isNotEmpty() },
            server = session.serverName.takeIf { it.isNotEmpty() },
            connectedAt = session.connectedAtMillis,
            region = session.region.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * `servers` has one row per occupied backend server — a server nobody is on is absent, not a
     * zero entry.
     */
    /**
     * `proxies` has one row per occupied proxy. An empty region string means the proxy declares
     * none — mapped to null rather than kept as "", so callers have one shape for "unknown".
     */
    internal fun toNetworkProxyCounts(reply: CountPlayersByProxyReply): NetworkProxyCounts =
        NetworkProxyCounts(
            proxies =
                reply.proxiesList.map {
                    ProxyPlayers(it.proxyId, it.region.takeIf(String::isNotEmpty), it.players)
                },
            total = reply.total,
        )

    internal fun toNetworkPlayerCounts(reply: CountPlayersByServerReply): NetworkPlayerCounts =
        NetworkPlayerCounts(
            byServer = reply.serversList.associate { it.serverName to it.players },
            total = reply.total,
        )
}
