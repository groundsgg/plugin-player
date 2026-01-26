package gg.grounds.permissions.services

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
import gg.grounds.grpc.permissions.RemoveGroupPermissionsReply
import gg.grounds.grpc.permissions.RemoveGroupPermissionsRequest
import gg.grounds.grpc.permissions.RemovePlayerGroupsReply
import gg.grounds.grpc.permissions.RemovePlayerGroupsRequest
import gg.grounds.grpc.permissions.RemovePlayerPermissionsReply
import gg.grounds.grpc.permissions.RemovePlayerPermissionsRequest
import gg.grounds.permissions.GrpcPermissionsAdminClient
import io.grpc.StatusRuntimeException
import org.slf4j.Logger

class PermissionsAdminService(private val logger: Logger) : AutoCloseable {
    private lateinit var client: GrpcPermissionsAdminClient

    fun configure(target: String) {
        close()
        client = GrpcPermissionsAdminClient.create(target)
    }

    fun createGroup(request: CreateGroupRequest): CreateGroupReply? =
        call("create group") { client.createGroup(request) }

    fun deleteGroup(request: DeleteGroupRequest): DeleteGroupReply? =
        call("delete group") { client.deleteGroup(request) }

    fun getGroup(request: GetGroupRequest): GetGroupReply? =
        call("get group") { client.getGroup(request) }

    fun listGroups(request: ListGroupsRequest): ListGroupsReply? =
        call("list groups") { client.listGroups(request) }

    fun addGroupPermissions(request: AddGroupPermissionsRequest): AddGroupPermissionsReply? =
        call("add group permissions") { client.addGroupPermissions(request) }

    fun removeGroupPermissions(
        request: RemoveGroupPermissionsRequest
    ): RemoveGroupPermissionsReply? =
        call("remove group permissions") { client.removeGroupPermissions(request) }

    fun addPlayerPermissions(request: AddPlayerPermissionsRequest): AddPlayerPermissionsReply? =
        call("add player permissions") { client.addPlayerPermissions(request) }

    fun removePlayerPermissions(
        request: RemovePlayerPermissionsRequest
    ): RemovePlayerPermissionsReply? =
        call("remove player permissions") { client.removePlayerPermissions(request) }

    fun addPlayerGroups(request: AddPlayerGroupsRequest): AddPlayerGroupsReply? =
        call("add player groups") { client.addPlayerGroups(request) }

    fun removePlayerGroups(request: RemovePlayerGroupsRequest): RemovePlayerGroupsReply? =
        call("remove player groups") { client.removePlayerGroups(request) }

    override fun close() {
        if (this::client.isInitialized) {
            client.close()
        }
    }

    private fun <T> call(action: String, block: () -> T): T? {
        return try {
            block()
        } catch (e: StatusRuntimeException) {
            logFailure(action, e.status.toString())
            null
        } catch (e: RuntimeException) {
            logFailure(action, e.message ?: e::class.java.name)
            null
        }
    }

    private fun logFailure(action: String, reason: String) {
        logger.warn("Permissions admin service failed (action={}, reason={})", action, reason)
    }
}
