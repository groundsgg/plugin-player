package gg.grounds.edition.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.util.GameProfile
import gg.grounds.edition.FloodgateLookup
import org.slf4j.Logger

/**
 * Marks a Bedrock player's game profile so the backend can tell.
 *
 * Nothing downstream of this proxy otherwise knows. Geyser terminates the Bedrock session and
 * connects here as an ordinary Java client, and Velocity's modern forwarding carries a UUID, a
 * username, skin properties and an address — no edition. Backends make do with the shape of the
 * Floodgate UUID (`mostSignificantBits == 0`), which is right for unlinked players and silently
 * wrong for linked ones, who arrive under their Mojang UUID.
 *
 * A game profile property is the carrier because the forwarding payload signs it. Velocity HMACs
 * `(version, address, uuid, name, properties, key)` with the forwarding secret, and the properties
 * list is the one extensible part of it — so a backend that trusts the payload at all can trust
 * this. That matters more than convenience here: the flag turns anti-cheat *off*, so a marker the
 * client could set would be a self-exemption for any modified Java client. It is why the client
 * brand is not used, despite Geyser announcing itself as `Geyser` in it.
 *
 * **Timing.** `PostLoginEvent` fires after login and before the player is sent to a backend, so the
 * property is in the profile by the time Velocity builds any forwarding payload — including on
 * every later server switch, which rebuilds it from the same profile.
 *
 * **Ordering.** Floodgate's own skin applier also rewrites the property list, but it copies the
 * list and removes only `textures`, so it cannot drop this one whichever way round the two run.
 *
 * Registered on every proxy. Missing Floodgate identifies a Java-only proxy; a broken Floodgate API
 * remains unknown and cannot create an affirmative Java marker.
 */
class EditionStampListener(private val floodgate: FloodgateLookup, private val logger: Logger) {

    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        stamp(event.player)
    }

    private fun stamp(player: Player) {
        val edition =
            when (floodgate.classify(player.uniqueId)) {
                true -> BEDROCK
                false -> JAVA
                null -> null
            }
        val updated = withEditionProperty(player.gameProfileProperties, edition)
        if (updated == player.gameProfileProperties) return
        player.gameProfileProperties = updated
        if (edition == null) {
            logger.debug("Removed unverified edition marker from {}", player.username)
        } else {
            logger.debug(
                "Marked {} with authenticated edition ({}={})",
                player.username,
                PROPERTY,
                edition,
            )
        }
    }

    companion object {
        /**
         * Namespaced because it rides in a list Mojang also writes to — `textures` is theirs.
         *
         * Backends require one of the authenticated values written below.
         */
        const val PROPERTY = "grounds:edition"

        /** Authenticated Floodgate classification. */
        const val BEDROCK = "bedrock"

        /** Explicit Java classification written by proxies where Floodgate is absent or says no. */
        const val JAVA = "java"

        /**
         * The property list to write, or null when [existing] already carries the marker.
         *
         * A profile is per connection, so the marker should never already be there — but stamping
         * twice would put two of them on the wire, and a backend that read the first would be right
         * only by luck.
         *
         * Appends rather than replaces: the list is where Mojang's `textures` lives, and dropping
         * that would take the player's skin with it.
         */
        fun withEditionProperty(existing: List<GameProfile.Property>): List<GameProfile.Property>? =
            if (existing.any { it.name == PROPERTY }) null
            else existing + GameProfile.Property(PROPERTY, BEDROCK, "")

        /**
         * Replaces all untrusted edition markers with one authenticated [value]. A null value is an
         * unknown classification and therefore removes markers without adding one.
         */
        fun withEditionProperty(
            existing: List<GameProfile.Property>,
            value: String?,
        ): List<GameProfile.Property> {
            require(value == null || value == BEDROCK || value == JAVA) {
                "Unsupported edition marker: $value"
            }
            val withoutEdition = existing.filterNot { it.name == PROPERTY }
            return if (value == null) withoutEdition
            else withoutEdition + GameProfile.Property(PROPERTY, value, "")
        }
    }
}
