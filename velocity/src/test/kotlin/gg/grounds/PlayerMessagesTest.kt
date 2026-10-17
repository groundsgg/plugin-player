package gg.grounds

import gg.grounds.i18n.BundleCheck
import gg.grounds.i18n.SystemCategory
import gg.grounds.i18n.SystemLevel
import gg.grounds.i18n.Translations
import java.util.Locale
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerMessagesTest {
    private val messages =
        Translations.forBundle("gg.grounds.player.messages", javaClass.classLoader)
    private val plain = PlainTextComponentSerializer.plainText()

    @Test
    fun `language errors are localized and player input remains literal`() {
        val rendered =
            messages.render(
                PlayerMessage.LANG_UNKNOWN,
                Locale.GERMAN,
                "code" to "<red>xx</red>",
                "available" to "en, de, es, fr",
            )

        assertEquals(
            "Unbekannte Sprache '<red>xx</red>'. Verfügbar: en, de, es, fr",
            plain.serialize(rendered),
        )
        assertEquals(SystemCategory.GENERAL, PlayerMessage.LANG_UNKNOWN.category)
        assertEquals(SystemLevel.ERROR, PlayerMessage.LANG_UNKNOWN.level)
    }

    @Test
    fun `English and German bundles cover every player message`() {
        assertEquals(
            emptyList<String>(),
            BundleCheck.problems(
                "gg.grounds.player.messages",
                javaClass.classLoader,
                PlayerMessage.entries,
                translations = listOf(Locale.GERMAN),
            ),
        )
    }
}
