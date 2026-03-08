package gg.grounds.permissions.commands

import com.velocitypowered.api.command.CommandSource
import net.kyori.adventure.text.Component

object PermissionsCommandMessages {
    fun sendUsage(source: CommandSource) {
        source.sendMessage(
            Component.text(
                "Usage: /permissions help | " +
                    "/permissions refresh [player|uuid] | " +
                    "/permissions player <player|uuid> <info|check|refresh> [permission] | " +
                    "/permissions player <player|uuid> permission <add|remove> <permission> [duration] | " +
                    "/permissions player <player|uuid> group <add|remove> <group> [duration] | " +
                    "/permissions group list | " +
                    "/permissions group <group> <create|info|delete> | " +
                    "/permissions group <group> permission <add|remove> <permission> [duration]"
            )
        )
    }

    fun sendHelp(source: CommandSource) {
        val message =
            Component.text("Permissions commands:")
                .append(Component.newline())
                .append(Component.text("/permissions help - Show this help."))
                .append(Component.newline())
                .append(
                    Component.text(
                        "/permissions refresh [player|uuid] - Refresh cached permissions."
                    )
                )
                .append(Component.newline())
                .append(
                    Component.text("/permissions player <player|uuid> info - Show player info.")
                )
                .append(Component.newline())
                .append(
                    Component.text(
                        "/permissions player <player|uuid> check <permission> - Check a permission."
                    )
                )
                .append(Component.newline())
                .append(
                    Component.text(
                        "/permissions player <player|uuid> refresh - Refresh a player's cache."
                    )
                )
                .append(Component.newline())
                .append(
                    Component.text(
                        "/permissions player <player|uuid> permission add <permission> [duration] - Add a player permission."
                    )
                )
                .append(Component.newline())
                .append(
                    Component.text(
                        "/permissions player <player|uuid> permission remove <permission> - Remove a player permission."
                    )
                )
                .append(Component.newline())
                .append(
                    Component.text(
                        "/permissions player <player|uuid> group add <group> [duration] - Add a player group."
                    )
                )
                .append(Component.newline())
                .append(
                    Component.text(
                        "/permissions player <player|uuid> group remove <group> - Remove a player group."
                    )
                )
                .append(Component.newline())
                .append(Component.text("/permissions group list - List available groups."))
                .append(Component.newline())
                .append(Component.text("/permissions group <group> create - Create a group."))
                .append(Component.newline())
                .append(Component.text("/permissions group <group> info - Show group info."))
                .append(Component.newline())
                .append(Component.text("/permissions group <group> delete - Delete a group."))
                .append(Component.newline())
                .append(
                    Component.text(
                        "/permissions group <group> permission add <permission> [duration] - Add a group permission."
                    )
                )
                .append(Component.newline())
                .append(
                    Component.text(
                        "/permissions group <group> permission remove <permission> - Remove a group permission."
                    )
                )
                .append(Component.newline())
                .append(Component.text("Durations: 30m, 1h, 7d, 2w (s/m/h/d/w)."))
        source.sendMessage(message)
    }
}
