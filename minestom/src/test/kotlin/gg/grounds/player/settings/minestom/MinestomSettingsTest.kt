package gg.grounds.player.settings.minestom

import gg.grounds.player.settings.SettingsDialog
import kotlin.test.assertTrue
import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.api.BinaryTagHolder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.minestom.server.MinecraftServer
import net.minestom.server.codec.Transcoder
import net.minestom.server.dialog.Dialog
import net.minestom.server.registry.RegistryTranscoder
import org.junit.jupiter.api.Test

class MinestomSettingsTest {

    @Test
    fun `the dialog encodes with the body's clicks and Done's close key`() {
        MinecraftServer.init()
        val token = Key.key("grounds", "menu/t/0")
        val dialog =
            SettingsDialog(
                title = Component.text("Settings"),
                body =
                    Component.text("x")
                        .clickEvent(
                            ClickEvent.custom(token, BinaryTagHolder.binaryTagHolder("{}"))
                        ),
                width = 300,
                done = Component.text("Done"),
                close = Key.key("grounds", "menu/close"),
            )
        val json =
            Dialog.REGISTRY_CODEC.encode(
                    RegistryTranscoder(Transcoder.JSON, MinecraftServer.process()),
                    MinestomSettings.dialog(dialog),
                )
                .orElseThrow()
                .toString()
        assertTrue("grounds:menu/t/0" in json, json)
        assertTrue("grounds:menu/close" in json, json)
        assertTrue("\"after_action\":\"none\"" in json, json)
    }
}
