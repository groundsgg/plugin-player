package gg.grounds.region

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.network.HandshakeIntent
import com.velocitypowered.api.network.ProtocolVersion
import gg.grounds.presence.PlayerPresenceService
import java.util.UUID
import org.slf4j.Logger

/**
 * Sends a joining player to their preferred region when geo-steering put them somewhere else.
 *
 * Runs at PostLogin rather than at initial-server selection: Velocity waits for PostLogin before it
 * picks a server, so the player never loads a lobby only to be moved off it, and the client is in
 * the configuration phase, where the transfer packet is valid. It also stays clear of
 * plugin-agones' `PlayerChooseInitialServerEvent` handler and its drain cookie.
 *
 * The wait is bounded by the presence client's timeout, and only happens when
 * [PreferredRegionPolicy.mayRedirect] says a redirect is possible at all — on a single-continent
 * network that is never.
 */
class PreferredRegionListener(
    private val policy: PreferredRegionPolicy,
    private val presence: PlayerPresenceService,
    private val isBedrock: (UUID) -> Boolean,
    private val logger: Logger,
) {
    @Subscribe
    fun onPostLogin(event: PostLoginEvent): EventTask? {
        val player = event.player
        val eligible =
            policy.mayRedirect(
                virtualHost = player.virtualHost.map { it.hostString }.orElse(null),
                isTransfer = player.handshakeIntent == HandshakeIntent.TRANSFER,
                supportsTransfer = player.protocolVersion >= ProtocolVersion.MINECRAFT_1_20_5,
                isBedrock = isBedrock(player.uniqueId),
            )
        if (!eligible) return null

        return EventTask.async {
            try {
                val target = policy.target(presence.getRegion(player.uniqueId)) ?: return@async
                if (!player.isActive) return@async
                logger.info(
                    "Transferring player to preferred region (player={}, region={}, host={}, port={})",
                    player.username,
                    target.code,
                    target.host,
                    target.port,
                )
                if (!presence.transferToHost(player, target.host, target.port)) {
                    logger.warn(
                        "Preferred-region redirect skipped because session handoff could not be issued (player={})",
                        player.username,
                    )
                }
            } catch (e: RuntimeException) {
                logger.warn("Preferred-region redirect skipped (player={})", player.username, e)
            }
        }
    }
}
