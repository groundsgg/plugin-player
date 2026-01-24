package gg.grounds.permissions

import gg.grounds.grpc.BaseGrpcClient
import gg.grounds.grpc.permissions.CheckPlayerPermissionReply
import gg.grounds.grpc.permissions.CheckPlayerPermissionRequest
import gg.grounds.grpc.permissions.GetPlayerPermissionsReply
import gg.grounds.grpc.permissions.GetPlayerPermissionsRequest
import gg.grounds.grpc.permissions.PermissionsServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.util.UUID
import java.util.concurrent.TimeUnit

class GrpcPermissionsClient
private constructor(
    channel: ManagedChannel,
    private val stub: PermissionsServiceGrpc.PermissionsServiceBlockingStub,
) : BaseGrpcClient(channel) {
    fun getPlayerPermissions(
        playerId: UUID,
        includeEffectivePermissions: Boolean = true,
        includeDirectPermissions: Boolean = true,
        includeGroups: Boolean = true,
    ): GetPlayerPermissionsReply {
        return stub
            .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .getPlayerPermissions(
                GetPlayerPermissionsRequest.newBuilder()
                    .setPlayerId(playerId.toString())
                    .setIncludeEffectivePermissions(includeEffectivePermissions)
                    .setIncludeDirectPermissions(includeDirectPermissions)
                    .setIncludeGroups(includeGroups)
                    .build()
            )
    }

    fun checkPlayerPermission(playerId: UUID, permission: String): CheckPlayerPermissionReply {
        return stub
            .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .checkPlayerPermission(
                CheckPlayerPermissionRequest.newBuilder()
                    .setPlayerId(playerId.toString())
                    .setPermission(permission)
                    .build()
            )
    }

    companion object {
        fun create(target: String): GrpcPermissionsClient {
            val channel = createChannel(target)
            val stub = PermissionsServiceGrpc.newBlockingStub(channel)
            return GrpcPermissionsClient(channel, stub)
        }

        fun isServiceUnavailable(status: Status.Code): Boolean =
            status == Status.Code.UNAVAILABLE || status == Status.Code.DEADLINE_EXCEEDED

        fun isServiceUnavailable(error: StatusRuntimeException): Boolean =
            isServiceUnavailable(error.status.code)

        private const val DEFAULT_TIMEOUT_MS = 2000L
    }
}
