package gg.grounds.locale.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import gg.grounds.PlayerMessage
import gg.grounds.i18n.Translations
import gg.grounds.locale.PlayerLocaleCache
import gg.grounds.locale.SupportedLanguages
import gg.grounds.presence.PlayerPresenceService
import java.util.concurrent.CompletableFuture
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
        messages: Translations,
    ): BrigadierCommand {
        val node =
            LiteralArgumentBuilder.literal<CommandSource>("lang")
                .executes { ctx ->
                    messages.send(ctx.source, PlayerMessage.LANG_USAGE, "available" to available())
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
                                messages.send(ctx.source, PlayerMessage.LANG_PLAYERS_ONLY)
                                return@executes 1
                            }

                            val code = StringArgumentType.getString(ctx, "code").lowercase()
                            val locale = SupportedLanguages.parse(code)
                            if (locale == null) {
                                messages.send(
                                    player,
                                    PlayerMessage.LANG_UNKNOWN,
                                    "code" to code,
                                    "available" to available(),
                                )
                                return@executes 1
                            }

                            cache.choose(player, locale)
                            messages.send(player, PlayerMessage.LANG_SET, "code" to code)
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

    private fun available(): String = SupportedLanguages.ALL.keys.joinToString(", ")
}
