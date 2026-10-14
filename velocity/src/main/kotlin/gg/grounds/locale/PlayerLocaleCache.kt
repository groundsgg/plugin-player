package gg.grounds.locale

import com.velocitypowered.api.proxy.Player
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Each online player's chosen language, held in memory so the render path (every message, every
 * tick) never makes a network call. Loaded from service-player on join, updated by `/lang`, dropped
 * on disconnect. A player who is absent here has set no preference — the caller uses the client's
 * announced locale.
 */
class PlayerLocaleCache {
    private val cache = ConcurrentHashMap<UUID, Locale>()

    fun get(playerId: UUID): Locale? = cache[playerId]

    fun set(playerId: UUID, locale: Locale) {
        cache[playerId] = locale
    }

    /**
     * Records [player]'s chosen language and makes it their effective locale in Velocity.
     *
     * The effective locale is what Velocity renders translatable messages with on the way out
     * (`GlobalTranslator`), and what a player's `Identity.LOCALE` pointer reports — so setting it
     * here carries the choice into every plugin on this proxy, including messages relayed from
     * another proxy, without each of them consulting this cache.
     */
    fun choose(player: Player, locale: Locale) {
        set(player.uniqueId, locale)
        player.effectiveLocale = locale
    }

    fun remove(playerId: UUID) {
        cache.remove(playerId)
    }

    fun clear() {
        cache.clear()
    }
}
