package gg.grounds.locale.listener

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import gg.grounds.locale.PlayerLocaleCache
import gg.grounds.locale.SupportedLanguages
import gg.grounds.presence.PlayerPresenceService
import java.util.UUID

/**
 * Seeds the [PlayerLocaleCache] from the player's stored preference on join, and clears it on
 * disconnect. Both run off the event thread — the join path makes a call to service-player, and a
 * language lookup must never hold up a login (a failure just leaves the client locale in effect).
 *
 * A backend that changed the preference itself (the lobby's `/settings`) says so with an empty
 * plugin message on [CHANNEL]. The message is a nudge, not a value: the language is read again from
 * service-player, so a backend cannot set anything the store does not hold, and a client — which
 * can send any plugin message it likes — is ignored outright.
 */
class LocaleConnectionListener(
    private val cache: PlayerLocaleCache,
    private val presence: PlayerPresenceService,
) {
    @Subscribe
    fun onPostLogin(event: PostLoginEvent): EventTask {
        val playerId = event.player.uniqueId
        return EventTask.async { reload(playerId) }
    }

    @Subscribe
    fun onPluginMessage(event: PluginMessageEvent): EventTask? {
        if (event.identifier != CHANNEL) return null
        // never forward it to the client, whoever sent it
        event.result = PluginMessageEvent.ForwardResult.handled()
        val server = event.source as? ServerConnection ?: return null
        val playerId = server.player.uniqueId
        return EventTask.async { reload(playerId) }
    }

    private fun reload(playerId: UUID) = applyStored(cache, playerId, presence.getLocale(playerId))

    @Subscribe
    fun onDisconnect(event: DisconnectEvent): EventTask {
        val playerId = event.player.uniqueId
        return EventTask.async { cache.remove(playerId) }
    }

    companion object {
        /**
         * The stored preference [tag], or none: a cleared or unsupported tag removes the entry, so
         * the client's own language applies again. A lookup that failed reads as none too; the next
         * refresh or login puts it right.
         */
        fun applyStored(cache: PlayerLocaleCache, playerId: UUID, tag: String?) {
            val locale = tag?.let(SupportedLanguages::parse)
            if (locale != null) cache.set(playerId, locale) else cache.remove(playerId)
        }

        val CHANNEL: MinecraftChannelIdentifier =
            MinecraftChannelIdentifier.create("grounds", "locale")
    }
}
