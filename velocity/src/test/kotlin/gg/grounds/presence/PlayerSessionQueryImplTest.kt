package gg.grounds.presence

import gg.grounds.player.presence.ProxyPlayerCount
import gg.grounds.player.presence.ProxyPlayerCounts
import gg.grounds.player.presence.ServerPlayerCount
import gg.grounds.player.presence.ServerPlayerCounts
import java.net.ServerSocket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayerSessionQueryImplTest {

    @Test
    fun countPlayersByServerMapsServersAndCarriesTotal() {
        val counts =
            ServerPlayerCounts(
                servers = listOf(ServerPlayerCount("lobby-1", 2), ServerPlayerCount("lobby-2", 5)),
                total = 8,
            )

        val mapped = PlayerSessionQueryImpl(PlayerPresenceService()).toNetworkPlayerCounts(counts)

        assertEquals(mapOf("lobby-1" to 2, "lobby-2" to 5), mapped.byServer)
        assertEquals(8, mapped.total)
    }

    @Test
    fun countPlayersByProxyKeepsAnAbsentRegionAbsent() {
        val counts =
            ProxyPlayerCounts(
                proxies =
                    listOf(
                        ProxyPlayerCount("velocity-1", "nl-ams1", 3),
                        ProxyPlayerCount("velocity-2", null, 1),
                    ),
                total = 4,
            )

        val mapped = PlayerSessionQueryImpl(PlayerPresenceService()).toNetworkProxyCounts(counts)

        assertEquals("nl-ams1", mapped.proxies[0].region)
        assertNull(mapped.proxies[1].region)
        assertEquals(4, mapped.total)
    }

    @Test
    fun countPlayersByServerReturnsNullWhenPresenceServiceIsUnavailable() {
        val presenceService = PlayerPresenceService()
        val unusedPort = ServerSocket(0).use { it.localPort }
        presenceService.configure("localhost:$unusedPort")

        try {
            assertNull(PlayerSessionQueryImpl(presenceService).countPlayersByServer())
        } finally {
            presenceService.close()
        }
    }
}
