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
 * [create] treats a missing Floodgate class as positive evidence that this is a Java-only proxy. A
 * present but incompatible or failing API remains unknown instead.
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
    private val availability: Availability,
    private val getInstance: Method? = null,
    private val isFloodgatePlayer: Method? = null,
) {

    @Volatile private var failed = false

    /** True for Bedrock, false for known Java, and null when Floodgate cannot classify safely. */
    fun classify(playerId: UUID): Boolean? {
        if (availability == Availability.JAVA_ONLY) return false
        if (availability == Availability.UNKNOWN || failed) return null
        return try {
            // Resolved per call rather than cached: Floodgate's singleton is not guaranteed to
            // exist when this proxy wires its listeners, only by the time a player logs in.
            val api = getInstance!!.invoke(null) ?: return lookupFailed(playerId, null)
            isFloodgatePlayer!!.invoke(api, playerId) as? Boolean
                ?: return lookupFailed(playerId, null)
        } catch (e: ReflectiveOperationException) {
            // Once, not per login: a broken API would otherwise write a line for every player who
            // ever joins.
            lookupFailed(playerId, e)
        } catch (e: RuntimeException) {
            lookupFailed(playerId, e)
        }
    }

    /**
     * Whether Floodgate knows this player as a Bedrock player.
     *
     * Answers false on any failure, and the direction is deliberate: false means "treat as Java"
     * for anti-cheat, which keeps checking the player. Edition stamping uses [classify] so the same
     * failure cannot become an affirmative Java marker.
     */
    fun isBedrock(playerId: UUID): Boolean = classify(playerId) == true

    private fun lookupFailed(playerId: UUID, cause: Throwable?): Boolean? {
        failed = true
        if (cause == null) {
            logger.warn(
                "Floodgate lookup returned no classification for {}; edition stamping is disabled",
                playerId,
            )
        } else {
            logger.warn(
                "Floodgate lookup failed; edition stamping is disabled and anti-cheat will treat players as Java",
                cause,
            )
        }
        return null
    }

    companion object {
        private const val API = "org.geysermc.floodgate.api.FloodgateApi"

        /**
         * Creates a classifier on every proxy. Missing Floodgate means this is a Java-only proxy; a
         * present but incompatible API is unknown and must not produce a Java marker.
         */
        fun create(logger: Logger): FloodgateLookup = create(logger) { Class.forName(API) }

        internal fun create(logger: Logger, loadApi: () -> Class<*>): FloodgateLookup =
            try {
                val api = loadApi()
                FloodgateLookup(
                    logger,
                    Availability.FLOODGATE,
                    api.getMethod("getInstance"),
                    api.getMethod("isFloodgatePlayer", UUID::class.java),
                )
            } catch (e: ClassNotFoundException) {
                FloodgateLookup(logger, Availability.JAVA_ONLY)
            } catch (e: NoSuchMethodException) {
                // Floodgate is here but is not the API this expects. Say so: a silent null would
                // hide a version skew until someone wondered why Bedrock players were being
                // flagged by anti-cheat.
                logger.warn("Floodgate is installed but {} is not the expected shape", API, e)
                FloodgateLookup(logger, Availability.UNKNOWN)
            } catch (e: LinkageError) {
                logger.warn("Floodgate is installed but {} could not be loaded", API, e)
                FloodgateLookup(logger, Availability.UNKNOWN)
            } catch (e: SecurityException) {
                logger.warn("Floodgate is installed but {} cannot be inspected", API, e)
                FloodgateLookup(logger, Availability.UNKNOWN)
            }

        private enum class Availability {
            JAVA_ONLY,
            FLOODGATE,
            UNKNOWN,
        }
    }
}
