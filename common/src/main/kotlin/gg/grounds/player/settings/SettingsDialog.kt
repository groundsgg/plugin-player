package gg.grounds.player.settings

import java.util.UUID
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component

/**
 * The settings dialog, for each engine to build with its own dialog API: a notice whose body is one
 * plain message [width] px wide, and a [done] button that sends the custom click [close].
 *
 * Both engines must keep it open after a body click (after-action NONE): closing on every click
 * would re-grab the mouse and recentre the cursor until the next dialog arrives. With NONE the
 * client never closes it, so [done] closes it server-side.
 */
class SettingsDialog(
    val title: Component,
    val body: Component,
    val width: Int,
    val done: Component,
    val close: Key,
)

/** What [SettingsMenu] needs from the server engine it runs on. */
interface SettingsPlatform<P> {
    fun id(player: P): UUID

    fun show(player: P, dialog: SettingsDialog)

    fun close(player: P)

    fun sendPluginMessage(player: P, channel: String, message: ByteArray)
}

/**
 * What a server adds to `/settings`. The lobby registers one; servers without one show no live
 * regions and need nothing done when a language is saved.
 */
interface SettingsHooks {
    /** Region codes that are up right now, for the region tab's "online now" line. */
    fun liveRegions(): Collection<String>

    /** A language choice was stored; [tag] is null for "follow the client". */
    fun languageSaved(playerId: UUID, tag: String?)
}
