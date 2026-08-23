package gg.grounds.edition

import java.lang.reflect.Method
import java.util.UUID
import org.slf4j.Logger

/**
 * Asks Floodgate whether a player came from Bedrock, without depending on Floodgate.
 *
 * Only one of the three proxies carries Floodgate — the Bedrock one — so a compile-time dependency
 * would put a class on the other two that can never resolve. Floodgate also publishes its API as a
 * moving `-SNAPSHOT` and nothing else, which is not a version this repo can pin. Reflection over
 * one method costs less than either, and `library-gui` already avoids the same dependency for the
 * same reason.
 *
 * [create] returns null when Floodgate is not installed, which is the ordinary state of the Java
 * proxies rather than an error worth logging loudly.
 *
 * **Why not just look at the UUID.** Floodgate builds an unlinked Bedrock player's UUID as `new
 * UUID(0, xuid)`, so the shape alone answers for them — which is what backends do today. It does
 * not answer for a player who has **linked** a Java account: they arrive under the linked account's
 * Mojang UUID and are shaped like anyone else. `isFloodgatePlayer` covers both, because it resolves
 * through `getPlayer(uuid)`, which falls back to scanning for a player whose `getCorrectUniqueId()`
 * matches. Confirmed in the bytecode of the build the network ships (`containers/plugin-floodgate`,
 * Floodgate 2.2.5 build 140), where that build also defaults to `enable-global-linking: true` — and
 * a global link is one the player may have made on any Geyser server, so linked players are not a
 * rare case.
 */
class FloodgateLookup
private constructor(
    private val logger: Logger,
    private val getInstance: Method,
    private val isFloodgatePlayer: Method,
) {

    @Volatile private var failed = false

    /**
     * Whether Floodgate knows this player as a Bedrock player.
     *
     * Answers false on any failure, and the direction is deliberate: false means "treat as Java",
     * which downstream means "keep checking them". The opposite default would let a Floodgate
     * hiccup quietly exempt players from anti-cheat.
     */
    fun isBedrock(playerId: UUID): Boolean {
        if (failed) return false
        return try {
            // Resolved per call rather than cached: Floodgate's singleton is not guaranteed to
            // exist when this proxy wires its listeners, only by the time a player logs in.
            val api = getInstance.invoke(null) ?: return false
            isFloodgatePlayer.invoke(api, playerId) as Boolean
        } catch (e: ReflectiveOperationException) {
            // Once, not per login: a broken API would otherwise write a line for every player who
            // ever joins.
            failed = true
            logger.warn("Floodgate lookup failed; treating every player as Java from here on", e)
            false
        }
    }

    companion object {
        private const val API = "org.geysermc.floodgate.api.FloodgateApi"

        /** Null when Floodgate is not installed on this proxy. */
        fun create(logger: Logger): FloodgateLookup? =
            try {
                val api = Class.forName(API)
                FloodgateLookup(
                    logger,
                    api.getMethod("getInstance"),
                    api.getMethod("isFloodgatePlayer", UUID::class.java),
                )
            } catch (e: ClassNotFoundException) {
                null
            } catch (e: NoSuchMethodException) {
                // Floodgate is here but is not the API this expects. Say so: a silent null would
                // hide a version skew until someone wondered why Bedrock players were being
                // flagged by anti-cheat.
                logger.warn("Floodgate is installed but {} is not the expected shape", API, e)
                null
            }
    }
}
