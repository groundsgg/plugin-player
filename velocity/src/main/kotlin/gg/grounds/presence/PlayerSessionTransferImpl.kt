package gg.grounds.presence

import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.proxy.api.PlayerSessionTransfer
import java.util.UUID

class PlayerSessionTransferImpl(
    private val proxy: ProxyServer,
    private val presence: PlayerPresenceService,
    private val isBedrock: (UUID) -> Boolean,
) : PlayerSessionTransfer {
    override fun transferToHost(playerId: UUID, host: String, port: Int): Boolean {
        val player = proxy.getPlayer(playerId).orElse(null) ?: return false
        if (player.protocolVersion < ProtocolVersion.MINECRAFT_1_20_5 || isBedrock(playerId))
            return false
        return presence.transferToHost(player, host, port)
    }
}
