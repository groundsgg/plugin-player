package gg.grounds.link

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import gg.grounds.PlayerMessage
import gg.grounds.i18n.Translations
import net.kyori.adventure.text.event.ClickEvent
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

    fun create(client: ForgeLinkClient, messages: Translations, logger: Logger): BrigadierCommand {
        val node =
            LiteralArgumentBuilder.literal<CommandSource>("link").executes { ctx ->
                val player = ctx.source as? Player
                if (player == null) {
                    messages.send(ctx.source, PlayerMessage.LINK_PLAYERS_ONLY)
                    return@executes 1
                }

                messages.send(player, PlayerMessage.LINK_WORKING)

                client
                    .startLink(player.uniqueId)
                    .thenAccept { url ->
                        val link =
                            messages
                                .render(PlayerMessage.LINK_CLICK, player)
                                .clickEvent(ClickEvent.openUrl(url))
                        messages.send(player, PlayerMessage.LINK_PROMPT, "link" to link)
                    }
                    .exceptionally { err ->
                        // The player gets a flat "couldn't do it"; the reason is ours to debug.
                        logger.warn(
                            "Failed to start Minecraft link (player={})",
                            player.uniqueId,
                            err,
                        )
                        messages.send(player, PlayerMessage.LINK_FAILED)
                        null
                    }
                1
            }

        return BrigadierCommand(node.build())
    }

    fun createUnlink(
        client: ForgeLinkClient,
        messages: Translations,
        logger: Logger,
    ): BrigadierCommand {
        val node =
            LiteralArgumentBuilder.literal<CommandSource>("unlink").executes { ctx ->
                val player = ctx.source as? Player
                if (player == null) {
                    messages.send(ctx.source, PlayerMessage.LINK_PLAYERS_ONLY)
                    return@executes 1
                }

                client
                    .unlink(player.uniqueId)
                    .thenRun { messages.send(player, PlayerMessage.UNLINK_DONE) }
                    .exceptionally { err ->
                        logger.warn("Failed to unlink (player={})", player.uniqueId, err)
                        messages.send(player, PlayerMessage.LINK_FAILED)
                        null
                    }
                1
            }

        return BrigadierCommand(node.build())
    }
}
