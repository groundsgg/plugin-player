package gg.grounds.permissions.listener

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import gg.grounds.permissions.PermissionsCache
import org.slf4j.Logger

class PermissionsConnectionListener(
    private val logger: Logger,
    private val permissionsCache: PermissionsCache,
) {
    @Subscribe
    fun onPostLogin(event: PostLoginEvent): EventTask {
        val player = event.player
        return EventTask.async {
            if (!permissionsCache.cachePlayer(player.uniqueId)) {
                logger.warn(
                    "Failed to load permissions on join (playerId={}, username={})",
                    player.uniqueId,
                    player.username,
                )
            }
        }
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent): EventTask {
        return EventTask.async { permissionsCache.removePlayer(event.player.uniqueId) }
    }
}
