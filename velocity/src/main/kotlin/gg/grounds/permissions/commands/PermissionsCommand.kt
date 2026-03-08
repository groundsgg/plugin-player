package gg.grounds.permissions.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.permissions.PermissionsCache
import gg.grounds.permissions.services.PermissionsAdminService
import gg.grounds.permissions.services.PermissionsService
import java.util.UUID
import net.kyori.adventure.text.Component

class PermissionsCommand(
    proxy: ProxyServer,
    permissionsCache: PermissionsCache,
    permissionsService: PermissionsService,
    permissionsAdminService: PermissionsAdminService,
) {
    private val context =
        PermissionsCommandContext(
            proxy,
            permissionsCache,
            permissionsService,
            permissionsAdminService,
        )
    private val adminHandler = PermissionsAdminCommandHandler(context)
    private val playerHandler = PermissionsPlayerCommandHandler(context)
    private val groupHandler = PermissionsGroupCommandHandler(context)
    private val suggestions = PermissionsSuggestions(proxy, permissionsAdminService)

    fun create(): BrigadierCommand {
        return BrigadierCommand(
            BrigadierCommand.literalArgumentBuilder("permissions")
                .executes { context ->
                    PermissionsCommandMessages.sendUsage(context.source)
                    Command.SINGLE_SUCCESS
                }
                .then(
                    BrigadierCommand.literalArgumentBuilder("help").executes { context ->
                        PermissionsCommandMessages.sendHelp(context.source)
                        Command.SINGLE_SUCCESS
                    }
                )
                .then(buildRefresh())
                .then(buildPlayer())
                .then(buildGroup())
        )
    }

    private fun buildRefresh() =
        BrigadierCommand.literalArgumentBuilder("refresh")
            .executes { context -> result(adminHandler.refreshAll(context.source)) }
            .then(
                BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                    .suggests(suggestions.player())
                    .executes { context ->
                        val playerId =
                            resolvePlayerIdOrSend(
                                context.source,
                                StringArgumentType.getString(context, PLAYER_ARGUMENT),
                            ) ?: return@executes 0
                        result(playerHandler.refresh(context.source, playerId))
                    }
            )

    private fun buildPlayer() =
        BrigadierCommand.literalArgumentBuilder("player")
            .then(
                BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                    .suggests(suggestions.player())
                    .then(
                        BrigadierCommand.literalArgumentBuilder("info").executes { context ->
                            val playerId = resolvePlayerIdOrSend(context) ?: return@executes 0
                            result(playerHandler.info(context.source, playerId))
                        }
                    )
                    .then(
                        BrigadierCommand.literalArgumentBuilder("check")
                            .then(
                                BrigadierCommand.requiredArgumentBuilder(
                                        "permission",
                                        StringArgumentType.word(),
                                    )
                                    .suggests(suggestions.permission())
                                    .executes { context ->
                                        val playerId =
                                            resolvePlayerIdOrSend(context) ?: return@executes 0
                                        val permission =
                                            StringArgumentType.getString(context, "permission")
                                        result(
                                            playerHandler.check(
                                                context.source,
                                                playerId,
                                                permission,
                                            )
                                        )
                                    }
                            )
                    )
                    .then(
                        BrigadierCommand.literalArgumentBuilder("refresh").executes { context ->
                            val playerId = resolvePlayerIdOrSend(context) ?: return@executes 0
                            result(playerHandler.refresh(context.source, playerId))
                        }
                    )
                    .then(
                        BrigadierCommand.literalArgumentBuilder("permission")
                            .requires { source -> PermissionsCommandAccess.isAdmin(source) }
                            .then(
                                BrigadierCommand.literalArgumentBuilder("add")
                                    .then(
                                        BrigadierCommand.requiredArgumentBuilder(
                                                "permission",
                                                StringArgumentType.word(),
                                            )
                                            .suggests(suggestions.permission())
                                            .executes { context ->
                                                val playerId =
                                                    resolvePlayerIdOrSend(context)
                                                        ?: return@executes 0
                                                val permission =
                                                    StringArgumentType.getString(
                                                        context,
                                                        "permission",
                                                    )
                                                result(
                                                    playerHandler.permissionAdd(
                                                        context.source,
                                                        playerId,
                                                        permission,
                                                        null,
                                                    )
                                                )
                                            }
                                            .then(
                                                BrigadierCommand.requiredArgumentBuilder(
                                                        "duration",
                                                        StringArgumentType.word(),
                                                    )
                                                    .suggests(suggestions.duration())
                                                    .executes { context ->
                                                        val playerId =
                                                            resolvePlayerIdOrSend(context)
                                                                ?: return@executes 0
                                                        val permission =
                                                            StringArgumentType.getString(
                                                                context,
                                                                "permission",
                                                            )
                                                        val duration =
                                                            StringArgumentType.getString(
                                                                context,
                                                                "duration",
                                                            )
                                                        result(
                                                            playerHandler.permissionAdd(
                                                                context.source,
                                                                playerId,
                                                                permission,
                                                                duration,
                                                            )
                                                        )
                                                    }
                                            )
                                    )
                            )
                            .then(
                                BrigadierCommand.literalArgumentBuilder("remove")
                                    .then(
                                        BrigadierCommand.requiredArgumentBuilder(
                                                "permission",
                                                StringArgumentType.word(),
                                            )
                                            .suggests(suggestions.permission())
                                            .executes { context ->
                                                val playerId =
                                                    resolvePlayerIdOrSend(context)
                                                        ?: return@executes 0
                                                val permission =
                                                    StringArgumentType.getString(
                                                        context,
                                                        "permission",
                                                    )
                                                result(
                                                    playerHandler.permissionRemove(
                                                        context.source,
                                                        playerId,
                                                        permission,
                                                    )
                                                )
                                            }
                                    )
                            )
                    )
                    .then(
                        BrigadierCommand.literalArgumentBuilder("group")
                            .requires { source -> PermissionsCommandAccess.isAdmin(source) }
                            .then(
                                BrigadierCommand.literalArgumentBuilder("add")
                                    .then(
                                        BrigadierCommand.requiredArgumentBuilder(
                                                "group",
                                                StringArgumentType.word(),
                                            )
                                            .suggests(suggestions.group())
                                            .executes { context ->
                                                val playerId =
                                                    resolvePlayerIdOrSend(context)
                                                        ?: return@executes 0
                                                val groupName =
                                                    StringArgumentType.getString(context, "group")
                                                result(
                                                    playerHandler.groupAdd(
                                                        context.source,
                                                        playerId,
                                                        groupName,
                                                        null,
                                                    )
                                                )
                                            }
                                            .then(
                                                BrigadierCommand.requiredArgumentBuilder(
                                                        "duration",
                                                        StringArgumentType.word(),
                                                    )
                                                    .suggests(suggestions.duration())
                                                    .executes { context ->
                                                        val playerId =
                                                            resolvePlayerIdOrSend(context)
                                                                ?: return@executes 0
                                                        val groupName =
                                                            StringArgumentType.getString(
                                                                context,
                                                                "group",
                                                            )
                                                        val duration =
                                                            StringArgumentType.getString(
                                                                context,
                                                                "duration",
                                                            )
                                                        result(
                                                            playerHandler.groupAdd(
                                                                context.source,
                                                                playerId,
                                                                groupName,
                                                                duration,
                                                            )
                                                        )
                                                    }
                                            )
                                    )
                            )
                            .then(
                                BrigadierCommand.literalArgumentBuilder("remove")
                                    .then(
                                        BrigadierCommand.requiredArgumentBuilder(
                                                "group",
                                                StringArgumentType.word(),
                                            )
                                            .suggests(suggestions.group())
                                            .executes { context ->
                                                val playerId =
                                                    resolvePlayerIdOrSend(context)
                                                        ?: return@executes 0
                                                val groupName =
                                                    StringArgumentType.getString(context, "group")
                                                result(
                                                    playerHandler.groupRemove(
                                                        context.source,
                                                        playerId,
                                                        groupName,
                                                    )
                                                )
                                            }
                                    )
                            )
                    )
            )

    private fun buildGroup() =
        BrigadierCommand.literalArgumentBuilder("group")
            .requires { source -> PermissionsCommandAccess.isAdmin(source) }
            .then(
                BrigadierCommand.literalArgumentBuilder("list").executes { context ->
                    result(groupHandler.list(context.source))
                }
            )
            .then(
                BrigadierCommand.requiredArgumentBuilder("group", StringArgumentType.word())
                    .suggests(suggestions.group())
                    .then(
                        BrigadierCommand.literalArgumentBuilder("create").executes { context ->
                            val groupName = StringArgumentType.getString(context, "group")
                            result(groupHandler.create(context.source, groupName))
                        }
                    )
                    .then(
                        BrigadierCommand.literalArgumentBuilder("info").executes { context ->
                            val groupName = StringArgumentType.getString(context, "group")
                            result(groupHandler.info(context.source, groupName))
                        }
                    )
                    .then(
                        BrigadierCommand.literalArgumentBuilder("delete").executes { context ->
                            val groupName = StringArgumentType.getString(context, "group")
                            result(groupHandler.delete(context.source, groupName))
                        }
                    )
                    .then(
                        BrigadierCommand.literalArgumentBuilder("permission")
                            .then(
                                BrigadierCommand.literalArgumentBuilder("add")
                                    .then(
                                        BrigadierCommand.requiredArgumentBuilder(
                                                "permission",
                                                StringArgumentType.word(),
                                            )
                                            .suggests(suggestions.permission())
                                            .executes { context ->
                                                val groupName =
                                                    StringArgumentType.getString(context, "group")
                                                val permission =
                                                    StringArgumentType.getString(
                                                        context,
                                                        "permission",
                                                    )
                                                result(
                                                    groupHandler.permissionAdd(
                                                        context.source,
                                                        groupName,
                                                        permission,
                                                        null,
                                                    )
                                                )
                                            }
                                            .then(
                                                BrigadierCommand.requiredArgumentBuilder(
                                                        "duration",
                                                        StringArgumentType.word(),
                                                    )
                                                    .suggests(suggestions.duration())
                                                    .executes { context ->
                                                        val groupName =
                                                            StringArgumentType.getString(
                                                                context,
                                                                "group",
                                                            )
                                                        val permission =
                                                            StringArgumentType.getString(
                                                                context,
                                                                "permission",
                                                            )
                                                        val duration =
                                                            StringArgumentType.getString(
                                                                context,
                                                                "duration",
                                                            )
                                                        result(
                                                            groupHandler.permissionAdd(
                                                                context.source,
                                                                groupName,
                                                                permission,
                                                                duration,
                                                            )
                                                        )
                                                    }
                                            )
                                    )
                            )
                            .then(
                                BrigadierCommand.literalArgumentBuilder("remove")
                                    .then(
                                        BrigadierCommand.requiredArgumentBuilder(
                                                "permission",
                                                StringArgumentType.word(),
                                            )
                                            .suggests(suggestions.permission())
                                            .executes { context ->
                                                val groupName =
                                                    StringArgumentType.getString(context, "group")
                                                val permission =
                                                    StringArgumentType.getString(
                                                        context,
                                                        "permission",
                                                    )
                                                result(
                                                    groupHandler.permissionRemove(
                                                        context.source,
                                                        groupName,
                                                        permission,
                                                    )
                                                )
                                            }
                                    )
                            )
                    )
            )

    private fun resolvePlayerIdOrSend(
        context: com.mojang.brigadier.context.CommandContext<CommandSource>
    ): UUID? {
        val raw = StringArgumentType.getString(context, PLAYER_ARGUMENT)
        return resolvePlayerIdOrSend(context.source, raw)
    }

    private fun resolvePlayerIdOrSend(source: CommandSource, raw: String): UUID? {
        val playerId = context.resolvePlayerId(source, raw)
        if (playerId == null) {
            source.sendMessage(Component.text("Unknown player or UUID."))
        }
        return playerId
    }

    private fun result(success: Boolean): Int {
        return if (success) {
            Command.SINGLE_SUCCESS
        } else {
            0
        }
    }

    companion object {
        private const val PLAYER_ARGUMENT = "player"
    }
}
