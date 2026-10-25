package gg.grounds.player.settings

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.key.Key

/**
 * Single-use click tokens, one set per player per shown dialog.
 *
 * The client builds the custom-click packet itself, so a static key like `grounds:menu/toggle/0`
 * can be sent by anyone, at any time, as often as they like. Each time a dialog is shown its clicks
 * are therefore issued as random tokens that only that player's current dialog knows. The first
 * accepted click consumes the whole set: a double click or a click on an outdated dialog resolves
 * to nothing, and every handler re-renders to issue the next set. Tokens say nothing about
 * permission; handlers still check what the renderer only shows.
 */
internal class ClickTokens(
    /**
     * What this set's tokens start with. Each menu needs its own: a handler cannot tell another
     * menu's token from a stale one of its own, so it treats every token it [owns] as consumed.
     */
    private val prefix: String = "menu/t/"
) {
    private val issued = ConcurrentHashMap<UUID, Map<String, Key>>()

    /**
     * Starts a new revision for [player], dropping all earlier tokens, and returns the mapping from
     * logical action to token for [BodyCanvas.render]. Equal actions share one token.
     */
    fun revision(player: UUID): (Key) -> Key {
        val tokens = ConcurrentHashMap<String, Key>()
        val byAction = ConcurrentHashMap<Key, Key>()
        issued[player] = tokens
        return { action ->
            byAction.computeIfAbsent(action) {
                Key.key(NAMESPACE, "$prefix${UUID.randomUUID()}").also {
                    tokens[it.value()] = action
                }
            }
        }
    }

    /**
     * The logical action behind [token], or null if it is unknown, stale or not [player]'s.
     * Accepting it ends the revision.
     */
    fun consume(player: UUID, token: Key): Key? {
        if (!owns(token)) return null
        val tokens = issued[player] ?: return null
        val action = tokens[token.value()] ?: return null
        // only one of two racing clicks can remove the set it found
        return if (issued.remove(player, tokens)) action else null
    }

    fun forget(player: UUID) {
        issued.remove(player)
    }

    fun owns(key: Key) = key.namespace() == NAMESPACE && key.value().startsWith(prefix)

    companion object {
        const val NAMESPACE = "grounds"
    }
}
