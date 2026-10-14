package gg.grounds.locale

import com.velocitypowered.api.proxy.Player
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerLocaleCacheTest {
    /** Just enough of a Player: an id, and a recorded effective locale. */
    private class FakePlayer(val id: UUID) {
        var effectiveLocale: Locale? = null

        val player: Player =
            Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) {
                _,
                method,
                args ->
                when (method.name) {
                    "getUniqueId" -> id
                    "setEffectiveLocale" -> {
                        effectiveLocale = args[0] as Locale?
                        null
                    }
                    "getEffectiveLocale" -> effectiveLocale
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as Player
    }

    @Test
    fun choosingALanguageCachesItAndMakesItTheEffectiveLocale() {
        val cache = PlayerLocaleCache()
        val fake = FakePlayer(UUID.randomUUID())

        cache.choose(fake.player, Locale.GERMAN)

        assertEquals(Locale.GERMAN, cache.get(fake.id))
        assertEquals(
            Locale.GERMAN,
            fake.effectiveLocale,
            "Velocity renders relayed messages and reports Identity.LOCALE from the effective locale",
        )
    }
}
