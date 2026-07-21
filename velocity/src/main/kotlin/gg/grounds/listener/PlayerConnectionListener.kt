package gg.grounds.listener

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.util.UuidUtils
import gg.grounds.config.MessagesConfig
import gg.grounds.grpc.player.LoginStatus
import gg.grounds.grpc.player.PlayerLoginReply
import gg.grounds.player.presence.PlayerLoginResult
import gg.grounds.presence.PlayerPresenceService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import net.kyori.adventure.text.Component
import org.slf4j.Logger

class PlayerConnectionListener(
    private val logger: Logger,
    private val playerPresenceService: PlayerPresenceService,
    private val messages: MessagesConfig,
    private val proxy: ProxyServer,
    private val plugin: Any,
    private val abandonedLoginGraceSeconds: Long = DEFAULT_ABANDONED_LOGIN_GRACE_SECONDS,
) {
    /**
     * Players whose session this proxy created at pre-login and who have not become a [Player] yet.
     *
     * The pair that keeps presence honest is pre-login → disconnect, and it has a hole: Velocity
     * only fires DisconnectEvent for connections that finished logging in. A client that dies in
     * between — an expired Mojang session, a crash, a pulled cable — leaves the row behind, and
     * every retry is then refused with "you are already online" until the 90s TTL reaps it. So the
     * proxy that created the row watches it: no player after the grace period, no session.
     */
    private val pendingLogins: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    @Subscribe
    fun onPreLogin(event: PreLoginEvent): EventTask {
        val name = event.username
        val playerId = event.uniqueId ?: UuidUtils.generateOfflinePlayerUuid(name)

        return EventTask.async {
            // The name is what makes the session findable from another proxy — without it a player
            // exists in presence but nobody can /msg them. PROXY_ID says which proxy holds them.
            when (val result = playerPresenceService.tryLogin(playerId, name, proxyId())) {
                is PlayerLoginResult.Success -> {
                    if (handleSuccess(event, name, playerId, result.reply)) {
                        return@async
                    }
                }
                is PlayerLoginResult.Unavailable -> {
                    logger.warn(
                        "Player presence login unavailable (playerId={}, username={}, reason={})",
                        playerId,
                        name,
                        result.message,
                    )
                    deny(event, messages.serviceUnavailable)
                }
                is PlayerLoginResult.Error -> {
                    logger.warn(
                        "Player presence login failed (playerId={}, username={}, reason={})",
                        playerId,
                        name,
                        result.message,
                    )
                    deny(event, messages.genericError)
                }
            }
        }
    }

    /** The connection became a real player — the disconnect path owns the session from here. */
    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        pendingLogins.remove(event.player.uniqueId)
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent): EventTask {
        pendingLogins.remove(event.player.uniqueId)
        val playerId = event.player.uniqueId
        val name = event.player.username

        return EventTask.async {
            val result = playerPresenceService.logout(playerId) ?: return@async
            if (result.removed) {
                logger.info(
                    "Player session logout completed (playerId={}, username={}, message={})",
                    playerId,
                    name,
                    result.message,
                )
            } else {
                logger.warn(
                    "Player session logout failed (playerId={}, username={}, message={})",
                    playerId,
                    name,
                    result.message,
                )
            }
        }
    }

    private fun handleSuccess(
        event: PreLoginEvent,
        name: String,
        playerId: UUID,
        reply: PlayerLoginReply,
    ): Boolean {
        val kickMessage =
            when (reply.status) {
                LoginStatus.LOGIN_STATUS_ACCEPTED -> {
                    logger.info(
                        "Player session created (playerId={}, username={}, status={})",
                        playerId,
                        name,
                        reply.status,
                    )
                    watchForAbandonedLogin(playerId, name)
                    return true
                }
                LoginStatus.LOGIN_STATUS_ALREADY_ONLINE -> messages.alreadyOnline
                LoginStatus.LOGIN_STATUS_INVALID_REQUEST -> messages.invalidRequest
                LoginStatus.LOGIN_STATUS_UNSPECIFIED,
                LoginStatus.LOGIN_STATUS_ERROR,
                LoginStatus.UNRECOGNIZED -> messages.genericError
            }

        logger.warn(
            "Player session rejected (playerId={}, username={}, status={}, message={})",
            playerId,
            name,
            reply.status,
            reply.message,
        )

        deny(event, kickMessage)
        return false
    }

    private fun deny(event: PreLoginEvent, message: String) {
        event.result = PreLoginEvent.PreLoginComponentResult.denied(Component.text(message))
    }

    /**
     * Keep the session pointing at the backend the player is actually on — a party warp wants to
     * send someone to the leader's server, and that only works if we know where people are.
     */
    @Subscribe
    fun onServerConnected(event: ServerConnectedEvent): EventTask {
        val playerId = event.player.uniqueId
        val serverName = event.server.serverInfo.name
        return EventTask.async { playerPresenceService.updateServer(playerId, serverName) }
    }

    private fun proxyId(): String = System.getenv("PROXY_ID") ?: ""

    private fun watchForAbandonedLogin(playerId: UUID, name: String) {
        pendingLogins.add(playerId)
        proxy.scheduler
            .buildTask(plugin, Runnable { releaseAbandonedLogin(playerId, name) })
            .delay(abandonedLoginGraceSeconds, TimeUnit.SECONDS)
            .schedule()
    }

    private fun releaseAbandonedLogin(playerId: UUID, name: String) {
        val online = proxy.getPlayer(playerId).isPresent
        if (!shouldReleaseAbandonedLogin(pendingLogins, playerId, online)) return

        val result = playerPresenceService.logout(playerId)
        logger.info(
            "Player session released after abandoned login (playerId={}, username={}, removed={})",
            playerId,
            name,
            result?.removed,
        )
    }

    companion object {
        /**
         * Long enough for a normal login to finish (auth, config phase, first server connect),
         * short enough that a player retrying after a crash is not locked out.
         */
        const val DEFAULT_ABANDONED_LOGIN_GRACE_SECONDS = 15L

        /**
         * True when the session this proxy created belongs to a connection that never arrived.
         *
         * Consumes the pending marker either way, so a second run cannot log out a player who
         * reconnected in the meantime.
         */
        @JvmStatic
        fun shouldReleaseAbandonedLogin(
            pending: MutableSet<UUID>,
            playerId: UUID,
            playerOnline: Boolean,
        ): Boolean {
            val wasPending = pending.remove(playerId)
            return wasPending && !playerOnline
        }
    }
}
