package gg.grounds.player.settings

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SidebarSettingsFlowTest {
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
        val visibility = LinkedBlockingQueue<Boolean>()

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

    private fun sidebarClick(dialog: SettingsDialog): Key {
        val node =
            nodes(dialog.body).first {
                val hover = it.hoverEvent()?.value() as? Component
                hover != null &&
                    plain.serialize(hover).startsWith("Show the scoreboard") &&
                    it.clickEvent() != null
            }
        return (node.clickEvent()!!.payload() as ClickEvent.Payload.Custom).key()
    }

    private class Backend : AutoCloseable {
        @Volatile var enabled = false
        @Volatile var fail = false
        var beforePut: () -> Unit = {}
        val writes = LinkedBlockingQueue<Boolean>()
        val server =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    var status = 200
                    val path = exchange.requestURI.path
                    val body =
                        when {
                            path.endsWith("/sidebar") -> {
                                if (exchange.requestMethod == "PUT") {
                                    val value =
                                        JsonParser.parseString(
                                                exchange.requestBody.reader().readText()
                                            )
                                            .asJsonObject
                                            .get("enabled")
                                            .asBoolean
                                    writes.add(value)
                                    beforePut()
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
            onSidebarSaved = { _, enabled -> platform.visibility.add(enabled) },
        )
    }

    @Test
    fun `sidebar toggle reads storage saves through its real click and survives reopening`() {
        Backend().use { backend ->
            val platform = Platform()
            val player = TestPlayer()
            menu(platform, backend).use { menu ->
                menu.open(player, SettingsMenu.Tab.GAMEPLAY)
                menu.onClick(player, sidebarClick(platform.dialog()))
                platform.dialog()
                assertEquals(
                    true,
                    backend.writes.poll(5, TimeUnit.SECONDS),
                    "Stored OFF must toggle ON",
                )
                assertEquals(true, platform.visibility.poll(5, TimeUnit.SECONDS))
                menu.forget(player.id)
                menu.open(player, SettingsMenu.Tab.GAMEPLAY)
                menu.onClick(player, sidebarClick(platform.dialog()))
                assertEquals(
                    false,
                    backend.writes.poll(5, TimeUnit.SECONDS),
                    "Reopened stored ON must toggle OFF",
                )
                assertEquals(false, platform.visibility.poll(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun `failed save rolls back and never announces a setting change`() {
        Backend().use { backend ->
            backend.fail = true
            val platform = Platform()
            val player = TestPlayer()
            menu(platform, backend).use { menu ->
                menu.open(player, SettingsMenu.Tab.GAMEPLAY)
                menu.onClick(player, sidebarClick(platform.dialog()))
                platform.dialog()
                assertEquals(true, backend.writes.poll(5, TimeUnit.SECONDS))
                assertNotNull(player.errors.poll(5, TimeUnit.SECONDS))
                val restored = platform.dialog()
                assertTrue(platform.visibility.isEmpty())
                backend.fail = false
                menu.onClick(player, sidebarClick(restored))
                assertEquals(
                    true,
                    backend.writes.poll(5, TimeUnit.SECONDS),
                    "Failed ON must have rolled back to OFF",
                )
                assertEquals(true, platform.visibility.poll(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun `two failed queued clicks restore the last committed value`() {
        Backend().use { backend ->
            backend.enabled = true
            backend.fail = true
            val release = CountDownLatch(1)
            val first = AtomicBoolean(true)
            backend.beforePut = {
                if (first.getAndSet(false)) check(release.await(4, TimeUnit.SECONDS))
            }
            val platform = Platform()
            val player = TestPlayer()
            menu(platform, backend).use { menu ->
                menu.open(player, SettingsMenu.Tab.GAMEPLAY)
                menu.onClick(player, sidebarClick(platform.dialog()))
                val off = platform.dialog()
                assertEquals(false, backend.writes.poll(5, TimeUnit.SECONDS))
                menu.onClick(player, sidebarClick(off))
                platform.dialog()
                release.countDown()
                assertEquals(true, backend.writes.poll(5, TimeUnit.SECONDS))
                var restored: SettingsDialog? = null
                repeat(2) {
                    assertNotNull(player.errors.poll(5, TimeUnit.SECONDS))
                    restored = platform.dialog()
                }
                assertTrue(platform.visibility.isEmpty())
                backend.fail = false
                menu.onClick(player, sidebarClick(checkNotNull(restored)))
                assertEquals(
                    false,
                    backend.writes.poll(5, TimeUnit.SECONDS),
                    "Both saves failed: stored ON must toggle OFF",
                )
                assertEquals(false, platform.visibility.poll(5, TimeUnit.SECONDS))
            }
        }
    }
}
