package gg.grounds.player.settings.minestom

import gg.grounds.modules.core.DefaultServiceRegistry
import gg.grounds.player.settings.SettingsDialog
import gg.grounds.runtime.GroundsServerContext
import gg.grounds.runtime.RuntimeEnvironment
import gg.grounds.runtime.ServerType
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.api.BinaryTagHolder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.minestom.server.MinecraftServer
import net.minestom.server.codec.Transcoder
import net.minestom.server.dialog.Dialog
import net.minestom.server.event.EventNode
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

    /** A node the runtime only creates; clicks never reached a module that did not hang it. */
    @Test
    fun `the module's listeners hang off the global handler, and come off on stop`() {
        MinecraftServer.init()
        val module = GroundsSettingsModule()
        module.install(
            object : GroundsServerContext {
                override val serverType = ServerType.MINIGAME
                override val environment = RuntimeEnvironment.TEST
                override val services = DefaultServiceRegistry()

                override fun eventNode(name: String) = EventNode.all(name)

                override fun onShutdown(action: () -> Unit) {}
            }
        )
        val attached = {
            MinecraftServer.getGlobalEventHandler().children.any { it.name == "grounds-settings" }
        }
        assertTrue(attached())
        module.stop()
        assertFalse(attached())
    }
}
