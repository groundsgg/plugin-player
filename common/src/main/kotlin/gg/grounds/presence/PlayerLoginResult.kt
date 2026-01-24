package gg.grounds.presence

import gg.grounds.grpc.player.PlayerLoginReply

sealed class PlayerLoginResult {
    data class Success(val reply: PlayerLoginReply) : PlayerLoginResult()

    data class Unavailable(val message: String) : PlayerLoginResult()

    data class Error(val message: String) : PlayerLoginResult()
}
