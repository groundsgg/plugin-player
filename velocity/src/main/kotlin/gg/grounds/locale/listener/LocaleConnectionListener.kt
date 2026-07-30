package gg.grounds.locale.listener

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import gg.grounds.locale.PlayerLocaleCache
import gg.grounds.locale.SupportedLanguages
import gg.grounds.presence.PlayerPresenceService

/**
 * Seeds the [PlayerLocaleCache] from the player's stored preference on join, and clears it on
 * disconnect. Both run off the event thread — the join path makes a gRPC call to service-player,
 * and a language lookup must never hold up a login (a failure just leaves the client locale in
 * effect).
 */
class LocaleConnectionListener(
    private val cache: PlayerLocaleCache,
    private val presence: PlayerPresenceService,
) {
    @Subscribe
    fun onPostLogin(event: PostLoginEvent): EventTask {
        val playerId = event.player.uniqueId
        return EventTask.async {
            val tag = presence.getLocale(playerId) ?: return@async
            SupportedLanguages.parse(tag)?.let { cache.set(playerId, it) }
        }
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent): EventTask {
        val playerId = event.player.uniqueId
        return EventTask.async { cache.remove(playerId) }
    }
}
