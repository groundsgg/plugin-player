package gg.grounds.link

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import gg.grounds.config.MessagesConfig
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.slf4j.Logger

/**
 * `/link` — connect a Microsoft account so friend sync can read the player's Minecraft friends, and
 * `/unlink` to revoke it.
 *
 * The consent has to happen in a browser (Microsoft will not hand out a token to a game client), so
 * the command's whole job is to fetch a URL from forge and put it in chat as something the player
 * can click.
 */
object LinkCommand {

    fun create(
        client: ForgeLinkClient,
        messages: MessagesConfig,
        logger: Logger,
    ): BrigadierCommand {
        val node =
            LiteralArgumentBuilder.literal<CommandSource>("link").executes { ctx ->
                val player = ctx.source as? Player
                if (player == null) {
                    ctx.source.sendMessage(
                        Component.text(messages.linkPlayersOnly, NamedTextColor.RED)
                    )
                    return@executes 1
                }

                player.sendMessage(Component.text(messages.linkWorking, NamedTextColor.GRAY))

                client
                    .startLink(player.uniqueId)
                    .thenAccept { url -> player.sendMessage(linkMessage(url, messages)) }
                    .exceptionally { err ->
                        // The player gets a flat "couldn't do it"; the reason is ours to debug.
                        logger.warn(
                            "Failed to start Minecraft link (player={})",
                            player.uniqueId,
                            err,
                        )
                        player.sendMessage(Component.text(messages.linkFailed, NamedTextColor.RED))
                        null
                    }
                1
            }

        return BrigadierCommand(node.build())
    }

    fun createUnlink(
        client: ForgeLinkClient,
        messages: MessagesConfig,
        logger: Logger,
    ): BrigadierCommand {
        val node =
            LiteralArgumentBuilder.literal<CommandSource>("unlink").executes { ctx ->
                val player = ctx.source as? Player
                if (player == null) {
                    ctx.source.sendMessage(
                        Component.text(messages.linkPlayersOnly, NamedTextColor.RED)
                    )
                    return@executes 1
                }

                client
                    .unlink(player.uniqueId)
                    .thenRun {
                        player.sendMessage(
                            Component.text(messages.unlinkDone, NamedTextColor.GREEN)
                        )
                    }
                    .exceptionally { err ->
                        logger.warn("Failed to unlink (player={})", player.uniqueId, err)
                        player.sendMessage(Component.text(messages.linkFailed, NamedTextColor.RED))
                        null
                    }
                1
            }

        return BrigadierCommand(node.build())
    }

    /**
     * The URL is long and single-use, so it is attached as a click target rather than pasted into
     * chat — a wrapped URL that the player has to retype by hand is not a link flow.
     */
    private fun linkMessage(url: String, messages: MessagesConfig): Component =
        Component.text(messages.linkPrompt, NamedTextColor.YELLOW)
            .append(Component.space())
            .append(
                Component.text(messages.linkClickHere, NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(url))
            )
}
