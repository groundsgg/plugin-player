package gg.grounds.listener

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.util.UuidUtils
import gg.grounds.config.MessagesConfig
import gg.grounds.grpc.player.LoginStatus
import gg.grounds.grpc.player.PlayerLoginReply
import gg.grounds.player.presence.PlayerLoginResult
import gg.grounds.presence.PlayerPresenceService
import java.util.UUID
import net.kyori.adventure.text.Component
import org.slf4j.Logger

class PlayerConnectionListener(
    private val logger: Logger,
    private val playerPresenceService: PlayerPresenceService,
    private val messages: MessagesConfig,
) {
    @Subscribe
    fun onPreLogin(event: PreLoginEvent): EventTask {
        val name = event.username
        val playerId = event.uniqueId ?: UuidUtils.generateOfflinePlayerUuid(name)

        return EventTask.async {
            when (val result = playerPresenceService.tryLogin(playerId)) {
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

    @Subscribe
    fun onDisconnect(event: DisconnectEvent): EventTask {
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
}
