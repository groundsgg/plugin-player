package gg.grounds.player.presence

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HttpPlayerPresenceClientTest {

    private lateinit var server: HttpServer
    private lateinit var client: HttpPlayerPresenceClient

    /** Path -> what to answer with. Recorded requests land in [seen]. */
    private val routes = ConcurrentHashMap<String, Pair<Int, String>>()
    private val seen = ConcurrentHashMap<String, RecordedRequest>()

    data class RecordedRequest(val method: String, val query: String?, val authorization: String?)

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange -> answer(exchange) }
        server.start()
        client =
            HttpPlayerPresenceClient(
                baseUrl = "127.0.0.1:${server.address.port}",
                tokenProvider = { "test-token" },
            )
    }

    @AfterEach
    fun stopServer() {
        client.close()
        server.stop(0)
    }

    private fun answer(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        seen[path] =
            RecordedRequest(
                method = exchange.requestMethod,
                query = exchange.requestURI.query,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
            )
        val (status, body) = routes[path] ?: (404 to "")
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        // A 204 must not carry a body, and the JDK server enforces it.
        if (status == 204 || bytes.isEmpty()) {
            exchange.sendResponseHeaders(status, -1)
        } else {
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        exchange.close()
    }

    @Test
    fun `a created session is accepted, and the workload token rides along`() {
        routes["/v1/players/sessions"] = 201 to ""

        val result = client.tryLogin(PLAYER_ID, "Notch", "velocity-1", "nl-ams1")

        assertEquals(PlayerLoginResult.Accepted, result)
        assertEquals("Bearer test-token", seen["/v1/players/sessions"]?.authorization)
    }

    @Test
    fun `409 is already-online, not an error`() {
        routes["/v1/players/sessions"] =
            409 to """{"title":"Player already online","status":409,"code":"already_online"}"""

        assertEquals(PlayerLoginResult.AlreadyOnline, client.tryLogin(PLAYER_ID))
    }

    @Test
    fun `a 5xx is unavailable, so the caller knows it learned nothing`() {
        routes["/v1/players/sessions"] =
            503 to
                """{"title":"Service unavailable","status":503,"detail":"store is down","code":"store_unavailable"}"""

        val result = client.tryLogin(PLAYER_ID)

        assertTrue(result is PlayerLoginResult.Unavailable, "expected Unavailable, got $result")
        assertEquals("store is down", (result as PlayerLoginResult.Unavailable).message)
    }

    @Test
    fun `an unreachable service is unavailable rather than an exception`() {
        client.close()
        val dead = HttpPlayerPresenceClient("127.0.0.1:1", tokenProvider = { null })

        assertTrue(dead.tryLogin(PLAYER_ID) is PlayerLoginResult.Unavailable)
        assertNull(dead.getSession(PLAYER_ID))
        assertEquals(emptyList<String>(), dead.suggestNames("no", 5))
        assertNull(dead.countPlayersByServer())
        dead.close()
    }

    @Test
    fun `a session is read with its absent fields left absent`() {
        routes["/v1/players/$PLAYER_ID/session"] =
            200 to
                """{"playerId":"$PLAYER_ID","playerName":"Notch","proxyId":"velocity-1",
                   |"serverName":null,"region":null,"connectedAt":"2026-08-04T10:00:00Z"}"""
                    .trimMargin()

        val session = client.getSession(PLAYER_ID)

        assertEquals("Notch", session?.playerName)
        assertEquals("velocity-1", session?.proxyId)
        assertNull(session?.serverName)
        assertNull(session?.region)
        assertEquals(1785837600000L, session?.connectedAtMillis)
    }

    @Test
    fun `a player who is not online reads as null`() {
        routes["/v1/players/$PLAYER_ID/session"] = 404 to """{"code":"not_found"}"""

        assertNull(client.getSession(PLAYER_ID))
    }

    @Test
    fun `logout scopes the delete to the calling proxy`() {
        routes["/v1/players/$PLAYER_ID/session"] = 204 to ""

        assertEquals(PlayerLogoutResult.Removed, client.logout(PLAYER_ID, "velocity-1"))
        val request = seen["/v1/players/$PLAYER_ID/session"]
        assertEquals("DELETE", request?.method)
        assertEquals("proxyId=velocity-1", request?.query)
    }

    @Test
    fun `logout without a proxy sends no scope`() {
        routes["/v1/players/$PLAYER_ID/session"] = 404 to ""

        assertEquals(PlayerLogoutResult.NotFound, client.logout(PLAYER_ID))
        assertNull(seen["/v1/players/$PLAYER_ID/session"]?.query)
    }

    @Test
    fun `a heartbeat batch reports what it touched`() {
        routes["/v1/players/sessions/heartbeats"] = 200 to """{"updated":3,"missing":1}"""

        val result = client.heartbeatBatch(listOf(PLAYER_ID, UUID.randomUUID()))

        assertTrue(result.success)
        assertEquals(3, result.updated)
        assertEquals(1, result.missing)
    }

    @Test
    fun `a rejected heartbeat batch counts every player as missing`() {
        routes["/v1/players/sessions/heartbeats"] =
            400 to """{"detail":"playerIds must be UUIDs","code":"invalid_request"}"""

        val result = client.heartbeatBatch(listOf(PLAYER_ID, UUID.randomUUID()))

        assertEquals(false, result.success)
        assertEquals(0, result.updated)
        assertEquals(2, result.missing)
        assertEquals("playerIds must be UUIDs", result.message)
    }

    @Test
    fun `a name resolves through the session collection`() {
        routes["/v1/players/sessions"] =
            200 to
                """{"playerId":"$PLAYER_ID","playerName":"Notch","proxyId":null,"serverName":null,
                   |"region":null,"connectedAt":"2026-08-04T10:00:00Z"}"""
                    .trimMargin()

        assertEquals(PLAYER_ID, client.resolveName("notch")?.playerId)
        assertEquals("name=notch", seen["/v1/players/sessions"]?.query)
    }

    @Test
    fun `suggestions come back as a plain list`() {
        routes["/v1/players/names/suggestions"] = 200 to """{"playerNames":["Notch","Nobody"]}"""

        assertEquals(listOf("Notch", "Nobody"), client.suggestNames("no", 10))
    }

    @Test
    fun `counts carry the total and drop an absent region`() {
        routes["/v1/players/counts/proxies"] =
            200 to
                """
                |{"proxies":[{"proxyId":"velocity-1","region":"nl-ams1","players":3},
                |{"proxyId":"velocity-2","region":null,"players":1}],"total":4}
                """
                    .trimMargin()

        val counts = client.countPlayersByProxy()

        assertEquals(4, counts?.total)
        assertEquals("nl-ams1", counts?.proxies?.get(0)?.region)
        assertNull(counts?.proxies?.get(1)?.region)
    }

    @Test
    fun `an unset locale reads as null`() {
        routes["/v1/players/$PLAYER_ID/locale"] = 200 to """{"locale":null}"""

        assertNull(client.getLocale(PLAYER_ID))
    }

    @Test
    fun `storing a locale reports whether it landed`() {
        routes["/v1/players/$PLAYER_ID/locale"] = 204 to ""

        assertTrue(client.setLocale(PLAYER_ID, "de-DE"))
        assertEquals("PUT", seen["/v1/players/$PLAYER_ID/locale"]?.method)
    }

    @Test
    fun `a server move that matched no session is false`() {
        routes["/v1/players/$PLAYER_ID/session/server"] = 404 to """{"code":"not_found"}"""

        assertEquals(false, client.updateServer(PLAYER_ID, "lobby-2"))
    }

    @Test
    fun `a base url without a scheme is still usable`() {
        assertEquals(
            "http://service-player.api.svc.cluster.local:9000",
            HttpPlayerPresenceClient.normalizeBaseUrl("service-player.api.svc.cluster.local:9000/"),
        )
        assertEquals(
            "https://player.example",
            HttpPlayerPresenceClient.normalizeBaseUrl(" https://player.example/ "),
        )
    }

    companion object {
        private val PLAYER_ID: UUID = UUID.fromString("8f3a1c2e-4b5d-4e6f-8a9b-0c1d2e3f4a5b")
    }
}
