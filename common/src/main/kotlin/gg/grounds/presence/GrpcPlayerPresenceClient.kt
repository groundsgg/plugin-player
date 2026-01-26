package gg.grounds.presence

import gg.grounds.grpc.BaseGrpcClient
import gg.grounds.grpc.player.PlayerLoginRequest
import gg.grounds.grpc.player.PlayerLogoutReply
import gg.grounds.grpc.player.PlayerLogoutRequest
import gg.grounds.grpc.player.PlayerPresenceServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.util.UUID
import java.util.concurrent.TimeUnit

class GrpcPlayerPresenceClient
private constructor(
    channel: ManagedChannel,
    private val stub: PlayerPresenceServiceGrpc.PlayerPresenceServiceBlockingStub,
) : BaseGrpcClient(channel) {
    fun tryLogin(playerId: UUID): PlayerLoginResult {
        return try {
            val reply =
                stub
                    .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .tryPlayerLogin(
                        PlayerLoginRequest.newBuilder().setPlayerId(playerId.toString()).build()
                    )
            PlayerLoginResult.Success(reply)
        } catch (e: StatusRuntimeException) {
            if (isServiceUnavailable(e.status.code)) {
                return PlayerLoginResult.Unavailable(e.status.toString())
            }
            PlayerLoginResult.Error(e.status.toString())
        } catch (e: RuntimeException) {
            PlayerLoginResult.Error(e.message ?: e::class.java.name)
        }
    }

    fun logout(playerId: UUID): PlayerLogoutReply {
        return try {
            stub
                .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .playerLogout(
                    PlayerLogoutRequest.newBuilder().setPlayerId(playerId.toString()).build()
                )
        } catch (e: StatusRuntimeException) {
            errorLogoutReply(e.status.toString())
        } catch (e: RuntimeException) {
            errorLogoutReply(e.message ?: e::class.java.name)
        }
    }

    companion object {
        fun create(target: String): GrpcPlayerPresenceClient {
            val channel = createChannel(target)
            val stub = PlayerPresenceServiceGrpc.newBlockingStub(channel)
            return GrpcPlayerPresenceClient(channel, stub)
        }

        private fun errorLogoutReply(message: String): PlayerLogoutReply =
            PlayerLogoutReply.newBuilder().setRemoved(false).setMessage(message).build()

        private fun isServiceUnavailable(status: Status.Code): Boolean =
            status == Status.Code.UNAVAILABLE || status == Status.Code.DEADLINE_EXCEEDED

        private const val DEFAULT_TIMEOUT_MS = 2000L
    }
}
