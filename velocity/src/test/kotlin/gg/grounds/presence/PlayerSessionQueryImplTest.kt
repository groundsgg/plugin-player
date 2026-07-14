package gg.grounds.presence

import gg.grounds.grpc.player.CountPlayersByServerReply
import gg.grounds.grpc.player.ServerPlayerCount
import java.net.ServerSocket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayerSessionQueryImplTest {

    @Test
    fun countPlayersByServerMapsServersAndCarriesTotal() {
        val reply =
            CountPlayersByServerReply.newBuilder()
                .addServers(
                    ServerPlayerCount.newBuilder().setServerName("lobby-1").setPlayers(2).build()
                )
                .addServers(
                    ServerPlayerCount.newBuilder().setServerName("lobby-2").setPlayers(5).build()
                )
                .setTotal(8)
                .build()

        val counts = PlayerSessionQueryImpl(PlayerPresenceService()).toNetworkPlayerCounts(reply)

        assertEquals(mapOf("lobby-1" to 2, "lobby-2" to 5), counts.byServer)
        assertEquals(8, counts.total)
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
