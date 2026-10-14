package gg.grounds.locale

import com.velocitypowered.api.proxy.Player
import gg.grounds.locale.listener.LocaleConnectionListener
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

    @Test
    fun followingTheClientAgainDropsTheChoiceAndTheEffectiveLocale() {
        // the menu's "Client default" stores nothing; a stale effective locale would keep the old
        // language for every relayed message until the next login
        val cache = PlayerLocaleCache()
        val fake = FakePlayer(UUID.randomUUID())
        cache.choose(fake.player, Locale.GERMAN)

        cache.followClient(fake.player)

        assertEquals(null, cache.get(fake.id))
        assertEquals(null, fake.effectiveLocale, "null makes Velocity use the client's locale")
    }

    @Test
    fun aRefreshAppliesTheStoredLanguageOrFallsBackToTheClient() {
        val cache = PlayerLocaleCache()
        val fake = FakePlayer(UUID.randomUUID())

        LocaleConnectionListener.applyStored(cache, fake.player, "de")
        assertEquals(Locale.GERMAN.language, fake.effectiveLocale?.language)

        LocaleConnectionListener.applyStored(cache, fake.player, "xx")
        assertEquals(null, fake.effectiveLocale, "an unsupported tag is no preference")

        LocaleConnectionListener.applyStored(cache, fake.player, "fr")
        LocaleConnectionListener.applyStored(cache, fake.player, null)
        assertEquals(null, cache.get(fake.id), "a cleared preference follows the client")
        assertEquals(null, fake.effectiveLocale)
    }
}
