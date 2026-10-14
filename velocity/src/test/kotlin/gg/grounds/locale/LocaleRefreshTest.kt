package gg.grounds.locale

import gg.grounds.locale.listener.LocaleConnectionListener
import java.util.Locale
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocaleRefreshTest {
    private val playerId = UUID.randomUUID()

    @Test
    fun aStoredLanguageReplacesTheCachedOne() {
        val cache = PlayerLocaleCache().apply { set(playerId, Locale.ENGLISH) }

        LocaleConnectionListener.applyStored(cache, playerId, "de")

        assertEquals(Locale.GERMAN.language, cache.get(playerId)?.language)
    }

    @Test
    fun aClearedPreferenceFallsBackToTheClientLanguage() {
        // the menu's "Client default" stores nothing; a stale entry must not keep the old language
        val cache = PlayerLocaleCache().apply { set(playerId, Locale.GERMAN) }

        LocaleConnectionListener.applyStored(cache, playerId, null)

        assertNull(cache.get(playerId))
    }

    @Test
    fun anUnsupportedTagIsTreatedAsNoPreference() {
        val cache = PlayerLocaleCache().apply { set(playerId, Locale.GERMAN) }

        LocaleConnectionListener.applyStored(cache, playerId, "xx")

        assertNull(cache.get(playerId))
    }
}
