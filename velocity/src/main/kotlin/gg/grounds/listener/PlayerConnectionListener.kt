package gg.grounds.listener

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.ResultedEvent.ComponentResult
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import gg.grounds.config.PluginConfig
import gg.grounds.player.presence.PlayerLoginStatus
import gg.grounds.presence.PlayerPresenceService
import net.kyori.adventure.text.Component
import org.slf4j.Logger

class PlayerConnectionListener(
    private val logger: Logger,
    private val playerPresenceService: PlayerPresenceService,
    private val messages: PluginConfig.Messages,
) {
    @Subscribe
    fun onLogin(event: LoginEvent): EventTask {
        val playerId = event.player.uniqueId
        val name = event.player.username

        return EventTask.async {
            val result = playerPresenceService.tryLogin(playerId)
            if (result == null) {
                event.result = ComponentResult.denied(Component.text(messages.serviceUnavailable))
                return@async
            }

            val kickMessage =
                when (result.status) {
                    PlayerLoginStatus.ACCEPTED -> {
                        logger.info("player session created: {} ({})", name, playerId)
                        return@async
                    }
                    PlayerLoginStatus.ALREADY_ONLINE -> messages.alreadyOnline
                    PlayerLoginStatus.INVALID_REQUEST -> messages.invalidRequest
                    PlayerLoginStatus.UNSPECIFIED,
                    PlayerLoginStatus.ERROR -> messages.genericError
                }

            logger.warn(
                "player session rejected: {} ({}) status={} message={}",
                name,
                playerId,
                result.status,
                result.message,
            )

            event.result = ComponentResult.denied(Component.text(kickMessage))
        }
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent): EventTask {
        val playerId = event.player.uniqueId
        val name = event.player.username

        return EventTask.async {
            val result = playerPresenceService.logout(playerId) ?: return@async
            logger.info(
                "player session logout: {} ({}) removed={} message={}",
                name,
                playerId,
                result.removed,
                result.message,
            )
        }
    }
}
