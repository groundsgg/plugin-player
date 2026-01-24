package gg.grounds.permissions.commands

import com.velocitypowered.api.command.CommandSource
import gg.grounds.grpc.permissions.AddGroupPermissionsRequest
import gg.grounds.grpc.permissions.CreateGroupRequest
import gg.grounds.grpc.permissions.DeleteGroupRequest
import gg.grounds.grpc.permissions.GetGroupRequest
import gg.grounds.grpc.permissions.ListGroupsRequest
import gg.grounds.grpc.permissions.PermissionGrant
import gg.grounds.grpc.permissions.RemoveGroupPermissionsRequest
import net.kyori.adventure.text.Component

class PermissionsGroupCommandHandler(private val context: PermissionsCommandContext) {
    fun create(source: CommandSource, groupName: String): Boolean {
        if (!PermissionsCommandAccess.isAdmin(source)) {
            source.sendMessage(Component.text("You do not have permission to manage groups."))
            return false
        }
        val reply =
            context.permissionsAdminService.createGroup(
                CreateGroupRequest.newBuilder().setGroupName(groupName).build()
            )
                ?: run {
                    source.sendMessage(Component.text("Failed to create group."))
                    return false
                }
        source.sendMessage(
            Component.text("Create group '$groupName' result: ${reply.applyResult.name}")
        )
        return true
    }

    fun list(source: CommandSource): Boolean {
        if (!PermissionsCommandAccess.isAdmin(source)) {
            source.sendMessage(Component.text("You do not have permission to manage groups."))
            return false
        }
        val reply =
            context.permissionsAdminService.listGroups(
                ListGroupsRequest.newBuilder().setIncludePermissions(false).build()
            )
                ?: run {
                    source.sendMessage(Component.text("Failed to list groups."))
                    return false
                }
        val groups =
            reply.groupsList.map { it.groupName }.sorted().takeIf { it.isNotEmpty() }
                ?: listOf("<none>")
        source.sendMessage(Component.text("Groups: ${groups.joinToString(", ")}"))
        return true
    }

    fun info(source: CommandSource, groupName: String): Boolean {
        if (!PermissionsCommandAccess.isAdmin(source)) {
            source.sendMessage(Component.text("You do not have permission to manage groups."))
            return false
        }
        val reply =
            context.permissionsAdminService.getGroup(
                GetGroupRequest.newBuilder().setGroupName(groupName).build()
            )
                ?: run {
                    source.sendMessage(Component.text("Failed to load group."))
                    return false
                }
        if (!reply.hasGroup()) {
            source.sendMessage(Component.text("Group '$groupName' not found."))
            return false
        }
        val group = reply.group
        val permissions =
            group.permissionGrantsList.map { it.permission }.sorted().takeIf { it.isNotEmpty() }
                ?: listOf("<none>")
        source.sendMessage(
            Component.text(
                "Group ${group.groupName} info: permissions=${permissions.joinToString(", ")}"
            )
        )
        return true
    }

    fun delete(source: CommandSource, groupName: String): Boolean {
        if (!PermissionsCommandAccess.isAdmin(source)) {
            source.sendMessage(Component.text("You do not have permission to manage groups."))
            return false
        }
        val reply =
            context.permissionsAdminService.deleteGroup(
                DeleteGroupRequest.newBuilder().setGroupName(groupName).build()
            )
                ?: run {
                    source.sendMessage(Component.text("Failed to delete group."))
                    return false
                }
        source.sendMessage(
            Component.text("Delete group '$groupName' result: ${reply.applyResult.name}")
        )
        context.refreshOnlinePlayers()
        return true
    }

    fun permissionAdd(
        source: CommandSource,
        groupName: String,
        permission: String,
        durationArg: String?,
    ): Boolean {
        if (!PermissionsCommandAccess.isAdmin(source)) {
            source.sendMessage(Component.text("You do not have permission to manage groups."))
            return false
        }
        if (permission.isBlank()) {
            source.sendMessage(Component.text("Group permission must be provided."))
            return false
        }
        val expiresAt = context.parseExpiryOrReport(source, durationArg)
        if (durationArg != null && expiresAt == null) {
            return false
        }
        val grantBuilder = PermissionGrant.newBuilder().setPermission(permission)
        expiresAt?.let { grantBuilder.setExpiresAt(it) }
        val reply =
            context.permissionsAdminService.addGroupPermissions(
                AddGroupPermissionsRequest.newBuilder()
                    .setGroupName(groupName)
                    .addPermissionGrants(grantBuilder.build())
                    .build()
            )
                ?: run {
                    source.sendMessage(Component.text("Failed to add group permission."))
                    return false
                }
        source.sendMessage(
            Component.text(
                "Add permission '$permission' to group '$groupName' result: ${reply.applyResult.name}"
            )
        )
        context.refreshOnlinePlayers()
        return true
    }

    fun permissionRemove(source: CommandSource, groupName: String, permission: String): Boolean {
        if (!PermissionsCommandAccess.isAdmin(source)) {
            source.sendMessage(Component.text("You do not have permission to manage groups."))
            return false
        }
        if (permission.isBlank()) {
            source.sendMessage(Component.text("Group permission must be provided."))
            return false
        }
        val reply =
            context.permissionsAdminService.removeGroupPermissions(
                RemoveGroupPermissionsRequest.newBuilder()
                    .setGroupName(groupName)
                    .addPermissions(permission)
                    .build()
            )
                ?: run {
                    source.sendMessage(Component.text("Failed to remove group permission."))
                    return false
                }
        source.sendMessage(
            Component.text(
                "Remove permission '$permission' from group '$groupName' result: ${reply.applyResult.name}"
            )
        )
        context.refreshOnlinePlayers()
        return true
    }
}
