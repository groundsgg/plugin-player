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

class LanguageSettingsFlowTest {
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
        val languages = LinkedBlockingQueue<String>()
        var notificationFails = false
        var localNotificationFails = false

        override fun id(player: TestPlayer) = player.id

        override fun show(player: TestPlayer, dialog: SettingsDialog) {
            shown.add(dialog)
        }

        override fun close(player: TestPlayer) {}

        override fun sendPluginMessage(player: TestPlayer, channel: String, message: ByteArray) {
            refreshes.add(channel to message.size)
            if (notificationFails) error("proxy unavailable")
        }

        fun dialog() = checkNotNull(shown.poll(5, TimeUnit.SECONDS)) { "Menu did not open" }
    }

    private fun nodes(c: Component): Sequence<Component> = sequence {
        yield(c)
        c.children().forEach { yieldAll(nodes(it)) }
    }

    private fun languageClick(dialog: SettingsDialog): Key {
        val node =
            nodes(dialog.body).first {
                val hover = it.hoverEvent()?.value() as? Component
                hover != null && plain.serialize(hover) == "Next" && it.clickEvent() != null
            }
        return (node.clickEvent()!!.payload() as ClickEvent.Payload.Custom).key()
    }

    private class Backend : AutoCloseable {
        @Volatile var language = "en"
        @Volatile var fail = false
        val server =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    var status = 200
                    val path = exchange.requestURI.path
                    val body =
                        when {
                            path.endsWith("/locale") -> {
                                if (exchange.requestMethod == "PUT") {
                                    val value =
                                        JsonParser.parseString(
                                                exchange.requestBody.reader().readText()
                                            )
                                            .asJsonObject
                                            .get("locale")
                                            .asString
                                    if (fail) status = 500 else language = value
                                }
                                "{\"locale\":\"$language\"}"
                            }
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
            onLanguageSaved = { _, tag ->
                platform.languages.add(tag ?: "client")
                if (platform.localNotificationFails) error("local renderer unavailable")
            },
        )
    }

    @Test
    fun `proxy notification failure preserves committed language and still refreshes local presentation`() {
        Backend().use { backend ->
            val platform = Platform().apply { notificationFails = true }
            val player = TestPlayer()
            menu(platform, backend).use { menu ->
                menu.open(player)
                menu.onClick(player, languageClick(platform.dialog()))
                assertEquals("de", platform.languages.poll(5, TimeUnit.SECONDS))
                assertEquals("de", backend.language)
                val translated = platform.dialog()
                assertTrue(plain.serialize(translated.body).contains("Deutsch"))
                assertTrue(
                    player.errors.isEmpty(),
                    "A committed language must not report a failed save",
                )
            }
        }
    }

    @Test
    fun `failed language write never announces a preference and restores the menu`() {
        Backend().use { backend ->
            backend.fail = true
            val platform = Platform()
            val player = TestPlayer()
            menu(platform, backend).use { menu ->
                menu.open(player)
                menu.onClick(player, languageClick(platform.dialog()))
                platform.dialog() // The accepted click first shows its optimistic language.
                assertNotNull(player.errors.poll(5, TimeUnit.SECONDS))
                assertTrue(plain.serialize(platform.dialog().body).contains("English"))
                assertEquals("en", backend.language)
                assertTrue(platform.languages.isEmpty())
                assertTrue(platform.refreshes.isEmpty())
            }
        }
    }

    @Test
    fun `local language notification failure still nudges the proxy and keeps the committed menu`() {
        Backend().use { backend ->
            val platform = Platform().apply { localNotificationFails = true }
            val player = TestPlayer()
            menu(platform, backend).use { menu ->
                menu.open(player)
                menu.onClick(player, languageClick(platform.dialog()))
                assertEquals("grounds:locale" to 0, platform.refreshes.poll(5, TimeUnit.SECONDS))
                assertTrue(plain.serialize(platform.dialog().body).contains("Deutsch"))
                assertEquals("de", backend.language)
                assertTrue(player.errors.isEmpty())
            }
        }
    }
}
