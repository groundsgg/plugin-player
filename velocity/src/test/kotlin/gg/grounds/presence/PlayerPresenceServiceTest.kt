package gg.grounds.presence

import com.sun.net.httpserver.HttpServer
import com.velocitypowered.api.proxy.Player
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class PlayerPresenceServiceTest {
    @Test
    fun `a source connection can send only one outstanding transfer`() {
        val profile = UUID.randomUUID()
        val issued = AtomicInteger()
        val transferred = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val mapper = JsonMapper.builder().build()
        server.createContext("/") { exchange ->
            val body = mapper.readTree(exchange.requestBody.readAllBytes())
            val response =
                if (exchange.requestURI.path.endsWith("/transfer")) {
                    issued.incrementAndGet()
                    """{"token":"${"v".repeat(43)}","expiresAt":"${Instant.now().plusSeconds(15)}"}"""
                } else {
                    """{"playerId":"$profile","sessionId":"${body.get("sessionId").asString()}","leaseExpiresAt":"${Instant.now().plusSeconds(90)}"}"""
                }
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(201, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val player =
            Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) {
                proxy,
                method,
                args ->
                when (method.name) {
                    "getUniqueId" -> profile
                    "getUsername" -> "TransferProbe"
                    "isActive" -> true
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.get(0)
                    "storeCookie" -> null
                    "transferToHost" -> {
                        transferred.incrementAndGet()
                        null
                    }
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as Player
        val presence = PlayerPresenceService()
        try {
            presence.configure("http://127.0.0.1:${server.address.port}")
            presence.claim(player, "source", "eu")
            assertTrue(presence.transferToHost(player, "target.invalid", 25565))
            assertFalse(presence.transferToHost(player, "other.invalid", 25565))
            assertEquals(1, issued.get())
            assertEquals(1, transferred.get())
        } finally {
            presence.close()
            server.stop(0)
        }
    }
}
