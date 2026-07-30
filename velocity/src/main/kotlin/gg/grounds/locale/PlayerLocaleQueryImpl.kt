package gg.grounds.locale

import gg.grounds.proxy.api.PlayerLocaleQuery
import java.util.Locale
import java.util.UUID

/**
 * Publishes the per-player language cache to other plugins through the ProxyServiceRegistry, so a
 * localized plugin (plugin-social today) can resolve a message in the player's chosen language
 * without knowing anything about how it is stored.
 */
class PlayerLocaleQueryImpl(private val cache: PlayerLocaleCache) : PlayerLocaleQuery {
    override fun localeOf(playerId: UUID): Locale? = cache.get(playerId)
}
