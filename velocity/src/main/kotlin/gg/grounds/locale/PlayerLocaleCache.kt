package gg.grounds.locale

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

    fun remove(playerId: UUID) {
        cache.remove(playerId)
    }

    fun clear() {
        cache.clear()
    }
}
