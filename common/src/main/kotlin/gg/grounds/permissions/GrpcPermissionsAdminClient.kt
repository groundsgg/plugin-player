package gg.grounds.permissions

import gg.grounds.grpc.BaseGrpcClient
import gg.grounds.grpc.permissions.AddGroupPermissionsReply
import gg.grounds.grpc.permissions.AddGroupPermissionsRequest
import gg.grounds.grpc.permissions.AddPlayerGroupsReply
import gg.grounds.grpc.permissions.AddPlayerGroupsRequest
import gg.grounds.grpc.permissions.AddPlayerPermissionsReply
import gg.grounds.grpc.permissions.AddPlayerPermissionsRequest
import gg.grounds.grpc.permissions.CreateGroupReply
import gg.grounds.grpc.permissions.CreateGroupRequest
import gg.grounds.grpc.permissions.DeleteGroupReply
import gg.grounds.grpc.permissions.DeleteGroupRequest
import gg.grounds.grpc.permissions.GetGroupReply
import gg.grounds.grpc.permissions.GetGroupRequest
import gg.grounds.grpc.permissions.ListGroupsReply
import gg.grounds.grpc.permissions.ListGroupsRequest
import gg.grounds.grpc.permissions.PermissionsAdminServiceGrpc
import gg.grounds.grpc.permissions.RemoveGroupPermissionsReply
import gg.grounds.grpc.permissions.RemoveGroupPermissionsRequest
import gg.grounds.grpc.permissions.RemovePlayerGroupsReply
import gg.grounds.grpc.permissions.RemovePlayerGroupsRequest
import gg.grounds.grpc.permissions.RemovePlayerPermissionsReply
import gg.grounds.grpc.permissions.RemovePlayerPermissionsRequest
import io.grpc.ManagedChannel
import java.util.concurrent.TimeUnit

class GrpcPermissionsAdminClient
private constructor(
    channel: ManagedChannel,
    private val stub: PermissionsAdminServiceGrpc.PermissionsAdminServiceBlockingStub,
) : BaseGrpcClient(channel) {
    fun createGroup(request: CreateGroupRequest): CreateGroupReply =
        stub.withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS).createGroup(request)

    fun deleteGroup(request: DeleteGroupRequest): DeleteGroupReply =
        stub.withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS).deleteGroup(request)

    fun getGroup(request: GetGroupRequest): GetGroupReply =
        stub.withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS).getGroup(request)

    fun listGroups(request: ListGroupsRequest): ListGroupsReply =
        stub.withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS).listGroups(request)

    fun addGroupPermissions(request: AddGroupPermissionsRequest): AddGroupPermissionsReply =
        stub
            .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .addGroupPermissions(request)

    fun removeGroupPermissions(
        request: RemoveGroupPermissionsRequest
    ): RemoveGroupPermissionsReply =
        stub
            .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .removeGroupPermissions(request)

    fun addPlayerPermissions(request: AddPlayerPermissionsRequest): AddPlayerPermissionsReply =
        stub
            .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .addPlayerPermissions(request)

    fun removePlayerPermissions(
        request: RemovePlayerPermissionsRequest
    ): RemovePlayerPermissionsReply =
        stub
            .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .removePlayerPermissions(request)

    fun addPlayerGroups(request: AddPlayerGroupsRequest): AddPlayerGroupsReply =
        stub.withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS).addPlayerGroups(request)

    fun removePlayerGroups(request: RemovePlayerGroupsRequest): RemovePlayerGroupsReply =
        stub
            .withDeadlineAfter(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .removePlayerGroups(request)

    companion object {
        fun create(target: String): GrpcPermissionsAdminClient {
            val channel = createChannel(target)
            val stub = PermissionsAdminServiceGrpc.newBlockingStub(channel)
            return GrpcPermissionsAdminClient(channel, stub)
        }

        private const val DEFAULT_TIMEOUT_MS = 2000L
    }
}
