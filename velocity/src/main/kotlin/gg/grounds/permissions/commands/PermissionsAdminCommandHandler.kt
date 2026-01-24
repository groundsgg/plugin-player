package gg.grounds.permissions.commands

import com.velocitypowered.api.command.CommandSource
import net.kyori.adventure.text.Component

class PermissionsAdminCommandHandler(private val context: PermissionsCommandContext) {
    fun refreshAll(source: CommandSource): Boolean {
        if (!PermissionsCommandAccess.isAdmin(source)) {
            source.sendMessage(Component.text("You do not have permission to refresh all players."))
            return false
        }
        context.refreshOnlinePlayers()
        source.sendMessage(Component.text("Permissions refreshed for online players."))
        return true
    }
}
