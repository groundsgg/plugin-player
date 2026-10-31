package gg.grounds.player.settings

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AlertSettingsFlowTest {
    @TempDir lateinit var temporary: Path
    private val plain = PlainTextComponentSerializer.plainText()

    private class TestPlayer(val id: UUID = UUID.randomUUID()) : Audience {
        val errors = LinkedBlockingQueue<Component>()

        override fun sendMessage(message: Component) {
            errors.add(message)
        }
    }

    private class Platform : SettingsPlatform<TestPlayer> {
        val shown = LinkedBlockingQueue<SettingsDialog>()
        val refreshes = LinkedBlockingQueue<Pair<String, Int>>()

        override fun id(player: TestPlayer) = player.id

        override fun show(player: TestPlayer, dialog: SettingsDialog) {
            shown.add(dialog)
        }

        override fun close(player: TestPlayer) {}

        override fun sendPluginMessage(player: TestPlayer, channel: String, message: ByteArray) {
            refreshes.add(channel to message.size)
        }

        fun dialog() = checkNotNull(shown.poll(5, TimeUnit.SECONDS)) { "Menu did not open" }
    }

    private fun nodes(c: Component): Sequence<Component> = sequence {
        yield(c)
        c.children().forEach { yieldAll(nodes(it)) }
    }

    private fun alertClick(dialog: SettingsDialog, setting: String): Key {
        val hint =
            when (setting) {
                "friend-alerts" -> "Tell me when friends"
                "party-invites" -> "Let players invite"
                else -> "Play a sound"
            }
        val node =
            nodes(dialog.body).first {
                val hover = it.hoverEvent()?.value() as? Component
                hover != null && plain.serialize(hover).startsWith(hint) && it.clickEvent() != null
            }
        return (node.clickEvent()!!.payload() as ClickEvent.Payload.Custom).key()
    }

    private class Backend(val setting: String) : AutoCloseable {
        @Volatile var enabled = false
        @Volatile var fail = false
        val writes = LinkedBlockingQueue<Boolean>()
        val server =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    var status = 200
                    val path = exchange.requestURI.path
                    val body =
                        when {
                            path.endsWith("/$setting/settings") -> {
                                if (exchange.requestMethod == "PUT") {
                                    val value =
                                        JsonParser.parseString(
                                                exchange.requestBody.reader().readText()
                                            )
                                            .asJsonObject
                                            .get("enabled")
                                            .asBoolean
                                    writes.add(value)
                                    if (fail) status = 500 else enabled = value
                                }
                                "{\"enabled\":$enabled}"
                            }
                            path.endsWith("/locale") -> "{\"locale\":\"en\"}"
                            path.endsWith("/region") -> "{\"region\":null}"
                            path.endsWith("/voice/settings") ->
                                "{\"partyVoice\":true,\"hints\":true}"
                            else -> "{\"enabled\":true}"
                        }
                    val bytes = body.toByteArray()
                    exchange.sendResponseHeaders(status, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                    exchange.close()
                }
                start()
            }
        val url
            get() = "http://127.0.0.1:${server.address.port}"

        override fun close() = server.stop(0)
    }

    private fun menu(platform: Platform, backend: Backend): SettingsMenu<TestPlayer> {
        val token = temporary.resolve("token")
        Files.writeString(token, "test-workload-token")
        return SettingsMenu(
            platform,
            PlayerSettingsClient(backend.url, backend.url, token),
            SettingsMenu.messages { false },
        )
    }

    @Test
    fun `alert toggle reads storage saves through its real click and survives reopening`() {
        for (setting in listOf("friend-alerts", "party-invites", "match-sounds")) Backend(setting)
            .use { backend ->
                val platform = Platform()
                val player = TestPlayer()
                menu(platform, backend).use { menu ->
                    menu.open(player, SettingsMenu.Tab.ALERTS)
                    menu.onClick(player, alertClick(platform.dialog(), setting))
                    platform.dialog()
                    assertEquals(
                        true,
                        backend.writes.poll(5, TimeUnit.SECONDS),
                        "Stored OFF must toggle ON",
                    )
                    assertTrue(platform.refreshes.isEmpty())
                    menu.forget(player.id)
                    menu.open(player, SettingsMenu.Tab.ALERTS)
                    menu.onClick(player, alertClick(platform.dialog(), setting))
                    assertEquals(
                        false,
                        backend.writes.poll(5, TimeUnit.SECONDS),
                        "Reopened stored ON must toggle OFF",
                    )
                    assertTrue(platform.refreshes.isEmpty())
                }
            }
    }

    @Test
    fun `failed save rolls back and never announces a setting change`() {
        for (setting in listOf("friend-alerts", "party-invites", "match-sounds")) Backend(setting)
            .use { backend ->
                backend.fail = true
                val platform = Platform()
                val player = TestPlayer()
                menu(platform, backend).use { menu ->
                    menu.open(player, SettingsMenu.Tab.ALERTS)
                    menu.onClick(player, alertClick(platform.dialog(), setting))
                    platform.dialog()
                    assertEquals(true, backend.writes.poll(5, TimeUnit.SECONDS))
                    assertNotNull(player.errors.poll(5, TimeUnit.SECONDS))
                    val restored = platform.dialog()
                    assertTrue(platform.refreshes.isEmpty())
                    backend.fail = false
                    menu.onClick(player, alertClick(restored, setting))
                    assertEquals(
                        true,
                        backend.writes.poll(5, TimeUnit.SECONDS),
                        "Failed ON must have rolled back to OFF",
                    )
                    assertTrue(platform.refreshes.isEmpty())
                }
            }
    }
}
