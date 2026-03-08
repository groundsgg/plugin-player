package gg.grounds.permissions.commands

import com.velocitypowered.api.command.CommandSource
import gg.grounds.grpc.permissions.AddPlayerGroupsRequest
import gg.grounds.grpc.permissions.AddPlayerPermissionsRequest
import gg.grounds.grpc.permissions.PermissionGrant
import gg.grounds.grpc.permissions.PlayerGroupMembership
import gg.grounds.grpc.permissions.RemovePlayerGroupsRequest
import gg.grounds.grpc.permissions.RemovePlayerPermissionsRequest
import java.util.UUID
import net.kyori.adventure.text.Component

class PermissionsPlayerCommandHandler(private val context: PermissionsCommandContext) {
    fun info(source: CommandSource, playerId: UUID): Boolean {
        if (!PermissionsCommandAccess.canAccessPlayer(source, playerId)) {
            source.sendMessage(Component.text("You do not have permission to list other players."))
            return false
        }

        val cached = context.getOrRefreshCached(source, playerId) ?: return false
        val directPermissions =
            cached.directPermissions.map { it.permission }.sorted().takeIf { it.isNotEmpty() }
                ?: listOf("<none>")
        val groups =
            cached.groupMemberships.map { it.groupName }.sorted().takeIf { it.isNotEmpty() }
                ?: listOf("<none>")
        val effectivePermissions =
            cached.effectivePermissions.sorted().takeIf { it.isNotEmpty() } ?: listOf("<none>")
        source.sendMessage(
            Component.text(
                "Player $playerId info: groups=${groups.joinToString(", ")}, " +
                    "direct=${directPermissions.joinToString(", ")}, " +
                    "effective=${effectivePermissions.joinToString(", ")}"
            )
        )
        return true
    }

    fun check(source: CommandSource, playerId: UUID, permission: String): Boolean {
        if (!PermissionsCommandAccess.canAccessPlayer(source, playerId)) {
            source.sendMessage(Component.text("You do not have permission to check other players."))
            return false
        }
        if (permission.isBlank()) {
            source.sendMessage(Component.text("Permission must be provided."))
            return false
        }
        val allowed =
            context.permissionsCache.checkCached(playerId, permission)
                ?: context.permissionsService.checkPlayerPermission(playerId, permission)
                ?: run {
                    source.sendMessage(Component.text("Failed to check permission."))
                    return false
                }
        source.sendMessage(
            Component.text(
                "Permission '$permission' for $playerId: ${if (allowed) "allowed" else "denied"}"
            )
        )
        return true
    }

    fun refresh(source: CommandSource, playerId: UUID): Boolean {
        if (!PermissionsCommandAccess.canAccessPlayer(source, playerId)) {
            source.sendMessage(
                Component.text("You do not have permission to refresh other players.")
            )
            return false
        }

        if (context.refreshPlayer(playerId)) {
            source.sendMessage(Component.text("Permissions refreshed for $playerId."))
            return true
        } else {
            source.sendMessage(Component.text("Failed to refresh permissions for $playerId."))
            return false
        }
    }

    fun permissionAdd(
        source: CommandSource,
        playerId: UUID,
        permission: String,
        durationArg: String?,
    ): Boolean {
        if (!PermissionsCommandAccess.isAdmin(source)) {
            source.sendMessage(Component.text("You do not have permission to manage players."))
            return false
        }
        if (permission.isBlank()) {
            source.sendMessage(Component.text("Permission must be provided."))
            return false
        }
        val expiresAt = context.parseExpiryOrReport(source, durationArg)
        if (durationArg != null && expiresAt == null) {
            return false
        }
        val grantBuilder = PermissionGrant.newBuilder().setPermission(permission)
        expiresAt?.let { grantBuilder.setExpiresAt(it) }
        val reply =
            context.permissionsAdminService.addPlayerPermissions(
                AddPlayerPermissionsRequest.newBuilder()
                    .setPlayerId(playerId.toString())
                    .addPermissionGrants(grantBuilder.build())
                    .build()
            )
                ?: run {
                    source.sendMessage(Component.text("Failed to add player permission."))
                    return false
                }
        source.sendMessage(
            Component.text(
                "Add permission '$permission' to player $playerId result: ${reply.applyResult.name}"
            )
        )
        context.refreshPlayer(playerId)
        return true
    }

    fun permissionRemove(source: CommandSource, playerId: UUID, permission: String): Boolean {
        if (!PermissionsCommandAccess.isAdmin(source)) {
            source.sendMessage(Component.text("You do not have permission to manage players."))
            return false
        }
        if (permission.isBlank()) {
            source.sendMessage(Component.text("Permission must be provided."))
            return false
        }
        val reply =
            context.permissionsAdminService.removePlayerPermissions(
                RemovePlayerPermissionsRequest.newBuilder()
                    .setPlayerId(playerId.toString())
                    .addPermissions(permission)
                    .build()
            )
                ?: run {
                    source.sendMessage(Component.text("Failed to remove player permission."))
                    return false
                }
        source.sendMessage(
            Component.text(
                "Remove permission '$permission' from player $playerId result: ${reply.applyResult.name}"
            )
        )
        context.refreshPlayer(playerId)
        return true
    }

    fun groupAdd(
        source: CommandSource,
        playerId: UUID,
        groupName: String,
        durationArg: String?,
    ): Boolean {
        if (!PermissionsCommandAccess.isAdmin(source)) {
            source.sendMessage(Component.text("You do not have permission to manage players."))
            return false
        }
        if (groupName.isBlank()) {
            source.sendMessage(Component.text("Group name must be provided."))
            return false
        }
        val expiresAt = context.parseExpiryOrReport(source, durationArg)
        if (durationArg != null && expiresAt == null) {
            return false
        }
        val membershipBuilder = PlayerGroupMembership.newBuilder().setGroupName(groupName)
        expiresAt?.let { membershipBuilder.setExpiresAt(it) }
        val reply =
            context.permissionsAdminService.addPlayerGroups(
                AddPlayerGroupsRequest.newBuilder()
                    .setPlayerId(playerId.toString())
                    .addGroupMemberships(membershipBuilder.build())
                    .build()
            )
                ?: run {
                    source.sendMessage(Component.text("Failed to add player group."))
                    return false
                }
        source.sendMessage(
            Component.text(
                "Add group '$groupName' to player $playerId result: ${reply.applyResult.name}"
            )
        )
        context.refreshPlayer(playerId)
        return true
    }

    fun groupRemove(source: CommandSource, playerId: UUID, groupName: String): Boolean {
        if (!PermissionsCommandAccess.isAdmin(source)) {
            source.sendMessage(Component.text("You do not have permission to manage players."))
            return false
        }
        if (groupName.isBlank()) {
            source.sendMessage(Component.text("Group name must be provided."))
            return false
        }
        val reply =
            context.permissionsAdminService.removePlayerGroups(
                RemovePlayerGroupsRequest.newBuilder()
                    .setPlayerId(playerId.toString())
                    .addGroupNames(groupName)
                    .build()
            )
                ?: run {
                    source.sendMessage(Component.text("Failed to remove player group."))
                    return false
                }
        source.sendMessage(
            Component.text(
                "Remove group '$groupName' from player $playerId result: ${reply.applyResult.name}"
            )
        )
        context.refreshPlayer(playerId)
        return true
    }
}
