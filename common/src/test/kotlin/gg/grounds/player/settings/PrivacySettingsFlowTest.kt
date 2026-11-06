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

class PrivacySettingsFlowTest {
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

    private fun click(dialog: SettingsDialog, hint: String, last: Boolean = false): Key {
        val audience = hint.startsWith("Who")
        val matches =
            nodes(dialog.body)
                .filter {
                    val hover = it.hoverEvent()?.value() as? Component
                    hover != null &&
                        plain.serialize(hover).startsWith(if (audience) "Next" else hint) &&
                        it.clickEvent() != null
                }
                .distinctBy { (it.clickEvent()!!.payload() as ClickEvent.Payload.Custom).key() }
                .toList()
        val node =
            if (audience) matches[if (hint.startsWith("Who may invite")) 0 else 1]
            else if (last) matches.last() else matches.first()
        return (node.clickEvent()!!.payload() as ClickEvent.Payload.Custom).key()
    }

    private class Backend : AutoCloseable {
        @Volatile
        var privacy = PrivacySettings(false, false, PrivacyAudience.FRIENDS, PrivacyAudience.NOBODY)
        @Volatile var fail = false
        @Volatile var failRead = false
        val writes = LinkedBlockingQueue<PrivacySettings>()
        val server =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    var status = 200
                    val body =
                        when {
                            exchange.requestURI.path.endsWith("/privacy") -> {
                                if (failRead && exchange.requestMethod == "GET") status = 503
                                if (exchange.requestMethod == "PUT") {
                                    val json =
                                        JsonParser.parseString(
                                                exchange.requestBody.reader().readText()
                                            )
                                            .asJsonObject
                                    val value =
                                        PrivacySettings(
                                            json.get("showStats").asBoolean,
                                            json.get("showOnline").asBoolean,
                                            PrivacyAudience.fromWire(
                                                json.get("partyInvites").asString
                                            ),
                                            PrivacyAudience.fromWire(
                                                json.get("privateMessages").asString
                                            ),
                                        )
                                    writes.add(value)
                                    if (fail) status = 500 else privacy = value
                                }
                                """{"showStats":${privacy.showStats},"showOnline":${privacy.showOnline},"partyInvites":"${privacy.partyInvites.wire}","privateMessages":"${privacy.privateMessages.wire}"}"""
                            }
                            exchange.requestURI.path.endsWith("/locale") -> """{"locale":"en"}"""
                            exchange.requestURI.path.endsWith("/region") -> """{"region":null}"""
                            exchange.requestURI.path.endsWith("/voice/settings") ->
                                """{"partyVoice":true,"hints":true}"""
                            else -> """{"enabled":true}"""
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
    fun `all four controls read canonical privacy and persist across reopening`() {
        Backend().use { backend ->
            val platform = Platform()
            val player = TestPlayer()
            menu(platform, backend).use { menu ->
                fun toggle(hint: String, last: Boolean = false): PrivacySettings {
                    menu.open(player, SettingsMenu.Tab.PRIVACY)
                    menu.onClick(player, click(platform.dialog(), hint, last))
                    platform.dialog()
                    val saved = checkNotNull(backend.writes.poll(5, TimeUnit.SECONDS))
                    platform.dialog() // canonical committed response
                    return saved
                }
                assertEquals(
                    PrivacySettings(true, false, PrivacyAudience.FRIENDS, PrivacyAudience.NOBODY),
                    toggle("Show my statistics"),
                )
                assertEquals(
                    PrivacySettings(true, true, PrivacyAudience.FRIENDS, PrivacyAudience.NOBODY),
                    toggle("Show when I am online"),
                )
                assertEquals(
                    PrivacySettings(true, true, PrivacyAudience.NOBODY, PrivacyAudience.NOBODY),
                    toggle("Who may invite", last = true),
                )
                assertEquals(
                    PrivacySettings(true, true, PrivacyAudience.NOBODY, PrivacyAudience.ANYONE),
                    toggle("Who may send", last = true),
                )
                menu.forget(player.id)
                menu.open(player, SettingsMenu.Tab.PRIVACY)
                menu.onClick(player, click(platform.dialog(), "Show my statistics"))
                assertFalse(checkNotNull(backend.writes.poll(5, TimeUnit.SECONDS)).showStats)
            }
        }
    }

    @Test
    fun `failed privacy save restores confirmed state and later writes preserve fresh unrelated changes`() {
        Backend().use { backend ->
            val platform = Platform()
            val player = TestPlayer()
            menu(platform, backend).use { menu ->
                backend.fail = true
                menu.open(player, SettingsMenu.Tab.PRIVACY)
                menu.onClick(player, click(platform.dialog(), "Show my statistics"))
                platform.dialog()
                assertTrue(checkNotNull(backend.writes.poll(5, TimeUnit.SECONDS)).showStats)
                assertNotNull(player.errors.poll(5, TimeUnit.SECONDS))
                val restored = platform.dialog()
                backend.fail = false
                // Another client changes audiences while this menu stays open.
                backend.privacy = backend.privacy.copy(privateMessages = PrivacyAudience.FRIENDS)
                menu.onClick(player, click(restored, "Show my statistics"))
                assertEquals(
                    PrivacySettings(true, false, PrivacyAudience.FRIENDS, PrivacyAudience.FRIENDS),
                    backend.writes.poll(5, TimeUnit.SECONDS),
                )
            }
        }
    }

    @Test
    fun `unavailable fresh privacy read never writes fallback values`() {
        Backend().use { backend ->
            val platform = Platform()
            val player = TestPlayer()
            menu(platform, backend).use { menu ->
                menu.open(player, SettingsMenu.Tab.PRIVACY)
                val opened = platform.dialog()
                backend.failRead = true
                menu.onClick(player, click(opened, "Show my statistics"))
                platform.dialog()
                assertNotNull(player.errors.poll(5, TimeUnit.SECONDS))
                platform.dialog()
                assertTrue(
                    backend.writes.isEmpty(),
                    "No PUT is permitted without canonical privacy",
                )
                assertFalse(backend.privacy.showStats)
            }
        }
    }
}
