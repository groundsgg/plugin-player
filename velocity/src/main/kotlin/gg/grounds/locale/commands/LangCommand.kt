package gg.grounds.locale.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import gg.grounds.locale.PlayerLocaleCache
import gg.grounds.locale.SupportedLanguages
import gg.grounds.presence.PlayerPresenceService
import java.util.concurrent.CompletableFuture
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.slf4j.Logger

/**
 * `/lang` — pick the language messages are shown in.
 *
 * `/lang` alone lists the choices; `/lang <code>` sets one. The cache is updated first, so the
 * change is felt on the next message, and the durable write to service-player runs off-thread
 * afterwards — a slow or failed database round-trip must not freeze the command, and the choice
 * still holds for the session even if the write is lost.
 */
object LangCommand {

    fun create(
        cache: PlayerLocaleCache,
        presence: PlayerPresenceService,
        logger: Logger,
    ): BrigadierCommand {
        val node =
            LiteralArgumentBuilder.literal<CommandSource>("lang")
                .executes { ctx ->
                    ctx.source.sendMessage(usage())
                    1
                }
                .then(
                    RequiredArgumentBuilder.argument<CommandSource, String>(
                            "code",
                            StringArgumentType.word(),
                        )
                        .suggests { _, builder ->
                            SupportedLanguages.ALL.keys.forEach(builder::suggest)
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val player = ctx.source as? Player
                            if (player == null) {
                                ctx.source.sendMessage(
                                    Component.text(
                                        "Only players can use this command.",
                                        NamedTextColor.RED,
                                    )
                                )
                                return@executes 1
                            }

                            val code = StringArgumentType.getString(ctx, "code").lowercase()
                            val locale = SupportedLanguages.parse(code)
                            if (locale == null) {
                                player.sendMessage(
                                    Component.text(
                                        "Unknown language '$code'. Available: ${available()}",
                                        NamedTextColor.RED,
                                    )
                                )
                                return@executes 1
                            }

                            cache.set(player.uniqueId, locale)
                            player.sendMessage(
                                Component.text("Language set to $code.", NamedTextColor.GREEN)
                            )
                            CompletableFuture.runAsync {
                                if (!presence.setLocale(player.uniqueId, code)) {
                                    logger.warn(
                                        "Failed to persist locale (player={}, code={})",
                                        player.uniqueId,
                                        code,
                                    )
                                }
                            }
                            1
                        }
                )

        return BrigadierCommand(node.build())
    }

    private fun usage(): Component =
        Component.text("Usage: /lang <code>. Available: ${available()}", NamedTextColor.YELLOW)

    private fun available(): String = SupportedLanguages.ALL.keys.joinToString(", ")
}
