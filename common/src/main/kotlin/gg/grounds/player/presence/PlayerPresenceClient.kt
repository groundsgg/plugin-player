package gg.grounds.player.presence

import java.util.UUID

interface PlayerPresenceClient : AutoCloseable {
    fun tryLogin(playerId: UUID): PlayerLoginResult

    fun logout(playerId: UUID): PlayerLogoutResult

    override fun close()
}
