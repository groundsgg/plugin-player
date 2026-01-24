package gg.grounds.permissions.commands

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import java.util.UUID

object PermissionsCommandAccess {
    private const val ADMIN_PERMISSION = "grounds.permissions.admin"

    fun isAdmin(source: CommandSource): Boolean {
        return source !is Player || source.hasPermission(ADMIN_PERMISSION)
    }

    fun canAccessPlayer(source: CommandSource, playerId: UUID): Boolean {
        return isAdmin(source) || (source is Player && source.uniqueId == playerId)
    }
}
