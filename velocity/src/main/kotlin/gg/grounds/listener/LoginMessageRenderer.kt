package gg.grounds.listener

import gg.grounds.PlayerMessage
import gg.grounds.i18n.Translations
import java.util.Locale
import net.kyori.adventure.text.Component

/** PreLogin precedes client locale negotiation; use the source language until a locale is known. */
internal object LoginMessageRenderer {
    private val messages =
        Translations.forBundle("gg.grounds.player.messages", javaClass.classLoader)

    fun render(key: PlayerMessage, override: String?, locale: Locale = Locale.ENGLISH): Component =
        if (override != null) Component.text(override) else messages.render(key, locale)
}
