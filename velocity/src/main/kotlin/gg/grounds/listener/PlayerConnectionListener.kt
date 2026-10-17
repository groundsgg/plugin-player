package gg.grounds.listener

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.player.CookieReceiveEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.PlayerMessage
import gg.grounds.config.MessagesConfig
import gg.grounds.player.presence.ExactPlayerLoginResult
import gg.grounds.player.presence.PlayerLogoutResult
import gg.grounds.presence.PlayerPresenceService
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import tools.jackson.databind.json.JsonMapper

class PlayerConnectionListener(
    private val logger: Logger,
    private val playerPresenceService: PlayerPresenceService,
    private val messages: MessagesConfig,
    private val proxy: ProxyServer,
    private val plugin: Any,
    private val abandonedLoginGraceSeconds: Long = DEFAULT_ABANDONED_LOGIN_GRACE_SECONDS,
) {
    /** Identity semantics keep callbacks from an older connection away from its reconnect. */
    private val pendingLogins: MutableSet<Player> =
        Collections.newSetFromMap(Collections.synchronizedMap(IdentityHashMap()))
    private val pendingTransfers: MutableSet<Player> =
        Collections.newSetFromMap(Collections.synchronizedMap(IdentityHashMap()))
    private val handoffCookies = ConcurrentHashMap<Player, CompletableFuture<ByteArray>>()
    private val mapper = JsonMapper.builder().build()

    @Subscribe
    fun onLogin(event: LoginEvent): EventTask =
        EventTask.async {
            val player = event.player
            if (
                player.handshakeIntent == com.velocitypowered.api.network.HandshakeIntent.TRANSFER
            ) {
                // A transfer is admitted only after the destination consumes the cookie handoff.
                pendingTransfers.add(player)
                return@async
            }
            val result = playerPresenceService.claim(player, proxyId(), region())
            when (result) {
                is ExactPlayerLoginResult.Accepted -> {
                    pendingLogins.add(player)
                    logger.info(
                        "Player session claimed (playerId={}, sessionId={}, username={})",
                        player.uniqueId,
                        result.session.sessionId,
                        player.username,
                    )
                    watchForAbandonedLogin(player)
                }
                ExactPlayerLoginResult.AlreadyOnline -> {
                    logger.warn(
                        "Player session rejected (playerId={}, username={}, reason=already_online)",
                        player.uniqueId,
                        player.username,
                    )
                    deny(
                        event,
                        LoginMessageRenderer.render(
                            PlayerMessage.LOGIN_ALREADY_ONLINE,
                            messages.alreadyOnline,
                        ),
                    )
                }
                is ExactPlayerLoginResult.Unavailable -> {
                    logger.warn(
                        "Player presence login unavailable (playerId={}, username={}, reason={})",
                        player.uniqueId,
                        player.username,
                        result.message,
                    )
                    deny(
                        event,
                        LoginMessageRenderer.render(
                            PlayerMessage.LOGIN_SERVICE_UNAVAILABLE,
                            messages.serviceUnavailable,
                        ),
                    )
                }
                is ExactPlayerLoginResult.Error -> {
                    logger.warn(
                        "Player presence login failed (playerId={}, username={}, reason={})",
                        player.uniqueId,
                        player.username,
                        result.message,
                    )
                    deny(
                        event,
                        LoginMessageRenderer.render(
                            PlayerMessage.LOGIN_ERROR,
                            messages.genericError,
                        ),
                    )
                }
            }
        }

    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        pendingLogins.remove(event.player)
    }

    @Subscribe
    fun onPostLoginTransfer(event: PostLoginEvent): EventTask? {
        val player = event.player
        if (!pendingTransfers.remove(player)) return null
        val cookie = CompletableFuture<ByteArray>()
        handoffCookies[player] = cookie
        return EventTask.async {
            try {
                player.requestCookie(HANDOFF_COOKIE)
                val bytes = cookie.get(COOKIE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (bytes.size > MAX_HANDOFF_COOKIE_BYTES) {
                    player.disconnect(
                        Component.text("This transfer could not be verified. Please reconnect.")
                    )
                    return@async
                }
                val json = mapper.readTree(bytes)
                val playerId = json.get("playerId")?.asString()
                val token = json.get("token")?.asString()?.takeIf(String::isNotBlank)
                if (playerId != player.uniqueId.toString() || token == null) {
                    player.disconnect(
                        Component.text("This transfer could not be verified. Please reconnect.")
                    )
                    return@async
                }
                val sessionId = java.util.UUID.randomUUID()
                when (val result = playerPresenceService.consumeHandoff(player, token, sessionId)) {
                    is ExactPlayerLoginResult.Accepted ->
                        logger.info(
                            "Player session handoff accepted (playerId={}, sessionId={})",
                            player.uniqueId,
                            sessionId,
                        )
                    else -> {
                        logger.warn(
                            "Player session handoff rejected (playerId={}, reason={})",
                            player.uniqueId,
                            result,
                        )
                        player.disconnect(
                            Component.text("This transfer could not be verified. Please reconnect.")
                        )
                    }
                }
            } catch (e: Exception) {
                logger.warn("Player session handoff unavailable (playerId={})", player.uniqueId, e)
                player.disconnect(
                    Component.text("This transfer could not be verified. Please reconnect.")
                )
            } finally {
                handoffCookies.remove(player)
            }
        }
    }

    @Subscribe
    fun onCookieReceive(event: CookieReceiveEvent) {
        if (event.originalKey != HANDOFF_COOKIE) return
        handoffCookies[event.player]?.complete(event.originalData)
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent): EventTask? {
        val player = event.player
        pendingLogins.remove(player)
        pendingTransfers.remove(player)
        handoffCookies
            .remove(player)
            ?.completeExceptionally(IllegalStateException("player disconnected"))
        val lease = playerPresenceService.remove(player) ?: return null
        return EventTask.async {
            when (val result = playerPresenceService.logoutExact(lease)) {
                PlayerLogoutResult.Removed ->
                    logger.info(
                        "Player session logout completed (playerId={}, sessionId={})",
                        lease.playerId,
                        lease.sessionId,
                    )
                PlayerLogoutResult.NotFound ->
                    logger.debug(
                        "Player session logout found nothing to remove (playerId={}, sessionId={})",
                        lease.playerId,
                        lease.sessionId,
                    )
                is PlayerLogoutResult.Failed ->
                    logger.warn(
                        "Player session logout failed (playerId={}, sessionId={}, message={})",
                        lease.playerId,
                        lease.sessionId,
                        result.message,
                    )
            }
        }
    }

    @Subscribe
    fun onServerConnected(event: ServerConnectedEvent): EventTask =
        EventTask.async {
            val lease = playerPresenceService.lease(event.player) ?: return@async
            playerPresenceService.updateServerExact(lease, event.server.serverInfo.name)
        }

    private fun deny(event: LoginEvent, message: Component) {
        event.result = ResultedEvent.ComponentResult.denied(message)
    }

    private fun proxyId(): String = System.getenv("PROXY_ID") ?: ""

    private fun region(): String = System.getenv("REGION") ?: ""

    fun transferToHost(player: Player, host: String, port: Int): Boolean {
        return playerPresenceService.transferToHost(player, host, port)
    }

    private fun watchForAbandonedLogin(player: Player) {
        proxy.scheduler
            .buildTask(plugin, Runnable { releaseAbandonedLogin(player) })
            .delay(abandonedLoginGraceSeconds, TimeUnit.SECONDS)
            .schedule()
    }

    private fun releaseAbandonedLogin(player: Player) {
        if (
            !shouldReleaseAbandonedLogin(
                pendingLogins,
                player,
                proxy.getPlayer(player.uniqueId).orElse(null) === player,
            )
        )
            return
        val lease = playerPresenceService.remove(player) ?: return
        val result = playerPresenceService.logoutExact(lease)
        logger.info(
            "Player session released after abandoned login (playerId={}, sessionId={}, removed={})",
            lease.playerId,
            lease.sessionId,
            result == PlayerLogoutResult.Removed,
        )
    }

    companion object {
        const val DEFAULT_ABANDONED_LOGIN_GRACE_SECONDS = 15L
        const val COOKIE_TIMEOUT_SECONDS = 3L
        const val MAX_HANDOFF_COOKIE_BYTES = 4096
        val HANDOFF_COOKIE: Key = PlayerPresenceService.HANDOFF_COOKIE

        @JvmStatic
        fun <T : Any> shouldReleaseAbandonedLogin(
            pending: MutableSet<T>,
            player: T,
            playerOnline: Boolean,
        ): Boolean = pending.remove(player) && !playerOnline
    }
}
