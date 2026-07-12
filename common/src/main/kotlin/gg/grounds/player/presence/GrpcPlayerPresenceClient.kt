package gg.grounds.player.presence

import gg.grounds.grpc.player.GetPlayerSessionRequest
import gg.grounds.grpc.player.PlayerHeartbeatBatchReply
import gg.grounds.grpc.player.PlayerHeartbeatBatchRequest
import gg.grounds.grpc.player.PlayerLoginRequest
import gg.grounds.grpc.player.PlayerLogoutReply
import gg.grounds.grpc.player.PlayerLogoutRequest
import gg.grounds.grpc.player.PlayerPresenceServiceGrpc
import gg.grounds.grpc.player.PlayerSessionInfo
import gg.grounds.grpc.player.ResolvePlayerNameRequest
import gg.grounds.grpc.player.SuggestPlayerNamesRequest
import gg.grounds.grpc.player.UpdatePlayerServerRequest
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.util.UUID
import java.util.concurrent.TimeUnit

class GrpcPlayerPresenceClient
private constructor(
    private val channel: ManagedChannel,
    private val stub: PlayerPresenceServiceGrpc.PlayerPresenceServiceBlockingStub,
) : AutoCloseable {
    fun tryLogin(playerId: UUID, playerName: String = "", proxyId: String = ""): PlayerLoginResult {
        return try {
            val reply =
                stub
                    .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .tryPlayerLogin(
                        PlayerLoginRequest.newBuilder()
                            .setPlayerId(playerId.toString())
                            .setPlayerName(playerName)
                            .setProxyId(proxyId)
                            .build()
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

    fun heartbeatBatch(playerIds: Collection<UUID>): PlayerHeartbeatBatchReply {
        return try {
            val request =
                PlayerHeartbeatBatchRequest.newBuilder()
                    .addAllPlayerIds(playerIds.map { it.toString() })
                    .build()
            stub
                .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .playerHeartbeatBatch(request)
        } catch (e: StatusRuntimeException) {
            errorHeartbeatBatchReply(e.status.toString())
        } catch (e: RuntimeException) {
            errorHeartbeatBatchReply(e.message ?: e::class.java.name)
        }
    }

    /**
     * The lookups below back cross-proxy features, and they run on the command path (`/msg`,
     * tab-complete). A failure means "I don't know", never an exception into Velocity's event loop
     * — the caller then falls back to what it can see locally.
     */
    fun getSession(playerId: UUID): PlayerSessionInfo? {
        return try {
            val reply =
                stub
                    .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .getPlayerSession(
                        GetPlayerSessionRequest.newBuilder()
                            .setPlayerId(playerId.toString())
                            .build()
                    )
            if (reply.found) reply.session else null
        } catch (e: RuntimeException) {
            null
        }
    }

    fun resolveName(playerName: String): PlayerSessionInfo? {
        return try {
            val reply =
                stub
                    .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .resolvePlayerName(
                        ResolvePlayerNameRequest.newBuilder().setPlayerName(playerName).build()
                    )
            if (reply.found) reply.session else null
        } catch (e: RuntimeException) {
            null
        }
    }

    fun suggestNames(prefix: String, limit: Int): List<String> {
        return try {
            stub
                .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .suggestPlayerNames(
                    SuggestPlayerNamesRequest.newBuilder().setPrefix(prefix).setLimit(limit).build()
                )
                .playerNamesList
        } catch (e: RuntimeException) {
            emptyList()
        }
    }

    fun updateServer(playerId: UUID, serverName: String): Boolean {
        return try {
            stub
                .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .updatePlayerServer(
                    UpdatePlayerServerRequest.newBuilder()
                        .setPlayerId(playerId.toString())
                        .setServerName(serverName)
                        .build()
                )
                .updated
        } catch (e: RuntimeException) {
            false
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
        fun create(target: String): GrpcPlayerPresenceClient {
            val channelBuilder = ManagedChannelBuilder.forTarget(target)
            channelBuilder.usePlaintext()
            val channel = channelBuilder.build()
            val stub = PlayerPresenceServiceGrpc.newBlockingStub(channel)
            return GrpcPlayerPresenceClient(channel, stub)
        }

        private fun errorLogoutReply(message: String): PlayerLogoutReply =
            PlayerLogoutReply.newBuilder().setRemoved(false).setMessage(message).build()

        private fun errorHeartbeatBatchReply(message: String): PlayerHeartbeatBatchReply =
            PlayerHeartbeatBatchReply.newBuilder()
                .setUpdated(0)
                .setMissing(0)
                .setSuccess(false)
                .setMessage(message)
                .build()

        private fun isServiceUnavailable(status: Status.Code): Boolean =
            status == Status.Code.UNAVAILABLE || status == Status.Code.DEADLINE_EXCEEDED

        private const val DEFAULT_TIMEOUT_MS = 2000L
    }
}
