package gg.grounds.player.presence.grpc

import gg.grounds.grpc.player.LoginStatus
import gg.grounds.grpc.player.PlayerLoginRequest
import gg.grounds.grpc.player.PlayerLogoutRequest
import gg.grounds.grpc.player.PlayerPresenceServiceGrpc
import gg.grounds.player.presence.PlayerLoginResult
import gg.grounds.player.presence.PlayerLoginStatus
import gg.grounds.player.presence.PlayerLogoutResult
import gg.grounds.player.presence.PlayerPresenceClient
import gg.grounds.player.presence.PlayerPresenceClientConfig
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import java.util.UUID
import java.util.concurrent.TimeUnit

class GrpcPlayerPresenceClient
private constructor(
    private val channel: ManagedChannel,
    private val stub: PlayerPresenceServiceGrpc.PlayerPresenceServiceBlockingStub,
    private val config: PlayerPresenceClientConfig,
) : PlayerPresenceClient {
    override fun tryLogin(playerId: UUID): PlayerLoginResult {
        return try {
            val reply =
                stub
                    .withDeadlineAfter(config.timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .tryPlayerLogin(
                        PlayerLoginRequest.newBuilder().setPlayerId(playerId.toString()).build()
                    )

            PlayerLoginResult(status = map(reply.status), message = reply.message)
        } catch (e: StatusRuntimeException) {
            PlayerLoginResult(PlayerLoginStatus.ERROR, e.status.toString())
        } catch (e: RuntimeException) {
            PlayerLoginResult(PlayerLoginStatus.ERROR, e.message ?: e::class.java.name)
        }
    }

    override fun logout(playerId: UUID): PlayerLogoutResult {
        return try {
            val reply =
                stub
                    .withDeadlineAfter(config.timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .playerLogout(
                        PlayerLogoutRequest.newBuilder().setPlayerId(playerId.toString()).build()
                    )
            PlayerLogoutResult(removed = reply.removed, message = reply.message)
        } catch (e: StatusRuntimeException) {
            PlayerLogoutResult(false, e.status.toString())
        } catch (e: RuntimeException) {
            PlayerLogoutResult(false, e.message ?: e::class.java.name)
        }
    }

    override fun close() {
        channel.shutdown()
        try {
            if (!channel.awaitTermination(3, TimeUnit.SECONDS)) {
                channel.shutdownNow()
                channel.awaitTermination(3, TimeUnit.SECONDS)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            channel.shutdownNow()
        }
    }

    companion object {
        fun create(config: PlayerPresenceClientConfig): GrpcPlayerPresenceClient {
            val channelBuilder = ManagedChannelBuilder.forTarget(config.target)
            if (config.plaintext) {
                channelBuilder.usePlaintext()
            }
            val channel = channelBuilder.build()
            val stub = PlayerPresenceServiceGrpc.newBlockingStub(channel)
            return GrpcPlayerPresenceClient(channel, stub, config)
        }

        private fun map(status: LoginStatus): PlayerLoginStatus {
            return when (status) {
                LoginStatus.LOGIN_STATUS_ACCEPTED -> PlayerLoginStatus.ACCEPTED
                LoginStatus.LOGIN_STATUS_ALREADY_ONLINE -> PlayerLoginStatus.ALREADY_ONLINE
                LoginStatus.LOGIN_STATUS_INVALID_REQUEST -> PlayerLoginStatus.INVALID_REQUEST
                LoginStatus.LOGIN_STATUS_ERROR -> PlayerLoginStatus.ERROR
                LoginStatus.LOGIN_STATUS_UNSPECIFIED,
                LoginStatus.UNRECOGNIZED -> PlayerLoginStatus.UNSPECIFIED
            }
        }
    }
}
