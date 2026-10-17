package gg.grounds.listener

import gg.grounds.PlayerMessage
import java.util.Locale
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LoginMessageRendererTest {
    private val plain = PlainTextComponentSerializer.plainText()

    @Test
    fun `default login denial uses the source bundle without a chat prefix`() {
        assertEquals(
            "You are already online.",
            plain.serialize(LoginMessageRenderer.render(PlayerMessage.LOGIN_ALREADY_ONLINE, null)),
        )
        assertEquals(
            "Du bist bereits online.",
            plain.serialize(
                LoginMessageRenderer.render(PlayerMessage.LOGIN_ALREADY_ONLINE, null, Locale.GERMAN)
            ),
        )
    }

    @Test
    fun `explicit operator override remains literal`() {
        val text = "<red>Custom denial</red>"
        assertEquals(
            text,
            plain.serialize(
                LoginMessageRenderer.render(PlayerMessage.LOGIN_ERROR, text, Locale.GERMAN)
            ),
        )
    }
}
