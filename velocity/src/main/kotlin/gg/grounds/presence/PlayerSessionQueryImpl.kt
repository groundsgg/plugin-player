package gg.grounds.presence

import gg.grounds.proxy.api.PlayerSessionInfo
import gg.grounds.proxy.api.PlayerSessionQuery
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
        )
    }
}
